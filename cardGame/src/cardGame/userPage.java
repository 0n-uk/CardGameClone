package cardGame;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import javax.swing.*;

/**
 * userPage — GUI login and registration system for The Card Game.
 * Users are stored in "users.txt" using the format:
 * username|password
 */
public class userPage extends JFrame {

    private JPanel loginPanel;
    private JLabel infoLabel;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JButton registerButton;

    private static final String DATA_FOLDER = "eclipse-workspace\\Personalstuff\\cardGame\\user_data";
    private static final String USER_FILE = DATA_FOLDER + "/users.txt";

    public userPage() {
        setTitle("The Card Game");
        setSize(700, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        mainPanel.setBackground(Color.BLACK);

        JLabel logoLabel = new JLabel("The Card Game", SwingConstants.CENTER);
        logoLabel.setForeground(Color.WHITE);
        logoLabel.setFont(new Font("SansSerif", Font.BOLD, 32));
        logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(logoLabel);

        JLabel welcomeLabel = new JLabel("Login to continue or register a new account.", SwingConstants.CENTER);
        welcomeLabel.setForeground(Color.WHITE);
        welcomeLabel.setFont(new Font("SansSerif", Font.ITALIC, 14));
        welcomeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(welcomeLabel);

        infoLabel = new JLabel("Enter your credentials below.", SwingConstants.CENTER);
        infoLabel.setForeground(Color.WHITE);
        infoLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        infoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(infoLabel);

        // --- Login panel ---
        loginPanel = new JPanel();
        loginPanel.setLayout(new BoxLayout(loginPanel, BoxLayout.Y_AXIS));
        loginPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        loginPanel.setBackground(Color.BLACK);

        JLabel userLabel = new JLabel("Username:");
        userLabel.setForeground(Color.WHITE);
        userLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginPanel.add(userLabel);

        usernameField = new JTextField();
        usernameField.setMaximumSize(new Dimension(300, 30));
        loginPanel.add(usernameField);
        loginPanel.add(Box.createVerticalStrut(10));

        JLabel passLabel = new JLabel("Password:");
        passLabel.setForeground(Color.WHITE);
        passLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginPanel.add(passLabel);

        passwordField = new JPasswordField();
        passwordField.setMaximumSize(new Dimension(300, 30));
        loginPanel.add(passwordField);
        loginPanel.add(Box.createVerticalStrut(5));

        JCheckBox showPassBox = new JCheckBox("Show Password");
        showPassBox.setForeground(Color.WHITE);
        showPassBox.setBackground(Color.BLACK);
        showPassBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        showPassBox.addActionListener(e -> passwordField.setEchoChar(showPassBox.isSelected() ? (char) 0 : '•'));
        loginPanel.add(showPassBox);
        loginPanel.add(Box.createVerticalStrut(10));

        loginButton = new JButton("Login");
        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        registerButton = new JButton("Register");
        registerButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        loginPanel.add(loginButton);
        loginPanel.add(Box.createVerticalStrut(8));
        loginPanel.add(registerButton);

        mainPanel.add(loginPanel);
        add(mainPanel);

        loginButton.addActionListener(e -> handleLogin());
        registerButton.addActionListener(e -> openRegistrationWindow());

        setVisible(true);

        // music player
        MusicPlayer.play("main_hub_theme.wav");
    }

    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword()).trim();

        if (username.isEmpty() || password.isEmpty()) {
            infoLabel.setText("Please enter both fields.");
            return;
        }

        Map<String, String> users = loadUsers();

        if (users.containsKey(username) && users.get(username).equals(password)) {
            infoLabel.setText("Login successful! Welcome, " + username + ".");
            
            // --- Sync user collections before opening hub ---
            UserCollectionSyncer.sync();

            dispose();
            new userHub(username);
        } else {
            infoLabel.setText("Invalid credentials. Try again or register.");
        }
    }

    private Map<String, String> loadUsers() {
        Map<String, String> users = new HashMap<>();

        try {
            File dataFolder = new File(DATA_FOLDER);
            if (!dataFolder.exists()) dataFolder.mkdirs();

            File file = new File(USER_FILE);
            if (!file.exists()) file.createNewFile();

            try (Scanner sc = new Scanner(file)) {
                while (sc.hasNextLine()) {
                    String line = sc.nextLine().trim();
                    if (line.isEmpty()) continue;
                    String[] parts = line.split("\\|", 2);
                    if (parts.length >= 2) {
                        users.put(parts[0], parts[1]);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return users;
    }

    private void openRegistrationWindow() {
        JFrame regFrame = new JFrame("Register");
        regFrame.setSize(400, 350);
        regFrame.setLocationRelativeTo(this);
        regFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        panel.setBackground(Color.BLACK);

        JLabel newUserLabel = new JLabel("Username:");
        newUserLabel.setForeground(Color.WHITE);
        newUserLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JTextField newUserField = new JTextField();
        newUserField.setMaximumSize(new Dimension(250, 30));
        newUserField.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel newPassLabel = new JLabel("Password:");
        newPassLabel.setForeground(Color.WHITE);
        newPassLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPasswordField newPassField = new JPasswordField();
        newPassField.setMaximumSize(new Dimension(250, 30));
        newPassField.setAlignmentX(Component.CENTER_ALIGNMENT);

        JCheckBox showPassBox = new JCheckBox("Show Password");
        showPassBox.setForeground(Color.WHITE);
        showPassBox.setBackground(Color.BLACK);
        showPassBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        showPassBox.addActionListener(e -> newPassField.setEchoChar(showPassBox.isSelected() ? (char) 0 : '•'));

        JLabel regInfo = new JLabel("", SwingConstants.CENTER);
        regInfo.setForeground(Color.WHITE);
        regInfo.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton registerConfirm = new JButton("Register");
        registerConfirm.setAlignmentX(Component.CENTER_ALIGNMENT);

        registerConfirm.addActionListener(e -> {
            String newUser = newUserField.getText().trim();
            String newPass = new String(newPassField.getPassword()).trim();

            if (newUser.isEmpty() || newPass.isEmpty()) {
                regInfo.setText("Fields cannot be empty.");
                return;
            }

            Map<String, String> users = loadUsers();
            if (users.containsKey(newUser)) {
                regInfo.setText("Username already exists.");
                return;
            }

            try {
                File userFile = new File(USER_FILE);
                userFile.getParentFile().mkdirs();
                if (!userFile.exists()) userFile.createNewFile();

                try (FileWriter fw = new FileWriter(userFile, true)) {
                    fw.write("=================================\n");
                    fw.write(String.format("%s|%s%n", newUser, newPass));
                    fw.write("=================================\n");
                    fw.flush();
                }
                
             // NEW — automatically create timestamp
                TimeStampManager.createUserIfMissing(newUser);


                // --- Sync user collections after registration ---
                UserCollectionSyncer.sync();

                regInfo.setText("Registration successful!");
            } catch (IOException ex) {
                regInfo.setText("Error saving user data.");
                ex.printStackTrace();
            }
        });

        panel.add(Box.createVerticalStrut(10));
        panel.add(newUserLabel);
        panel.add(Box.createVerticalStrut(5));
        panel.add(newUserField);
        panel.add(Box.createVerticalStrut(10));
        panel.add(newPassLabel);
        panel.add(Box.createVerticalStrut(5));
        panel.add(newPassField);
        panel.add(Box.createVerticalStrut(5));
        panel.add(showPassBox);
        panel.add(Box.createVerticalStrut(15));
        panel.add(registerConfirm);
        panel.add(Box.createVerticalStrut(10));
        panel.add(regInfo);

        regFrame.add(panel);
        regFrame.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(userPage::new);
    }
}
