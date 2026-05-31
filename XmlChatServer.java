import org.w3c.dom.Document;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class XmlChatServer {
    public static void main(String[] args) throws IOException {
        ChatConfig config = ChatConfig.load("chat.properties", args);
        ChatLog log = new ChatLog(config.logging);
        ChatServerState<ClientHandler> state = new ChatServerState<>(config.historySize, log);

        try (ServerSocket serverSocket = new ServerSocket(config.port)) {
            log.info("XML-сервер запущен на порту " + config.port);
            while (true) {
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(config.clientTimeoutMillis);
                new Thread(new ClientHandler(socket, state, log), "xml-client").start();
            }
        }
    }

    private static class ClientHandler implements Runnable, ChatServerState.ClientEndpoint {
        private final Socket socket;
        private final ChatServerState<ClientHandler> state;
        private final ChatLog log;
        private DataOutputStream output;
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
                DataInputStream input = new DataInputStream(socket.getInputStream());
                output = new DataOutputStream(socket.getOutputStream());
                while (!socket.isClosed()) {
                    handle(XmlProtocol.read(input));
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

        private void handle(Document document) throws Exception {
            if (!"command".equals(XmlProtocol.rootName(document))) {
                send(XmlProtocol.error("Ожидалась команда"));
                return;
            }
            switch (XmlProtocol.attr(document, "name")) {
                case "login" -> {
                    name = XmlProtocol.text(document, "name");
                    type = XmlProtocol.text(document, "type");
                    if (type.isBlank()) {
                        type = "XMLClient";
                    }
                    ChatServerState.LoginResult<ClientHandler> result = state.login(this);
                    if (result.success) {
                        session = result.session;
                        send(XmlProtocol.success(result.session, null, result.history));
                        state.announceLogin(result.session);
                    } else {
                        send(XmlProtocol.error(result.error));
                    }
                }
                case "list" -> send(valid(XmlProtocol.text(document, "session"))
                        ? XmlProtocol.success(null, state.users(), null)
                        : XmlProtocol.error("Неверная сессия"));
                case "message" -> {
                    String requestSession = XmlProtocol.text(document, "session");
                    if (valid(requestSession)) {
                        state.message(requestSession, XmlProtocol.text(document, "message"));
                        send(XmlProtocol.success(null, null, null));
                    } else {
                        send(XmlProtocol.error("Неверная сессия"));
                    }
                }
                case "logout" -> {
                    String requestSession = XmlProtocol.text(document, "session");
                    if (valid(requestSession)) {
                        send(XmlProtocol.success(null, null, null));
                        state.logout(requestSession);
                    } else {
                        send(XmlProtocol.error("Неверная сессия"));
                    }
                }
                default -> send(XmlProtocol.error("Неизвестная команда"));
            }
        }

        private boolean valid(String requestSession) {
            return requestSession != null && requestSession.equals(session) && state.hasSession(requestSession);
        }

        private synchronized void send(Document document) throws Exception {
            XmlProtocol.write(output, document);
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
            try {
                send(XmlProtocol.event(event));
            } catch (Exception e) {
                throw new IOException(e);
            }
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
