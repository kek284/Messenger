import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class XmlChatTransport implements ChatTransport {
    private Socket socket;
    private DataInputStream input;
    private DataOutputStream output;
    private ChatListener listener;
    private String session;
    private final BlockingQueue<Document> responses = new LinkedBlockingQueue<>();

    @Override
    public void connect(String host, int port, String name, ChatListener listener) throws Exception {
        this.listener = listener;
        socket = new Socket(host, port);
        input = new DataInputStream(socket.getInputStream());
        output = new DataOutputStream(socket.getOutputStream());
        new Thread(this::readLoop, "xml-client-reader").start();

        Document login = XmlProtocol.command("login");
        XmlProtocol.append(login, login.getDocumentElement(), "name", name);
        XmlProtocol.append(login, login.getDocumentElement(), "type", "SwingXMLClient");
        send(login);

        Document response = takeResponse();
        ensureSuccess(response);
        session = XmlProtocol.text(response, "session");
        for (ChatEvent event : history(response)) {
            listener.onEvent(event);
        }
    }

    @Override
    public void sendMessage(String message) throws Exception {
        Document command = commandWithSession("message");
        XmlProtocol.append(command, command.getDocumentElement(), "message", message);
        send(command);
        ensureSuccess(takeResponse());
    }

    @Override
    public List<UserInfo> users() throws Exception {
        send(commandWithSession("list"));
        Document response = takeResponse();
        ensureSuccess(response);
        List<UserInfo> result = new ArrayList<>();
        NodeList users = response.getElementsByTagName("user");
        for (int i = 0; i < users.getLength(); i++) {
            Element user = (Element) users.item(i);
            result.add(new UserInfo(XmlProtocol.text(user, "name"), XmlProtocol.text(user, "type")));
        }
        return result;
    }

    @Override
    public void logout() throws Exception {
        if (session != null && !socket.isClosed()) {
            send(commandWithSession("logout"));
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
                Document document = XmlProtocol.read(input);
                if ("event".equals(XmlProtocol.rootName(document))) {
                    listener.onEvent(event(document));
                } else {
                    responses.offer(document);
                }
            }
        } catch (Exception e) {
            if (listener != null && session != null) {
                listener.onError("Соединение закрыто: " + e.getMessage());
            }
        }
    }

    private Document commandWithSession(String name) throws Exception {
        Document command = XmlProtocol.command(name);
        XmlProtocol.append(command, command.getDocumentElement(), "session", session);
        return command;
    }

    private synchronized void send(Document document) throws Exception {
        XmlProtocol.write(output, document);
    }

    private Document takeResponse() throws Exception {
        Document response = responses.poll(10, TimeUnit.SECONDS);
        if (response == null) {
            throw new IllegalStateException("Сервер не ответил");
        }
        return response;
    }

    private void ensureSuccess(Document document) {
        if ("error".equals(XmlProtocol.rootName(document))) {
            throw new IllegalStateException(XmlProtocol.text(document, "message"));
        }
    }

    private ChatEvent event(Document document) {
        String name = XmlProtocol.attr(document, "name");
        return switch (name) {
            case "message" -> new ChatEvent(ChatEvent.Kind.MESSAGE, XmlProtocol.text(document, "name"), XmlProtocol.text(document, "message"));
            case "userlogin" -> new ChatEvent(ChatEvent.Kind.USER_LOGIN, XmlProtocol.text(document, "name"), null);
            case "userlogout" -> new ChatEvent(ChatEvent.Kind.USER_LOGOUT, XmlProtocol.text(document, "name"), null);
            default -> new ChatEvent(ChatEvent.Kind.SYSTEM, null, XmlProtocol.text(document, "message"));
        };
    }

    private List<ChatEvent> history(Document document) {
        List<ChatEvent> result = new ArrayList<>();
        NodeList items = document.getElementsByTagName("item");
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            ChatEvent.Kind kind = ChatEvent.Kind.valueOf(item.getAttribute("kind"));
            result.add(new ChatEvent(kind, XmlProtocol.text(item, "name"), XmlProtocol.text(item, "message")));
        }
        return result;
    }
}
