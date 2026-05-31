import javax.swing.SwingUtilities;

public class ObjectChatClient {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatClientFrame("Object Chat Client", new ObjectChatTransport()).setVisible(true));
    }
}
