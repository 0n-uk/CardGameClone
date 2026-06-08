package cardGame;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class CardCollection extends JFrame {

	// --- Display constants ---
	// These define the size of card images and the layout of the gallery grid
	private static final int IMAGE_WIDTH = 150;
	private static final int IMAGE_HEIGHT = 150;
	private static final int CARDS_PER_ROW = 5; // number of cards per row in grid
	private static final int GAP = 15; // spacing between cards

	private final String username; // the current user's username
	private final List<cards> userCards = new ArrayList<>(); // list of user's card objects

	/**
	 * Constructor for the card collection window
	 * 
	 * @param username the user whose cards to display
	 */
	public CardCollection(String username) {
		this.username = username;

		// --- JFrame setup ---
		setTitle(username + "'s Card Collection");
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setSize(1100, 750);
		setLocationRelativeTo(null); // center on screen
		setLayout(new BorderLayout());
		setResizable(false);

		// === Top bar (header + buttons) ===
		JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
		topBar.setBackground(new Color(25, 25, 25)); // dark background

		// Title label
		JLabel titleLabel = new JLabel(username + "'s Collection");
		titleLabel.setForeground(Color.WHITE);
		titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
		topBar.add(titleLabel);

		// --- Buttons ---
		JButton backButton = new JButton("← Back to Hub");
		JButton removeButton = new JButton("Remove Card");
		JButton sortButton = new JButton("Sort");
		JButton buildDeckButton = new JButton("Build Deck"); // Opens deck builder

		// Style and add all buttons
		for (JButton btn : new JButton[] { backButton, removeButton, sortButton, buildDeckButton }) {
			btn.setBackground(Color.WHITE);
			btn.setForeground(Color.BLACK);
			btn.setFocusPainted(false);
			btn.setFont(new Font("Arial", Font.PLAIN, 13));
			topBar.add(btn);
		}
		add(topBar, BorderLayout.NORTH);

		// --- Button actions ---
		backButton.addActionListener(e -> {
			dispose(); // close current window
			new userHub(username); // open hub
		});

		buildDeckButton.addActionListener(e -> {
			dispose();
			new deckBuilder(username); // open deck builder window
		});

		// === Load user's cards from file or registration ===
		loadUserCollection();

		// Sort cards by ID for consistent order
		userCards.sort(Comparator.comparing(cards::getCardID));

		// Keep track of how many of each card the user has
		Map<String, Integer> cardCounts = new HashMap<>();

		// === Main gallery grid for displaying cards ===
		JPanel cardGrid = new JPanel(new GridLayout(0, CARDS_PER_ROW, GAP, GAP));
		cardGrid.setBackground(new Color(30, 30, 30));
		cardGrid.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

		if (userCards.isEmpty()) {
			// Display message if no cards
			JLabel emptyLabel = new JLabel("You have no cards yet!", SwingConstants.CENTER);
			emptyLabel.setForeground(Color.LIGHT_GRAY);
			emptyLabel.setFont(new Font("Arial", Font.BOLD, 18));
			cardGrid.add(emptyLabel);
		} else {
			// Build map of unique cards + count
			Map<String, cards> uniqueCards = new HashMap<>();

			for (cards c : userCards) {
				String id = c.getCardID();
				cardCounts.put(id, cardCounts.getOrDefault(id, 0) + 1);
				uniqueCards.putIfAbsent(id, c);
			}

			// Display only unique cards
			for (cards c : uniqueCards.values()) {
				int count = cardCounts.get(c.getCardID());
				cardGrid.add(createCardPanel(c, count));
			}

		}

		// === Scroll container to handle lots of cards ===
		JPanel container = new JPanel(new BorderLayout());
		container.setBackground(new Color(30, 30, 30));
		container.add(cardGrid, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(container);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		scrollPane.getViewport().setBackground(new Color(25, 25, 25));

		// 🔥 Make scrolling speed identical to deckBuilder
		scrollPane.getVerticalScrollBar().setUnitIncrement(10);

		add(scrollPane, BorderLayout.CENTER);

		setVisible(true); // show the window
	}

	/**
	 * Load the user's card collection from file and create card objects. If the
	 * user doesn't have a collection yet, register them.
	 */
	private void loadUserCollection() {
		try {
			UserCollectionSyncer.sync(); // ensure collection is up-to-date

			// Load all available cards
			Map<String, cards> allCards = cardLoader.loadAllCards();

			// Load the user's saved collection
			List<String> userCardIDs = cardLoader.loadUserCollection(username);

			// If no cards, register the user
			if (userCardIDs.isEmpty()) {
				cardLoader.registerUserCollection(username);
				userCardIDs = cardLoader.loadUserCollection(username);
			}

			// Convert each ID into a full card object
			for (String id : userCardIDs) {
				cards base = allCards.get(id.trim());
				if (base != null) {
					String imagePath = "/images/" + id.trim() + ".png";
					cards c = new cards(base.getName(), base.getSpecial(), base.getAtk(), base.getHp(), id.trim(),
							imagePath, base.getRarity());
					userCards.add(c);
				}
			}

			System.out.println("Loaded " + userCards.size() + " cards for " + username);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Create a JPanel representing a single card in the gallery. Clicking the card
	 * opens the zoomed-in CardDetailDialog.
	 */
	private JPanel createCardPanel(cards card, int count) {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setPreferredSize(new Dimension(160, 220));
		panel.setBackground(new Color(45, 45, 45));
		panel.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 2));

		// --------------------------------
		// CARD IMAGE
		// --------------------------------
		JLabel imageLabel = createCardImageLabel(card.getImageURL());
		imageLabel.setLayout(new BorderLayout()); // allow overlays
		panel.add(imageLabel, BorderLayout.CENTER);

		// --------------------------------
		// DUPLICATE BADGE (xN)
		// --------------------------------
		// --------------------------------
		// DUPLICATE BADGE (xN)
		// --------------------------------
		if (count > 1) {
			JLabel badge = new JLabel("x" + count);
			badge.setOpaque(true);
			badge.setBackground(new Color(0, 0, 0, 180));
			badge.setForeground(Color.WHITE);
			badge.setFont(new Font("Arial", Font.BOLD, 14));
			badge.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

			JPanel badgeContainer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
			badgeContainer.setOpaque(false);
			badgeContainer.add(badge);

			// ★ Move badge slightly upward
			badgeContainer.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
			// ↑ negative top padding moves it upward visually

			imageLabel.add(badgeContainer, BorderLayout.NORTH);
		}

		// --------------------------------
		// NAME LABEL
		// --------------------------------
		JLabel nameLabel = new JLabel(card.getName(), JLabel.CENTER);
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setFont(new Font("Arial", Font.BOLD, 14));
		panel.add(nameLabel, BorderLayout.NORTH);

		// --------------------------------
		// STATS LABEL
		// --------------------------------
		JLabel statsLabel = new JLabel("ATK: " + card.getAtk() + " | HP: " + card.getHp(), JLabel.CENTER);
		statsLabel.setForeground(Color.LIGHT_GRAY);
		statsLabel.setFont(new Font("Arial", Font.PLAIN, 12));
		panel.add(statsLabel, BorderLayout.SOUTH);

		// --------------------------------
		// HOVER + CLICK HANDLING
		// --------------------------------
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
				// Open zoomed-in dialog
				new CardDetailDialog(CardCollection.this, card, card.getName());
			}
		});

		return panel;
	}

	/**
	 * Load a card image from resources, with a fallback placeholder if missing.
	 */
	private JLabel createCardImageLabel(String imagePath) {
		java.net.URL imgURL = getClass().getResource(imagePath);
		ImageIcon icon;

		if (imgURL != null) {
			icon = new ImageIcon(imgURL);
		} else {
			java.net.URL placeholderURL = getClass().getResource("/images/placeholder.png");
			icon = new ImageIcon(placeholderURL);
		}

		Image scaled = icon.getImage().getScaledInstance(IMAGE_WIDTH, IMAGE_HEIGHT, Image.SCALE_SMOOTH);
		JLabel label = new JLabel(new ImageIcon(scaled));
		label.setHorizontalAlignment(JLabel.CENTER);
		return label;
	}

	// --- Popup card detail view (single active dialog) ---
	private static class CardDetailDialog extends JDialog {

		private static CardDetailDialog activeDialog = null;

		public CardDetailDialog(JFrame parent, cards card, String displayName) {

			if (activeDialog != null) {
				activeDialog.dispose();
			}
			activeDialog = this;

			super.setTitle(displayName);
			super.setModal(false);
			setLayout(new BorderLayout(20, 20));
			getContentPane().setBackground(new Color(25, 25, 25));

			// --- Card image ---
			JLabel imageLabel = new JLabel();
			java.net.URL imgURL = CardCollection.class.getResource(card.getImageURL());
			ImageIcon icon;

			if (imgURL != null) {
				icon = new ImageIcon(imgURL);
			} else {
				java.net.URL placeholderURL = CardCollection.class.getResource("/images/placeholder.png");
				icon = new ImageIcon(placeholderURL);
			}

			Image scaled = icon.getImage().getScaledInstance(300, 300, Image.SCALE_SMOOTH);
			imageLabel.setIcon(new ImageIcon(scaled));

			// --- Info panel ---
			JPanel infoPanel = new JPanel();
			infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
			infoPanel.setBackground(new Color(25, 25, 25));

			JLabel nameLabel = new JLabel(displayName);
			nameLabel.setForeground(Color.WHITE);
			nameLabel.setFont(new Font("Arial", Font.BOLD, 24));

			// -----------------------------------------------------
			// ★ NEW: RARITY STARS
			// -----------------------------------------------------
			// -----------------------------------------------------
			// ★ NEW: RARITY (Unicode stars with proper font support)
			// -----------------------------------------------------
			int rarity = card.getRarity();
			StringBuilder stars = new StringBuilder();

			for (int i = 0; i < rarity; i++) {
				stars.append("★ ");
			}

			JLabel rarityLabel = new JLabel(stars.toString());

			// Pick a font that supports ★
			Font starFont = null;
			String[] starFonts = { "Segoe UI Symbol", // Best on Windows
					"DejaVu Sans", // Best cross-platform
					"Arial Unicode MS", "Arial" };

			for (String f : starFonts) {
				starFont = new Font(f, Font.BOLD, 22);
				if (starFont.canDisplay('★')) {
					rarityLabel.setFont(starFont);
					break;
				}
			}

			rarityLabel.setForeground(new Color(255, 215, 0)); // gold
			// -----------------------------------------------------

			// -----------------------------------------------------

			JLabel atkLabel = new JLabel("Attack: " + card.getAtk());
			atkLabel.setForeground(new Color(180, 180, 180));
			atkLabel.setFont(new Font("Arial", Font.PLAIN, 18));

			JLabel hpLabel = new JLabel("Health: " + card.getHp());
			hpLabel.setForeground(new Color(180, 180, 180));
			hpLabel.setFont(new Font("Arial", Font.PLAIN, 18));

			// Format special text
			String[] words = card.getSpecial().split(" ");
			StringBuilder formatted = new StringBuilder(
					"<html><div style='width:230px;text-align:left;white-space:normal;'>");
			formatted.append("<b>Special:</b> ");
			for (int i = 0; i < words.length; i++) {
				formatted.append(words[i]).append(" ");
				if ((i + 1) % 12 == 0)
					formatted.append("<br>");
			}
			formatted.append("</div></html>");

			JLabel specialLabel = new JLabel(formatted.toString());
			specialLabel.setForeground(new Color(160, 160, 255));
			specialLabel.setFont(new Font("Arial", Font.ITALIC, 16));

			JButton closeButton = new JButton("Close");
			closeButton.setBackground(Color.LIGHT_GRAY);
			closeButton.setForeground(Color.BLACK);
			closeButton.setFocusPainted(false);
			closeButton.addActionListener(e -> dispose());

			// Add everything
			infoPanel.add(nameLabel);
			infoPanel.add(rarityLabel); // ★ Add rarity stars under name
			infoPanel.add(Box.createVerticalStrut(10));
			infoPanel.add(atkLabel);
			infoPanel.add(hpLabel);
			infoPanel.add(Box.createVerticalStrut(10));
			infoPanel.add(specialLabel);
			infoPanel.add(Box.createVerticalStrut(20));
			infoPanel.add(closeButton);

			add(imageLabel, BorderLayout.WEST);
			add(infoPanel, BorderLayout.CENTER);

			setSize(650, 420);
			setLocationRelativeTo(parent);

			addWindowListener(new java.awt.event.WindowAdapter() {
				@Override
				public void windowClosed(java.awt.event.WindowEvent e) {
					if (activeDialog == CardDetailDialog.this) {
						activeDialog = null;
					}
				}
			});

			setVisible(true);
		}
	}

	// === MAIN METHOD FOR TESTING ===
	public static void main(String[] args) {
		String testUsername = "jacob"; // sample user
		SwingUtilities.invokeLater(() -> new CardCollection(testUsername));
	}
}
