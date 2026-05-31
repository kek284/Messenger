import java.io.Serializable;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ChatEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    public enum Kind {
        MESSAGE, USER_LOGIN, USER_LOGOUT, SYSTEM
    }

    public final Kind kind;
    public final String name;
    public final String text;
    public final String time;

    public ChatEvent(Kind kind, String name, String text) {
        this.kind = kind;
        this.name = name;
        this.text = text;
        this.time = LocalTime.now().format(TIME);
    }

    public String format() {
        return switch (kind) {
            case MESSAGE -> "[" + time + "] " + name + ": " + text;
            case USER_LOGIN -> "[" + time + "] " + name + " подключился к чату";
            case USER_LOGOUT -> "[" + time + "] " + name + " вышел из чата";
            case SYSTEM -> "[" + time + "] " + text;
        };
    }
}
