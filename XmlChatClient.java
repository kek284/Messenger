import javax.swing.SwingUtilities;

public class XmlChatClient {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatClientFrame("XML Chat Client", new XmlChatTransport()).setVisible(true));
    }
}
