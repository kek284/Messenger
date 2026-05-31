import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ObjectChatTransport implements ChatTransport {
    private Socket socket;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    private ChatListener listener;
    private volatile String session;
    private final BlockingQueue<ObjectResponse> responses = new LinkedBlockingQueue<>();

    @Override
    public void connect(String host, int port, String name, ChatListener listener) throws Exception {
        this.listener = listener;
        socket = new Socket(host, port);
        output = new ObjectOutputStream(socket.getOutputStream());
        output.flush();
        input = new ObjectInputStream(socket.getInputStream());
        new Thread(this::readLoop, "object-client-reader").start();

        send(ObjectRequest.login(name, "SwingObjectClient"));
        ObjectResponse response = takeResponse();
        if (!response.success) {
            throw new IllegalStateException(response.error);
        }
        session = response.session;
        startHeartbeat();
        for (ChatEvent event : response.history) {
            listener.onEvent(event);
        }
    }

    @Override
    public void sendMessage(String message) throws Exception {
        send(ObjectRequest.message(session, message));
        ensureSuccess(takeResponse());
    }

    @Override
    public List<UserInfo> users() throws Exception {
        send(ObjectRequest.list(session));
        ObjectResponse response = takeResponse();
        ensureSuccess(response);
        return response.users;
    }

    @Override
    public void logout() throws Exception {
        if (session != null && !socket.isClosed()) {
            send(ObjectRequest.logout(session));
            takeResponse();
            session = null;
        }
        close();
    }

    @Override
    public void close() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception ignored) {
        }
    }

    private void readLoop() {
        try {
            while (!socket.isClosed()) {
                Object object = input.readObject();
                if (object instanceof ObjectResponse response) {
                    responses.offer(response);
                } else if (object instanceof ChatEvent event) {
                    listener.onEvent(event);
                }
            }
        } catch (Exception e) {
            if (listener != null && session != null) {
                listener.onError("Соединение закрыто: " + e.getMessage());
            }
        }
    }

    private void startHeartbeat() {
        Thread heartbeat = new Thread(() -> {
            while (session != null && socket != null && !socket.isClosed()) {
                try {
                    Thread.sleep(30000);
                    String currentSession = session;
                    if (currentSession != null && !socket.isClosed()) {
                        send(ObjectRequest.ping(currentSession));
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    if (listener != null && session != null) {
                        listener.onError("Соединение потеряно: " + e.getMessage());
                    }
                    close();
                    return;
                }
            }
        }, "object-heartbeat");
        heartbeat.setDaemon(true);
        heartbeat.start();
    }

    private synchronized void send(Object object) throws Exception {
        output.writeObject(object);
        output.flush();
    }

    private ObjectResponse takeResponse() throws Exception {
        ObjectResponse response = responses.poll(10, TimeUnit.SECONDS);
        if (response == null) {
            throw new IllegalStateException("Сервер не ответил");
        }
        return response;
    }

    private void ensureSuccess(ObjectResponse response) {
        if (!response.success) {
            throw new IllegalStateException(response.error);
        }
    }
}
