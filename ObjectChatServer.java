import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class ObjectChatServer {
    public static void main(String[] args) throws IOException {
        ChatConfig config = ChatConfig.load("chat.properties", args);
        ChatLog log = new ChatLog(config.logging);
        ChatServerState<ClientHandler> state = new ChatServerState<>(config.historySize, log);

        try (ServerSocket serverSocket = new ServerSocket(config.port)) {
            log.info("Object-сервер запущен на порту " + config.port);
            while (true) {
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(config.clientTimeoutMillis);
                new Thread(new ClientHandler(socket, state, log), "object-client").start();
            }
        }
    }

    private static class ClientHandler implements Runnable, ChatServerState.ClientEndpoint {
        private final Socket socket;
        private final ChatServerState<ClientHandler> state;
        private final ChatLog log;
        private ObjectOutputStream output;
        private String name;
        private String type;
        private String session;

        ClientHandler(Socket socket, ChatServerState<ClientHandler> state, ChatLog log) {
            this.socket = socket;
            this.state = state;
            this.log = log;
        }

        @Override
        public void run() {
            try (Socket ignored = socket) {
                output = new ObjectOutputStream(socket.getOutputStream());
                output.flush();
                ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
                while (!socket.isClosed()) {
                    Object object = input.readObject();
                    if (object instanceof ObjectRequest request) {
                        handle(request);
                    }
                }
            } catch (SocketTimeoutException e) {
                log.info("Клиент " + safeName() + " отключен по таймауту");
            } catch (EOFException ignored) {
                log.info("Клиент " + safeName() + " закрыл соединение");
            } catch (Exception e) {
                log.info("Соединение с " + safeName() + " завершено: " + e.getMessage());
            } finally {
                state.logoutClient(this);
            }
        }

        private void handle(ObjectRequest request) throws IOException {
            switch (request.command) {
                case "login" -> {
                    name = request.name;
                    type = request.type == null || request.type.isBlank() ? "ObjectClient" : request.type;
                    ChatServerState.LoginResult<ClientHandler> result = state.login(this);
                    if (result.success) {
                        session = result.session;
                        send(ObjectResponse.ok(result.session, null, result.history));
                        state.announceLogin(result.session);
                    } else {
                        send(ObjectResponse.error(result.error));
                    }
                }
                case "list" -> send(valid(request.session)
                        ? ObjectResponse.ok(null, state.users(), null)
                        : ObjectResponse.error("Неверная сессия"));
                case "message" -> {
                    if (valid(request.session)) {
                        state.message(request.session, request.message);
                        send(ObjectResponse.ok(null, null, null));
                    } else {
                        send(ObjectResponse.error("Неверная сессия"));
                    }
                }
                case "logout" -> {
                    if (valid(request.session)) {
                        send(ObjectResponse.ok(null, null, null));
                        state.logout(request.session);
                    } else {
                        send(ObjectResponse.error("Неверная сессия"));
                    }
                }
                default -> send(ObjectResponse.error("Неизвестная команда"));
            }
        }

        private boolean valid(String requestSession) {
            return requestSession != null && requestSession.equals(session) && state.hasSession(requestSession);
        }

        private synchronized void send(Object object) throws IOException {
            output.writeObject(object);
            output.flush();
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String type() {
            return type;
        }

        @Override
        public void sendEvent(ChatEvent event) throws IOException {
            send(event);
        }

        @Override
        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }

        private String safeName() {
            return name == null ? socket.getRemoteSocketAddress().toString() : name;
        }
    }
}
