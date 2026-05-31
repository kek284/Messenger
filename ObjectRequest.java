import java.io.Serializable;

public class ObjectRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String command;
    public final String name;
    public final String type;
    public final String session;
    public final String message;

    private ObjectRequest(String command, String name, String type, String session, String message) {
        this.command = command;
        this.name = name;
        this.type = type;
        this.session = session;
        this.message = message;
    }

    public static ObjectRequest login(String name, String type) {
        return new ObjectRequest("login", name, type, null, null);
    }

    public static ObjectRequest list(String session) {
        return new ObjectRequest("list", null, null, session, null);
    }

    public static ObjectRequest message(String session, String message) {
        return new ObjectRequest("message", null, null, session, message);
    }

    public static ObjectRequest logout(String session) {
        return new ObjectRequest("logout", null, null, session, null);
    }

    public static ObjectRequest ping(String session) {
        return new ObjectRequest("ping", null, null, session, null);
    }
}
