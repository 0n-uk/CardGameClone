package cardGame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class selectPack extends JFrame {

    private PackTimestampIO timeIO;

    private static final int PACK_WIDTH = 475;
    private static final int PACK_HEIGHT = 650;

    private final String username;

    private long lastOpenedTime = 0;
    private static final long COOLDOWN_DURATION = 12 * 60 * 60 * 1000; // 12 hours

    private JLabel cooldownClock;  // clock overlay icon
    private boolean packOpened = false; // tracks if pack has been opened this session

    public selectPack(String username) {

        this.username = username;
        
        probabilitor.initialize();

        // ====== LOAD TIMESTAMP ======
        this.timeIO = new PackTimestampIO(username);
        this.lastOpenedTime = timeIO.loadTimestamp();

        // Window config
        setTitle("Select a Pack");
        setSize(900, 1100);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(30, 30, 30));

        // ---------------------------------------------------------
        // TOP BAR
        // ---------------------------------------------------------
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(30, 30, 30));
        topBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.WHITE));

        JLabel title = new JLabel("New Pack for " + username);
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        title.setForeground(Color.WHITE);

        JLabel subtitle = new JLabel("Click the pack below to open it");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 15));
        subtitle.setForeground(Color.LIGHT_GRAY);

        JPanel titleBox = new JPanel(new GridLayout(2, 1));
        titleBox.setOpaque(false);
        titleBox.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 0));
        titleBox.add(title);
        titleBox.add(subtitle);

        JButton returnButton = new JButton("← Back to Hub");
        returnButton.setBackground(Color.WHITE);
        returnButton.setForeground(Color.BLACK);
        returnButton.setFocusPainted(false);
        returnButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        returnButton.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        returnButton.addActionListener(e -> {
            dispose();
            new userHub(username);
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        buttonPanel.setOpaque(false);
        buttonPanel.add(returnButton);

        topBar.add(titleBox, BorderLayout.WEST);
        topBar.add(buttonPanel, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // ---------------------------------------------------------
        // CENTER PANEL
        // ---------------------------------------------------------
        JPanel centerPanel = new JPanel();
        centerPanel.setBackground(new Color(30, 30, 30));
        centerPanel.setLayout(new GridBagLayout());
        add(centerPanel, BorderLayout.CENTER);

        // ---------------------------------------------------------
        // PACK PANEL WITH CLOCK OVERLAY
        // ---------------------------------------------------------
        JLayeredPane packLayer = new JLayeredPane();
        packLayer.setPreferredSize(new Dimension(PACK_WIDTH, PACK_HEIGHT));
        packLayer.setLayout(null);

        // Base pack panel
        JPanel packPanel = new JPanel();
        packPanel.setBounds(0, 0, PACK_WIDTH, PACK_HEIGHT);
        packPanel.setBackground(new Color(60, 60, 60));
        packPanel.setBorder(BorderFactory.createLineBorder(Color.WHITE, 2));
        packPanel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        packPanel.setLayout(new BorderLayout());
        packPanel.add(createPackImage("a_new_dawn.png"), BorderLayout.CENTER);

        packPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!packOpened) packPanel.setBackground(new Color(80, 80, 80));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!packOpened) packPanel.setBackground(new Color(60, 60, 60));
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (!packOpened) handlePackClick();
            }
        });

        packLayer.add(packPanel, JLayeredPane.DEFAULT_LAYER);

        // ------- CLOCK ICON IN TOP-RIGHT -------
        cooldownClock = createClockIcon();
        cooldownClock.setBounds(PACK_WIDTH - 90, 10, 80, 80); // top-right
        packLayer.add(cooldownClock, JLayeredPane.PALETTE_LAYER);

        // Show clock if in cooldown
        long now = System.currentTimeMillis();
        if (now - lastOpenedTime < COOLDOWN_DURATION) {
            cooldownClock.setVisible(true);
            packOpened = true; // already in cooldown, pack cannot be clicked
        } else {
            cooldownClock.setVisible(false);
        }

        centerPanel.add(packLayer);

        setVisible(true);
    }

    // ---------------------------------------------------------
    // PACK IMAGE
    // ---------------------------------------------------------
    private JLabel createPackImage(String fileName) {
        String path = "/packArt/" + fileName;
        java.net.URL imgURL = getClass().getResource(path);

        ImageIcon icon;
        if (imgURL != null) icon = new ImageIcon(imgURL);
        else icon = new ImageIcon(getClass().getResource("/packArt/placeholder.png"));

        Image scaled = icon.getImage().getScaledInstance(PACK_WIDTH, PACK_HEIGHT, Image.SCALE_SMOOTH);
        return new JLabel(new ImageIcon(scaled), JLabel.CENTER);
    }

    // ---------------------------------------------------------
    // CLOCK ICON CREATOR
    // ---------------------------------------------------------
    private JLabel createClockIcon() {
        String path = "/packArt/clock.png";
        java.net.URL imgURL = getClass().getResource(path);

        ImageIcon icon;
        if (imgURL != null) icon = new ImageIcon(imgURL);
        else icon = new ImageIcon(getClass().getResource("/packArt/placeholder.png"));

        Image scaled = icon.getImage().getScaledInstance(80, 80, Image.SCALE_SMOOTH);
        JLabel label = new JLabel(new ImageIcon(scaled));
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showCooldownRemaining();
            }
        });

        return label;
    }

    // ---------------------------------------------------------
    // CLOCK POPUP — SHOW REMAINING TIME
    // ---------------------------------------------------------
    private void showCooldownRemaining() {
        long now = System.currentTimeMillis();
        long diff = now - lastOpenedTime;

        long remaining = COOLDOWN_DURATION - diff;

        if (remaining <= 0) {
            JOptionPane.showMessageDialog(this, "Your next pack is ready!");
            cooldownClock.setVisible(false);
            packOpened = false; // allow clicking again after cooldown
            return;
        }

        long hours = remaining / (1000 * 60 * 60);
        long minutes = (remaining / (1000 * 60)) % 60;

        JOptionPane.showMessageDialog(
                this,
                "Next pack available in:\n" + hours + "h " + minutes + "m"
        );
    }

    // ---------------------------------------------------------
    // PACK CLICK HANDLER
    // ---------------------------------------------------------
    // ---------------------------------------------------------
    // PACK CLICK HANDLER
    // ---------------------------------------------------------
    private void handlePackClick() {

        long currentTime = System.currentTimeMillis();
        long timeSinceLast = currentTime - lastOpenedTime;

        if (timeSinceLast < COOLDOWN_DURATION) {
            long remaining = COOLDOWN_DURATION - timeSinceLast;
            long hours = remaining / (1000 * 60 * 60);
            long minutes = (remaining / (1000 * 60)) % 60;

            JOptionPane.showMessageDialog(
                    this,
                    "Pack already opened.\nCome back in " + hours + "h " + minutes + "m.",
                    "Cooldown Active",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Open this pack?",
                "Confirm",
                JOptionPane.YES_NO_OPTION
        );

        if (confirm == JOptionPane.YES_OPTION) {

            lastOpenedTime = currentTime;
            timeIO.saveTimestamp(currentTime);

            cooldownClock.setVisible(true);
            packOpened = true;

            // ---------------------------------------------------------
            // ★★★ PACK GENERATION USING probabilitor
            // ---------------------------------------------------------
            List<cards> pack = probabilitor.generatePack(8);

            // ---------------------------------------------------------
            // ★★★ SAVE PACK INTO USER COLLECTION
            // ---------------------------------------------------------
            List<String> cardIDs = new ArrayList<>();
            for (cards c : pack) {
                cardIDs.add(c.getCardID());
            }

            // Append the new cards to this user's stored collection
            cardLoader.saveUserCollection(username, cardIDs);

            // ---------------------------------------------------------
            // OPEN PACK OPENING FRAME
            // ---------------------------------------------------------
            new PackOpeningFrame(pack);
        }

    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new selectPack("jacob"));
    }
}
