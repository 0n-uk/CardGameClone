package cardGame;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;

public class BattleGUI extends JFrame {

	private BattleSystem battle;

	private JPanel playerFieldPanel = new JPanel(new GridLayout(2, 4, 10, 10));
	private JPanel enemyFieldPanel = new JPanel(new GridLayout(2, 4, 10, 10));
	private JPanel centerWrapper;
	private JPanel handPanel = new JPanel(new FlowLayout());
	private static final int IMAGE_SIZE = 120;
	private static final int IMAGE_WIDTH = 120;
	private static final int IMAGE_HEIGHT = 120;

	private static final int CARD_HEIGHT = 145;

	// === NEW: Generic Graveyard Auto-Place State ===
	// === NEW: Martyr Auto-Place Variables ===
		public boolean isGraveyardAutoPlaceMode = false;
		public int autoPlaceRow = -1;
		public int autoPlaceCol = -1;
		public BattleCard autoPlaceCaster = null;

	private JLabel turnLabel = new JLabel();

	// === MAKE BUTTONS GLOBAL ===
	private JButton drawBtn = new JButton("Draw");
	
	

	private JButton endTurnBtn = new JButton("End Turn");
	private JButton readyBtn = new JButton("Ready?");
	// === GAME OVER FLAG ===
	// === GAME OVER FLAG ===
	private boolean isGameOver = false;
	private boolean isGameOverTimerRunning = false; // <--- NEW: Network Sync Lock!
	// === UI STATE VARIABLES ===
	private cards selectedCardForPlacement = null;
	private BattleCard selectedAttacker = null;
	private boolean isTargetingMode = false;

	// === NEW: SPECIAL TARGETING VARIABLES ===
	private boolean isSpecialTargetingMode = false;
	private int requiredSpecialTargets = 0;
	private List<BattleCard> selectedSpecialTargets = new ArrayList<>();
	private BattleCard specialCaster = null;
	private String pendingSpecialCommand = "";

	// === NEW: Phase 2 Revive Placement Variables ===
	public static boolean isRevivePlacementMode = false;
	public static String pendingReviveId = "";

	// Add this map to hold your loaded specials
	private Map<String, String> allSpecials;
	// === NEW: Graveyard Targeting State ===
	public static boolean isGraveyardTargeting = false;
	public static BattleCard currentReviveCaster = null; // Remembers who cast the spell (The Magic Vase)

	// === TOGGLEABLE SETTINGS ===
	private int coinflipPopupDuration = 2500; // 10 seconds (in milliseconds). Change this anytime!
	// === GAME FLAGS ===

	private boolean matchStarted = false; // <--- ADD THIS LINE!
	// === POST-GAME MENU ===
	private JDialog postGameDialog;

	// === ADD LOCAL PLAYER FLAG ===
	private boolean isLocalPlayer1;
	private String localUsername;
	public int selectedHandIndex = -1; // -1 means nothing is selected
	// === NEW: Animation Queue & Game Over Lock System ===
	private int activeAnimations = 0;
	private java.util.List<Runnable> coinflipQueue = new java.util.ArrayList<>();
	private boolean isCoinflipPlaying = false;
	private java.util.concurrent.atomic.AtomicInteger pendingDeaths = new java.util.concurrent.atomic.AtomicInteger(0);

	public BattleGUI(String username, String username2, boolean isLocalPlayer1) {


		this.isLocalPlayer1 = isLocalPlayer1; // <--- THIS IS CRITICAL!
		// Load dictionaries

		// ... rest of constructor
		Map<String, cards> allCards = cardLoader.loadAllCards();
		this.allSpecials = cardLoader.loadSpecials(); // <--- NEW!

		// 1. Figure out who the local user is
		// 1. Figure out who the local user is AND save it globally!
		this.localUsername = isLocalPlayer1 ? username : username2;

		// 2. Prompt the local player to select their deck
		DeckIO localDeckIO = new DeckIO(this.localUsername);
		String selectedDeckName = localDeckIO.showDeckSelectionDialog();
		List<String> localIDs = localDeckIO.loadDeck(selectedDeckName);

		// 3. Enemy deck starts COMPLETELY EMPTY! The network will fill it.
		List<cards> localDeck = buildDeck(localIDs, allCards);
		List<cards> enemyDeck = new ArrayList<>();

		// 4. Assign them
		List<cards> deck1 = isLocalPlayer1 ? localDeck : enemyDeck;
		List<cards> deck2 = isLocalPlayer1 ? enemyDeck : localDeck;

		// 5. Pass allCards and isLocalPlayer1 into the backend
		battle = new BattleSystem(deck1, deck2, allCards, isLocalPlayer1);

		// === UPDATED: Lock the engine instantly before queuing the animation! ===
		battle.setCoinflipCallback((isHeads, isLocal) -> {
		    activeAnimations++; // <--- THE FIX: Locked instantly!
		    SwingUtilities.invokeLater(() -> {
		        coinflipQueue.add(() -> showCoinflipAnimation(isHeads, isLocal));
		        playNextCoinflip();
		    });
		});
		// === THE FIX: Give the engine the walkie-talkie to show toasts! ===
				battle.setToastCallback(msg -> SwingUtilities.invokeLater(() -> showToast(msg, 3000)));

		battle.setSpecialCoinflipCallback((isHeads, headsMsg, tailsMsg, onComplete) -> {
		    activeAnimations++; // <--- THE FIX: Locked instantly!
		    SwingUtilities.invokeLater(() -> {
		        coinflipQueue.add(() -> showSpecialCoinflipAnimation(isHeads, headsMsg, tailsMsg, onComplete));
		        playNextCoinflip();
		    });
		});

		String networkId = isLocalPlayer1 ? "P1" : "P2";

		battle.onDefensiveCoinflipRequest = (isHeads, hMsg, tMsg, onComplete) -> {
		    activeAnimations++; // <--- THE FIX: Locked instantly!
		    SwingUtilities.invokeLater(() -> {
		        coinflipQueue.add(() -> showSpecialCoinflipAnimation(isHeads, hMsg, tMsg, onComplete));
		        playNextCoinflip();
		    });
		};

		// 6. Pass localIDs to the network so it can broadcast them to the opponent
		battle.startNetwork(networkId, () -> {
		    SwingUtilities.invokeLater(() -> refreshBoard());
		}, localIDs);

		battle.setDisconnectCallback(() -> SwingUtilities.invokeLater(() -> {
			if (!isGameOver) {
				isGameOver = true;
				matchStarted = false;
				triggerGameOver("Opponent disconnected. You win!");
			}
		}));
		
		battle.allSpecials = this.allSpecials; // Now the engine can read the strings!

	
		// === NEW: Universal ON_DEATH Tripwire (Upgraded for Multi-Kills!) ===
		battle.setDeathCallback((deadCard, wasPlayer1) -> {
		    pendingDeaths.incrementAndGet(); // 1. Instantly lock the Game Over scanner!
		    
		    SwingUtilities.invokeLater(() -> {
		        String cardId = deadCard.getBaseCard().getCardID().trim();
		        String commandStr = allSpecials.get(cardId);

		        if (commandStr != null && commandStr.toUpperCase().contains("ON_DEATH")) {
		            boolean isMyCard = (wasPlayer1 == isLocalPlayer1);

		            if (isMyCard) {
		                // === THE FIX: Slice out ONLY the ON_DEATH segment before broadcasting! ===
		                String deathSlice = SpecialsLibrary.extractSegment(commandStr, "ON_DEATH");
		                
		                if (deathSlice != null) {
		                    String syncedCommand = loadTheDice(deathSlice);
		                    
		                    // === THE THREAD FIX: Broadcast FIRST, before the Modal Dialog freezes the thread! ===
		                    battle.broadcastSpecialExecution(deadCard, null, syncedCommand);
		                    SpecialsLibrary.parseAndExecute(battle, syncedCommand, true, deadCard, new java.util.ArrayList<>());
		                }
		            }
		        }
		        
		        // Unlock the scanner FIRST, then check the board!
		        pendingDeaths.decrementAndGet(); 
		        refreshBoard(); 
		    });
		});
		
		// Tell the engine what to do when it wants to force the Graveyard open
				battle.onForceGraveyardOpen = () -> showGraveyard();

		// === UPDATE THE TITLE SO YOU KNOW WHO IS WHO ===
		setTitle("Card Battle - " + (isLocalPlayer1 ? "PLAYER 1" : "PLAYER 2"));
		setSize(1000, 725);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setLayout(new BorderLayout());

		// This paints the "floor" of your main window black.
		// (If you already added this at the bottom from the last step,
		// it's actually better practice to move it up here!)
		getContentPane().setBackground(new Color(20, 20, 20));

		// Increased height to 400 to perfectly fit 2 rows of cards!
		int fieldHeight = 350;
		Dimension fieldSize = new Dimension(Integer.MAX_VALUE, fieldHeight);

		// Lock the Enemy Field
		enemyFieldPanel.setMinimumSize(fieldSize);
		enemyFieldPanel.setPreferredSize(fieldSize);
		enemyFieldPanel.setMaximumSize(fieldSize);

		// Lock the Player Field
		playerFieldPanel.setMinimumSize(fieldSize);
		playerFieldPanel.setPreferredSize(fieldSize);
		playerFieldPanel.setMaximumSize(fieldSize);

		// ... then proceed with your centerWrapper code

		// === TOP (Enemy Field) ===
		enemyFieldPanel.setBorder(BorderFactory.createTitledBorder("Enemy Field"));

		// === CENTER (Player Field) ===
		// === CENTER (Player Field) ===
		playerFieldPanel.setBorder(BorderFactory.createTitledBorder("Your Field"));
		centerWrapper = new JPanel(); // <--- Now it uses the global one!
		centerWrapper.setLayout(new BoxLayout(centerWrapper, BoxLayout.Y_AXIS));

		// 1. Add Enemy Field
		centerWrapper.add(enemyFieldPanel);

		// 2. Add the "Spring" (Expands to fill all empty space in the middle!)
		centerWrapper.add(Box.createVerticalGlue());

		// 3. Add Player Field (Pushed right up against the Hand panel)
		centerWrapper.add(playerFieldPanel);

		add(centerWrapper, BorderLayout.CENTER);

		// === BOTTOM (Hand + Buttons) ===
		JPanel bottomPanel = new JPanel(new BorderLayout());

		handPanel.setBorder(BorderFactory.createTitledBorder("Your Hand"));
		bottomPanel.add(handPanel, BorderLayout.CENTER);

		JPanel buttonPanel = new JPanel();
		// === NEW: Style the Ready Button ===
		readyBtn.setUI(new javax.swing.plaf.basic.BasicButtonUI());
		readyBtn.setBackground(new Color(100, 200, 100)); // Nice bright green!
		readyBtn.setForeground(Color.BLACK);
		readyBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
		readyBtn.setFocusPainted(false);

		readyBtn.addActionListener(e -> {
			readyBtn.setEnabled(false);
			readyBtn.setBackground(Color.GRAY);
			readyBtn.setText("Waiting...");
			battle.setReady();
		});

		// === NEW: Listen for Graveyard UI requests from the Library! ===
		battle.setGraveyardUICallback((caster) -> SwingUtilities.invokeLater(() -> {
			isGraveyardTargeting = true;
			currentReviveCaster = caster;
			showGraveyard();
		}));

		// Add the global buttons to the panel
		buttonPanel.add(readyBtn); // <--- Add this first!

		// Add the global buttons to the panel
		buttonPanel.add(drawBtn);

		buttonPanel.add(endTurnBtn);

		bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

		add(bottomPanel, BorderLayout.SOUTH);

		// === LEFT PANEL (Turn Label & Graveyard) ===
		JPanel leftPanel = new JPanel(new BorderLayout());
		leftPanel.setBackground(new Color(0, 0, 0)); // Match the dark background

		// Put the turn label at the top
		leftPanel.add(turnLabel, BorderLayout.NORTH);

		// Build the Graveyard Button
		java.net.URL placeholderURL = getClass().getResource("/packArt/skull.png");
		ImageIcon graveIcon = (placeholderURL != null) ? new ImageIcon(placeholderURL) : null;

		// Scale it down so it's not massive
		if (graveIcon != null) {
			Image graveScaled = graveIcon.getImage().getScaledInstance(80, 80, Image.SCALE_SMOOTH);
			graveIcon = new ImageIcon(graveScaled);
		}

		JButton graveyardBtn = new JButton(graveIcon);

		// === THE SILVER BULLET: Tell the button to ignore Windows/Mac native styling!
		// ===
		graveyardBtn.setUI(new javax.swing.plaf.basic.BasicButtonUI());

		// Now it will 100% respect your custom colors
		graveyardBtn.setBackground(Color.BLACK);
		graveyardBtn.setOpaque(true);

		graveyardBtn.setFocusPainted(false);
		graveyardBtn.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.GRAY), "Graveyard",
				0, 0, null, Color.WHITE));

		// Hook up the popup
		graveyardBtn.addActionListener(e -> showGraveyard());

		// Stick the button in the center of the left panel
		leftPanel.add(graveyardBtn, BorderLayout.CENTER);

		// Add the whole panel to the left side of the window!
		add(leftPanel, BorderLayout.WEST);

		// === APPLY GRAYSCALE PALETTE TO PANELS ===
		Color darkBackground = new Color(20, 20, 20); // Very dark gray, almost black
		Color fieldBackground = new Color(25, 25, 25); // Dark gray

		// Set wrapper and bottom panel backgrounds
		centerWrapper.setBackground(darkBackground);
		bottomPanel.setBackground(darkBackground);
		buttonPanel.setBackground(darkBackground);

		// Set field and hand backgrounds
		enemyFieldPanel.setBackground(fieldBackground);
		playerFieldPanel.setBackground(fieldBackground);
		handPanel.setBackground(fieldBackground);

		// Optional: Make the borders and text white so they show up on the dark
		// background
		enemyFieldPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.GRAY),
				"Enemy Field", 0, 0, null, Color.WHITE));
		playerFieldPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.GRAY),
				"Your Field", 0, 0, null, Color.WHITE));
		handPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.GRAY), "Your Hand", 0,
				0, null, Color.WHITE));
		turnLabel.setForeground(Color.WHITE);

		// === LOCK HAND PANEL HEIGHT ===
		// We make it around 220 pixels tall to comfortably fit a card + the titled
		// border
		Dimension handSize = new Dimension(Integer.MAX_VALUE, 220);
		handPanel.setPreferredSize(handSize);
		handPanel.setMinimumSize(handSize);

		// === FIX TURN LABEL BACKGROUND ===
		turnLabel.setForeground(Color.WHITE); // Keep text white
		turnLabel.setOpaque(true); // Force it to paint its own background
		turnLabel.setBackground(new Color(20, 20, 20)); // Set it to your dark background color

		// Also set the main window's background to dark so no white bleeds through the
		// edges
		getContentPane().setBackground(new Color(20, 20, 20));

		// === BUTTON ACTIONS ===

		drawBtn.addActionListener((ActionEvent e) -> {
			// Renamed to avoid clashing with the 'Dimension handSize' we made earlier!
			int currentHandCount = battle.getLocalHand(isLocalPlayer1).size();

			// Try to draw the card
			boolean success = battle.draw(isLocalPlayer1);

			if (!success) {
				// If it failed, figure out why and show the cool new popup!
				if (currentHandCount >= 4) {
					showToast("Your hand is full!");
				} else {
					showToast("Your deck is empty!");
				}
			}
			refreshBoard();
		});

		/*
		 * // Update your Attack Button attackBtn.addActionListener((ActionEvent e) -> {
		 * if (selectedAttacker != null) { isTargetingMode = !isTargetingMode; // Toggle
		 * on/off if (isTargetingMode) { showToast("Select an enemy target to attack!",
		 * 1500); } refreshBoard(); // Redraw to highlight targets } else {
		 * showToast("Click a card on your field to select it first!", 1500); } });
		 */

		endTurnBtn.addActionListener(e -> {
			// === THE FIX: Wipe all visual states clean! ===
			selectedAttacker = null;
			selectedCardForPlacement = null;
			isTargetingMode = false;
			isSpecialTargetingMode = false;
			selectedSpecialTargets.clear();

			// End the turn and redraw
			battle.endTurn();
			refreshBoard();
		});

		refreshBoard();
		setVisible(true);
	}
	// === NEW: Plays coinflips one-by-one so they don't overlap! ===
	private void playNextCoinflip() {
	    if (isCoinflipPlaying || coinflipQueue.isEmpty()) return;
	    isCoinflipPlaying = true;
	    coinflipQueue.remove(0).run();
	}
	// === NEW: Network RNG Synchronizer ===
	private String loadTheDice(String rawCommand) {
	    // Check for "CF", "if_2:", OR the "ON_DEATH" tripwire!
	    boolean hasCoinflip = rawCommand != null && (
	        rawCommand.contains("CF") || 
	        rawCommand.contains("ON_DEATH") ||  // <--- THIS SAVES THE WOLF!
	        (rawCommand.contains("if_2:") && !rawCommand.contains("if_2: null"))
	    );

	    if (hasCoinflip && !rawCommand.contains("COIN:")) {
	        boolean isHeads = Math.random() < 0.5;
	        return rawCommand + " | COIN:" + (isHeads ? "HEADS" : "TAILS");
	    }
	    return rawCommand;
	}

	

	// ==========================================
	// Refresh Everything
	// ==========================================

	private void refreshBoard() {

		// === GUI SANITY CHECK ===
		// If the engine says we aren't holding a card anymore, wipe the seat memory!
		if (selectedCardForPlacement == null) {
			selectedHandIndex = -1;
		}
		
		// === CORE ENGINE FEATURE: Forced Steal Placement ===
		if (battle.stolenCardReadyForPlacement != null) {
		    selectedCardForPlacement = battle.stolenCardReadyForPlacement;
		    selectedHandIndex = battle.getLocalHand(isLocalPlayer1).indexOf(battle.stolenCardReadyForPlacement);
		}

		// === CORE ENGINE FEATURE: Auto-Grab ===
		cards forcedCard = battle.consumeQueuedAutoSelect();
		if (forcedCard != null) {
			selectedCardForPlacement = forcedCard;
			selectedHandIndex = battle.queuedAutoSelectIndex; // Grab the backend's seat number!
			showToast("Card auto-selected! Click an empty slot to place it.");
		}
		// 1. Check if match started
		if (battle.getLocalHand(true).size() == 4 && battle.getLocalHand(false).size() == 4) {
			matchStarted = true;
		}
		playerFieldPanel.removeAll();
		enemyFieldPanel.removeAll();
		handPanel.removeAll();

		turnLabel.setText("Current Turn: Player " + (battle.isPlayer1Turn() ? "1" : "2"));
		boolean isMyTurn = (battle.isPlayer1Turn() == this.isLocalPlayer1);

		drawBtn.setEnabled(true);
		// === THE FIX: Lock the End Turn button if a forced placement is active! ===
		endTurnBtn.setEnabled(isMyTurn && battle.stolenCardReadyForPlacement == null);

		if (matchStarted) {
			readyBtn.setVisible(false);
		} else if (!isGameOver) {
			readyBtn.setVisible(true);
		}

		BattleCard[][] myField = battle.getLocalField2D(isLocalPlayer1);
		BattleCard[][] enemyField = battle.getNetworkEnemyField2D(isLocalPlayer1);

		// === // === THE FIX: Route targets based on the spell type! ===
		List<BattleCard> specialTargets = new ArrayList<>();

		if (pendingSpecialCommand.contains("target_any")) {
		    // Add EVERYTHING (Allies + Enemies)
		    specialTargets.addAll(battle.getFriendlyField(isLocalPlayer1));
		    specialTargets.addAll(SpecialsLibrary.getValidTargets(battle, true));
		    if (specialCaster != null) specialTargets.remove(specialCaster); // The Beast can't eat itself!
		    
		} else if (pendingSpecialCommand.contains("target_ally")) {
		    specialTargets = battle.getFriendlyField(isLocalPlayer1);
		    if (specialCaster != null) {
		        specialTargets.remove(specialCaster); // The Pigeon can't pick itself!
		    }
		    if (pendingSpecialCommand.contains("constructor_menu")) {
		        specialTargets.removeIf(bc -> !bc.getBaseCard().getCardID().equals("01991"));
		    }
		} else {
		    specialTargets = SpecialsLibrary.getValidTargets(battle, true);
		}

		// === THE FIX: Filter out Silenced cards so the player cannot click them! ===
		if (pendingSpecialCommand != null && pendingSpecialCommand.contains("steal_special")) {
		    specialTargets.removeIf(bc -> battle.hasStatus(bc, "SILENCED"));
		}
		List<BattleCard> attackableTargets = SpecialsLibrary.getValidTargets(battle, false);

		List<BattleCard> validTargets = battle.getEnemyCards();

		// === ENEMY FIELD (Targeting & Inspecting) ===
		for (int r = 0; r <= 1; r++) {
			for (int c = 0; c < 4; c++) {
				BattleCard bc = enemyField[r][c];
				boolean isDefense = (r == 1);

				if (bc == null) {
					enemyFieldPanel.add(createEmptySlot(isDefense));
				} else {
					JPanel cardPanel = createCardPanel(bc.getBaseCard(), bc, isDefense);

					// --- NORMAL ATTACK TARGETING ---
					if (isTargetingMode && attackableTargets.contains(bc)) {
						cardPanel.setBorder(BorderFactory.createLineBorder(Color.RED, 3));
						cardPanel.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
						cardPanel.addMouseListener(new java.awt.event.MouseAdapter() {
							public void mouseClicked(java.awt.event.MouseEvent evt) {

								// === SAFETY NET: Catch the silent crash! ===
								try {
									String targetId = bc.getBaseCard().getCardID().trim();
									String targetCommandStr = allSpecials.get(targetId);

									// === THE FIX: Lock the attacker into a local time capsule! ===
									BattleCard lockedAttacker = selectedAttacker;

									// Hand ALL the defense logic over to the Library!
									SpecialsLibrary.handleDefensiveTrick(battle, lockedAttacker, bc, targetCommandStr, finalTarget -> {
										if (finalTarget != null) {
											// Use the locked memory, not the wiped GUI state!
											executeFinalAttack(lockedAttacker, finalTarget);
										}
									});

								} catch (Exception ex) {
									System.err.println("CRASH INSIDE BATTLE.ATTACK: " + ex.getMessage());
									ex.printStackTrace();
								}

								// NOW it is safe to wipe the global GUI state!
								selectedAttacker = null;
								isTargetingMode = false;
								refreshBoard();
							}
							
						});
					}
					// --- SPECIAL ATTACK TARGETING ---
	                // THE FIX: This MUST say specialTargets.contains(bc) so it bypasses taunt!
	                // === CORE ENGINE FEATURE: Multi-Target Special Selection ===
	                else if (isSpecialTargetingMode && specialTargets.contains(bc)) {
	                    
	                    // 1. Check if we ALREADY clicked this specific card
	                    if (selectedSpecialTargets.contains(bc)) {
	                        // Highlight it MAGENTA to show it's locked in for the attack!
	                        cardPanel.setBorder(BorderFactory.createLineBorder(Color.MAGENTA, 4));
	                        cardPanel.setToolTipText("Target locked!");
	                    } else {
	                        // 2. Not clicked yet! Highlight it ORANGE to show it's a valid option!
	                        cardPanel.setBorder(BorderFactory.createLineBorder(Color.ORANGE, 3));
	                        cardPanel.setCursor(new Cursor(Cursor.HAND_CURSOR));
	                        cardPanel.setToolTipText("Click to lock in target " + (selectedSpecialTargets.size() + 1) + " of " + requiredSpecialTargets);
	                        
	                        cardPanel.addMouseListener(new java.awt.event.MouseAdapter() {
	                            public void mouseClicked(java.awt.event.MouseEvent evt) {
	                                
	                                // Add this card to our "cart"
	                                selectedSpecialTargets.add(bc);

	                                // Have we collected enough targets?
	                             // Have we collected enough targets?
	                                if (selectedSpecialTargets.size() >= requiredSpecialTargets) {
	                                    
	                                    // 1. Roll the network dice
	                                    String syncedCommand = loadTheDice(pendingSpecialCommand);
	                                    
	                                    // === THE FIX: Snapshot the cart so it survives the 1.5s Coinflip! ===
	                                    java.util.List<BattleCard> targetsSnapshot = new java.util.ArrayList<>(selectedSpecialTargets);
	                                    
	                                    // 2. Broadcast to Player 2
	                                    battle.broadcastSpecialExecution(specialCaster, targetsSnapshot, syncedCommand);
	                                    
	                                    // 3. Execute locally
	                                    SpecialsLibrary.parseAndExecute(battle, syncedCommand, true, specialCaster, targetsSnapshot);
	                                    
	                                    // 4. Clean up the UI state safely!
	                                    isSpecialTargetingMode = false;
	                                    specialCaster = null;
	                                    selectedSpecialTargets.clear();
	                                
	                                } 
	                                
	                                // Refresh the board (This turns the card we just clicked Magenta!)
	                                refreshBoard();
	                            }
	                        });
	                    }
	                }
					// === NEUTRAL INSPECTION (Enemy Cards) ===
					else {
						cardPanel.setCursor(new Cursor(Cursor.HAND_CURSOR));
						cardPanel.addMouseListener(new java.awt.event.MouseAdapter() {
							public void mouseClicked(java.awt.event.MouseEvent evt) {
								String cardName = bc.getBaseCard().getName();
								String flavorText = bc.getBaseCard().getSpecial();

								JOptionPane.showMessageDialog(BattleGUI.this, flavorText, cardName + " - Info",
										JOptionPane.INFORMATION_MESSAGE);
							}
						});
					}

					enemyFieldPanel.add(cardPanel);
				}
			}
		}

		// === PLAYER FIELD (Selection, Placement & Inspecting) ===
		for (int r = 1; r >= 0; r--) {
			for (int c = 0; c < 4; c++) {
				BattleCard bc = myField[r][c];
				boolean isDefense = (r == 1);
				final int row = r;
				final int col = c;

				if (bc == null) {
					JPanel emptySlot = createEmptySlot(isDefense);

					// --- NEW: PHASE 2 MANUAL PLACEMENT ---
					if (isMyTurn && isRevivePlacementMode) {
						emptySlot.setBorder(BorderFactory.createLineBorder(Color.MAGENTA, 3));
						emptySlot.setCursor(new Cursor(Cursor.HAND_CURSOR));
						emptySlot.addMouseListener(new java.awt.event.MouseAdapter() {
							public void mouseClicked(java.awt.event.MouseEvent evt) {

								// === THE FIX: The GUI is dumb again! It just passes the coordinates. ===
								SpecialsLibrary.executeSlotAction(battle, "REVIVE", pendingReviveId, row, col,
										currentReviveCaster, true);

								// 3. Clean up the UI
								isRevivePlacementMode = false;
								pendingReviveId = "";
								currentReviveCaster = null;
								refreshBoard();
							}
						});

					}

					else if (isMyTurn && selectedCardForPlacement != null) {
						emptySlot.setBorder(BorderFactory.createLineBorder(new Color(100, 255, 100), 3));
						emptySlot.setCursor(new Cursor(Cursor.HAND_CURSOR));
						emptySlot.addMouseListener(new java.awt.event.MouseAdapter() {
							public void mouseClicked(java.awt.event.MouseEvent evt) {
								
								// 1. Place the card
								cards baseCardToPlace = selectedCardForPlacement;
								boolean success = battle.placeCard(baseCardToPlace, row, col);

								// 2. === INTERCEPT MANUAL GUI POPUPS ===
								if (success) {
									if (battle.stolenCardReadyForPlacement == baseCardToPlace) {
										battle.stolenCardReadyForPlacement = null;
									}
									
									BattleCard newlyPlacedCard = battle.getLocalField2D(isLocalPlayer1)[row][col];
									String cardId = newlyPlacedCard.getBaseCard().getCardID();
									String fullCommandStr = allSpecials.get(cardId);

									String onPlaySlice = SpecialsLibrary.extractSegment(fullCommandStr, "ON_PLAY");

if (onPlaySlice != null) {
										
										// === PATH B: NEW Targeted ON_PLAY Intercept! (Ghouly Face) ===
										int neededTargets = extractTargetCount(onPlaySlice);
											
										if (neededTargets > 0) {
											// Ask the Library how many valid targets exist on the board!
											List<BattleCard> validEnemies = SpecialsLibrary.getValidTargets(battle, true);
											int availableEnemies = validEnemies.size();
												
											// Cap the needed targets to whatever is actually alive
											neededTargets = Math.min(neededTargets, availableEnemies);
												
											if (neededTargets > 0) {
												// Hijack the GUI into targeting mode!
												isSpecialTargetingMode = true;
												requiredSpecialTargets = neededTargets;
												selectedSpecialTargets.clear();
												specialCaster = newlyPlacedCard;
												pendingSpecialCommand = onPlaySlice;
													
												showToast("ON PLAY: Select " + neededTargets + " enemy target(s)!", 2500);
											} else {
												// The Failsafe triggers!
												showToast("No valid targets! " + newlyPlacedCard.getBaseCard().getName() + "'s special fizzled.", 2500);
											}
										}
									}
								} // End of if (success)
								
								selectedCardForPlacement = null;
								refreshBoard();
							}
						});

						
					}
					playerFieldPanel.add(emptySlot);
				}
				else {
					JPanel cardPanel = createCardPanel(bc.getBaseCard(), bc, isDefense);
					
					
					// --- NEW: FRIENDLY SPECIAL TARGETING ---
					if (isSpecialTargetingMode && specialTargets.contains(bc)) {
						if (selectedSpecialTargets.contains(bc)) {
							cardPanel.setBorder(BorderFactory.createLineBorder(Color.MAGENTA, 4));
							cardPanel.setToolTipText("Target locked!");
						} else {
							cardPanel.setBorder(BorderFactory.createLineBorder(Color.ORANGE, 3));
							cardPanel.setCursor(new Cursor(Cursor.HAND_CURSOR));
							cardPanel.setToolTipText("Click to lock in target " + (selectedSpecialTargets.size() + 1) + " of " + requiredSpecialTargets);
							
							cardPanel.addMouseListener(new java.awt.event.MouseAdapter() {
								public void mouseClicked(java.awt.event.MouseEvent evt) {
									selectedSpecialTargets.add(bc);

									if (selectedSpecialTargets.size() >= requiredSpecialTargets) {
										String syncedCommand = loadTheDice(pendingSpecialCommand);
										java.util.List<BattleCard> targetsSnapshot = new java.util.ArrayList<>(selectedSpecialTargets);
										
										battle.broadcastSpecialExecution(specialCaster, targetsSnapshot, syncedCommand);
										SpecialsLibrary.parseAndExecute(battle, syncedCommand, true, specialCaster, targetsSnapshot);
										
										isSpecialTargetingMode = false;
										specialCaster = null;
										selectedSpecialTargets.clear();
									} 
									refreshBoard();
								}
							});
						}
					}
					// --- END OF NEW BLOCK ---

					// === POPUP MENU (Active Attacker) ===
					// === POPUP MENU (Active Attacker) ===
					// === THE FIX: Lock the popup menu if we are currently aiming a special! ===
					else if (!isSpecialTargetingMode && !isTargetingMode && isMyTurn && bc.getActions() > 0 && r == 0) {
						cardPanel.setCursor(new Cursor(Cursor.HAND_CURSOR));
						cardPanel.addMouseListener(new java.awt.event.MouseAdapter() {
							public void mouseClicked(java.awt.event.MouseEvent evt) {
								
								// === ADJUSTMENT D: Block Active Attacker clicks ===
								if (battle.stolenCardReadyForPlacement != null) {
									showToast("You must place the stolen card first!", 1500);
									return; // Blocks them from clicking anything else!
								}
								
								selectedCardForPlacement = null;
								JPopupMenu actionMenu = new JPopupMenu();

								JMenuItem attackItem = new JMenuItem("Attack");
								JMenuItem specialItem = new JMenuItem("Special");
								JMenuItem infoItem = new JMenuItem("Read Card Info");

								// === LOCK ATTACK BUTTON IF PACIFIED ===
								if (battle.hasStatus(bc, "PACIFIED")) {
								    attackItem.setEnabled(false);
								    attackItem.setToolTipText("This card has been pacified and cannot attack!");
								}

								// === 1. EXTRACT INNATE SLICE ===
								String cardId = bc.getBaseCard().getCardID();
								String fullCommandStr = allSpecials.get(cardId);
								String innateSlice = SpecialsLibrary.extractSegment(fullCommandStr, "ACTIVE");
								if (innateSlice == null) {
								    innateSlice = SpecialsLibrary.extractSegment(fullCommandStr, "CHOSEN");
								}

								// === 2. EXTRACT STOLEN SLICE (If it exists) ===
								String stolenSlice = null;
								if (bc.getOverriddenSpecial() != null) {
								    String stolenStr = bc.getOverriddenSpecial();
								    stolenSlice = SpecialsLibrary.extractSegment(stolenStr, "ACTIVE");
								    if (stolenSlice == null) {
								        stolenSlice = SpecialsLibrary.extractSegment(stolenStr, "CHOSEN");
								    }
								}

								// === 3. BUTTON LOCK LOGIC ===
								if (bc.isSpecialDisabled()) {
								    specialItem.setEnabled(false);
								    specialItem.setToolTipText("This card's special ability has been exhausted!");
								} else if (battle.hasStatus(bc, "SILENCED")) {
								    // === THE MISSING CHECK: Lock the button if they were silenced! ===
								    specialItem.setEnabled(false);
								    specialItem.setToolTipText("This card is silenced and cannot use special abilities!");
								} else if (innateSlice == null && stolenSlice == null) {
								    // If BOTH are null/passive, the button greys out.
								    specialItem.setEnabled(false);
								    specialItem.setToolTipText("This card has no active abilities available right now.");
								}

								// === 4. THE ACTION LISTENER (With the Sub-Menu Dialog) ===
								final String finalInnateSlice = innateSlice;
								final String finalStolenSlice = stolenSlice;

								specialItem.addActionListener(e -> {
								    // If the card is holding a stolen ability, prompt the user!
								    if (finalStolenSlice != null) {
								        // Label it clearly in case their innate ability is a passive
								        String innateName = (finalInnateSlice != null) ? "Innate Ability" : "Innate (Passive)";
								        Object[] options = {innateName, "Stolen Ability", "Cancel"};
								        
								        int choice = javax.swing.JOptionPane.showOptionDialog(
								            BattleGUI.this,
								            "Which special ability would you like to use?",
								            "Select Ability",
								            javax.swing.JOptionPane.YES_NO_CANCEL_OPTION,
								            javax.swing.JOptionPane.QUESTION_MESSAGE,
								            null,
								            options,
								            options[0]
								        );

								        if (choice == 0) { // Clicked "Innate Ability"
								            if (finalInnateSlice == null) {
								                showToast("The innate ability is passive and cannot be clicked!", 1500);
								            } else {
								                triggerSpecialFromUI(bc, finalInnateSlice);
								            }
								        } else if (choice == 1) { // Clicked "Stolen Ability"
								            triggerSpecialFromUI(bc, finalStolenSlice);
								        }
								        // If choice == 2 (Cancel) or they closed the window, do nothing!
								    } 
								    // If they haven't stolen anything, just fire the innate ability normally!
								    else {
								        triggerSpecialFromUI(bc, finalInnateSlice);
								    }
								});

								attackItem.addActionListener(e -> {
								    selectedAttacker = bc;
								    isTargetingMode = true;
								    refreshBoard();
								});

								infoItem.addActionListener(e -> {
								    String cardName = bc.getBaseCard().getName();
								    String flavorText = bc.getBaseCard().getSpecial();
								    javax.swing.JOptionPane.showMessageDialog(BattleGUI.this, flavorText, cardName + " - Info",
								            javax.swing.JOptionPane.INFORMATION_MESSAGE);
								});

								// Build the Menu
								actionMenu.add(attackItem);
								actionMenu.addSeparator();
								actionMenu.add(specialItem);
								actionMenu.addSeparator();
								actionMenu.add(infoItem);

								if (cardPanel.isShowing()) {
								    actionMenu.show(cardPanel, evt.getX(), evt.getY());
								}
							}
						});
					}
					//neutral special inspector function
					else {
						cardPanel.setCursor(new Cursor(Cursor.HAND_CURSOR));
						cardPanel.addMouseListener(new java.awt.event.MouseAdapter() {
							public void mouseClicked(java.awt.event.MouseEvent evt) {
								
								// === ADJUSTMENT D: Block Neutral Inspector clicks ===
								if (battle.stolenCardReadyForPlacement != null) {
									showToast("You must place the stolen card first!", 1500);
									return; // Blocks them from clicking anything else!
								}
								
								// Clear UI state first
								selectedAttacker = null;
								selectedCardForPlacement = null;
								isTargetingMode = false;
								refreshBoard();

								// Pop up the Info Dialog
								String cardName = bc.getBaseCard().getName();
								String flavorText = bc.getBaseCard().getSpecial();
								JOptionPane.showMessageDialog(BattleGUI.this, flavorText, cardName + " - Info",
										JOptionPane.INFORMATION_MESSAGE);
							}
						});
					}

					if (selectedAttacker == bc) {
						cardPanel.setBorder(BorderFactory.createLineBorder(Color.CYAN, 3));
					}

					playerFieldPanel.add(cardPanel);
				}
			}
		}

		// === HAND (Selection) ===
		int seatIndex = 0; // Track the physical seat number!

		// === THE VISUAL CLONE FIX ===
		// Create a temporary clone of the locked list so we can "consume" locks as we draw the GUI!
		List<cards> tempLocks = new ArrayList<>(battle.getLockedCards());

		for (cards c : battle.getLocalHand(isLocalPlayer1)) {
		    JPanel panel = createCardPanel(c, null, false);

		    // Check if this specific copy should be locked
		    boolean isLocked = false;
		    if (tempLocks.contains(c)) {
		        isLocked = true;
		        tempLocks.remove(c); // Consume the lock so the NEXT identical card renders normally!
		    }

		    // We must "snapshot" the seat number so the mouse listener remembers it
		    final int currentSeat = seatIndex;

		    if (isMyTurn) {
		        if (isLocked) {
		            panel.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 3));
		            panel.setToolTipText("This card has retreated and cannot be played this turn.");
		        } else {
		            panel.setCursor(new Cursor(Cursor.HAND_CURSOR));
		            panel.addMouseListener(new java.awt.event.MouseAdapter() {
		                public void mouseClicked(java.awt.event.MouseEvent evt) {

							// === ADJUSTMENT D: Block Hand Selection clicks ===
							if (battle.stolenCardReadyForPlacement != null) {
								showToast("You must place the stolen card first!", 1500);
								return; // Blocks them from clicking anything else!
							}

		                    // === THE MOUSE LISTENER FIX ===
		                    if (selectedHandIndex == currentSeat && selectedCardForPlacement == c) {
		                        selectedCardForPlacement = null;
		                        selectedHandIndex = -1; // Deselect
		                    } else {
		                        selectedCardForPlacement = c;
		                        selectedHandIndex = currentSeat; // Save the seat!
		                        selectedAttacker = null;
		                        isTargetingMode = false;
		                    }
		                    refreshBoard();
		                }
		            });

		            // === THE DRAWING BORDER FIX ===
		            if (selectedHandIndex == currentSeat && selectedCardForPlacement == c) {
		                panel.setBorder(BorderFactory.createLineBorder(Color.GREEN, 3));
		            }
		        }
		    }
		    handPanel.add(panel);
		    seatIndex++; // Move to the next seat for the next card
		}

		revalidate();
		repaint();
		// === UPDATED: Win/Loss Scanner (With Network Sync Delay!) ===
		// === UPDATED: Win/Loss Scanner (With Network Sync Delay & UI Locks!) ===
				if (!isGameOver && matchStarted && activeAnimations == 0 && pendingDeaths.get() == 0 
				        && !battle.isGraveyardAutoPlaceMode && !isRevivePlacementMode && battle.stolenCardReadyForPlacement == null) {
				    
				    boolean localDied = battle.hasPlayerLost(isLocalPlayer1);
				    boolean enemyDied = battle.hasPlayerLost(!isLocalPlayer1);

				    // If it looks like someone died, make sure we aren't already running a timer!
				    if ((enemyDied || localDied) && !isGameOverTimerRunning) {
				        isGameOverTimerRunning = true;
				        
				        // Wait 1.5 seconds for any delayed network packets to arrive from the opponent!
				        javax.swing.Timer validationTimer = new javax.swing.Timer(1500, evt -> {
				            isGameOverTimerRunning = false;
				            
				            // The network had 1.5 seconds to catch up. 
				            // Check everything one last time to be absolutely sure!
				            if (!isGameOver && matchStarted && activeAnimations == 0 && pendingDeaths.get() == 0 
				                    && !battle.isGraveyardAutoPlaceMode && !isRevivePlacementMode && battle.stolenCardReadyForPlacement == null) {
				                
				                boolean confirmLocalDied = battle.hasPlayerLost(isLocalPlayer1);
				                boolean confirmEnemyDied = battle.hasPlayerLost(!isLocalPlayer1);
				                
				                if (confirmEnemyDied) {
				                    isGameOver = true;
				                    matchStarted = false;
				                    triggerGameOver("VICTORY! The enemy has been completely wiped out.");
				                } else if (confirmLocalDied) {
				                    isGameOver = true;
				                    matchStarted = false;
				                    triggerGameOver("DEFEAT! Your forces have been completely wiped out.");
				                }
				            }
				        });
				        validationTimer.setRepeats(false);
				        validationTimer.start();
				    }
				}
	}

	private int extractTargetCount(String commandStr) {
		if (commandStr == null) return 0;
		String lowerCmd = commandStr.toLowerCase();

		// === THE FIX: Check for ANY Targeting ===
				int anyTargetIdx = lowerCmd.indexOf("target_any(");
				if (anyTargetIdx != -1) {
					int endIdx = lowerCmd.indexOf(")", anyTargetIdx);
					if (endIdx != -1) {
						try { return Integer.parseInt(lowerCmd.substring(anyTargetIdx + 11, endIdx).trim()); } catch (Exception e) {}
					}
				}

				// 1. === NEW: Check for Friendly Targeting ===
				int allyTargetIdx = lowerCmd.indexOf("target_ally(");
				if (allyTargetIdx != -1) {
					int endIdx = lowerCmd.indexOf(")", allyTargetIdx);
					if (endIdx != -1) {
						try { return Integer.parseInt(lowerCmd.substring(allyTargetIdx + 12, endIdx).trim()); } catch (Exception e) {}
					}
				}

		// 2. Check for Enemy Targeting
		int targetIdx = lowerCmd.indexOf("target(");
		if (targetIdx != -1) {
			int endIdx = lowerCmd.indexOf(")", targetIdx);
			if (endIdx != -1) {
				try { return Integer.parseInt(lowerCmd.substring(targetIdx + 7, endIdx).trim()); } catch (Exception e) {}
			}
		}

		if (commandStr.toUpperCase().contains("PASSIVE")) return 0;

		// === LEGACY SCANNER ===
		String[] manualTargetActions = { "instakill[", "stealcard[", "strike[", "debuffatk[" }; 
		for (String actionPrefix : manualTargetActions) {
			int startIndex = lowerCmd.indexOf(actionPrefix);
			if (startIndex != -1) {
				int bracketStart = lowerCmd.indexOf("[", startIndex);
				int bracketEnd = lowerCmd.indexOf("]", bracketStart);
				if (bracketStart != -1 && bracketEnd != -1) {
					try { return Integer.parseInt(lowerCmd.substring(bracketStart + 1, bracketEnd).trim()); } catch (Exception e) {}
				}
			}
		}
		return 0;
	}
	// ==========================================
	// "Toast" Notification Popups
	// ==========================================

	// 1. The default quick toast (1.5 seconds) for standard errors
	private void showToast(String message) {
		showToast(message, 1500);
	}

	// 2. The upgraded toast that accepts a custom timer!
	private void showToast(String message, int durationInMillis) {
		JDialog toast = new JDialog(this, false); // 'false' means it won't block the game!
		toast.setUndecorated(true);
		toast.setFocusableWindowState(false); // Prevents it from stealing your mouse focus
		toast.getContentPane().setBackground(new Color(60, 60, 60));

		// Add a nice white border so it pops against the dark background
		toast.getRootPane().setBorder(BorderFactory.createLineBorder(Color.WHITE, 2));

		JLabel label = new JLabel(message, SwingConstants.CENTER);
		label.setForeground(Color.WHITE);
		label.setFont(new Font("SansSerif", Font.BOLD, 16));
		label.setBorder(BorderFactory.createEmptyBorder(15, 30, 15, 30));

		toast.add(label);
		toast.pack();

		// Center it perfectly over the game window
		toast.setLocationRelativeTo(this);

		toast.setVisible(true);

		// === THE FIX: Uses your custom timer variable instead of a hardcoded 1500! ===
		javax.swing.Timer timer = new javax.swing.Timer(durationInMillis, evt -> toast.dispose());
		timer.setRepeats(false);
		timer.start();
	}

	// ==========================================
	// Coinflip Animator
	// ==========================================
	private void showCoinflipAnimation(boolean isHeads, boolean isLocalPlayer) {
		JDialog flipDialog = new JDialog(this, false);
		flipDialog.setUndecorated(true);
		flipDialog.setFocusableWindowState(false);
		flipDialog.getContentPane().setBackground(new Color(40, 40, 40));
		flipDialog.getRootPane().setBorder(BorderFactory.createLineBorder(Color.WHITE, 2));
		flipDialog.setLayout(new BorderLayout());

		// 1. Exact file names so "coin.png" is Heads (0) and "coin3.png" is Tails (2)
		String[] coinFiles = { "/images/coin.png", // Frame 0: HEADS
				"/images/coin2.png", // Frame 1: In-between
				"/images/coin3.png", // Frame 2: TAILS
				"/images/coin4.png" // Frame 3: In-between
		};

		ImageIcon[] frames = new ImageIcon[4];
		for (int i = 0; i < 4; i++) {
			java.net.URL url = getClass().getResource(coinFiles[i]);
			if (url != null) {
				Image scaled = new ImageIcon(url).getImage().getScaledInstance(80, 80, Image.SCALE_SMOOTH);
				frames[i] = new ImageIcon(scaled);
			} else {
				System.out.println("Could not find: " + coinFiles[i]);
			}
		}

		// 2. Setup the UI components
		JLabel imageLabel = new JLabel();
		if (frames[0] != null)
			imageLabel.setIcon(frames[0]);
		imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
		imageLabel.setBorder(BorderFactory.createEmptyBorder(15, 20, 5, 20));

		JLabel textLabel = new JLabel("Flipping Coin...", SwingConstants.CENTER);
		textLabel.setForeground(Color.LIGHT_GRAY);
		textLabel.setFont(new Font("SansSerif", Font.ITALIC, 16));
		textLabel.setBorder(BorderFactory.createEmptyBorder(5, 30, 15, 30));

		flipDialog.add(imageLabel, BorderLayout.NORTH);
		flipDialog.add(textLabel, BorderLayout.CENTER);

		// === THE FIX 1: Lock the exact size so the border NEVER jumps! ===
		flipDialog.setSize(350, 180);
		flipDialog.setLocationRelativeTo(centerWrapper);
		flipDialog.setVisible(true);

		// 3. The Animation Loop
		final int[] currentFrame = { 0 };
		final int[] flipCount = { 0 };
		int totalFlips = 15; // Flips 15 times (1.5 seconds) before stopping

		javax.swing.Timer animTimer = new javax.swing.Timer(100, null);
		animTimer.addActionListener(e -> {
			flipCount[0]++;
			currentFrame[0] = (currentFrame[0] + 1) % 4; // Cycle 0, 1, 2, 3

			if (frames[currentFrame[0]] != null) {
				imageLabel.setIcon(frames[currentFrame[0]]);
			}

			// 4. When the flip is done, stop the animation and show the results!
			if (flipCount[0] >= totalFlips) {
				animTimer.stop();

				// === THE FIX 2: Perspective Math! ===
				// If I am Player 1, I see the true coin face.
				// If I am Player 2, I am looking at the opposite side of the coin!
				boolean showHeadsOnThisScreen = isLocalPlayer ? isHeads : !isHeads;

				if (showHeadsOnThisScreen && frames[0] != null) {
					imageLabel.setIcon(frames[0]); // Snaps to coin.png (Heads)
				} else if (!showHeadsOnThisScreen && frames[2] != null) {
					imageLabel.setIcon(frames[2]); // Snaps to coin3.png (Tails)
				}

				String face = showHeadsOnThisScreen ? "Heads" : "Tails";

				boolean localGoesFirst = (isHeads == isLocalPlayer);
				String who = localGoesFirst ? "You go 1st!" : "You go 2nd!";

				textLabel.setText("Coinflip: " + face + "! " + who);
				textLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
				textLabel.setForeground(Color.WHITE);

				// Note: We completely removed the flipDialog.pack() from here
				// so it doesn't try to auto-resize the box and ruin the layout!

				// 5. Start the master kill timer
				// 5. Start the master kill timer
				javax.swing.Timer killTimer = new javax.swing.Timer(coinflipPopupDuration, evt -> {
				    flipDialog.dispose();
				    
				    // === THE QUEUE FIX: Unlock the queue so later coinflips can play! ===
				    isCoinflipPlaying = false;
				    activeAnimations--;
				    playNextCoinflip();
				    
				    // Once ALL animations finish, it is safe to check the board!
				    if (activeAnimations == 0 && pendingDeaths.get() == 0) {
				        refreshBoard();
				    }
				});
				killTimer.setRepeats(false);
				killTimer.start();
			}
		});

		animTimer.start();
	}

	// ==========================================
	// Special Coinflip Animator (With Callback Pause!)
	// ==========================================
	// Update the method signature to accept the strings!
	private void showSpecialCoinflipAnimation(boolean isHeads, String headsMsg, String tailsMsg, Runnable onComplete) {
		JDialog flipDialog = new JDialog(this, false);
		flipDialog.setUndecorated(true);
		flipDialog.setFocusableWindowState(false);
		flipDialog.getContentPane().setBackground(new Color(40, 40, 40));
		flipDialog.getRootPane().setBorder(BorderFactory.createLineBorder(Color.MAGENTA, 3)); // Magenta so it matches
																								// targeting!
		flipDialog.setLayout(new BorderLayout());

		String[] coinFiles = { "/images/coin.png", "/images/coin2.png", "/images/coin3.png", "/images/coin4.png" };
		ImageIcon[] frames = new ImageIcon[4];
		for (int i = 0; i < 4; i++) {
			java.net.URL url = getClass().getResource(coinFiles[i]);
			if (url != null)
				frames[i] = new ImageIcon(new ImageIcon(url).getImage().getScaledInstance(80, 80, Image.SCALE_SMOOTH));
		}

		JLabel imageLabel = new JLabel();
		if (frames[0] != null)
			imageLabel.setIcon(frames[0]);
		imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
		imageLabel.setBorder(BorderFactory.createEmptyBorder(15, 20, 5, 20));

		JLabel textLabel = new JLabel("Rolling for Special...", SwingConstants.CENTER);
		textLabel.setForeground(Color.LIGHT_GRAY);
		textLabel.setFont(new Font("SansSerif", Font.ITALIC, 16));
		textLabel.setBorder(BorderFactory.createEmptyBorder(5, 30, 15, 30));

		flipDialog.add(imageLabel, BorderLayout.NORTH);
		flipDialog.add(textLabel, BorderLayout.CENTER);

		flipDialog.setSize(350, 180);
		flipDialog.setLocationRelativeTo(centerWrapper);
		flipDialog.setVisible(true);

		final int[] currentFrame = { 0 };
		final int[] flipCount = { 0 };
		int totalFlips = 15;

		javax.swing.Timer animTimer = new javax.swing.Timer(100, null);
		animTimer.addActionListener(e -> {
			flipCount[0]++;
			currentFrame[0] = (currentFrame[0] + 1) % 4;
			if (frames[currentFrame[0]] != null)
				imageLabel.setIcon(frames[currentFrame[0]]);

			if (flipCount[0] >= totalFlips) {
				animTimer.stop();

				// Snap to the final face
				if (isHeads && frames[0] != null)
					imageLabel.setIcon(frames[0]);
				else if (!isHeads && frames[2] != null)
					imageLabel.setIcon(frames[2]);

				// === THE FIX: Use our dynamic text and a neutral color! ===
				// === THE FIX: Use our dynamic text and a neutral color! ===
				String face = isHeads ? "Heads" : "Tails";
				String outcome = isHeads ? headsMsg : tailsMsg;

				// === THE WORD-WRAP FIX ===
				// === THE WORD-WRAP FIX (Perfectly Centered) ===
				textLabel.setText("<html><p align='center' style='width: 230px;'>" + face + " - " + outcome + "</p></html>");
				textLabel.setFont(new Font("SansSerif", Font.BOLD, 16));

				// Changed to Yellow so it looks like a neutral "System Announcement"
				textLabel.setForeground(new Color(255, 215, 0));
				// Wait 1.5 seconds for the player to read the result, THEN execute the backend
				// logic!
				// Wait 1.5 seconds for the player to read the result, THEN execute the backend logic!
				javax.swing.Timer killTimer = new javax.swing.Timer(1500, evt -> {
				    flipDialog.dispose();
				    if (onComplete != null) onComplete.run();
				    
				    // === THE QUEUE FIX ===
				    isCoinflipPlaying = false;
				    activeAnimations--;
				    playNextCoinflip();
				    
				    // Once ALL animations finish, it is safe to check the board!
				    if (activeAnimations == 0 && pendingDeaths.get() == 0) {
				        refreshBoard();
				    }
				});
				killTimer.setRepeats(false);
				killTimer.start();
			}
		});
		animTimer.start();
	}

	// ==========================================
	// Graveyard Popup Viewer
	// ==========================================
	// ==========================================
	// Graveyard Popup Viewer
	// ==========================================
	private void showGraveyard() {

		List<cards> grave = battle.getGraveyard();

		if (grave.isEmpty()) {

			showToast("The graveyard is completely empty!");

			return;

		}

		JDialog graveDialog = new JDialog(this, "Graveyard", true);

		graveDialog.setSize(600, 500);

		graveDialog.setLocationRelativeTo(this);

		// === CHANGED: Dialog background to pure black ===

		graveDialog.getContentPane().setBackground(Color.BLACK);

		// Math to figure out how many rows we need for a 4-column grid

		int cols = 4;

		int rows = (int) Math.ceil((double) grave.size() / cols);

		// Wrap everything in a nice dark panel

		JPanel gridPanel = new JPanel(new GridLayout(rows, cols, 10, 10));

		// === CHANGED: Grid background to pure black ===

		gridPanel.setBackground(Color.BLACK);

		gridPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		// 1. Grab your graveyard list using your engine's existing method!
	    java.util.List<cards> graveList = battle.getGraveyard(); 
	    
	    // === THE UPGRADED LIMBO FILTER ===
	    // 2. Clone the list so we only alter the UI, not the actual engine memory!
	    java.util.List<cards> displayList = new java.util.ArrayList<>(graveList);
	    
	    // 3. Figure out who is triggering the graveyard (Active Spell vs Death Sequence)
	    String hiddenCasterId = null;
	    if (currentReviveCaster != null) {
	        hiddenCasterId = currentReviveCaster.getBaseCard().getCardID();
	    } else if (battle.autoPlaceCaster != null) {
	        hiddenCasterId = battle.autoPlaceCaster.getBaseCard().getCardID();
	    }
	    
	    // 4. If we found a caster, slice exactly one copy out of the visual list!
	    if (hiddenCasterId != null) {
	        hiddenCasterId = hiddenCasterId.trim(); // Trim spaces just in case!
	        
	     // === THE FIX: The Grave Digger Restriction ===
	        if (hiddenCasterId.equals("01092")) {
	            // Grave Diggers refuse to dig up their own kind! 
	            // Remove ALL copies of 01092 from the visual list.
	            displayList.removeIf(c -> c.getCardID().trim().equals("01092"));
	        } else {
	        
	        for (int i = displayList.size() - 1; i >= 0; i--) {
	            if (displayList.get(i).getCardID().trim().equals(hiddenCasterId)) {
	                displayList.remove(i);
	                break; // Stop after removing exactly ONE!
	            }
	        }
	        }
	    }
	    
	    // 5. THE FIX: Change your for-loop to read from 'displayList' instead of the raw graveyard!
	    for (cards c : displayList) {

			JPanel cardUI = createCardPanel(c, null, false);

			// Attach the Interceptor Listener to every card in the Graveyard!

			// Attach the Interceptor Listener to every card in the Graveyard!
						cardUI.addMouseListener(new java.awt.event.MouseAdapter() {
							@Override
							public void mouseClicked(java.awt.event.MouseEvent e) {

								// ==========================================
								// PATH A: Manual Placement (The Magic Vase)
								// ==========================================
								if (isGraveyardTargeting) {
									// 1. Highlight it!
									cardUI.setBorder(BorderFactory.createLineBorder(Color.MAGENTA, 4));
									cardUI.paintImmediately(0, 0, cardUI.getWidth(), cardUI.getHeight());

									// 2. Turn off Graveyard mode, and turn ON Board Placement mode!
									isGraveyardTargeting = false;
									isRevivePlacementMode = true;
									pendingReviveId = String.format("%05d", Integer.parseInt(c.getCardID()));

									// 3. Wait 0.4s to show the highlight, then prompt them for a slot!
									javax.swing.Timer timer = new javax.swing.Timer(400, actionEvent -> {
										graveDialog.dispose();
										refreshBoard();
										showToast("Select an empty slot on your field to summon the card!", 2500);
									});
									timer.setRepeats(false);
									timer.start();
								}
								
								// ==========================================
								// PATH B: Automatic Placement (The Martyr)
								// ==========================================
								else if (battle.isGraveyardAutoPlaceMode) {
									// 1. Highlight it! 
									cardUI.setBorder(BorderFactory.createLineBorder(Color.CYAN, 4));
									cardUI.paintImmediately(0, 0, cardUI.getWidth(), cardUI.getHeight());

									// 2. Lock in the target ID and turn off the mode
									battle.isGraveyardAutoPlaceMode = false;
									String targetId = String.format("%05d", Integer.parseInt(c.getCardID()));

									// 3. Wait 0.4s, close dialog, and INSTANTLY place it!
									javax.swing.Timer timer = new javax.swing.Timer(400, actionEvent -> {
										graveDialog.dispose();
										
										// Read the coordinates straight from the backend!
										SpecialsLibrary.executeSlotAction(battle, "REVIVE", targetId, 
												battle.autoPlaceRow, battle.autoPlaceCol, battle.autoPlaceCaster, true);
										
										battle.autoPlaceCaster = null;
										refreshBoard();
									});
									timer.setRepeats(false);
									timer.start();
								}
							}
						});

			gridPanel.add(cardUI);

		}

		// Throw the grid into a scroll pane so it handles overflows

		JScrollPane scroll = new JScrollPane(gridPanel);

		scroll.getViewport().setBackground(Color.BLACK);

		scroll.getVerticalScrollBar().setUnitIncrement(16);

		scroll.setBorder(null);

		graveDialog.add(scroll);

		// === NEW: The "Cancel" Safety Net ===

		// === THE UPGRADED "CANCEL" SAFETY NET ===
		// === THE UPGRADED "CANCEL" SAFETY NET ===
				graveDialog.addWindowListener(new java.awt.event.WindowAdapter() {
					@Override
					public void windowClosing(java.awt.event.WindowEvent windowEvent) {
						// Cancel Path A
						if (isGraveyardTargeting) {
							isGraveyardTargeting = false;
							currentReviveCaster = null;
						}
						// Cancel Path B (Engine State)
						if (battle.isGraveyardAutoPlaceMode) {
							battle.isGraveyardAutoPlaceMode = false;
							
							// === THE FIX: Broadcast a cancel packet so the opponent unlocks! ===
							if (battle.autoPlaceCaster != null) {
							    battle.broadcastSpecialExecution(battle.autoPlaceCaster, null, "PASSIVE | NONE | if_1: cancel_revive[0] | if_2: null");
							}
							
							battle.autoPlaceCaster = null;
							refreshBoard();
						}
					}
				});
		graveDialog.setVisible(true);

		// Throw the grid into a scroll pane so it handles overflows

	}

	// ==========================================
	// Game Over Handler
	// ==========================================
	// ==========================================
	// Game Over & Post-Game Lobby
	// ==========================================
	private void triggerGameOver(String message) {
		// Lock the game down
		drawBtn.setEnabled(false);

		endTurnBtn.setEnabled(false);

		showPostGameMenu(message);
	}

	private void showPostGameMenu(String message) {
		if (postGameDialog != null && postGameDialog.isVisible())
			return;

		// 'false' means it is NON-BLOCKING! You can still look at the cards behind it.
		postGameDialog = new JDialog(this, "Match Finished", false);
		postGameDialog.setSize(450, 200);
		postGameDialog.setLocationRelativeTo(this);
		postGameDialog.getContentPane().setBackground(new Color(25, 25, 25));
		postGameDialog.setLayout(new BorderLayout());

		((JComponent) postGameDialog.getContentPane()).setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));

		JLabel msgLabel = new JLabel("<html><center><br>" + message + "</center></html>", SwingConstants.CENTER);
		msgLabel.setForeground(Color.WHITE);
		msgLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
		postGameDialog.add(msgLabel, BorderLayout.CENTER);

		JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 20));
		btnPanel.setOpaque(false);

		JButton rematchBtn = new JButton("Request Rematch");
		JButton leaveBtn = new JButton("Return to Hub");

		// === UPDATED: Dark grey background, pure BLACK text! ===
		rematchBtn.setUI(new javax.swing.plaf.basic.BasicButtonUI());
		rematchBtn.setBackground(new Color(150, 150, 150));
		rematchBtn.setForeground(Color.BLACK); // <--- Forces black text
		rematchBtn.setFocusPainted(false);

		leaveBtn.setUI(new javax.swing.plaf.basic.BasicButtonUI());
		leaveBtn.setBackground(new Color(150, 150, 150));
		leaveBtn.setForeground(Color.BLACK); // <--- Forces black text
		leaveBtn.setFocusPainted(false);

		/*
		 * leaveBtn.setBackground(new Color(40, 40, 40));
		 * leaveBtn.setForeground(Color.WHITE); leaveBtn.setFocusPainted(false);
		 */

		// Action: Request Rematch
		rematchBtn.addActionListener(e -> {
			rematchBtn.setText("Waiting for opponent...");
			rematchBtn.setEnabled(false);
			battle.requestRematch();
		});

		// Action: Leave Match
		// Action: Leave Match
		leaveBtn.addActionListener(e -> {
			battle.rejectRematch();
			postGameDialog.dispose();
			this.dispose(); // This completely closes the BattleGUI!

			// === THE FIX: Use the global variable to open the Hub! ===
			new userHub(this.localUsername);
		});

		btnPanel.add(rematchBtn);
		btnPanel.add(leaveBtn);
		postGameDialog.add(btnPanel, BorderLayout.SOUTH);

		// === HOOK UP THE BACKEND CALLBACKS ===
		// === HOOK UP THE BACKEND CALLBACKS ===
		battle.setRematchCallbacks(
				// When enemy responds or disconnects...
				() -> SwingUtilities.invokeLater(() -> {

					// === THE FIX: Erase any showToast() commands from inside these brackets! ===
					if (battle.hasEnemyDeclined()) {
						msgLabel.setText("<html><center><br>The opponent has left the lobby.</center></html>");
						rematchBtn.setText("Opponent Left");
						rematchBtn.setEnabled(false);
						leaveBtn.setBackground(new Color(255, 100, 100));

					} else if (battle.doesEnemyWantRematch()) {
						// Update the menu label instead of showing a confusing toast!
						msgLabel.setText("<html><center><br>Opponent wants a rematch!</center></html>");
					}
				}),

				// When the game officially resets!
				() -> SwingUtilities.invokeLater(() -> {
					isGameOver = false;
					matchStarted = false;
					postGameDialog.dispose(); // Closes the post-game menu!

					readyBtn.setEnabled(true);
					readyBtn.setBackground(new Color(100, 200, 100));
					readyBtn.setText("Ready?");
					readyBtn.setVisible(true);

					showToast("Match Restarted! Click Ready to begin.", 2000);
					
					
					refreshBoard();
				})

		);

		postGameDialog.setVisible(true);
	}

	// === UPDATED: Now knows if it should draw a wide or tall empty slot! ===
	// === UPDATED: Stretches to outline the maximum zone space! ===
	private JPanel createEmptySlot(boolean isDefense) {
		JPanel empty = new JPanel();

		// Deep black background for the empty zone
		empty.setBackground(Color.BLACK);

		// === THE FIX: Change 1 to 3 ===
		empty.setBorder(BorderFactory.createLineBorder(Color.GRAY, 3));

		// By returning this directly without the GridBagLayout wrapper,
		// the main GridLayout will automatically stretch it to fill 100% of the zone!
		return empty;
	}

	// ==========================================
	// Create Hand Card Button
	// ==========================================

	// === UPDATED: Now requires a boolean to know if it should rotate! ===
	// === UPDATED: Text is now at the TOP of the card ===
	// === UPDATED: Attack cards stay vertical, Defense cards go horizontal ===
	private JPanel createCardPanel(cards card, BattleCard battleCard, boolean isDefense) {
		JPanel innerPanel = new JPanel(new BorderLayout());
		innerPanel.setBackground(new Color(80, 80, 80));
		innerPanel.setBorder(BorderFactory.createLineBorder(Color.WHITE, 2));

		// Set tight, perfect dimensions based on its stance!
		// Set tight, perfect dimensions based on its stance!
		if (isDefense) {
			innerPanel.setPreferredSize(new Dimension(CARD_HEIGHT, IMAGE_SIZE)); // Wide mode
		} else {
			innerPanel.setPreferredSize(new Dimension(IMAGE_SIZE, CARD_HEIGHT)); // Tall mode
		}

		// Inside createCardPanel() in BattleGUI.java...
		
		// Inside createCardPanel() in BattleGUI.java...
		
			
				
				// Pass BOTH booleans into the image builder!
				// Inside createCardPanel() in BattleGUI.java...
				// We no longer need to calculate booleans here. Just pass the objects directly!
				JLabel imageLabel = createCardImageLabel(card, battleCard, isDefense);
				innerPanel.add(imageLabel, BorderLayout.CENTER);

		JPanel infoContainer = new JPanel(new GridLayout(2, 1));
		infoContainer.setOpaque(false);

		JLabel nameLabel = new JLabel(card.getName(), SwingConstants.CENTER);
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setFont(new Font("SansSerif", Font.BOLD, 12));

		// If it is on the field, get the BattleCard HP. If it is in the hand, check for
		// saved HP!
		int currentHp = (battleCard != null) ? battleCard.getHp() : battle.getSavedHp(card);

		// === THE FIX: Check if the card is on the field to get its buffed ATK! ===
		int currentAtk = (battleCard != null) ? battleCard.getAtk() : card.getAtk();

		// Plug our new dynamic currentAtk variable into the label!
		JLabel statsLabel = new JLabel("ATK: " + currentAtk + "  |  HP: " + currentHp, SwingConstants.CENTER);

		if (currentHp <= 2) {
			statsLabel.setForeground(new Color(255, 100, 100));
		} else {
			statsLabel.setForeground(Color.LIGHT_GRAY);
		}
		statsLabel.setFont(new Font("Arial", Font.PLAIN, 12));

		infoContainer.add(nameLabel);
		infoContainer.add(statsLabel);
		innerPanel.add(infoContainer, BorderLayout.NORTH);

		// Wrap the panel so the GridLayout doesn't ruin our perfect rectangles!
		JPanel wrapper = new JPanel(new GridBagLayout());

		// === THE FIX: Keep the playmat slot visible behind the card! ===
		// Inside createCardPanel, right at the bottom where you make the wrapper:

		if (battleCard != null) {
			wrapper.setBackground(Color.BLACK);
			// === THE FIX: Change 1 to 3 ===
			wrapper.setBorder(BorderFactory.createLineBorder(Color.GRAY, 3));
		} else {
			wrapper.setOpaque(false);
			// === THE FIX: Give hand cards a 3px invisible border to hold the space! ===
			wrapper.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
		}

		wrapper.add(innerPanel);
		return wrapper;

	}

	// === UPDATED: Actually spins the image 90 degrees ===
	// === UPDATED: Safely and synchronously draws the image so it doesn't turn
	// invisible! ===
	// === UPDATED: Now accepts the isTargeted flag! ===
	// === UPDATED: Now accepts isUntargetable! ===
	// === UPGRADED: Now takes the base card and battle card directly! ===
	// === UPGRADED: Dynamic Top-Right Sticker Tray & Aura Fix! ===
		private JLabel createCardImageLabel(cards baseCard, BattleCard battleCard, boolean rotate) {
			java.net.URL imgURL = getClass().getResource(baseCard.getImageURL());
			ImageIcon icon = (imgURL != null) ? new ImageIcon(imgURL)
					: new ImageIcon(getClass().getResource("/images/placeholder.png"));

			BufferedImage bi = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2d = bi.createGraphics();

			java.awt.geom.AffineTransform oldTransform = g2d.getTransform();

			if (rotate) {
				g2d.rotate(Math.toRadians(90), IMAGE_WIDTH / 2.0, IMAGE_HEIGHT / 2.0);
			}

			g2d.drawImage(icon.getImage(), 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT, null);
			g2d.setTransform(oldTransform); 
			
			// ==========================================
			// STICKER SYSTEM (Dynamic Top-Right Row)
			// ==========================================
			if (battleCard != null) {
				
				// --- DYNAMIC STAT CHECKS ---
				boolean isTargeted = battleCard.isBeingTargeted();
				boolean isShielded = battle.hasStatus(battleCard, "UNTARGETABLE") || 
									 battle.hasStatus(battleCard, "REFLECT") || 
									 battleCard.getDodges() > 0;
				boolean isDefDebuffed = battle.hasStatus(battleCard, "DEF_DOWN");
				boolean isAtkUp = battleCard.getAtk() > baseCard.getAtk();
				
				// === THE AURA FIX: Check the OPPONENT'S field for Buffo! ===
				boolean opponentHasBuffoAura = false;
				boolean isMyCard = battle.isCasterPlayer1(battleCard);
				BattleCard[][] enemyField = isMyCard ? battle.getField2() : battle.getField1();
				
				for (int r = 0; r < 2; r++) {
					for (int c = 0; c < 4; c++) {
						if (enemyField[r][c] != null && battle.hasStatus(enemyField[r][c], "AURAATKDOWN_3")) {
							opponentHasBuffoAura = true;
						}
					}
				}
				
				boolean isAtkDown = battleCard.getAtk() < baseCard.getAtk() || opponentHasBuffoAura;
				boolean isHpUp = battleCard.getHp() > baseCard.getHp();

				// --- THE DYNAMIC TRAY MATH ---
				int stickerSize = 30; // Shrunk slightly so 3 or 4 can fit in a row cleanly!
				int padding = 4;
				
				// Start exactly in the Top-Right corner!
				int currentX = IMAGE_WIDTH - stickerSize - padding;
				int currentY = padding;

				// --- 1. Target Crosshair ---
				if (isTargeted) {
					java.net.URL targetUrl = getClass().getResource("/images/target_sticker.png"); 
					if (targetUrl != null) {
						g2d.drawImage(new ImageIcon(targetUrl).getImage(), currentX, currentY, stickerSize, stickerSize, null);
					} else {
						g2d.setColor(new Color(255, 0, 0, 150));
						g2d.fillOval(currentX, currentY, stickerSize, stickerSize);
						g2d.setColor(Color.RED);
						g2d.setStroke(new BasicStroke(3));
						g2d.drawLine(currentX + (stickerSize/2), currentY + 5, currentX + (stickerSize/2), currentY + stickerSize - 5);
						g2d.drawLine(currentX + 5, currentY + (stickerSize/2), currentX + stickerSize - 5, currentY + (stickerSize/2));
					}
					currentX -= (stickerSize + padding); // Shift left for the next sticker!
				}

				// --- 2. Shields ---
				if (isShielded) {
					java.net.URL shieldUrl = getClass().getResource("/images/shield_sticker.png"); 
					if (shieldUrl != null) {
						g2d.drawImage(new ImageIcon(shieldUrl).getImage(), currentX, currentY, stickerSize, stickerSize, null);
					} else {
						g2d.setColor(new Color(0, 100, 255, 180)); 
						g2d.fillRoundRect(currentX, currentY, stickerSize, stickerSize, 8, 8);
						g2d.setColor(Color.WHITE);
						g2d.setFont(new Font("Arial", Font.BOLD, 18));
						g2d.drawString("S", currentX + 8, currentY + 22);
					}
					currentX -= (stickerSize + padding);
				} else if (isDefDebuffed) { 
					java.net.URL shieldMinusUrl = getClass().getResource("/images/debuff_sticker.png"); 
					if (shieldMinusUrl != null) {
						g2d.drawImage(new ImageIcon(shieldMinusUrl).getImage(), currentX, currentY, stickerSize, stickerSize, null);
					} else {
						g2d.setColor(new Color(150, 0, 200, 180)); 
						g2d.fillRoundRect(currentX, currentY, stickerSize, stickerSize, 8, 8);
						g2d.setColor(Color.WHITE);
						g2d.setFont(new Font("Arial", Font.BOLD, 22));
						g2d.drawString("-", currentX + 10, currentY + 22);
					}
					currentX -= (stickerSize + padding);
				}

				// --- 3. ATK Buffs/Debuffs ---
				if (isAtkUp) {
					java.net.URL swordPlusUrl = getClass().getResource("/images/sword_plus_asset.png"); 
					if (swordPlusUrl != null) {
						g2d.drawImage(new ImageIcon(swordPlusUrl).getImage(), currentX, currentY, stickerSize, stickerSize, null);
					} else {
						g2d.setColor(new Color(0, 200, 0, 180)); 
						g2d.fillOval(currentX, currentY, stickerSize, stickerSize);
						g2d.setColor(Color.WHITE);
						g2d.setFont(new Font("Arial", Font.BOLD, 20));
						g2d.drawString("+", currentX + 8, currentY + 22);
					}
					currentX -= (stickerSize + padding);
				} else if (isAtkDown) {
					java.net.URL swordMinusUrl = getClass().getResource("/images/atk_decrease_sticker.png"); 
					if (swordMinusUrl != null) {
						g2d.drawImage(new ImageIcon(swordMinusUrl).getImage(), currentX, currentY, stickerSize, stickerSize, null);
					} else {
						g2d.setColor(new Color(200, 0, 0, 180)); 
						g2d.fillOval(currentX, currentY, stickerSize, stickerSize);
						g2d.setColor(Color.WHITE);
						g2d.setFont(new Font("Arial", Font.BOLD, 24));
						g2d.drawString("-", currentX + 10, currentY + 21);
					}
					currentX -= (stickerSize + padding);
				}

				// --- 4. HP Up ---
				if (isHpUp) {
					java.net.URL heartPlusUrl = getClass().getResource("/images/heart_plus_asset.png"); 
					if (heartPlusUrl != null) {
						g2d.drawImage(new ImageIcon(heartPlusUrl).getImage(), currentX, currentY, stickerSize, stickerSize, null);
					} else {
						g2d.setColor(new Color(255, 100, 150, 180)); 
						g2d.fillOval(currentX, currentY, stickerSize, stickerSize);
						g2d.setColor(Color.WHITE);
						g2d.setFont(new Font("Arial", Font.BOLD, 20));
						g2d.drawString("+", currentX + 8, currentY + 22);
					}
					currentX -= (stickerSize + padding);
				}
			}

			g2d.dispose();

			JLabel label = new JLabel(new ImageIcon(bi));
			label.setHorizontalAlignment(SwingConstants.CENTER);
			return label;
		}
	// ==========================================
	// Attack Handler
	// ==========================================

	/*
	 * private void handleAttack() { // A tiny helper class just to format the
	 * dropdown text! class CardItem { BattleCard bc; String label;
	 * 
	 * CardItem(BattleCard bc, String label) { this.bc = bc; this.label = label; }
	 * 
	 * public String toString() { return label; } // This is what the dropdown
	 * actually displays }
	 * 
	 * List<BattleCard> attackers = battle.getAttackableCards();
	 * 
	 * if (attackers.isEmpty()) { showToast("No cards can attack."); return; }
	 * 
	 * // Build a beautiful, readable list of your attackers CardItem[]
	 * attackerItems = new CardItem[attackers.size()]; for (int i = 0; i <
	 * attackers.size(); i++) { BattleCard bc = attackers.get(i); attackerItems[i] =
	 * new CardItem(bc, "Your " + bc.getBaseCard().getName() + " (ATK: " +
	 * bc.getAtk() + ")"); }
	 * 
	 * CardItem selectedAttacker = (CardItem) JOptionPane.showInputDialog(this,
	 * "Choose Your Attacker", "Attack", JOptionPane.PLAIN_MESSAGE, null,
	 * attackerItems, attackerItems[0]);
	 * 
	 * if (selectedAttacker == null) return; BattleCard attacker =
	 * selectedAttacker.bc; // Extract the real card
	 * 
	 * List<BattleCard> targets = battle.getEnemyCards();
	 * 
	 * if (targets.isEmpty()) { showToast("No targets available."); return; }
	 * 
	 * // Build a beautiful, readable list of the enemy targets CardItem[]
	 * targetItems = new CardItem[targets.size()]; for (int i = 0; i <
	 * targets.size(); i++) { BattleCard bc = targets.get(i); targetItems[i] = new
	 * CardItem(bc, "Enemy " + bc.getBaseCard().getName() + " (HP: " + bc.getHp() +
	 * ")"); }
	 * 
	 * CardItem selectedTarget = (CardItem) JOptionPane.showInputDialog(this,
	 * "Choose Enemy Target", "Attack", JOptionPane.PLAIN_MESSAGE, null,
	 * targetItems, targetItems[0]);
	 * 
	 * if (selectedTarget == null) return; BattleCard target = selectedTarget.bc; //
	 * Extract the real card
	 * 
	 * battle.attack(attacker, target); refreshBoard();
	 * 
	 * }
	 */

	private List<cards> buildDeck(List<String> ids, Map<String, cards> allCards) {

		List<cards> deck = new ArrayList<>();

		for (String id : ids) {
			if (allCards.containsKey(id)) {
				deck.add(allCards.get(id));
			}
		}

		return deck;
	}
	
	// === NEW HELPER: The actual punch thrown after all tricks and animations finish! ===
	private void executeFinalAttack(BattleCard attacker, BattleCard target) {
	    try {
	        // 1. The engine calculates the damage and syncs the network
	        boolean fatalBlow = battle.attack(attacker, target);
	        
	        // 2. Check for ON_KILL triggers!
	        if (fatalBlow) {
	            String attackerId = attacker.getBaseCard().getCardID();
	            String commandStr = allSpecials.get(attackerId);
	            
	            if (commandStr != null && commandStr.contains("ON_KILL")) {
	                // === THE FIX: Slice out ONLY the ON_KILL segment! ===
	                String killSlice = SpecialsLibrary.extractSegment(commandStr, "ON_KILL");
	                
	                if (killSlice != null) {
	                    java.util.List<BattleCard> emptyTargets = new java.util.ArrayList<>();
	                    battle.broadcastSpecialExecution(attacker, emptyTargets, killSlice);
	                    SpecialsLibrary.parseAndExecute(battle, killSlice, true, attacker, emptyTargets);
	                }
	            }
	        }
	        
	        // 3. Repaint the GUI so the new HP values show up!
	        refreshBoard(); 
	        
	    } catch (Exception ex) {
	        System.err.println("CRASH IN FINAL ATTACK EXECUTION: " + ex.getMessage());
	        ex.printStackTrace();
	    }
	}
	
	// === NEW HELPER: Generalized Special Execution ===
	private void triggerSpecialFromUI(BattleCard caster, String activeSlice) {
	    if (activeSlice == null || activeSlice.trim().isEmpty() || activeSlice.equals("NONE")) {
	        showToast("This ability is passive and cannot be clicked!", 1500);
	        return;
	    }

	    String effectiveCommand = SpecialsLibrary.getEffectiveCommand(battle, activeSlice);
	    int neededTargets = extractTargetCount(effectiveCommand);

	    List<BattleCard> possibleTargets = new ArrayList<>();
	    if (effectiveCommand.contains("target_any")) {
	        possibleTargets.addAll(battle.getFriendlyField(isLocalPlayer1));
	        possibleTargets.addAll(battle.getEnemyCards());
	        possibleTargets.remove(caster); 
	    }  else if (effectiveCommand.contains("target_ally")) {
	        possibleTargets = battle.getFriendlyField(isLocalPlayer1);
	        possibleTargets.remove(caster); 
	        if (effectiveCommand.contains("constructor_menu")) {
	            possibleTargets.removeIf(ally -> !ally.getBaseCard().getCardID().equals("01991"));
	        }
	    } else {
	        possibleTargets = battle.getEnemyCards();
	    }

	    // === THE FIX: Ensure the UI math ignores Silenced cards! ===
	    if (effectiveCommand.contains("steal_special")) {
	        possibleTargets.removeIf(target -> battle.hasStatus(target, "SILENCED"));
	    }

	    if (neededTargets > possibleTargets.size()) {
	        showToast("Not enough valid targets! You need " + neededTargets + ".", 2000);
	        return; 
	    }

	  
	    if (neededTargets > 0) {
	        isSpecialTargetingMode = true;
	        requiredSpecialTargets = neededTargets;
	        selectedSpecialTargets.clear();
	        specialCaster = caster;
	        pendingSpecialCommand = activeSlice;
	        showToast("Select " + neededTargets + " target(s) for the special!", 2000);
	        refreshBoard();
	    } else {
	        String packagedCommand = loadTheDice(activeSlice);
	        battle.broadcastSpecialExecution(caster, null, packagedCommand);
	        SpecialsLibrary.parseAndExecute(battle, packagedCommand, true, caster, null);
	        refreshBoard();
	    }
	}

	// ==========================================
	// MAIN FOR TESTING
	// ==========================================

	// ==========================================
	// MAIN FOR TESTING
	// ==========================================

	public static void main(String[] args) {

		String testUser1 = "jacob";
		String testUser2 = "jacob"; // change if needed

		SwingUtilities.invokeLater(() -> {
			// Quick popup to choose Player 1 or Player 2 for testing
			int choice = JOptionPane.showOptionDialog(null, "Launch as Player 1 or Player 2?", "Instance Launcher",
					JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
					new String[] { "Player 1", "Player 2" }, "Player 1");

			// If they close the window without picking, exit safely
			if (choice == JOptionPane.CLOSED_OPTION) {
				System.exit(0);
			}

			// If they clicked the first button ("Player 1"), this becomes true.
			boolean isP1 = (choice == JOptionPane.YES_OPTION);

			// Now we pass ALL THREE arguments!
			new BattleGUI(testUser1, testUser2, isP1);
		});
	}
}