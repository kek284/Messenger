import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ObjectResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    public final boolean success;
    public final String error;
    public final String session;
    public final List<UserInfo> users;
    public final List<ChatEvent> history;

    private ObjectResponse(boolean success, String error, String session, List<UserInfo> users, List<ChatEvent> history) {
        this.success = success;
        this.error = error;
        this.session = session;
        this.users = users == null ? List.of() : new ArrayList<>(users);
        this.history = history == null ? List.of() : new ArrayList<>(history);
    }

    public static ObjectResponse ok(String session, List<UserInfo> users, List<ChatEvent> history) {
        return new ObjectResponse(true, null, session, users, history);
    }

    public static ObjectResponse error(String error) {
        return new ObjectResponse(false, error, null, null, null);
    }
}
