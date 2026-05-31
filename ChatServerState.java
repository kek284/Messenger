import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ChatServerState<T extends ChatServerState.ClientEndpoint> {
    public interface ClientEndpoint {
        String name();

        String type();

        void sendEvent(ChatEvent event) throws IOException;

        void close();
    }

    private final int historySize;
    private final ChatLog log;
    private final Map<String, T> clientsBySession = new HashMap<>();
    private final Map<String, String> sessionsByName = new HashMap<>();
    private final Deque<ChatEvent> history = new ArrayDeque<>();

    public ChatServerState(int historySize, ChatLog log) {
        this.historySize = historySize;
        this.log = log;
    }

    public synchronized LoginResult<T> login(T client) {
        if (client.name() == null || client.name().isBlank()) {
            return LoginResult.error("Ник не может быть пустым");
        }
        if (sessionsByName.containsKey(client.name())) {
            return LoginResult.error("Ник уже занят");
        }
        String session = UUID.randomUUID().toString();
        clientsBySession.put(session, client);
        sessionsByName.put(client.name(), session);
        List<ChatEvent> oldHistory = history();
        log.info(client.name() + " вошел в чат, session=" + session);
        return LoginResult.ok(session, oldHistory);
    }

    public synchronized void announceLogin(String session) {
        T client = clientsBySession.get(session);
        if (client != null) {
            broadcast(new ChatEvent(ChatEvent.Kind.USER_LOGIN, client.name(), null));
        }
    }

    public synchronized boolean hasSession(String session) {
        return clientsBySession.containsKey(session);
    }

    public synchronized List<UserInfo> users() {
        List<UserInfo> users = new ArrayList<>();
        for (T client : clientsBySession.values()) {
            users.add(new UserInfo(client.name(), client.type()));
        }
        return users;
    }

    public synchronized void message(String session, String message) {
        T client = clientsBySession.get(session);
        if (client == null) {
            return;
        }
        ChatEvent event = new ChatEvent(ChatEvent.Kind.MESSAGE, client.name(), message);
        addHistory(event);
        log.info(client.name() + ": " + message);
        broadcast(event);
    }

    public synchronized void logout(String session) {
        T client = clientsBySession.remove(session);
        if (client == null) {
            return;
        }
        sessionsByName.remove(client.name());
        client.close();
        log.info(client.name() + " вышел из чата");
        broadcast(new ChatEvent(ChatEvent.Kind.USER_LOGOUT, client.name(), null));
    }

    public synchronized void logoutClient(T client) {
        String session = sessionsByName.get(client.name());
        if (session != null) {
            logout(session);
        } else {
            client.close();
        }
    }

    private void addHistory(ChatEvent event) {
        history.addLast(event);
        while (history.size() > historySize) {
            history.removeFirst();
        }
    }

    private List<ChatEvent> history() {
        return new ArrayList<>(history);
    }

    private void broadcast(ChatEvent event) {
        for (T client : new ArrayList<>(clientsBySession.values())) {
            try {
                client.sendEvent(event);
            } catch (IOException e) {
                log.info("Ошибка отправки клиенту " + client.name() + ": " + e.getMessage());
            }
        }
    }

    public static class LoginResult<T> {
        public final boolean success;
        public final String error;
        public final String session;
        public final List<ChatEvent> history;

        private LoginResult(boolean success, String error, String session, List<ChatEvent> history) {
            this.success = success;
            this.error = error;
            this.session = session;
            this.history = history;
        }

        public static <T> LoginResult<T> ok(String session, List<ChatEvent> history) {
            return new LoginResult<>(true, null, session, history);
        }

        public static <T> LoginResult<T> error(String error) {
            return new LoginResult<>(false, error, null, List.of());
        }
    }
}
