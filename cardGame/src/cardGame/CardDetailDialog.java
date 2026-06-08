package cardGame;

import javax.swing.*;
import java.awt.*;

public class CardDetailDialog extends JDialog {

    public CardDetailDialog(JFrame parent, cards card) {
        super(parent, card.getName(), true); // modal dialog

        // ------------------
        // Dialog properties
        // ------------------
        setLayout(new BorderLayout(20, 20));
        getContentPane().setBackground(new Color(30, 30, 30));
        setSize(650, 450);
        setLocationRelativeTo(parent);

        // ------------------
        // Card image
        // ------------------
        JLabel imageLabel = new JLabel();
        ImageIcon icon = new ImageIcon(card.getImageURL());
        Image scaled = icon.getImage().getScaledInstance(300, 300, Image.SCALE_SMOOTH);
        imageLabel.setIcon(new ImageIcon(scaled));
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(imageLabel, BorderLayout.WEST);

        // ------------------
        // Info panel
        // ------------------
        JPanel infoPanel = new JPanel();
        infoPanel.setBackground(new Color(30, 30, 30));
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));

        JLabel nameLabel = new JLabel(card.getName());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(new Font("Arial", Font.BOLD, 24));
        infoPanel.add(nameLabel);

        // -----------------------------------------------------
        // ★ NEW: RARITY STARS (same system as Collection dialog)
        // -----------------------------------------------------
        int rarity = card.getRarity();
        StringBuilder stars = new StringBuilder();

        for (int i = 0; i < rarity; i++) {
            stars.append("★ ");
        }

        JLabel rarityLabel = new JLabel(stars.toString());

        // Best font that can display stars
        Font starFont = null;
        String[] starFonts = {
            "Segoe UI Symbol",
            "DejaVu Sans",
            "Arial Unicode MS",
            "Arial"
        };

        for (String f : starFonts) {
            starFont = new Font(f, Font.BOLD, 22);
            if (starFont.canDisplay('★')) {
                rarityLabel.setFont(starFont);
                break;
            }
        }

        rarityLabel.setForeground(new Color(255, 215, 0)); // gold
        rarityLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Add the rarity underneath the name
        infoPanel.add(rarityLabel);

        infoPanel.add(Box.createVerticalStrut(10));
        // -----------------------------------------------------

        JLabel atkLabel = new JLabel("Attack: " + card.getAtk());
        atkLabel.setForeground(Color.LIGHT_GRAY);
        atkLabel.setFont(new Font("Arial", Font.PLAIN, 18));
        infoPanel.add(atkLabel);

        JLabel hpLabel = new JLabel("Health: " + card.getHp());
        hpLabel.setForeground(Color.LIGHT_GRAY);
        hpLabel.setFont(new Font("Arial", Font.PLAIN, 18));
        infoPanel.add(hpLabel);

        infoPanel.add(Box.createVerticalStrut(10));

        // ------------------
        // Special text (HTML formatted)
        // ------------------
        String[] words = card.getSpecial().split(" ");
        StringBuilder formatted = new StringBuilder(
                "<html><div style='width:230px;text-align:left;white-space:normal;'>");
        formatted.append("<b>Special:</b> ");
        for (int i = 0; i < words.length; i++) {
            formatted.append(words[i]).append(" ");
            if ((i + 1) % 12 == 0) formatted.append("<br>");
        }
        formatted.append("</div></html>");

        JLabel specialLabel = new JLabel(formatted.toString());
        specialLabel.setForeground(new Color(160, 160, 255));
        specialLabel.setFont(new Font("Arial", Font.ITALIC, 16));
        infoPanel.add(specialLabel);

        infoPanel.add(Box.createVerticalStrut(20));

        // ------------------
        // Close button
        // ------------------
        JButton closeButton = new JButton("Close");
        closeButton.setBackground(Color.LIGHT_GRAY);
        closeButton.setForeground(Color.BLACK);
        closeButton.setFocusPainted(false);
        closeButton.addActionListener(e -> dispose());
        infoPanel.add(closeButton);

        add(infoPanel, BorderLayout.CENTER);

        setVisible(true);
    }

}
