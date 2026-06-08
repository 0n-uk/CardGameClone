package cardGame;

import java.awt.*;
import javax.swing.*;

public class userHub extends JFrame {

    private JLabel infoLabel;

    public userHub(String username) {
        // ===== FRAME SETUP =====
        setTitle("The Card Game");
        setSize(1000, 1000);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        setLocationRelativeTo(null);

        // ===== MAIN HUB PANEL =====
        JPanel hubPanel = new JPanel();
        hubPanel.setLayout(new BoxLayout(hubPanel, BoxLayout.Y_AXIS));
        hubPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        hubPanel.setBackground(Color.BLACK);

        // ===== WELCOME LABEL =====
        JLabel logoLabel = new JLabel("Welcome, " + username + "!", SwingConstants.CENTER);
        logoLabel.setFont(new Font("SansSerif", Font.BOLD, 32));
        logoLabel.setForeground(Color.WHITE);
        logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        hubPanel.add(logoLabel);
        hubPanel.add(Box.createVerticalStrut(15));

        // ===== INFO LABEL =====
        infoLabel = new JLabel("Select an action below:", SwingConstants.CENTER);
        infoLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        infoLabel.setForeground(Color.WHITE);
        infoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        hubPanel.add(infoLabel);
        hubPanel.add(Box.createVerticalStrut(20));

        // ===== BUTTON GRID =====
        JPanel buttonPanel = new JPanel(new GridLayout(2, 2, 20, 20));
        buttonPanel.setBackground(Color.BLACK);

        JButton viewCardsButton = new JButton("View Card Collection");
        JButton openPacksButton = new JButton("Open Card Packs");
        JButton dailyMissionsButton = new JButton("Daily Missions");
        JButton battleButton = new JButton("Battle with Other Users");

        Font btnFont = new Font("SansSerif", Font.BOLD, 18);
        for (JButton b : new JButton[]{viewCardsButton, openPacksButton, dailyMissionsButton, battleButton}) {
            b.setFont(btnFont);
            b.setBackground(Color.WHITE);
            b.setFocusPainted(false);
            buttonPanel.add(b);
        }

        hubPanel.add(buttonPanel);
        hubPanel.add(Box.createVerticalStrut(40));

        // ===== LOGOUT BUTTON =====
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));
        bottomPanel.setBackground(Color.BLACK);
        bottomPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton logoutButton = new JButton("Logout");
        logoutButton.setFont(new Font("SansSerif", Font.BOLD, 16));

        bottomPanel.add(Box.createHorizontalGlue());
        bottomPanel.add(logoutButton);
        bottomPanel.add(Box.createHorizontalGlue());
        hubPanel.add(bottomPanel);
        
        add(hubPanel);
        setVisible(true);

        MusicPlayer.play("main_hub_theme.wav");

        // ===== ACTION LISTENERS — IMMEDIATE =====
        logoutButton.addActionListener(e -> {
            dispose();
            new userPage();
        });

        //coming soon
        viewCardsButton.addActionListener(e -> {
                infoLabel.setText("Card collection coming soon!");
        });

        openPacksButton.addActionListener(e -> {
                infoLabel.setText("Card packs coming soon!");   
        });

        dailyMissionsButton.addActionListener(e -> {
            infoLabel.setText("Daily missions coming soon!");
        });
        battleButton.addActionListener(e -> {
            String[] options = {"Host a Battle", "Join a Battle"};
            int choice = JOptionPane.showOptionDialog(
                    null,
                    "Do you want to host or join a battle?",
                    "Matchmaking",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0]
            );

            if (choice == JOptionPane.CLOSED_OPTION) return; // user canceled

            if (choice == 0) { // Host
                // 1. Launch the server in the background
                new Thread(() -> ChatServer.main(new String[0])).start();

                // 2. Close the UserHub window
                dispose();
                
                // 3. Launch BattleGUI as Player 1
                // (Real username is first, Player 2 is a placeholder)
                SwingUtilities.invokeLater(() ->
                        new BattleGUI(username, "Opponent", true)
                );

            } else if (choice == 1) { // Join
                // 1. Close the UserHub window
                dispose();
                
                // 2. Launch BattleGUI as Player 2
                // (Player 1 is a placeholder, Real username is SECOND)
                SwingUtilities.invokeLater(() ->
                        new BattleGUI("Host", username, false) 
                );
            }
        });

    }


    // ===== HELPER METHODS =====
    private void enableMainButtons(boolean enable, JButton... buttons) {
        for (JButton b : buttons) {
            b.setEnabled(enable);
        }
    }

}