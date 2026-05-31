import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ChatConfig {
    public final int port;
    public final int historySize;
    public final boolean logging;
    public final int clientTimeoutMillis;

    private ChatConfig(int port, int historySize, boolean logging, int clientTimeoutMillis) {
        this.port = port;
        this.historySize = historySize;
        this.logging = logging;
        this.clientTimeoutMillis = clientTimeoutMillis;
    }

    public static ChatConfig load(String fileName, String[] args) {
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(fileName)) {
            properties.load(input);
        } catch (IOException ignored) {
            System.out.println("Конфиг " + fileName + " не найден, используются значения по умолчанию.");
        }

        int port = integer(properties, "port", 5555);
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        return new ChatConfig(
                port,
                integer(properties, "historySize", 20),
                Boolean.parseBoolean(properties.getProperty("logging", "true")),
                integer(properties, "clientTimeoutMillis", 60000)
        );
    }

    private static int integer(Properties properties, String key, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
