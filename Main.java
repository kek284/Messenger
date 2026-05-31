import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatClientFrame("Chat Client", new ObjectChatTransport()).setVisible(true));
    }
}
