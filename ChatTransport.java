import java.util.List;

public interface ChatTransport {
    void connect(String host, int port, String name, ChatListener listener) throws Exception;

    void sendMessage(String message) throws Exception;

    List<UserInfo> users() throws Exception;

    void logout() throws Exception;

    void close();

    interface ChatListener {
        void onEvent(ChatEvent event);

        void onError(String message);
    }
}
