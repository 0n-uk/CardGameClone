package cardGame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class PackOpeningFrame extends JFrame {

	private final List<cards> packCards; // the 8 randomly chosen cards
	private static final int BACK_WIDTH = 200; // maximum width for pack-art back
	private static final int BACK_HEIGHT = 350; // maximum height for pack-art back
	private static final int CARD_WIDTH = 150; // front card size
	private static final int CARD_HEIGHT = 150;

	private int nextFlipIndex = 0; // enforce flipping order

	public PackOpeningFrame(List<cards> packCards) {
		this.packCards = packCards;

		// ---------- SORT CARDS BEFORE DISPLAY ----------
		packCards.sort((a, b) -> {
			int rarityCompare = Integer.compare(a.getRarity(), b.getRarity());
			if (rarityCompare != 0)
				return rarityCompare;

			double avgA = a.getAtk() + (a.getHp() / 2.0);
			double avgB = b.getAtk() + (b.getHp() / 2.0);

			return Double.compare(avgA, avgB);
		});

		setTitle("Pack Opened!");
		setSize(1000, 800);
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setLocationRelativeTo(null);
		setLayout(new BorderLayout());

		// ---------- TOP BAR ----------
		JPanel topBar = new JPanel(new BorderLayout());
		topBar.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		topBar.setBackground(new Color(30, 30, 30));

		JLabel title = new JLabel("Pack Opened!");
		title.setFont(new Font("SansSerif", Font.BOLD, 22));
		title.setForeground(Color.WHITE);

		JButton hubButton = new JButton("Return to Hub");
		hubButton.setBackground(Color.WHITE);
		hubButton.setForeground(Color.BLACK);
		hubButton.setFocusPainted(false);
		hubButton.addActionListener(e -> dispose());

		topBar.add(title, BorderLayout.WEST);
		topBar.add(hubButton, BorderLayout.EAST);

		add(topBar, BorderLayout.NORTH);

		// ---------- CARD GRID ----------
		JPanel grid = new JPanel(new GridLayout(2, 4, 20, 20));
		grid.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
		grid.setBackground(new Color(45, 45, 45));

		for (int i = 0; i < packCards.size(); i++) {
			JLabel cardLabel = createCardLabel(packCards.get(i), i);
			grid.add(cardLabel);
		}

		add(grid, BorderLayout.CENTER);

		getContentPane().setBackground(new Color(40, 40, 40));
		setVisible(true);
	}

	private JLabel createCardLabel(cards c, int index) {

	    JLabel label = new JLabel();
	    label.setHorizontalAlignment(SwingConstants.CENTER);
	    label.setVerticalAlignment(SwingConstants.CENTER);
	    label.putClientProperty("flipped", false);

	    // Load pack back image
	    java.net.URL backURL = getClass().getResource("/packArt/a_new_dawn.png");
	    ImageIcon backIcon = new ImageIcon(backURL);
	    Image scaledBack = getScaledImagePreserveAspect(backIcon.getImage(), BACK_WIDTH, BACK_HEIGHT);
	    label.setIcon(new ImageIcon(scaledBack));

	    label.addMouseListener(new MouseAdapter() {
	        @Override
	        public void mouseClicked(MouseEvent e) {

	            boolean flipped = (boolean) label.getClientProperty("flipped");

	            // ----------------------------------------------------
	            // PHASE 1: ENFORCE ORDER — ONLY UNTIL FLIP IS DONE
	            // ----------------------------------------------------
	            if (!flipped && index != nextFlipIndex) {
	                return;
	            }

	            // ----------------------------------------------------
	            // PHASE 2: FIRST CLICK — FLIP THE CARD
	            // ----------------------------------------------------
	            if (!flipped) {

	                // Build card panel (name + image + stats)
	                JPanel cardPanel = new JPanel(new BorderLayout());
	                cardPanel.setBackground(new Color(45, 45, 45));
	                cardPanel.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 2));

	                // ---- NAME ----
	                JLabel nameLabel = new JLabel(c.getName(), JLabel.CENTER);
	                nameLabel.setForeground(Color.WHITE);
	                nameLabel.setFont(new Font("Arial", Font.BOLD, 14));
	                cardPanel.add(nameLabel, BorderLayout.NORTH);
	                
	                

	                // ---- IMAGE ----
	                java.net.URL imgURL = getClass().getResource("/images/" + c.getCardID() + ".png");
	                ImageIcon icon = (imgURL != null)
	                        ? new ImageIcon(imgURL)
	                        : new ImageIcon(getClass().getResource("/images/placeholder.png"));

	                Image scaled = icon.getImage().getScaledInstance(CARD_WIDTH, CARD_HEIGHT, Image.SCALE_SMOOTH);
	                JLabel imageLabel = new JLabel(new ImageIcon(scaled));
	                imageLabel.setHorizontalAlignment(JLabel.CENTER);
	                cardPanel.add(imageLabel, BorderLayout.CENTER);

	                // ---- STATS ----
	                JLabel statsLabel = new JLabel("ATK: " + c.getAtk() + " | HP: " + c.getHp(), JLabel.CENTER);
	                statsLabel.setForeground(Color.LIGHT_GRAY);
	                statsLabel.setFont(new Font("Arial", Font.PLAIN, 12));
	                cardPanel.add(statsLabel, BorderLayout.SOUTH);

	                // Replace back-image with full card layout
	                label.setIcon(null);
	                label.setLayout(new BorderLayout());
	                label.add(cardPanel, BorderLayout.CENTER);

	                label.putClientProperty("flipped", true);
	                nextFlipIndex++;

	                return;
	            }

	            // ----------------------------------------------------
	            // PHASE 3: ALL CARDS FLIPPED → OPEN DETAIL DIALOG
	            // ----------------------------------------------------
	            if (nextFlipIndex == packCards.size()) {
	                new CardDetailDialog(PackOpeningFrame.this, c);
	            }

	        }
	    });

	    return label;
	}

	// Utility to scale image while preserving aspect ratio
	private Image getScaledImagePreserveAspect(Image srcImg, int maxWidth, int maxHeight) {

		int width = srcImg.getWidth(null);
		int height = srcImg.getHeight(null);

		double ratio = Math.min((double) maxWidth / width, (double) maxHeight / height);

		int newWidth = (int) (width * ratio);
		int newHeight = (int) (height * ratio);

		return srcImg.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
	}
}
