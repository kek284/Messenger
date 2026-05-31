import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ChatLog {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final boolean enabled;

    public ChatLog(boolean enabled) {
        this.enabled = enabled;
    }

    public void info(String message) {
        if (enabled) {
            System.out.println("[" + LocalDateTime.now().format(FORMAT) + "] " + message);
        }
    }
}
