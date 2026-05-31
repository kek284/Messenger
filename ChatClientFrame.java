import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

public class ChatClientFrame extends JFrame implements ChatTransport.ChatListener {
    private static final Color APP_BG = new Color(18, 24, 32);
    private static final Color PANEL_BG = new Color(28, 36, 48);
    private static final Color FIELD_BG = new Color(38, 48, 62);
    private static final Color CHAT_BG = new Color(14, 20, 29);
    private static final Color TEXT = new Color(229, 234, 241);
    private static final Color MUTED = new Color(139, 152, 170);
    private static final Color ACCENT = new Color(0, 184, 148);
    private static final Color DANGER = new Color(226, 91, 91);

    private final ChatTransport transport;
    private final JTextField hostField = new JTextField("localhost", 12);
    private final JSpinner portSpinner = new JSpinner(new SpinnerNumberModel(5555, 1, 65535, 1));
    private final JTextField nameField = new JTextField("user", 10);
    private final JButton connectButton = new JButton("Подключиться");
    private final JButton listButton = new JButton("Участники");
    private final JTextPane chatArea = new JTextPane();
    private final JTextField messageField = new JTextField();
    private final JButton sendButton = new JButton("Отправить");
    private boolean connected;

    public ChatClientFrame(String title, ChatTransport transport) {
        super(title);
        this.transport = transport;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(820, 560));
        setSize(900, 620);
        setLocationRelativeTo(null);
        buildUi();
        setConnected(false);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
            }
        });
    }

    private void buildUi() {
        getContentPane().setBackground(APP_BG);
        setLayout(new BorderLayout());
        setupStyles();

        chatArea.setEditable(false);
        chatArea.setBackground(CHAT_BG);
        chatArea.setBorder(new EmptyBorder(18, 18, 18, 18));
        chatArea.setCaretColor(TEXT);
        chatArea.setFont(new Font("SansSerif", Font.PLAIN, 15));

        JPanel top = new JPanel(new BorderLayout(18, 10));
        top.setBackground(PANEL_BG);
        top.setBorder(new EmptyBorder(16, 18, 16, 18));

        JLabel title = new JLabel("Messenger");
        title.setForeground(TEXT);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        actions.add(connectButton);
        actions.add(listButton);

        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        header.add(title, BorderLayout.WEST);
        header.add(actions, BorderLayout.EAST);

        JPanel controls = new JPanel(new GridBagLayout());
        controls.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 0, 8);
        gbc.anchor = GridBagConstraints.WEST;

        addControl(controls, gbc, label("Сервер"), false);
        addControl(controls, gbc, hostField, true);
        addControl(controls, gbc, label("Порт"), false);
        addControl(controls, gbc, portSpinner, false);
        addControl(controls, gbc, label("Ник"), false);
        addControl(controls, gbc, nameField, true);

        top.add(header, BorderLayout.NORTH);
        top.add(controls, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(12, 0));
        bottom.setBackground(PANEL_BG);
        bottom.setBorder(new EmptyBorder(14, 18, 14, 18));
        bottom.add(messageField, BorderLayout.CENTER);
        bottom.add(sendButton, BorderLayout.EAST);

        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(36, 46, 60)));
        scrollPane.getViewport().setBackground(CHAT_BG);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);

        styleField(hostField);
        styleField(nameField);
        styleField(messageField);
        styleSpinner(portSpinner);
        styleButton(connectButton, ACCENT);
        styleButton(listButton, new Color(77, 97, 124));
        styleButton(sendButton, ACCENT);

        add(top, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        connectButton.addActionListener(e -> {
            if (connected) {
                disconnect();
            } else {
                connect();
            }
        });
        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());
        listButton.addActionListener(e -> showUsers());
    }

    private void connect() {
        try {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                throw new IllegalStateException("Введите ник");
            }
            transport.connect(hostField.getText().trim(), (Integer) portSpinner.getValue(), name, this);
            append(new ChatEvent(ChatEvent.Kind.SYSTEM, null, "Подключено"));
            setConnected(true);
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void disconnect() {
        try {
            transport.logout();
        } catch (Exception ignored) {
            transport.close();
        }
        setConnected(false);
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (message.isEmpty()) {
            return;
        }
        if (!connected) {
            showError("Сначала подключитесь к серверу");
            return;
        }
        try {
            transport.sendMessage(message);
            messageField.setText("");
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void showUsers() {
        try {
            List<UserInfo> users = transport.users();
            JOptionPane.showMessageDialog(this, String.join("\n", users.stream().map(UserInfo::toString).toList()),
                    "Участники чата", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void setConnected(boolean connected) {
        this.connected = connected;
        hostField.setEnabled(!connected);
        portSpinner.setEnabled(!connected);
        nameField.setEnabled(!connected);
        messageField.setEnabled(true);
        messageField.setEditable(true);
        sendButton.setEnabled(connected);
        listButton.setEnabled(connected);
        connectButton.setText(connected ? "Отключиться" : "Подключиться");
        styleButton(connectButton, connected ? DANGER : ACCENT);
        if (connected) {
            messageField.requestFocusInWindow();
        }
    }

    @Override
    public void onEvent(ChatEvent event) {
        SwingUtilities.invokeLater(() -> append(event));
    }

    @Override
    public void onError(String message) {
        SwingUtilities.invokeLater(() -> {
            append(new ChatEvent(ChatEvent.Kind.SYSTEM, null, message));
            setConnected(false);
        });
    }

    private void append(ChatEvent event) {
        StyledDocument document = chatArea.getStyledDocument();
        try {
            switch (event.kind) {
                case MESSAGE -> {
                    document.insertString(document.getLength(), "[" + event.time + "] ", document.getStyle("time"));
                    document.insertString(document.getLength(), event.name + ": ", document.getStyle("author"));
                    document.insertString(document.getLength(), event.text + "\n", document.getStyle("message"));
                }
                case USER_LOGIN, USER_LOGOUT, SYSTEM ->
                        document.insertString(document.getLength(), event.format() + "\n", document.getStyle("system"));
            }
        } catch (BadLocationException ignored) {
        }
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Ошибка", JOptionPane.ERROR_MESSAGE);
    }

    private void setupStyles() {
        Style base = chatArea.addStyle("base", null);
        StyleConstants.setFontFamily(base, "SansSerif");
        StyleConstants.setFontSize(base, 15);

        Style message = chatArea.addStyle("message", base);
        StyleConstants.setForeground(message, TEXT);

        Style author = chatArea.addStyle("author", base);
        StyleConstants.setForeground(author, ACCENT);
        StyleConstants.setBold(author, true);

        Style time = chatArea.addStyle("time", base);
        StyleConstants.setForeground(time, MUTED);
        StyleConstants.setFontSize(time, 13);

        Style system = chatArea.addStyle("system", base);
        StyleConstants.setForeground(system, new Color(170, 183, 201));
        StyleConstants.setItalic(system, true);
    }

    private JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        label.setFont(new Font("SansSerif", Font.BOLD, 12));
        return label;
    }

    private void addControl(JPanel panel, GridBagConstraints gbc, Component component, boolean fill) {
        gbc.gridx++;
        gbc.weightx = fill ? 1 : 0;
        gbc.fill = fill ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        panel.add(component, gbc);
    }

    private void styleField(JTextField field) {
        field.setBackground(FIELD_BG);
        field.setForeground(TEXT);
        field.setCaretColor(TEXT);
        field.setSelectionColor(ACCENT.darker());
        field.setSelectedTextColor(Color.WHITE);
        field.setFont(new Font("SansSerif", Font.PLAIN, 14));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(55, 68, 86)),
                new EmptyBorder(8, 10, 8, 10)
        ));
    }

    private void styleSpinner(JSpinner spinner) {
        spinner.setFont(new Font("SansSerif", Font.PLAIN, 14));
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor defaultEditor) {
            styleField(defaultEditor.getTextField());
        }
    }

    private void styleButton(JButton button, Color color) {
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(9, 14, 9, 14));
        button.setFont(new Font("SansSerif", Font.BOLD, 13));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
}
