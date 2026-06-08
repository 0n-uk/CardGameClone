package cardGame;

import javax.swing.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import java.util.*;
import java.util.List;
import java.io.*;

/**
 * GUI class. Visuals are preserved from your original implementation.
 * This class now delegates logic to CardModel and I/O to DeckIO.
 */
public class deckBuilder extends JFrame {

    private static final int IMAGE_SIZE = 150;
    private static final int GAP = 15;
    private static final int COLLECTION_PANEL_HEIGHT = 500;
    private static final int DECK_PANEL_HEIGHT = 500;

    private final String username;
    private final CardModel model;
    private final DeckIO deckIO;

    private final List<cards> userCards = new ArrayList<>(); // will be populated from model
    private JPanel deckPanel;

    private final Map<String, JPanel> collectionPanels = new HashMap<>();

    public deckBuilder(String username) {
        this.username = username;
        this.model = new CardModel(username);
        this.deckIO = new DeckIO(username);

        // copy model's userCards into local reference for easy iteration (keeps UI code similar)
        this.userCards.addAll(model.getUserCards());

        setTitle(username + "'s Deck Builder");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1100, 1000);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // ===== HEADER =====
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(30, 30, 30));
        header.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JLabel title = new JLabel("Welcome, " + username);
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        title.setForeground(Color.WHITE);

        JLabel subtitle = new JLabel("Let's build a deck");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 16));
        subtitle.setForeground(Color.LIGHT_GRAY);

        JPanel titleBox = new JPanel(new GridLayout(2, 1));
        titleBox.setOpaque(false);
        titleBox.add(title);
        titleBox.add(subtitle);

        // --- Back, Save, and Load buttons ---
        JButton backButton = new JButton("← Back to Collection");
        backButton.addActionListener(e -> {
            dispose();
            new CardCollection(username);
        });

        JButton saveButton = new JButton("💾 Save Deck");
        saveButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        saveButton.addActionListener(e -> {
            String deckName = JOptionPane.showInputDialog(deckBuilder.this, "Enter a name for your deck:", "Save Deck",
                    JOptionPane.PLAIN_MESSAGE);

            if (deckName == null || deckName.trim().isEmpty())
                return;

            deckName = deckName.trim();
            if (deckIO.doesDeckExist(deckName)) {
                int choice = JOptionPane.showConfirmDialog(deckBuilder.this,
                        "A deck with this name already exists. Overwrite?", "Confirm Overwrite",
                        JOptionPane.YES_NO_OPTION);
                if (choice != JOptionPane.YES_OPTION)
                    return;
            }

            deckIO.saveDeck(deckName, model.getDeckCounts());
            JOptionPane.showMessageDialog(deckBuilder.this, "Deck saved successfully!", "Saved",
                    JOptionPane.INFORMATION_MESSAGE);
        });

        JButton deleteButton = new JButton("🗑 Delete Deck");
        deleteButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        deleteButton.addActionListener(e -> {
            String deckName = deckIO.showDeckDeletionDialog();
            if (deckName != null) {
                int confirm = JOptionPane.showConfirmDialog(deckBuilder.this,
                        "Are you sure you want to delete the deck \"" + deckName + "\"?", "Confirm Delete",
                        JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    deckIO.deleteDeck(deckName);
                }
            }
        });

        JButton loadButton = new JButton("📂 Load Deck");
        loadButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        loadButton.addActionListener(e -> {
            String deckName = deckIO.showDeckSelectionDialog();
            if (deckName != null) {
                // load list of ids then convert to counts
                List<String> ids = deckIO.loadDeck(deckName);
                Map<String, Integer> tempCounts = new HashMap<>();
                for (String id : ids) tempCounts.put(id, tempCounts.getOrDefault(id, 0) + 1);
                model.setDeckCounts(tempCounts);
                refreshDeckDisplay();
                JOptionPane.showMessageDialog(deckBuilder.this, "Deck \"" + deckName + "\" loaded successfully!",
                        "Loaded", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        
        JButton newDeckButton = new JButton("🆕 New Deck");
        newDeckButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        newDeckButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(deckBuilder.this,
                    "This will clear your current deck. Continue?", "Confirm New Deck",
                    JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                // 1. Clear the model
                model.clearDeck();

                // 2. Clear the deck panel UI
                deckPanel.removeAll();
                deckPanel.revalidate();
                deckPanel.repaint();

                // 3. Clear autosave so it doesn't reload old cards
                deckIO.saveDeck("autosave", new HashMap<>());

                JOptionPane.showMessageDialog(deckBuilder.this, "Deck cleared. Start building your new deck!",
                        "New Deck", JOptionPane.INFORMATION_MESSAGE);
            }
        });
     // --- Guide Button ---
        JButton guideButton = new JButton("?") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // draw white circle
                g2.setColor(Color.WHITE);
                g2.fillOval(0, 0, getWidth(), getHeight());
                
                // draw black question mark
                g2.setColor(Color.BLACK);
                g2.setFont(getFont().deriveFont(Font.BOLD, getHeight() * 0.6f));
                FontMetrics fm = g2.getFontMetrics();
                String text = "?";
                int x = (getWidth() - fm.stringWidth(text)) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(text, x, y);
                g2.dispose();
            }
        };

        guideButton.setPreferredSize(new Dimension(40, 40));
        guideButton.setFocusPainted(false);
        guideButton.setBorderPainted(false);
        guideButton.setContentAreaFilled(false);
        guideButton.setOpaque(false);

        // Action listener to show deck rules
        guideButton.addActionListener(e -> {
            String msg = "<html><b>Deck Building Rules:</b><br/>" +
                    "- Total deck size: 16 cards<br/>" +
                    "- 8 Weak cards: avg stats ≤ 6<br/>" +
                    "- 6 Strong cards: avg stats > 6.5 and ≤ 8<br/>" +
                    "- 2 Powerful cards: avg stats > 8.5<br/>" +
                    "- plus 2 spell or item cards<br/>" +
                    "Click on a card to add or remove it from your deck.</html>";
            JOptionPane.showMessageDialog(deckBuilder.this, msg, "Deck Guide", JOptionPane.INFORMATION_MESSAGE);
        });

       



        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(backButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(loadButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(newDeckButton);


        header.add(titleBox, BorderLayout.WEST);
        header.add(buttonPanel, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        // ===== MAIN SPLIT =====
        JPanel mainPanel = new JPanel(new GridLayout(1, 2, 25, 0));
        mainPanel.setBackground(Color.BLACK);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // === LEFT: COLLECTION ===
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(new Color(40, 40, 40));

        JLabel cardsLabel = new JLabel("Your Cards:", SwingConstants.CENTER);
        cardsLabel.setForeground(Color.WHITE);
        cardsLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        cardsLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        JPanel cardGrid = new JPanel(new GridLayout(0, 3, GAP, GAP));
        cardGrid.setBackground(new Color(45, 45, 45));

        Set<String> addedIds = new HashSet<>();
        for (cards c : userCards) {
            if (!addedIds.contains(c.getCardID())) {
                JPanel panel = createCardPanel(c, false);
                cardGrid.add(panel);
                collectionPanels.put(c.getCardID(), panel);
                addedIds.add(c.getCardID());
            }
        }

        JPanel collectionWrapper = new JPanel(new BorderLayout());
        collectionWrapper.setBackground(new Color(45, 45, 45));
        collectionWrapper.add(cardGrid, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(collectionWrapper);
        scroll.getVerticalScrollBar().setUnitIncrement(10);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setViewportView(collectionWrapper);
        scroll.setPreferredSize(new Dimension(0, COLLECTION_PANEL_HEIGHT));

        leftPanel.add(cardsLabel, BorderLayout.NORTH);
        leftPanel.add(scroll, BorderLayout.CENTER);

        // === RIGHT: DECK ===
        deckPanel = new JPanel(new GridLayout(0, 3, GAP, GAP));
        deckPanel.setBackground(new Color(45, 45, 45));

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(new Color(40, 40, 40));

        JLabel deckLabel = new JLabel("Your Deck:", SwingConstants.CENTER);
        deckLabel.setForeground(Color.WHITE);
        deckLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        deckLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        JPanel deckWrapper = new JPanel(new BorderLayout());
        deckWrapper.setBackground(new Color(45, 45, 45));
        deckWrapper.add(deckPanel, BorderLayout.NORTH);

        JScrollPane deckScroll = new JScrollPane(deckWrapper);
        deckScroll.getVerticalScrollBar().setUnitIncrement(16);
        deckScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        deckScroll.getViewport().setBackground(new Color(45, 45, 45));
        deckScroll.setPreferredSize(new Dimension(0, DECK_PANEL_HEIGHT));

        rightPanel.add(deckLabel, BorderLayout.NORTH);
        rightPanel.add(deckScroll, BorderLayout.CENTER);

        mainPanel.add(leftPanel);
        mainPanel.add(rightPanel);
        add(mainPanel, BorderLayout.CENTER);
        
        // Add guide button to rightPanel (deck side)
        JPanel guideWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        guideWrapper.setOpaque(false);
        guideWrapper.add(guideButton);
        rightPanel.add(guideWrapper, BorderLayout.SOUTH);

        // ===== AUTOSAVE WHEN WINDOW CLOSES =====
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                deckIO.saveDeck("autosave", model.getDeckCounts());
            }
        });

        // ===== LOAD LAST AUTOSAVE DECK ===== (attempt to load autosave if present)
        List<String> autos = deckIO.loadDeck("autosave");
        if (!autos.isEmpty()) {
            Map<String, Integer> tempCounts = new HashMap<>();
            for (String id : autos) tempCounts.put(id, tempCounts.getOrDefault(id, 0) + 1);
            model.setDeckCounts(tempCounts);
        }

        refreshDeckDisplay();

        setVisible(true);
    }

    // ----- Rebuild the deckPanel from model.getDeckCounts()
    private void refreshDeckDisplay() {
        deckPanel.removeAll();
        Map<String, Integer> deckCounts = model.getDeckCounts();
        for (Map.Entry<String, Integer> e : deckCounts.entrySet()) {
            String cardId = e.getKey();
            int count = e.getValue();
            cards card = model.findCardById(cardId);
            if (card != null) {
                for (int i = 0; i < count; i++) {
                    JPanel p = createCardPanel(card, true);
                    deckPanel.add(p);
                }
            }
        }
        deckPanel.revalidate();
        deckPanel.repaint();

        // repaint collection badges
        for (JPanel panel : collectionPanels.values()) panel.repaint();
    }

    // ===== CREATE CARD PANEL (visually identical to your original) =====
    private JPanel createCardPanel(cards card, boolean fromDeck) {
        JPanel panel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintChildren(Graphics g) {
                super.paintChildren(g);
                if (!fromDeck) {
                    int available = model.getCardCounts().getOrDefault(card.getCardID(), 0)
                            - model.getDeckCounts().getOrDefault(card.getCardID(), 0);

                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(Color.BLACK);
                    g2.fillOval(IMAGE_SIZE - 25, 5, 20, 20);
                    g2.setFont(new Font("SansSerif", Font.BOLD, 14));

                    if (available == 0) {
                        g2.setColor(Color.RED);
                    } else {
                        g2.setColor(Color.WHITE);
                    }

                    String text = String.valueOf(available);
                    FontMetrics fm = g2.getFontMetrics();
                    int textWidth = fm.stringWidth(text);
                    g2.drawString(text, IMAGE_SIZE - 15 - textWidth / 2, 20);
                    g2.dispose();
                }
            }
        };

        panel.setBackground(new Color(60, 60, 60));
        panel.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 2));
        panel.setPreferredSize(new Dimension(IMAGE_SIZE, IMAGE_SIZE + 40));

        JLabel imageLabel = new JLabel();
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imageLabel.setIcon(loadSquareImage(card.getImageURL()));
        
     // ---- STAR OVERLAY ----
        JLabel starOverlay = createStarOverlay(card);
        imageLabel.setLayout(null);  // allow absolute positioning

        int overlayWidth = 45;
        int overlayHeight = 30;

        starOverlay.setBounds(
            IMAGE_SIZE - overlayWidth - 5, 
            IMAGE_SIZE - overlayHeight - 5, 
            overlayWidth, 
            overlayHeight
        );

        imageLabel.add(starOverlay);



        JLabel nameLabel = new JLabel(card.getName(), SwingConstants.CENTER);
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(new Font("SansSerif", Font.BOLD, 13));

        panel.add(imageLabel, BorderLayout.CENTER);
        panel.add(nameLabel, BorderLayout.SOUTH);

        String wrappedSpecial = wrapText(card.getSpecial(), 7);
        panel.setToolTipText("<html><b>" + card.getName() + "</b><br/>" + "ATK: " + card.getAtk() + "<br/>" + "HP: "
                + card.getHp() + "<br/>" + "Special: " + wrappedSpecial + "<br/>" + "Click to "
                + (fromDeck ? "remove from deck" : "add to deck") + "</html>");

        if (!fromDeck) {
            panel.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    panel.setBorder(BorderFactory.createLineBorder(Color.WHITE, 3));
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    panel.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 2));
                }

              
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    String cardId = card.getCardID();
                    
                    int owned = model.getCardCounts().getOrDefault(cardId, 0);
                    int inDeck = model.getDeckCounts().getOrDefault(cardId, 0);
                    int available = owned - inDeck;

                    // 1. Check player-owned limit (your existing rule)
                    if (available <= 0) {
                        JOptionPane.showMessageDialog(deckBuilder.this,
                            "You do not have any more copies of this card to add!", 
                            "Deck Limit Reached", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    // 2. NEW: global max-duplicate limit per deck (2 copies)
                    int MAX_DUPLICATES = 2;
                    if (inDeck >= MAX_DUPLICATES) {
                        JOptionPane.showMessageDialog(deckBuilder.this,
                            "You can only have " + MAX_DUPLICATES + " copies of this card in a deck!", 
                            "Duplicate Limit", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }


                    if (!model.addToDeck(cardId)) {
                        JOptionPane.showMessageDialog(deckBuilder.this,
                            "Cannot add card due to deck size or category limits!", 
                            "Deck Limit Reached", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    // save to autosave after successful add
                    deckIO.saveDeck("autosave", model.getDeckCounts());

                    // refresh UI
                    refreshDeckDisplay();
                }

            });
        } else {
            panel.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    String cardId = card.getCardID();
                    if (model.removeFromDeck(cardId)) {
                        deckPanel.remove(panel);
                        deckPanel.revalidate();
                        deckPanel.repaint();
                        JPanel collectionPanel = collectionPanels.get(cardId);
                        if (collectionPanel != null) collectionPanel.repaint();
                    }
                }

                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    panel.setBorder(BorderFactory.createLineBorder(Color.RED, 3));
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    panel.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 2));
                }
            });
        }

        return panel;
    }
    
 // ===== Create STAR overlay (top-left, single star + number) =====
    private JLabel createStarOverlay(cards card) {
        int rarity = card.getRarity();

        if (rarity <= 0) {
            return new JLabel(); // no overlay for rarity 0
        }

        String starSymbol = "★";

        // -------- Font fallback --------
        Font starFont = null;
        String[] starFonts = {
            "Segoe UI Symbol",
            "DejaVu Sans",
            "Arial Unicode MS",
            "Arial"
        };

        for (String f : starFonts) {
            Font tryFont = new Font(f, Font.BOLD, 26);
            if (tryFont.canDisplay('★')) {
                starFont = tryFont;
                break;
            }
        }

        if (starFont == null)
            starFont = new Font("SansSerif", Font.BOLD, 26);

        // Text: "★ 3"
     // Text: "★ 3"
        HighlightLabel stars = new HighlightLabel(starSymbol + " " + rarity);
        stars.setFont(starFont);
        stars.setOpaque(false);

        // top-left alignment
        stars.setAlignmentX(0f);
        stars.setAlignmentY(0f);
        stars.setBounds(5, 5, 100, 40);

        
        // if using null layout, also set bounds:
        stars.setBounds(5, 5, 80, 40);

        return stars;
    }




    private String wrapText(String text, int wordsPerLine) {
        String[] words = text.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            sb.append(words[i]).append(" ");
            if ((i + 1) % wordsPerLine == 0)
                sb.append("<br/>");
        }
        return sb.toString().trim();
    }

   
    private ImageIcon loadSquareImage(String path) {
        try {
            // Load from classpath
            InputStream is = getClass().getResourceAsStream(path);

            // Fallback if the resource is missing
            if (is == null) {
                is = getClass().getResourceAsStream("/images/placeholder.png");
            }

            BufferedImage img = ImageIO.read(is);

            int size = Math.min(img.getWidth(), img.getHeight());
            int x = (img.getWidth() - size) / 2;
            int y = (img.getHeight() - size) / 2;

            BufferedImage cropped = img.getSubimage(x, y, size, size);
            Image scaled = cropped.getScaledInstance(IMAGE_SIZE, IMAGE_SIZE, Image.SCALE_SMOOTH);

            return new ImageIcon(scaled);
        } catch (Exception e) {
            e.printStackTrace();
            return new ImageIcon();
        }
    }


    private static class HighlightLabel extends JLabel {
        public HighlightLabel(String text) {
            super(text);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();

            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            String text = getText();
            FontMetrics fm = g2.getFontMetrics(getFont());

            int x = 0;
            int y = fm.getAscent();

            // --- Gold highlight / outline ---
            g2.setColor(new Color(0, 0, 0)); // GOLD
            g2.drawString(text, x + 1, y + 1); 
            g2.drawString(text, x - 1, y - 1);
            g2.drawString(text, x + 1, y - 1);
            g2.drawString(text, x - 1, y + 1);

            // --- Main black text ---
            g2.setColor(Color.WHITE);
            g2.drawString(text, x, y);

            g2.dispose();
        }
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new deckBuilder("jacob"));
    }
}
