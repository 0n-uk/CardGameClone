package cardGame;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;
import javax.swing.*;
import javax.swing.text.*;

public class ChatClientGUI {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private JFrame frame;
    private JTextPane chatArea;
    private JTextField inputField;
    private JScrollPane scrollPane;
    private JButton leaveButton;
    private String username;

    public ChatClientGUI(String serverIp, int port, String username) {
        this.username = username;
        setupGUI();
        connect(serverIp, port);
    }


    private void setupGUI() {
        frame = new JFrame("Chat - Logged in as: " + username);
        chatArea = new JTextPane();
        inputField = new JTextField();
        leaveButton = new JButton("Leave Chat");

        chatArea.setEditable(false);
        scrollPane = new JScrollPane(chatArea);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(leaveButton, BorderLayout.EAST);

        frame.setLayout(new BorderLayout());
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        frame.setSize(400, 500);
        frame.setLocationRelativeTo(null); 
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setVisible(true);

        inputField.addActionListener(e -> sendMessage());
        leaveButton.addActionListener(e -> leaveChat());

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                leaveChat();
            }
        });
    }

    private void connect(String serverIp, int port) {
        try {
            socket = new Socket(serverIp, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            new Thread(() -> {
                try {
                    String msg;
                    while ((msg = in.readLine()) != null) {
                        appendCenteredMessage(msg);
                    }
                } catch (IOException e) {
                    appendCenteredMessage("Disconnected from server.");
                }
            }).start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                    "Could not connect to: " + serverIp + ":" + port);
            returnToHub();
        }
    }


    private void sendMessage() {
        String text = inputField.getText().trim();
        if (!text.isEmpty() && out != null) {
            out.println(username + ": " + text);
            inputField.setText("");
        }
    }

    private void appendCenteredMessage(String message) {
        StyledDocument doc = chatArea.getStyledDocument();
        SimpleAttributeSet center = new SimpleAttributeSet();
        StyleConstants.setAlignment(center, StyleConstants.ALIGN_CENTER);
        
        try {
            doc.insertString(doc.getLength(), message + "\n", null);
            doc.setParagraphAttributes(doc.getLength() - 1, 1, center, false);
            chatArea.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void leaveChat() {
        try {
            if (out != null) out.println(username + " has left the chat.");
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
        returnToHub();
    }

    private void returnToHub() {
        if (frame != null) frame.dispose();
    }

  
}