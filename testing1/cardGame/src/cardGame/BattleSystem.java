package cardGame;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;
import java.util.function.Consumer;

public class BattleSystem implements BattleAPI {

	private Random syncedRng;

	// Tracks the last used special for the Pirate Parrot!
	private String lastCastSpecial = "";

	private List<cards> deck1;
	private List<cards> deck2;

	private List<cards> hand1;
	private List<cards> hand2;

	// The backend uses this to hand the stolen card over to the GUI
	public cards stolenCardReadyForPlacement = null;

	public boolean isGraveyardAutoPlaceMode = false;
	public int autoPlaceRow = -1;
	public int autoPlaceCol = -1;
	public BattleCard autoPlaceCaster = null;

	public DefensiveCoinflipBridge onDefensiveCoinflipRequest;

	private cards queuedAutoSelectCard = null;
	public boolean isStealPlacementMode = false;
	public String pendingStealId = "";
	private BattleCard[][] field1;
	private BattleCard[][] field2;
	private Consumer<BattleCard> graveyardUICallback;
	// === NETWORK VARIABLES ===
	private PrintWriter out;
	private BufferedReader in;
	private String myPlayerId;
	private Runnable onBoardUpdate; // This tells the GUI to
	private Runnable onEnemyDisconnect; // Tells GUI the opponent closed the game
	// === UPDATED: Now sends raw booleans (isHeads, isLocalPlayer1) to the GUI ===
	private java.util.function.BiConsumer<Boolean, Boolean> onCoinflip;
	private java.util.function.Consumer<String> onToast; // <--- NEW: Tells GUI to show a toast!
	// === THE FIX: Make this public so the Library can read card names for the menu! ===
		public Map<String, cards> allCards;
	private boolean isLocalPlayer1;
	private String myDeckString;
	private long sharedRandomSeed = 12345; // Default fallback
	// 2. The Engine (The RNG object locked to that specific blueprint)
		private Random sharedRng = new Random(sharedRandomSeed);
		private Random specialRng = new Random(888); // The Glass Box Seed!
	// Near the top
	private List<cards> graveyard = new ArrayList<>();
	// === REMATCH VARIABLES ===
	private boolean localWantsRematch = false;
	private boolean enemyWantsRematch = false;
	private boolean enemyDeclined = false;
	// === READY PHASE VARIABLES ===
	private boolean localReady = false;
	private boolean enemyReady = false;
	private Runnable onRematchStateChange; // Tells GUI if enemy answered
	private Runnable onGameReset; // Tells GUI to redraw everything
	private SpecialCoinflipListener specialCoinflipCallback;
	// === THE FIX: Use LinkedHashMap to guarantee identical iteration order across the network! ===
		private Map<BattleCard, Map<String, Integer>> activeStatuses = new java.util.LinkedHashMap<>();

	// === NEW: The Engine needs to read the dictionary for Passives! ===
	public java.util.Map<String, String> allSpecials;

	// === PERSISTENT CARD STATES ===
	private Map<cards, Integer> savedCardHp = new HashMap<>();
	private List<cards> lockedCards = new ArrayList<>();
	// Import java.util.function.Consumer; at the top if needed!
	private java.util.function.BiConsumer<BattleCard, Boolean> deathCallback;

	public int queuedAutoSelectIndex = -1;

	public Runnable onForceGraveyardOpen;

	// At the bottom with your getters

	private boolean p1Turn;

	// === UPDATED CONSTRUCTOR ===
	public BattleSystem(List<cards> deck1, List<cards> deck2, Map<String, cards> allCards, boolean isLocalPlayer1) {

		this.deck1 = new ArrayList<>(deck1);
		this.deck2 = new ArrayList<>(deck2);

		this.hand1 = new ArrayList<>();
		this.hand2 = new ArrayList<>();

		this.field1 = new BattleCard[2][4];
		this.field2 = new BattleCard[2][4];

		this.allCards = allCards;
		this.isLocalPlayer1 = isLocalPlayer1;
		this.p1Turn = true;
		

		// Notice we removed the shuffle and deal logic!
		// We can't deal hands until the opponent arrives with their deck!
	}
	// Add this getter anywhere in the class
		public Random getSpecialRng() {
			return specialRng;
		}

	public interface SpecialCoinflipListener {
		void onFlip(boolean isHeads, String headsMsg, String tailsMsg, Runnable onComplete);
	}

	public interface DefensiveCoinflipBridge {
		void playAnimation(boolean isHeads, String headsMsg, String tailsMsg, Runnable onComplete);
	}

	public void setGraveyardUICallback(Consumer<BattleCard> callback) {
		this.graveyardUICallback = callback;
	}

	public void setDeathCallback(java.util.function.BiConsumer<BattleCard, Boolean> callback) {
		this.deathCallback = callback;
	}

	// === NEW: Let the GUI give us the toast method ===
	public void setToastCallback(java.util.function.Consumer<String> toastCallback) {
		this.onToast = toastCallback;
	}

	// === UPDATED: Setter for the new BiConsumer ===
	public void setCoinflipCallback(java.util.function.BiConsumer<Boolean, Boolean> callback) {
		this.onCoinflip = callback;
	}

	public void setDisconnectCallback(Runnable callback) {
		this.onEnemyDisconnect = callback;
	}

	public void setSpecialCoinflipCallback(SpecialCoinflipListener callback) {
		this.specialCoinflipCallback = callback;
	}

	// Helper methods for the GUI
	public int getSavedHp(cards c) {
		return savedCardHp.containsKey(c) ? savedCardHp.get(c) : c.getHp();
	}

	public boolean isCardLocked(cards c) {
		return lockedCards.contains(c);
	}

	// === UPDATED: Now requires your local deck IDs ===
	// === UPDATED: No more shouting into empty rooms! ===
	public void startNetwork(String playerId, Runnable guiRefreshCallback, List<String> localDeckIDs) {
		this.myPlayerId = playerId;
		this.onBoardUpdate = guiRefreshCallback;

		this.myDeckString = String.join(",", localDeckIDs);

		try {
			Socket socket = new Socket("localhost", 5000);
			out = new PrintWriter(socket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

			out.println("SYSTEM_JOIN_" + myPlayerId);

			// Just shout our deck to the room. We will wait to sync seeds!
			out.println("CMD:DECK:" + myDeckString + " - Sent by: " + myPlayerId);
			new Thread(() -> {
				try {
					String msg;
					while ((msg = in.readLine()) != null) {

						// === THE FIX: The Inner Try-Catch ===
						// This absorbs data collisions so the socket doesn't die!
						try {
							processNetworkCommand(msg);
						} catch (Exception syncError) {
							System.err.println("Ignored a background sync glitch: " + syncError.getMessage());
						}

					}
					// If we cleanly drop out of the loop, the socket actually closed
					handleOpponentDisconnect();

				} catch (Exception e) {
					// If the socket violently crashes
					handleOpponentDisconnect();
				}
			}).start();
		} catch (Exception e) {
			System.out.println("Could not connect to server.");
		}
	}

	private void processNetworkCommand(String msg) {

		// === THE FIX: Bulletproof Echo Filter ===
		// We trim everything and ignore case to guarantee hidden spaces don't break it!
		if (msg != null && msg.toUpperCase().contains(("- Sent by: " + myPlayerId).toUpperCase().trim())) {
			System.out.println("NETWORK: Caught and dropped own echo!");
			return;
		}
		/*
		 * // === THE FIX: If the message contains our own ID, drop it immediately! ===
		 * if (msg.contains(myPlayerId)) { return; }
		 */

		if (msg.startsWith("CMD:END_TURN")) {
			// === THE FIX: The network now ticks down the statuses and clears card locks! ===
			processTurnAdvancement();
			
			if (onBoardUpdate != null)
				onBoardUpdate.run();
		}

		// === NEW: Catch the random seed from Player 1 ===
		else if (msg.startsWith("CMD:SYNC_SEED:")) {
			try {
				// Splits "CMD:SYNC_SEED:173849203" and grabs the number
				this.sharedRandomSeed = Long.parseLong(msg.split(":")[2].trim());
			} catch (Exception e) {
				System.out.println("Failed to parse seed: " + msg);
			}
		}
		
		// === DEFENSIVE COINFLIP SYNC ===
		else if (msg.startsWith("CMD:DEFEND_ANIM:")) {
		    try {
		    	String payload = msg.split(" - Sent by:")[0].substring("CMD:DEFEND_ANIM:".length());
		        String[] parts = payload.split("\\|");
		        boolean isHeads = Boolean.parseBoolean(parts[0]);
		        String headsMsg = parts[1];
		        String tailsMsg = parts[2];
		        
		        if (onDefensiveCoinflipRequest != null) {
		            // Queue the animation for the defending player!
		            // We pass an empty Runnable because the actual CMD:ATTACK packet 
		            // will arrive 1.5 seconds later to execute the actual damage!
		            onDefensiveCoinflipRequest.playAnimation(isHeads, headsMsg, tailsMsg, () -> {});
		        }
		    } catch (Exception e) {
		        System.out.println("Error parsing network defense animation: " + msg);
		    }
		}

		// === BULLETPROOF DECK SYNC (FIXED RACE CONDITION) ===
		else if (msg.startsWith("CMD:DECK:") || msg.startsWith("CMD:DECK_REPLY:")) {
			try {
				boolean isReply = msg.startsWith("CMD:DECK_REPLY:");
				String prefix = isReply ? "CMD:DECK_REPLY:" : "CMD:DECK:";

				String payload = msg.substring(prefix.length());
				String idString = payload.split(" - Sent by:")[0].trim();

				System.out.println("SYNCING DECK! Received IDs: [" + idString + "]");
				String[] enemyIDs = idString.split(",");
				List<cards> enemyDeck = isLocalPlayer1 ? deck2 : deck1;
				enemyDeck.clear();

				for (String id : enemyIDs) {
					String cleanId = id.trim();
					if (allCards.containsKey(cleanId))
						enemyDeck.add(allCards.get(cleanId));
				}
				System.out.println("Enemy Deck successfully populated with " + enemyDeck.size() + " cards.");

				// If hands are empty, setup seed but WAIT for READY to deal!
				if (hand1.isEmpty() && hand2.isEmpty()) {
					if (!isReply) {
						long newSeed = System.currentTimeMillis();
						this.sharedRandomSeed = newSeed;
						if (out != null)
							out.println("CMD:SYNC_SEED:" + newSeed);
						if (out != null)
							out.println("CMD:DECK_REPLY:" + myDeckString + " - Sent by: " + myPlayerId);
					}
				}

				checkReady(); // Check if they already clicked ready!
				if (onBoardUpdate != null)
					onBoardUpdate.run();

			} catch (Exception e) {
				System.out.println("Error parsing deck sync: " + msg);
			}
		}

		// === UNIVERSAL SPECIAL RECEIVER ===
		else if (msg.startsWith("CMD:CAST_SPECIAL:")) {
			try {
				// Strip the meta-data
				// === THE FIX: Use explicit " - Sent by:" so arrows don't break the string! ===
				String payload = msg.split(" - Sent by:")[0].substring("CMD:CAST_SPECIAL:".length());

				// Expected format: CasterCoords | TargetCoords | RawCommandString
				String[] parts = payload.split("\\|", 3);

				BattleCard caster = getCardFromCoords(parts[0]);
				List<BattleCard> targets = new ArrayList<>();

				if (!parts[1].equals("NONE")) {
					for (String tCoord : parts[1].split(",")) {
						BattleCard t = getCardFromCoords(tCoord);
						if (t != null)
							targets.add(t);
					}
				}

				String commandStr = parts[2];

				// Execute on the opponent's screen!
				// isLocalPlayerCasting is FALSE because the opponent fired it!
				SpecialsLibrary.parseAndExecute(this, commandStr, false, caster, targets);

			} catch (Exception e) {
				System.out.println("Network sync error for Special Cast: " + msg);
			}
		}

		// === OPPONENT DREW CARD ===
		else if (msg.startsWith("CMD:DRAW:")) {
			boolean player1Drew = msg.contains("CMD:DRAW:P1");
			drawCard(player1Drew);
			if (onBoardUpdate != null)
				onBoardUpdate.run();
		}

		// === BULLETPROOF PLACE CARD (UPDATED FOR 2D) ===
		else if (msg.startsWith("CMD:PLACE:")) {
			try {
				// Safely extract the payload before the " - Sent by:" part
				String payload = msg.substring("CMD:PLACE:".length());
				// === THE FIX: Use explicit " - Sent by:" ===
				String coordString = payload.split(" - Sent by:")[0].trim();

				// Split the coordinates by the colon
				String[] parts = coordString.split(":");
				int handIndex = Integer.parseInt(parts[0]);
				int row = Integer.parseInt(parts[1]);
				int col = Integer.parseInt(parts[2]);

				// Now we pass ALL THREE numbers into the helper!
				networkPlaceCard(handIndex, row, col);
				if (onBoardUpdate != null)
					onBoardUpdate.run();

			} catch (Exception e) {
				System.out.println("Error parsing 2D network place command: " + msg);
			}
		}

		// === BULLETPROOF ATTACK (UPDATED FOR FRIENDLY FIRE) ===
		else if (msg.startsWith("CMD:ATTACK:")) {
			try {
				String payload = msg.substring("CMD:ATTACK:".length());
				String coords = payload.split(" - Sent by:")[0].trim();
				
				String[] parts = coords.split(":");
				int aRow = Integer.parseInt(parts[0]);
				int aCol = Integer.parseInt(parts[1]);
				int tRow = Integer.parseInt(parts[2]);
				int tCol = Integer.parseInt(parts[3]);

				// === THE FIX: Catch the Friendly Fire flag! ===
				// Default to false for backwards compatibility with older attacks
				boolean isFriendlyFire = false;
				if (parts.length > 4) {
					isFriendlyFire = Boolean.parseBoolean(parts[4]);
				}
				
				// === THE FIX: Extract the forced damage from the packet! ===
				int forcedDamage = -1;
				if (parts.length > 5) {
					forcedDamage = Integer.parseInt(parts[5]);
				}

				// Pass the flag into the network attack method
				networkAttack(aRow, aCol, tRow, tCol, isFriendlyFire, forcedDamage);
				if (onBoardUpdate != null)
					onBoardUpdate.run();

			} catch (Exception e) {
				System.out.println("Error parsing 2D network attack command: " + msg);
			}
		}
		// === REMATCH NETWORK TRIGGERS ===
		else if (msg.startsWith("CMD:REMATCH:YES")) {
			this.enemyWantsRematch = true;
			if (onRematchStateChange != null)
				onRematchStateChange.run();
			checkRematchCondition();
		} else if (msg.startsWith("CMD:REMATCH:NO")) {
			this.enemyDeclined = true;
			if (onRematchStateChange != null)
				onRematchStateChange.run();
		}
		// === OPPONENT CLICKED READY ===
		else if (msg.startsWith("CMD:READY")) {
			this.enemyReady = true;
			checkReady();
		}
	}

	// === NEW HELPER: Forces the opponent to place a card from their hand ===
	// === UPDATED HELPER: Places the card at the exact coordinates ===
	private void networkPlaceCard(int handIndex, int row, int col) {
		List<cards> hand = getCurrentHand();
		BattleCard[][] field = p1Turn ? field1 : field2;

		if (handIndex >= 0 && handIndex < hand.size() && field[row][col] == null) {

			// 1. Create the card
			BattleCard newEnemyCard = new BattleCard(hand.remove(handIndex));

			// 2. Stamp the coordinates AND the Dog Tag permanently!
			newEnemyCard.setCoords(row, col);
			newEnemyCard.setOwnerIsP1(!isLocalPlayer1);

			// 3. Lock it into the grid

			// 3. Lock it into the grid
			field[row][col] = newEnemyCard;

			// 4. === THE TRIGGER ===
			// Now that the card is officially on the board, fire its ON_PLAY abilities!
			// (Notice we pass 'false' because the opponent played this, not us!)
			triggerEvent(newEnemyCard, false, "ON_PLAY", "ON_SUMMON", "ENTER_FIELD");

		} else {
			System.out.println("Network Place Failed! Hand size: " + hand.size() + ", Requested Index: " + handIndex
					+ ", Requested Target: Row " + row + " Col " + col);
		}
	}

	// === UPDATED: Processes 2D network combat ===
	// === UPDATED: Processes 2D network combat (Now supports Friendly Fire!) ===
	private void networkAttack(int aRow, int aCol, int tRow, int tCol, boolean isFriendlyFire,int forcedDamage) {
		BattleCard[][] activeField = p1Turn ? field1 : field2;
		BattleCard[][] inactiveField = p1Turn ? field2 : field1;

		if (aRow >= 0 && aRow < 2 && aCol >= 0 && aCol < 4 && tRow >= 0 && tRow < 2 && tCol >= 0 && tCol < 4) {

			BattleCard attacker = activeField[aRow][aCol];

			// === THE FIX: If Friendly Fire is true, the target is on the ATTACKER'S side!
			// ===
			BattleCard target = isFriendlyFire ? activeField[tRow][tCol] : inactiveField[tRow][tCol];

			if (attacker != null && target != null) {
				int damageDealt;
				if (forcedDamage != -1) {
					damageDealt = forcedDamage;
				} else {
					damageDealt = SpecialsLibrary.applyDamageModifiers(this, attacker, target, attacker.getAtk());
				}
				
				target.setHp(target.getHp() - damageDealt);
				attacker.useAction();
				
				// === NEW: ON_ATTACK TRIPWIRE FOR NETWORK ===
				// === NEW: ON_ATTACK TRIPWIRE ===
				try {
					String attackerId = attacker.getBaseCard().getCardID();
					String fullCommandStr = allSpecials != null ? allSpecials.get(attackerId) : null;
					String attackSlice = SpecialsLibrary.extractSegment(fullCommandStr, "ON_ATTACK");
					
					if (attackSlice != null) {
						boolean isLocalAttackerOwner = (isCasterPlayer1(attacker) == isLocalPlayer1);
						
						// Package the victim into a list so the Battle Bot knows who to deal bonus damage to!
						java.util.List<BattleCard> victimList = new java.util.ArrayList<>();
						victimList.add(target);
						
						SpecialsLibrary.parseAndExecute(this, attackSlice, isLocalAttackerOwner, attacker, victimList);
					}
				} catch (Exception ex) {
					System.err.println("CRASH IN ON_ATTACK TRIGGER!");
					ex.printStackTrace();
				}
				// === NEW: ON_DEFEND TRIPWIRE (Executes on both screens simultaneously!) ===
				try {
					String targetId = target.getBaseCard().getCardID();
					String fullCommandStr = allSpecials != null ? allSpecials.get(targetId) : null;
					String defendSlice = SpecialsLibrary.extractSegment(fullCommandStr, "ON_DEFEND");
					
					// Only trigger passive post-hit abilities here! 
					if (defendSlice != null && (defendSlice.contains("berry_drop") || defendSlice.contains("vine_thorns"))) {
						boolean isLocalTargetOwner = (isCasterPlayer1(target) == isLocalPlayer1);
						
						// Package the attacker into a list so Vine Thorns knows who to hit back!
						java.util.List<BattleCard> hitList = new java.util.ArrayList<>();
						hitList.add(attacker);
						
						SpecialsLibrary.parseAndExecute(this, defendSlice, isLocalTargetOwner, target, hitList);
					}
				} catch (Exception ex) {
					System.err.println("CRASH IN ON_DEFEND TRIGGER!");
					ex.printStackTrace();
				}

				if (target.getHp() <= 0) {
					// === THE FIX: Route network deaths through the pipeline too! ===
					destroyCard(target);
				}
			}
		}
	}

	// ==================================================
	// GETTERS (Used by GUI)
	// ==================================================

	public List<cards> getCurrentHand() {
		return p1Turn ? hand1 : hand2;
	}

	// Returns flattened field as List for easier GUI use
	public List<BattleCard> getCurrentField() {
		return Arrays.asList(p1Turn ? field1[0] : field2[0]);
	}

	public List<BattleCard> getEnemyField() {
		return Arrays.asList(p1Turn ? field2[0] : field1[0]);
	}

	public boolean isPlayer1Turn() {
		return p1Turn;
	}

	public List<cards> getGraveyard() {
		return graveyard;
	}

	// ==================================================
	// NETWORK GETTERS (Fixed Perspectives)
	// ==================================================

	public List<cards> getLocalHand(boolean isLocalPlayer1) {
		return isLocalPlayer1 ? hand1 : hand2;
	}

	public List<BattleCard> getLocalField(boolean isLocalPlayer1) {
		return Arrays.asList(isLocalPlayer1 ? field1[0] : field2[0]);
	}

	public List<BattleCard> getNetworkEnemyField(boolean isLocalPlayer1) {
		return Arrays.asList(isLocalPlayer1 ? field2[0] : field1[0]);
	}

	// === NEW: 2D Field Getters ===
	public BattleCard[][] getLocalField2D(boolean isLocalPlayer1) {
		return isLocalPlayer1 ? field1 : field2;
	}

	public BattleCard[][] getNetworkEnemyField2D(boolean isLocalPlayer1) {
		return isLocalPlayer1 ? field2 : field1;
	}

	public void broadcastSpecialExecution(BattleCard caster, List<BattleCard> targets, String commandStr) {
		if (out == null)
			return;

		/*
		 * // === THE MUTE BUTTON === // Drop death commands so we don't cause infinite
		 * loops or inverted targeting! if (commandStr != null &&
		 * (commandStr.contains("bounce_to_hand") || commandStr.contains("donothing") ||
		 * commandStr.contains("revive_self"))) {
		 * System.out.println("NETWORK: Muted broadcast for native death ability: " +
		 * commandStr); return; // Drop the packet completely! }
		 */

		String casterCoords = getCardNetworkCoords(caster);

		List<String> targetCoords = new ArrayList<>();
		if (targets != null && !targets.isEmpty()) {
			for (BattleCard t : targets) {
				targetCoords.add(getCardNetworkCoords(t));
			}
		}
		String targetsPayload = targetCoords.isEmpty() ? "NONE" : String.join(",", targetCoords);

		// Looks like: CMD:CAST_SPECIAL:F1:0:0|F2:0:1,F2:0:2|01|CF|if_1...
		out.println("CMD:CAST_SPECIAL:" + casterCoords + "|" + targetsPayload + "|" + commandStr + " - Sent by: "
				+ myPlayerId);
	}

	// === UPDATED: Only Attack position (Row 0) can attack! ===
	public List<BattleCard> getAttackableCards() {
		List<BattleCard> attackers = new ArrayList<>();
		BattleCard[][] myField = p1Turn ? field1 : field2;

		for (int c = 0; c < 4; c++) {
			BattleCard bc = myField[0][c]; // Only check row 0
			if (bc != null && bc.getActions() > 0) {
				attackers.add(bc);
			}
		}
		return attackers;
	}

	// === UPDATED: Defense cards protect the Attack cards behind them! ===
	public List<BattleCard> getEnemyCards() {
		List<BattleCard> targets = new ArrayList<>();
		BattleCard[][] enemyField = p1Turn ? field2 : field1;

		for (int c = 0; c < 4; c++) {
			if (enemyField[1][c] != null) {
				// There is a Defense card here! It blocks the attack card.
				targets.add(enemyField[1][c]);
			} else if (enemyField[0][c] != null) {
				// No Defense card. The Attack card is exposed!
				targets.add(enemyField[0][c]);
			}
		}
		return targets;
	}

	// Enemy cards that exist

	// ==================================================
	// GAME ACTIONS (GUI CALLS THESE)
	// ==================================================

	// === UPDATED: Now requires the player ID ===
	public boolean draw(boolean isLocalPlayer1) {
		boolean success = drawCard(isLocalPlayer1);

		// Broadcast the draw, explicitly tagging it with WHO drew
		if (success && out != null) {
			String drawId = isLocalPlayer1 ? "P1" : "P2";
			out.println("CMD:DRAW:" + drawId + " - Sent by: " + myPlayerId);
		}

		return success;
	}

	// === UPDATED: No longer relies on p1Turn! ===
	private boolean drawCard(boolean isLocalPlayer1) {
		// Look at the passed-in boolean to decide whose deck to use
		List<cards> deck = isLocalPlayer1 ? deck1 : deck2;
		List<cards> hand = isLocalPlayer1 ? hand1 : hand2;

		if (hand.size() >= 4) {
			return false;
		}

		if (deck.isEmpty())
			return false;

		hand.add(deck.remove(0));
		return true;
	}

	public boolean placeCard(cards card, int row, int col) {
		// === THE LOCKOUT CHECK ===
		// === THE MULTI-CARD LOCKOUT FIX ===
	    // Check how many copies of this card we have vs how many are actually locked!
	   

		List<cards> hand = getCurrentHand();
		BattleCard[][] field = p1Turn ? field1 : field2;
		
		 int handCount = java.util.Collections.frequency(hand, card);
		    int lockedCount = java.util.Collections.frequency(lockedCards, card);
		    
		    if (lockedCount >= handCount) {
		        return false; // ALL copies of this card in your hand are currently locked!
		    }

		int handIndex = hand.indexOf(card);
		if (handIndex == -1)
			return false;

		if (field[row][col] == null) {
			BattleCard newCard = new BattleCard(hand.remove(handIndex));

			// === NEW: Stamp the coordinates AND the Dog Tag permanently! ===
			newCard.setCoords(row, col);
			newCard.setOwnerIsP1(isLocalPlayer1);

			// === RESTORE PERSISTENT DAMAGE ===

			// === RESTORE PERSISTENT DAMAGE ===
			if (savedCardHp.containsKey(card)) {
				newCard.setHp(savedCardHp.get(card));
			}

			// Lock it into the grid
			field[row][col] = newCard;

			// === THE NEW HOOK: Fire ON_PLAY abilities! ===
			// We pass 'true' because the local player is the one placing this card!
			triggerEvent(newCard, true, "ON_PLAY", "ON_SUMMON", "ENTER_FIELD");

			if (out != null) {
				out.println("CMD:PLACE:" + handIndex + ":" + row + ":" + col + " - Sent by: " + myPlayerId);
			}
			return true;
		}
		return false;
	}

	// GUI passes actual BattleCard objects
	// GUI passes actual BattleCard objects
	// === UPDATED: Finds 2D coordinates for combat ===
	public boolean attack(BattleCard attacker, BattleCard target) {
		if (attacker == null || target == null)
			return false;
		if (attacker.getActions() <= 0)
			return false;

		int aRow = -1, aCol = -1, tRow = -1, tCol = -1;

		// === NEW: Friendly Fire Tracker ===
		boolean isFriendlyFire = false;

		BattleCard[][] myField = p1Turn ? field1 : field2;
		BattleCard[][] enemyField = p1Turn ? field2 : field1;

		// Find exactly where the attacker and target are sitting
		for (int r = 0; r < 2; r++) {
			for (int c = 0; c < 4; c++) {
				if (myField[r][c] == attacker) {
					aRow = r;
					aCol = c;
				}

				// Normal Attack (Crosses the board)
				if (enemyField[r][c] == target) {
					tRow = r;
					tCol = c;
				}
				// === NEW: Friendly Fire Attack! (Target is on my side!) ===
				else if (myField[r][c] == target) {
					tRow = r;
					tCol = c;
					isFriendlyFire = true;
				}
			}
		}

		// === THE FIX: The backend is dumb again! It asks the Library for the final
		// math. ===
		// === THE FIX: Force the network receiver to use the Library math! ===
		int baseDamage = attacker.getAtk();
		int damageDealt = SpecialsLibrary.applyDamageModifiers(this, attacker, target, baseDamage);

		target.setHp(target.getHp() - damageDealt);
		attacker.useAction();
		
		// === NEW: ON_ATTACK TRIPWIRE ===
		// === NEW: ON_ATTACK TRIPWIRE ===
		try {
			String attackerId = attacker.getBaseCard().getCardID();
			String fullCommandStr = allSpecials != null ? allSpecials.get(attackerId) : null;
			String attackSlice = SpecialsLibrary.extractSegment(fullCommandStr, "ON_ATTACK");
			
			if (attackSlice != null) {
				boolean isLocalAttackerOwner = (isCasterPlayer1(attacker) == isLocalPlayer1);
				
				// Package the victim into a list so the Battle Bot knows who to deal bonus damage to!
				java.util.List<BattleCard> victimList = new java.util.ArrayList<>();
				victimList.add(target);
				
				SpecialsLibrary.parseAndExecute(this, attackSlice, isLocalAttackerOwner, attacker, victimList);
			}
		} catch (Exception ex) {
			System.err.println("CRASH IN ON_ATTACK TRIGGER!");
			ex.printStackTrace();
		}
		// === NEW: ON_DEFEND TRIPWIRE (Executes on both screens simultaneously!) ===
				try {
					String targetId = target.getBaseCard().getCardID();
					String fullCommandStr = allSpecials != null ? allSpecials.get(targetId) : null;
					String defendSlice = SpecialsLibrary.extractSegment(fullCommandStr, "ON_DEFEND");
					
					// Only trigger passive post-hit abilities here! 
					if (defendSlice != null && (defendSlice.contains("berry_drop") || defendSlice.contains("vine_thorns"))) {
						boolean isLocalTargetOwner = (isCasterPlayer1(target) == isLocalPlayer1);
						
						// Package the attacker into a list so Vine Thorns knows who to hit back!
						java.util.List<BattleCard> hitList = new java.util.ArrayList<>();
						hitList.add(attacker);
						
						SpecialsLibrary.parseAndExecute(this, defendSlice, isLocalTargetOwner, target, hitList);
					}
				} catch (Exception ex) {
					System.err.println("CRASH IN ON_DEFEND TRIGGER!");
					ex.printStackTrace();
				}
		

		boolean fatalBlow = false; // <--- NEW: Track if the hit was a kill!

		// Remove target if dead
		if (target.getHp() <= 0) {

			// === THE FIX: Route it through the official pipeline! ===
			// This handles the graveyard, the arrays, AND triggers the tripwire!
			destroyCard(target);

			fatalBlow = true; // <--- Keep this for ON_KILL effects!
		}

		// === THE FIX: Append the exact damage dealt to the packet! ===
				if (out != null && aRow != -1 && tRow != -1) {
					String ffFlag = isFriendlyFire ? ":true" : ":false";
					out.println(
							"CMD:ATTACK:" + aRow + ":" + aCol + ":" + tRow + ":" + tCol + ffFlag + ":" + damageDealt + " - Sent by: " + myPlayerId);
				}

		return fatalBlow; // <--- THE FIX: Return true ONLY if the target died!
	}

	public void endTurn() {
		// === THE FIX: Run the perfectly synced engine logic ===
		processTurnAdvancement();

		// Broadcast to the network
		if (out != null) {
			out.println("CMD:END_TURN - Sent by: " + myPlayerId);
		}
	}
	// ==================================================
	// WIN CHECK
	// ==================================================

	// ==================================================
	// WIN / LOSS CHECK
	// ==================================================

	// === UPDATED: Scans all 8 slots, the hand, and the deck! ===
	public boolean hasPlayerLost(boolean checkPlayer1) {
		List<cards> deck = checkPlayer1 ? deck1 : deck2;
		List<cards> hand = checkPlayer1 ? hand1 : hand2;
		BattleCard[][] field = checkPlayer1 ? field1 : field2;

		// 1. If they have cards in their deck or hand, they haven't lost!
		if (!deck.isEmpty() || !hand.isEmpty()) {
			return false;
		}

		// 2. Scan all 8 slots on their field (both Attack and Defense rows)
		for (int r = 0; r < 2; r++) {
			for (int c = 0; c < 4; c++) {
				if (field[r][c] != null) {
					return false; // Found a surviving card! They are still alive.
				}
			}
		}

		// 3. If deck, hand, and all 8 field slots are totally empty... they are wiped
		// out.
		return true;
	}

	// ==================================================
	// PRIVATE HELPERS
	// ==================================================

	private void dealHand(List<cards> hand, List<cards> deck) {
		for (int i = 0; i < 4 && !deck.isEmpty(); i++) {
			hand.add(deck.remove(0));
		}
	}

	private void resetActions(BattleCard[][] field) {
		for (int r = 0; r < 2; r++) { // <--- Loop through both rows
			for (int c = 0; c < 4; c++) {
				if (field[r][c] != null) {
					// Force defense cards (row 1) to have 0 actions so they can't attack!
					if (r == 1)
						field[r][c].useAction();
					else
						field[r][c].resetActions();
				}
			}
		}
	}

	// ==================================================
	// POST-GAME HUB LOGIC
	// ==================================================
	public void setRematchCallbacks(Runnable stateChange, Runnable gameReset) {
		this.onRematchStateChange = stateChange;
		this.onGameReset = gameReset;
	}

	public boolean hasEnemyDeclined() {
		return enemyDeclined;
	}

	public boolean doesEnemyWantRematch() {
		return enemyWantsRematch;
	}

	public void requestRematch() {
		this.localWantsRematch = true;
		if (out != null)
			out.println("CMD:REMATCH:YES - Sent by: " + myPlayerId);
		checkRematchCondition();
	}

	public void rejectRematch() {
		if (out != null)
			out.println("CMD:REMATCH:NO - Sent by: " + myPlayerId);
	}

	public void setReady() {
		this.localReady = true;
		if (out != null)
			out.println("CMD:READY - Sent by: " + myPlayerId);
		checkReady();
	}

	private void checkReady() {
		if (localReady && enemyReady && hand1.isEmpty() && hand2.isEmpty() && !deck1.isEmpty() && !deck2.isEmpty()) {

			// 1. Create a LOCAL seeded random just for the setup phase
			Random setupRng = new Random(this.sharedRandomSeed);
			
			// 2. Shuffle both decks using the SETUP RNG
			// Because both computers use the same seed and see the same deck sizes, 
			// the shuffle will be identical on both screens!
			Collections.shuffle(this.deck1, setupRng);
			Collections.shuffle(this.deck2, setupRng);

			dealHand(hand1, this.deck1);
			dealHand(hand2, this.deck2);

			// 3. Use the SETUP RNG for the turn flip
			this.p1Turn = setupRng.nextBoolean();

			// 4. IMPORTANT: Re-initialize your Battle RNG with a fresh seed
			// This "Resets the deck" for the actual match so both players start at Card #1
			this.syncedRng = new Random(this.sharedRandomSeed + 99); 

			if (onCoinflip != null) {
				onCoinflip.accept(this.p1Turn, this.isLocalPlayer1);
			}

			if (onBoardUpdate != null)
				onBoardUpdate.run();
		}
	}

	private void checkRematchCondition() {
		if (localWantsRematch && enemyWantsRematch) {
			resetFullGame();
		}
	}

	private void resetFullGame() {
		// 1. Reset states
		localWantsRematch = false;
		enemyWantsRematch = false;
		enemyDeclined = false;
		localReady = false;
		enemyReady = false;
		graveyard.clear();
		
		// === THE MEMORY LEAK FIX: Completely wipe the engine's memory of old sticky notes! ===
				activeStatuses.clear();

		// 2. Wipe all 8 field slots
		for (int r = 0; r < 2; r++) {
			for (int c = 0; c < 4; c++) {
				field1[r][c] = null;
				field2[r][c] = null;
			}
		}

		// 3. Empty hands & decks
		hand1.clear();
		hand2.clear();
		deck1.clear();
		deck2.clear();

		// === THE FIX: Rebuild our local deck before the game starts! ===
		List<cards> localDeck = isLocalPlayer1 ? deck1 : deck2;
		String[] myIDs = myDeckString.split(",");
		for (String id : myIDs) {
			String cleanId = id.trim();
			if (allCards.containsKey(cleanId)) {
				localDeck.add(allCards.get(cleanId));
			}
		}

		// 4. Player 1 generates a brand new shuffle seed for the rematch!
		if (isLocalPlayer1) {
			long newSeed = System.currentTimeMillis();
			this.sharedRandomSeed = newSeed;
			out.println("CMD:SYNC_SEED:" + newSeed);
		}

		// 5. Resend our deck IDs. This automatically triggers the new deal!
		if (out != null)
			out.println("CMD:DECK:" + myDeckString + " - Sent by: " + myPlayerId);

		// 6. Tell the GUI to redraw the fresh board
		if (onGameReset != null)
			onGameReset.run();
	}

	private void handleOpponentDisconnect() {
		this.enemyDeclined = true; // Force the rematch state to declined

		// 1. If we are in the post-game menu, update the buttons
		if (onRematchStateChange != null) {
			onRematchStateChange.run();
		}

		// 2. If we are mid-game, trigger the game-over screen!
		if (onEnemyDisconnect != null) {
			onEnemyDisconnect.run();
		}
	}

	public String getCardNetworkCoords(BattleCard target) {
	    if (target == null) return "NONE";

	    for (int r = 0; r < 2; r++) {
	        for (int c = 0; c < 4; c++) {
	            if (field1[r][c] == target) return "F1:" + r + ":" + c;
	            if (field2[r][c] == target) return "F2:" + r + ":" + c;
	        }
	    }
	    
	    // === THE GHOST FIX: If it's dead, send its ID so the opponent can find it! ===
	    boolean isP1 = isCasterPlayer1(target);
	    return "GRAVE:" + (isP1 ? "P1:" : "P2:") + target.getBaseCard().getCardID();
	}

	public BattleCard getCardFromCoords(String coords) {
	    if (coords == null || coords.equals("NONE") || coords.isEmpty()) return null;

	    // === THE GHOST FIX: Reconstruct the dead card so the opponent sees the popup! ===
	    if (coords.startsWith("GRAVE:")) {
	        String[] parts = coords.split(":");
	        boolean isP1 = parts[1].equals("P1");
	        String cardId = parts[2].trim();
	        
	        cards baseCard = allCards.get(cardId);
	        if (baseCard == null) return null;
	        
	        // Build the Ghost and stamp its dog tags!
	        BattleCard ghost = new BattleCard(baseCard);
	        ghost.setOwnerIsP1(isP1);
	        return ghost;
	    }

	    String[] parts = coords.split(":");
	    boolean isF1 = parts[0].equals("F1");
	    int r = Integer.parseInt(parts[1]);
	    int c = Integer.parseInt(parts[2]);
	    return isF1 ? field1[r][c] : field2[r][c];
	}

	// === GENERALIZED EVENT TRIGGER ===
	// === GENERALIZED EVENT TRIGGER (Now supports Lists/Varargs!) ===
	// Call it like: triggerEvent(caster, isLocal, "ON_PLAY", "ON_SUMMON",
	// "ON_ENTER_BOARD");
	// === GENERALIZED EVENT TRIGGER (Now supports Lists/Varargs AND booleans!) ===
	public boolean triggerEvent(BattleCard caster, boolean isLocalCaster, String... eventKeywords) {
		if (caster == null || allSpecials == null) return false;
		
		// === ENFORCE THE DISABLE FLAG ===
		// If the card failed its coinflip, it is dead to the world. Skip the event!
		if (caster.isSpecialDisabled()) {
			return false; 
		}
		
		// === THE FIX: The Aura Catch-Up System ===
		// If a card is entering the board for ANY reason, immediately check for passive Auras!
		// === THE FIX: The Aura Catch-Up System ===
				// If a card is entering the board for ANY reason, immediately check for passive Auras!
				for (String kw : eventKeywords) {
				    if (kw.equals("ON_PLAY")) {
				        
				        // 1. Water Spirit Check
				        int waterShield = getAuraModifier(caster, "WATERSHIELD");
				        if (waterShield > 0) {
				            caster.setHp(caster.getHp() + waterShield);
				            System.out.println("SYSTEM: " + caster.getBaseCard().getName() + " entered a Water Shield! Gained +" + waterShield + " HP!");
				            updateBoard();
				        }
				        
				        // 2. === NEW: Mystic Dragon Check ===
				        int mysticAura = getAuraModifier(caster, "MYSTICAURA");
				        if (mysticAura > 0) {
				            // A. Buff the newly placed card!
				            caster.setAtk(caster.getAtk() + mysticAura);
				            caster.setHp(caster.getHp() + mysticAura);
				            System.out.println("SYSTEM: " + caster.getBaseCard().getName() + " bathed in Mystic Aura! Gained +" + mysticAura + "/+" + mysticAura + "!");
				            
				            // B. Find the Dragon and feed it a bonus action point!
				            boolean isP1 = isCasterPlayer1(caster);
				            BattleCard[][] myField = isP1 ? field1 : field2;
				            for (int r = 0; r < 2; r++) {
				                for (int c = 0; c < 4; c++) {
				                    BattleCard ally = myField[r][c];
				                    // If this specific ally is radiating the aura, it must be the Dragon!
				                    if (ally != null && getStatusModifier(ally, "MYSTICAURA") > 0) {
				                        ally.setActions(ally.getActions() + 1);
				                        System.out.println("SYSTEM: Mystic Dragon synergizes with the new arrival and gains +1 attack!");
				                    }
				                }
				            }
				            updateBoard();
				        }
				        
				        break; // Only apply it once!
				    }
				
		}

		try {
			if (allSpecials == null || caster == null)
				return false; // <--- THE FIX: Return false instead of just stoppingg

			String cardId = caster.getBaseCard().getCardID();
			String fullCommandStr = allSpecials.get(cardId);

			// Loop through every keyword in the list you passed in!
			for (String keyword : eventKeywords) {

				// Ask the Chef to slice out the exact event segment!
				String eventSlice = SpecialsLibrary.extractSegment(fullCommandStr, keyword);

				// If we found a match for this keyword, fire it and stop searching!
				if (eventSlice != null) {
					System.out
							.println("ENGINE: Triggering " + keyword + " effect for " + caster.getBaseCard().getName());
					SpecialsLibrary.parseAndExecute(this, eventSlice, isLocalCaster, caster, null);
					return true; // <--- THE FIX: We fired an event! Return true!
				}
			}
		} catch (Exception ex) {
			System.err.println("CRASH IN EVENT TRIGGER!");
			ex.printStackTrace();
		}

		return false; // <--- THE FIX: Nothing fired, let the engine know!
	}

	// === NEW HELPER: Scans for an event without triggering it ===
	public boolean hasEvent(BattleCard target, String triggerType) {
		if (target == null || allSpecials == null)
			return false;
		String specialStr = allSpecials.get(target.getBaseCard().getCardID());
		return specialStr != null && specialStr.contains(triggerType);
	}
	
	// === NEW HELPER: Instantly checks if I am the owner of this card ===
		public boolean doIOwnThisCard(BattleCard target) {
			if (target == null) return false;
			boolean isP1sCard = isCasterPlayer1(target);
			boolean amIPlayer1 = String.valueOf(myPlayerId).trim().equalsIgnoreCase("P1");
			return (isP1sCard && amIPlayer1) || (!isP1sCard && !amIPlayer1);
		}
		
		// === NEW HELPER: Guarantees both screens process the end of a turn identically! ===
		private void processTurnAdvancement() {
			// 1. Remove the summoning sickness lock from any bounced cards!
			lockedCards.clear();

			// 2. Reset the actions of the player whose turn is currently ending
			resetActions(p1Turn ? field1 : field2);
			
			// 3. Flip the turn!
			p1Turn = !p1Turn;
			
			

			// === THE GENERALIZED FIX: Fire ON_TURN_START for the new active player! ===
						BattleCard[][] currentField = p1Turn ? field1 : field2;
						boolean isLocalTurn = (p1Turn == isLocalPlayer1);

						for (int r = 0; r < 2; r++) {
							for (int c = 0; c < 4; c++) {
								if (currentField[r][c] != null) {
									// The engine blindly asks the dictionary if this card has an ON_TURN_START trick!
									triggerEvent(currentField[r][c], isLocalTurn, "ON_TURN_START");
								}
							}
						}

			Iterator<Map.Entry<BattleCard, Map<String, Integer>>> cardIt = activeStatuses.entrySet().iterator();
			while (cardIt.hasNext()) {
				Map.Entry<BattleCard, Map<String, Integer>> cardEntry = cardIt.next();
				BattleCard auraCard = cardEntry.getKey();

				// We check the NEW current player's field to see if the card belongs to them
				boolean belongsToCurrentPlayer = false;
				for (int r = 0; r < 2; r++) {
					for (int c = 0; c < 4; c++) {
						if (currentField[r][c] == auraCard) {
							belongsToCurrentPlayer = true;
							break;
						}
					}
				}

				// === THE FIX: Unwrap the loop! ===
				Map<String, Integer> statuses = cardEntry.getValue();
				Iterator<Map.Entry<String, Integer>> statusIt = statuses.entrySet().iterator();
				
				while (statusIt.hasNext()) {
					Map.Entry<String, Integer> statusEntry = statusIt.next();
					String fadingStatus = statusEntry.getKey();
					
					// Time Bombs tick every single turn. Everything else ticks only on the owner's turn (Rounds).
					// If the sticky note is explicitly tagged as a TURN effect, tick it down now.
					// Otherwise, only tick it down if a full round has passed for this player.
					// If the sticky note is explicitly tagged as a TURN effect, tick it down now.
					// Otherwise, only tick it down if a full round has passed for this player.
					if (fadingStatus.endsWith("_TURN") || belongsToCurrentPlayer) {
						
						// === THE GENERALIZED TICK HOOK ===
						// The engine blindly hands the sticky note to the Library.
						SpecialsLibrary.handleStatusTick(this, auraCard, fadingStatus);

						int newDuration = statusEntry.getValue() - 1;
						
						if (newDuration <= 0) {
							// Ask the Library if it should reset or die
							int resetTime = SpecialsLibrary.handleExpiringStatus(this, auraCard, fadingStatus);
							
							if (resetTime > 0) {
								statusEntry.setValue(resetTime);
							} else {
								statusIt.remove(); 
							}
						} else {
							statusEntry.setValue(newDuration);
						}
					}
				}
			}
		}
		
		
	// ==================================================
	// BATTLE API IMPLEMENTATION (For SpecialsSystem)
	// ==================================================
		@Override
		public void destroyCard(BattleCard target) {
		    if (target == null) return;
		    
		    // === THE FIX: THE ALREADY DEAD SHIELD ===
		    // Sweep the board to see if this exact card is physically in a slot
		    boolean isOnBoard = false;
		    for (int r = 0; r < 2; r++) {
		        for (int c = 0; c < 4; c++) {
		            if (field1[r][c] == target || field2[r][c] == target) {
		                isOnBoard = true;
		                break;
		            }
		        }
		    }
		    
		    // If it's not on the board, it's already dead! Cut off the phantom trigger!
		    if (!isOnBoard) {
		        System.out.println("SYSTEM: Prevented ghost death trigger on " + target.getBaseCard().getName());
		        return; 
		    }

		    // Capture the owner BEFORE we remove it
		    boolean wasPlayer1 = isCasterPlayer1(target);
		    
		    // Process the death
		    target.setHp(0);
		    graveyard.add(target.getBaseCard());
		    removeCardFromAllFields(target);
		    
		    // Trigger ON_DEATH events safely
		    if (deathCallback != null) {
		        deathCallback.accept(target, wasPlayer1); 
		    }
		}
		@Override
		public void bounceCardToHand(BattleCard target, boolean isP1) {
		    if (target == null) return;
		    
		    // Reset the flags so the card is "Fresh" in the hand
		    target.setDying(false); 
		    target.setDeathProcessed(false); 
		 // === THE FIX: Save the exact HP it had right before it died/bounced! ===
		    savedCardHp.put(target.getBaseCard(), target.getHp());
		    removeCardFromAllFields(target);

		    // === THE GHOST FIX: Pluck the physical card out of the graveyard! ===
		    for (int i = graveyard.size() - 1; i >= 0; i--) {
		        if (graveyard.get(i).getCardID().equals(target.getBaseCard().getCardID())) {
		            graveyard.remove(i);
		            break; // Found it! Erase it from the grave!
		        }
		    }

		    // Add it to the correct owner's hand
		    List<cards> hand = isP1 ? hand1 : hand2; 
		    hand.add(target.getBaseCard());
		    
		    System.out.println("ENGINE: Wolf returned to hand. Fate reset.");
		}

		@Override
		public void reviveSelf(BattleCard target, boolean isP1) {
			if (target == null) return;
			target.setDying(false); // It survived! Clear the flag.

			int maxHp = target.getBaseCard().getHp();
			target.setHp(maxHp);
			savedCardHp.put(target.getBaseCard(), maxHp);
			System.out.println("ENGINE: Restored to full HP natively!");
		}

		
		@Override
		public void finalizeDeath(BattleCard target, boolean isP1) {
		    if (target == null) return;

		    // We do NOT add to the graveyard here, because destroyCard() already did it!
		    // We also do NOT trigger the deathCallback here, because that causes the infinite loop!
		    
		    target.setHp(0);
		    removeCardFromAllFields(target);
		    System.out.println("ENGINE: Buried natively!");
		}
	@Override
	public void setLastCastSpecial(String rawCommand) {
		this.lastCastSpecial = rawCommand;
	}

	@Override
	public String getLastCastSpecial() {
		return this.lastCastSpecial;
	}

	@Override
	public void updateBoard() {
		if (onBoardUpdate != null)
			onBoardUpdate.run();
	}

	@Override
	public void showToast(String message) {
		if (onToast != null)
			onToast.accept(message);
	}

	@Override
	public Random getSharedRng() {
		return syncedRng; 
	}

	@Override
	public List<BattleCard> getTargetableEnemies(boolean isLocalPlayerCasting) {
		List<BattleCard> targets = new ArrayList<>();
		// If local player casts, enemies are field2. If opponent casts, enemies are
		// field1.
		BattleCard[][] enemyField = isLocalPlayerCasting ? field2 : field1;

		for (int r = 0; r < 2; r++) {
			for (int c = 0; c < 4; c++) {
				if (enemyField[r][c] != null)
					targets.add(enemyField[r][c]);
			}
		}
		return targets;
	}

	@Override
	public List<BattleCard> getFriendlyField(boolean isLocalPlayerCasting) {
		List<BattleCard> targets = new ArrayList<>();
		BattleCard[][] myField = isLocalPlayerCasting ? field1 : field2;

		for (int r = 0; r < 2; r++) {
			for (int c = 0; c < 4; c++) {
				if (myField[r][c] != null)
					targets.add(myField[r][c]);
			}
		}
		return targets;
	}

	@Override
	public void returnToHand(BattleCard target, boolean isLocalCaster) {
		if (target == null) {
			System.out.println("NETWORK ERROR: returnToHand received a null target!");
			return;
		}
		// === THE MEMORY LEAK FIX: Scrub sticky notes before returning to hand! ===
				if (activeStatuses.containsKey(target)) {
				    activeStatuses.remove(target);
				}

		System.out
				.println("NETWORK SYNC: Attempting to remove " + target.getBaseCard().getName() + " from the board...");

		boolean belongsToPlayer1 = false;
		boolean found = false;

		// 1. Blindly sweep Player 1's absolute field
		for (int r = 0; r < 2; r++) {
			for (int c = 0; c < 4; c++) {
				if (field1[r][c] == target) {
					field1[r][c] = null; // Remove it!
					belongsToPlayer1 = true;
					found = true;
					break;
				}
			}
		}

		// 2. Blindly sweep Player 2's absolute field
		if (!found) {
			for (int r = 0; r < 2; r++) {
				for (int c = 0; c < 4; c++) {
					if (field2[r][c] == target) {
						field2[r][c] = null; // Remove it!
						belongsToPlayer1 = false;
						found = true;
						break;
					}
				}
			}
		}

		if (!found) {
			System.out.println("NETWORK ERROR: Could not find " + target.getBaseCard().getName() + " on the board!");
			return;
		}

		System.out.println("NETWORK SYNC: Successfully pulled card off the field! Returning to owner's hand.");

		// 4. Return it to the correct absolute hand
				cards baseCard = target.getBaseCard();

				// === THE FIX: Memorize the HP before throwing away the BattleCard token! ===
				savedCardHp.put(baseCard, target.getHp());

				if (belongsToPlayer1) {
					hand1.add(baseCard);
				} else {
					hand2.add(baseCard);
				}

		// 5. Lock it so it suffers from "summoning sickness" and can't be instantly
		// replayed!
		// === THE FIX: Only lock the card if it's returning to the LOCAL player's hand!
		// ===
		// If it's the opponent's card bouncing, we don't care about locking it locally.
		boolean isMyCard = (belongsToPlayer1 == isLocalPlayer1);

		if (isMyCard) {
			lockedCards.add(baseCard);
			System.out.println("GUI: Applied summoning sickness lock to returned card.");
		} else {
			System.out.println("GUI: Card returned to opponent's hand. Ignoring local lock.");
		}
	}

	@Override
	public void openGraveyardUI(BattleCard caster) {
		// Only trigger the UI if the GUI is actually listening!
		if (graveyardUICallback != null) {
			graveyardUICallback.accept(caster);
		}
	}
	
	@Override
	public boolean summonTokenToField(String cardId, BattleCard caster) {
		if (allCards != null && allCards.containsKey(cardId)) {
			cards tokenBase = allCards.get(cardId);
			boolean isP1 = isCasterPlayer1(caster);
			BattleCard[][] myField = isP1 ? field1 : field2;

			// Find the first empty slot to place the token
			for (int r = 0; r < 2; r++) {
				for (int c = 0; c < 4; c++) {
					if (myField[r][c] == null) {
						BattleCard token = new BattleCard(tokenBase);
						token.setCoords(r, c);
						token.setOwnerIsP1(isP1);
						myField[r][c] = token;

						System.out.println("SYSTEM: Token " + tokenBase.getName() + " manufactured!");

						// === THE FIX: Kickstart the Token's abilities! ===
						// Without this, spawned tokens never start their Growth or Spawner timers!
						boolean isLocalOwner = (isP1 == isLocalPlayer1);
						triggerEvent(token, isLocalOwner, "ON_PLAY");

						updateBoard();
						return true;
					}
				}
			}
		}
		System.out.println("SYSTEM ERROR: Tried to spawn token " + cardId + " but field is full or ID is wrong!");
		return false;
	}

	@Override
	public void modifyHp(BattleCard target, int amount) {
		if (target == null)
			return;
		target.setHp(target.getHp() + amount);

		if (target.getHp() <= 0) {
			destroyCard(target);
		}
	}

	public void removeCardFromAllFields(BattleCard target) {
	    if (target == null) return;
	    
	    // === THE MEMORY LEAK FIX: Scrub all sticky notes so timers don't trigger from the grave/hand! ===
	    if (activeStatuses.containsKey(target)) {
	        activeStatuses.remove(target);
	    }
	    
	    // 1. Scan the ENTIRE board and delete by exact Object Identity!
	    for (int r = 0; r < 2; r++) {
	        for (int c = 0; c < 4; c++) {
	            if (field1[r][c] == target) {
	                field1[r][c] = null;
	            }
	            if (field2[r][c] == target) {
	                field2[r][c] = null;
	            }
	        }
	    }
	    
	    // ❌ DO NOT PUT target.setCoords(-1, -1) HERE! The ON_DEATH abilities need to know where the card died!
	}

	@Override
	public void addStatus(BattleCard target, String status, int duration) {
		if (target != null) {
            // === THE FIX: The inner map must also be a LinkedHashMap! ===
			activeStatuses.computeIfAbsent(target, k -> new java.util.LinkedHashMap<>()).put(status, duration);
			
            // === NEW DEBUG LINE ===
			System.out.println("SYSTEM: Glued [" + status + "] sticky note to " + target.getBaseCard().getName()
					+ " for " + duration + " rounds!");
		}
	}

	@Override
	public int getStatusModifier(BattleCard target, String statusPrefix) {
		if (activeStatuses.containsKey(target)) {
			// Check all sticky notes on this card
			for (String status : activeStatuses.get(target).keySet()) {
				// If we find a note that matches our prefix (like "REDUCETO_")
				if (status.startsWith(statusPrefix + "_")) {
					String[] parts = status.split("_");
					if (parts.length > 1) {
						try {
							// Return the number attached to it!
							return Integer.parseInt(parts[1]);
						} catch (Exception e) {
						}
					}
				}
			}
		}
		return -1; // Return -1 if the card doesn't have this sticky note
	}

	@Override
	public void changeCardOwnership(BattleCard target, boolean isLocalCaster) {
		if (target == null) {
			System.out.println("NETWORK ERROR: changeCardOwnership received a null target!");
			return;
		}
		// === THE MEMORY LEAK FIX: Scrub sticky notes before stealing! ===
				if (activeStatuses.containsKey(target)) {
				    activeStatuses.remove(target);
				}

		System.out.println("NETWORK SYNC: Troubadour is stealing " + target.getBaseCard().getName() + "!");

		boolean originallyBelongedToPlayer1 = false;
		boolean found = false;

		// 1. Blindly sweep Player 1's absolute field to remove it
		for (int r = 0; r < 2; r++) {
			for (int c = 0; c < 4; c++) {
				if (field1[r][c] == target) {
					field1[r][c] = null; // Yank it off the board!
					originallyBelongedToPlayer1 = true;
					found = true;
					break;
				}
			}
		}

		// 2. Blindly sweep Player 2's absolute field to remove it
		if (!found) {
			for (int r = 0; r < 2; r++) {
				for (int c = 0; c < 4; c++) {
					if (field2[r][c] == target) {
						field2[r][c] = null; // Yank it off the board!
						originallyBelongedToPlayer1 = false;
						found = true;
						break;
					}
				}
			}
		}

		if (!found) {
			System.out.println("NETWORK ERROR: Could not find " + target.getBaseCard().getName() + " to steal!");
			return;
		}

		// 3. Drop it straight into the NEW owner's hand!
		// We DO NOT auto-place it on the board anymore. We put it in the hand
		// as a holding cell so the player can manually click an empty slot.
		boolean newOwnerIsPlayer1 = !originallyBelongedToPlayer1;
		cards baseCard = target.getBaseCard();
		
		// === THE FIX: Save the exact HP it had right before it was stolen! ===
				savedCardHp.put(baseCard, target.getHp());

		if (newOwnerIsPlayer1) {
			hand1.add(baseCard);
			System.out.println("GUI: Stolen card temporarily sent to Hand 1 for manual placement.");
		} else {
			hand2.add(baseCard);
			System.out.println("GUI: Stolen card temporarily sent to Hand 2 for manual placement.");
		}
	}

	@Override
	public void triggerGraveyardAutoPlace(BattleCard caster, int targetRow, int targetCol) {
		// 1. Load the ghost coordinates into the engine's memory
		this.isGraveyardAutoPlaceMode = true;
		this.autoPlaceRow = targetRow;
		this.autoPlaceCol = targetCol;
		this.autoPlaceCaster = caster;

		// 2. Tell the GUI to pop the screen (if the GUI is listening!)
		if (onForceGraveyardOpen != null) {
			onForceGraveyardOpen.run();
		}
	}

	@Override
	public void forcePlacementMode(String cardId) {
	    // 1. Board Full Failsafe!
	    boolean hasEmptySlot = false;
	    BattleCard[][] myField = isLocalPlayer1 ? field1 : field2;
	    for (int r = 0; r < 2; r++) {
	        for (int c = 0; c < 4; c++) {
	            if (myField[r][c] == null) {
	                hasEmptySlot = true;
	                break;
	            }
	        }
	    }
	    
	    if (!hasEmptySlot) {
	        showToast("Your field is full! The stolen card remains in your hand.");
	        return; // Abort forced mode!
	    }

	    // 2. Find the exact card we just dumped into your hand
	    cards stolenCard = null;
	    java.util.List<cards> myHand = isLocalPlayer1 ? hand1 : hand2;

	    for (cards c : myHand) {
	        if (c.getCardID().equals(cardId)) {
	            stolenCard = c;
	            break;
	        }
	    }

	    // 3. Hand it over to the GUI and lock it down!
	    if (stolenCard != null) {
	        stolenCardReadyForPlacement = stolenCard;
	        showToast("Stolen card acquired! You MUST place it on the field.");
	        updateBoard(); 
	    }
	}

	@Override
	public cards pluckFromHand(String cardId, boolean isLocalPlayer) {
		// Find the specific card ID in the hand and yank it out when the slot is
		// clicked
		java.util.List<cards> targetHand = (isLocalPlayer == isLocalPlayer1) ? hand1 : hand2;
		for (int i = 0; i < targetHand.size(); i++) {
			if (targetHand.get(i).getCardID().equals(cardId)) {
				return targetHand.remove(i);
			}
		}
		return null;
	}

	@Override
	public void removeStatus(BattleCard target, String status) {
		if (target != null && activeStatuses.containsKey(target)) {
			activeStatuses.get(target).remove(status);
			
			// Clean up the map if the card has no more sticky notes!
			if (activeStatuses.get(target).isEmpty()) {
				activeStatuses.remove(target);
			}
			
			System.out.println("SYSTEM: Ripped the [" + status + "] sticky note off " + target.getBaseCard().getName() + "!");
		}
	}
	@Override
	public int getAuraModifier(BattleCard target, String auraPrefix) {
		BattleCard[][] targetField = null;

		// 1. Find which side of the board the target is sitting on
		for (int r = 0; r < 2; r++) {
			for (int c = 0; c < 4; c++) {
				if (field1[r][c] == target)
					targetField = field1;
				if (field2[r][c] == target)
					targetField = field2;
			}
		}

		if (targetField == null)
			return 0; // Target isn't on the board!

		int totalAura = 0;

		// 2. Scan every single card on that side of the field
		for (int r = 0; r < 2; r++) {
			for (int c = 0; c < 4; c++) {
				BattleCard ally = targetField[r][c];
				if (ally != null) {
					// If an ally is radiating the aura, add it to the total!
					int val = getStatusModifier(ally, auraPrefix);
					if (val != -1) {
						totalAura += val;
					}
				}
			}
		}
		return totalAura;
	}

	public boolean isCasterPlayer1(BattleCard caster) {
		if (caster == null) return false;

		// 1. Scan the board. If we find it, STAMP THE DOG TAG permanently!
		for (int r = 0; r < 2; r++) {
			for (int c = 0; c < 4; c++) {
				if (field1[r][c] == caster) {
					caster.setOwnerIsP1(true);
					return true;
				}
				if (field2[r][c] == caster) {
					caster.setOwnerIsP1(false);
					return false;
				}
			}
		}

		// 2. AMNESIA FIX: If it's dead or in the hand, trust the Dog Tag!
		return caster.isOwnerP1();
	}

	@Override
	public cards popFromGraveyard() {
		// If the graveyard is empty, return null!
		if (graveyard == null || graveyard.isEmpty()) {
			return null;
		}
		// Remove and return the MOST RECENTLY destroyed card (the top of the pile)
		return graveyard.remove(graveyard.size() - 1);
	}

	@Override
	public boolean summonToField(cards baseCard, BattleCard caster) {
		boolean isP1 = isCasterPlayer1(caster);
		BattleCard[][] myField = isP1 ? field1 : field2;
		
		// Determine if the person playing the game owns this caster
		boolean isLocalCaster = (isP1 == isLocalPlayer1);

		// Scan the field for an empty slot
		for (int r = 0; r < 2; r++) {
			for (int c = 0; c < 4; c++) {
				if (myField[r][c] == null) {
				    // Empty slot found! Create a fresh BattleCard and put it here.
				    BattleCard newCard = new BattleCard(baseCard);
				    
				    // Stamp the Dog Tag permanently!
				    newCard.setOwnerIsP1(isP1);
				    newCard.setCoords(r, c); // <--- Added this so the engine knows where it lives!
				    
				    myField[r][c] = newCard;
				    
				    // === THE FIX: Fire ON_PLAY abilities for automatically revived cards! ===
				    triggerEvent(newCard, isLocalCaster, "ON_PLAY", "ON_SUMMON", "ENTER_FIELD");
				    
				    return true;
				}
			}
		}
		return false; // The field is completely full!
	}
	@Override
	public BattleCard processDefensiveTricks(BattleCard attacker, BattleCard target, String commandStr) {
		if (target == null)
			return null;

		// The engine no longer looks it up! It just reads the string the GUI passed
		// down.
		if (commandStr != null && commandStr.contains("ON_DEFEND")) {
			if (commandStr.contains("confuse_redirect")) {
				return doConfuseRedirect(attacker, target);
			}
		}

		return target; // No tricks? Proceed normally.
	}

	@Override
	public BattleCard doConfuseRedirect(BattleCard attacker, BattleCard target) {
		// 50/50 Coinflip!
		boolean heads = Math.random() < 0.5;

		if (heads) {
			System.out.println("T-BOT: Coinflip won! T-Bot takes the hit.");
			return target; // Attack proceeds normally
		}

		System.out.println("T-BOT: Coinflip lost! Redirecting attack to another card!");

		// === THE FIX: Look at the ATTACKER'S side of the board, not T-Bot's! ===
		boolean isAttackerP1 = isCasterPlayer1(attacker);
		BattleCard[][] targetField = isAttackerP1 ? field1 : field2;

		java.util.List<BattleCard> validVictims = new java.util.ArrayList<>();

		for (int r = 0; r < 2; r++) {
			for (int c = 0; c < 4; c++) {
				BattleCard potentialVictim = targetField[r][c];

				// Add to the list if it's a real card AND it is NOT the attacker!
				if (potentialVictim != null && potentialVictim != attacker) {
					validVictims.add(potentialVictim);
				}
			}
		}

		// Failsafe: If the attacker is completely alone on their field, T-Bot takes the
		// hit!
		if (validVictims.isEmpty()) {
			System.out.println("T-BOT: Attacker has no allies to punch. T-Bot takes the hit anyway!");
			return target;
		}

		// Pick a random teammate to take the punch!
		int randomIndex = (int) (Math.random() * validVictims.size());
		BattleCard newVictim = validVictims.get(randomIndex);

		System.out.println("T-BOT: Attack successfully redirected to " + newVictim.getBaseCard().getName() + "!");
		return newVictim;
	}

	@Override
	public BattleCard[][] getField1() {
		return field1; // Or whatever your Player 1 array variable is named!
	}

	@Override
	public BattleCard[][] getField2() {
		return field2; // Or whatever your Player 2 array variable is named!
	}

	@Override
	public boolean summonToSpecificSlot(cards baseCard, boolean isLocalPlayer, int row, int col) {
		// If the spell belongs to me, it goes to my field.
		// If it belongs to the opponent, it goes to their field!
		boolean isP1 = isLocalPlayer ? isLocalPlayer1 : !isLocalPlayer1;
		BattleCard[][] myField = isP1 ? field1 : field2;

		// Only summon if the slot the player clicked is actually empty!
		if (myField[row][col] == null) {
		    BattleCard newCard = new BattleCard(baseCard);

		    // Stamp the GPS coordinates AND the Dog Tag!
		    newCard.setCoords(row, col);
		    newCard.setOwnerIsP1(isP1);

		    myField[row][col] = newCard;
		    
		    // === THE FIX: Fire ON_PLAY abilities for revived cards! ===
		    triggerEvent(newCard, isLocalPlayer, "ON_PLAY", "ON_SUMMON", "ENTER_FIELD");
		    
		    return true;
		}
		return false;
	}

	@Override
	public void forceToHand(cards baseCard, BattleCard caster) {
		boolean isP1 = isCasterPlayer1(caster);
		// Add it directly to the owner's hand list
		if (isP1) {
			hand1.add(baseCard);
		} else {
			hand2.add(baseCard);
		}
	}

	@Override
	public void forceDraw(BattleCard caster, int amount) {
		// === THE FIX: Use the dog tags! No more relative guessing! ===
		boolean isP1 = isCasterPlayer1(caster);

		List<cards> targetDeck = isP1 ? deck1 : deck2;
		List<cards> targetHand = isP1 ? hand1 : hand2;

		for (int i = 0; i < amount; i++) {
			if (!targetDeck.isEmpty()) {
				cards drawnCard = targetDeck.remove(0);
				targetHand.add(drawnCard);
				System.out.println("BATTLE SYSTEM: Forced " + drawnCard.getName() + " into hand!");
			} else {
				System.out.println("BATTLE SYSTEM: Deck is empty! Cannot draw any more cards.");
				break;
			}
		}
	}
	@Override
	public void stealEnemyDraw(BattleCard caster, int amount) {
		if (caster == null) return;
		
		boolean isP1 = isCasterPlayer1(caster);
		
		// Map the target deck and destination hand based on who cast it!
		java.util.List<cards> targetDeck = isP1 ? deck2 : deck1;
		java.util.List<cards> myHand = isP1 ? hand1 : hand2;
		
		for (int i = 0; i < amount; i++) {
			if (!targetDeck.isEmpty()) {
				// Pop the top card off the enemy's deck
				cards stolenCard = targetDeck.remove(0);
				
				// Add it to our hand!
				myHand.add(stolenCard);
				System.out.println("SYSTEM: " + caster.getBaseCard().getName() + " stole " + stolenCard.getName() + " from the enemy's deck!");
			} else {
				System.out.println("SYSTEM: Enemy deck is empty! The Conjurer grabs nothing.");
			}
		}
		
		// Update the UI so the new card appears in the hand panel
		updateBoard();
	}

	@Override
	public void triggerDefensiveCoinflip(boolean isHeads, String headsMsg, String tailsMsg, Runnable onComplete) {
		if (onDefensiveCoinflipRequest != null) {
			onDefensiveCoinflipRequest.playAnimation(isHeads, headsMsg, tailsMsg, onComplete);
		} else {
			// Failsafe if GUI isn't listening: just execute instantly
			onComplete.run();
		}
	}

	@Override
	public cards pluckFromGraveyard(String cardId) {
		if (graveyard == null || graveyard.isEmpty())
			return null;

		// Search backwards to get the most recently destroyed copy of this card
		for (int i = graveyard.size() - 1; i >= 0; i--) {
			if (graveyard.get(i).getCardID().equals(cardId)) {
				return graveyard.remove(i); // Remove it from the grave and return it!
			}
		}
		return null;
	}

	@Override
	public boolean hasStatus(BattleCard target, String status) {
		return activeStatuses.containsKey(target) && activeStatuses.get(target).containsKey(status);
	}

	// 3. Update the Overridden API method at the bottom of the file
	@Override
	public void triggerSpecialCoinflip(boolean isHeads, String headsMsg, String tailsMsg, Runnable onComplete) {
		if (specialCoinflipCallback != null) {
			specialCoinflipCallback.onFlip(isHeads, headsMsg, tailsMsg, onComplete);
		} else {
			onComplete.run();
		}
	}

	@Override
	public void healPlayerField(boolean isPlayer1) {
		// No more guessing! We know exactly who to heal!
		BattleCard[][] myField = isPlayer1 ? field1 : field2;

		for (int r = 0; r < 2; r++) {
			for (int c = 0; c < 4; c++) {
				if (myField[r][c] != null) {
					int maxHp = myField[r][c].getBaseCard().getHp();
					myField[r][c].setHp(maxHp);
				}
			}
		}
	}

	@Override
	public void damageEnemyField(BattleCard attacker, int damageAmount) {
		// === THE FIX: We now use the physical attacker to determine sides! ===
		boolean isPlayer1 = isCasterPlayer1(attacker);
		BattleCard[][] enemyField = isPlayer1 ? field2 : field1;

		List<BattleCard> hitList = new ArrayList<>();

		// 1. Gather all living enemies
		for (int r = 0; r < 2; r++) {
			for (int c = 0; c < 4; c++) {
				if (enemyField[r][c] != null && enemyField[r][c].getHp() > 0) {
					hitList.add(enemyField[r][c]);
				}
			}
		}

		// Are we the local screen generating the UI?
		boolean isLocalAttack = false;

		// 2. Bomb them!
		for (BattleCard target : hitList) {
			
			String targetId = target.getBaseCard().getCardID();
			String commandStr = allSpecials != null ? allSpecials.get(targetId) : null;

			// === THE UPGRADE: Route the explosion through the Defensive Switchboard! ===
			SpecialsLibrary.handleDefensiveTrick(this, attacker, target, commandStr, isLocalAttack, finalTarget -> {
				
				// Once the coinflip animation finishes (or if there wasn't one), this executes!
				if (finalTarget != null) {
					
					// Calculate final math through armor/shields
					int damageDealt = SpecialsLibrary.applyDamageModifiers(this, attacker, finalTarget, damageAmount);

					if (damageDealt > 0) {
						finalTarget.setHp(finalTarget.getHp() - damageDealt);
						System.out.println("BOMB hit " + finalTarget.getBaseCard().getName() + " for " + damageDealt + " damage!");

						if (finalTarget.getHp() <= 0) {
							destroyCard(finalTarget);
						}
					} else {
						System.out.println(finalTarget.getBaseCard().getName() + " completely deflected the explosion!");
					}
					
					// Update the GUI to show the damage!
					updateBoard();
				}
			});
		}
	}

	@Override
	public void triggerAutoSelect(cards targetCard, boolean isLocalPlayer) {
		// THE FIX: If the player sitting at this computer didn't cast the spell, DO NOT
		// show the UI!
		if (!isLocalPlayer)
			return;

		java.util.List<cards> myHand = isLocalPlayer1 ? hand1 : hand2;

		queuedAutoSelectCard = targetCard;
		queuedAutoSelectIndex = myHand.size() - 1;

		updateBoard();
	}

	// The GUI will call this to safely snatch the card and clear the queue
	public cards consumeQueuedAutoSelect() {
		cards temp = queuedAutoSelectCard;
		queuedAutoSelectCard = null;
		return temp;
	}
	
	@Override
	public void transformCard(BattleCard target, String newCardId) {
	    // Failsafe: Make sure the target exists and the dictionary has the new card!
	    if (target == null || allCards == null || !allCards.containsKey(newCardId)) return;
	    
	    cards newBase = allCards.get(newCardId);
	    
	    // 1. Rewrite the card's identity
	    target.transformInto(newBase);
	    
	    // 2. Rip off any sticky notes (like Pacify, Time Bombs, or Reflect Shields)
	    // We want it to be a completely fresh Coco-Goon or Evolution!
	    if (activeStatuses.containsKey(target)) {
	        activeStatuses.remove(target);
	    }
	    
	    System.out.println("SYSTEM: Transformed into " + newBase.getName() + "!");
	    
	    // === THE FIX: Kickstart the new card's abilities! ===
	    // Without this, the newly evolved Small Ent won't start its own growth timer!
	    boolean isLocalOwner = (isCasterPlayer1(target) == isLocalPlayer1);
	    triggerEvent(target, isLocalOwner, "ON_PLAY", "ON_TRANSFORM");
	}

	@Override
	public void nukeField() {
		List<BattleCard> targetsToDestroy = new ArrayList<>();

		for (int r = 0; r < 2; r++) {
			for (int c = 0; c < 4; c++) {
				// Only add them to the hit-list if they are actually alive!
				if (field1[r][c] != null && field1[r][c].getHp() > 0)
					targetsToDestroy.add(field1[r][c]);
				if (field2[r][c] != null && field2[r][c].getHp() > 0)
					targetsToDestroy.add(field2[r][c]);
			}
		}

		for (BattleCard target : targetsToDestroy) {
			destroyCard(target);
		}
	}
	@Override
	public void broadcastDefensiveCoinflip(boolean isHeads, String headsMsg, String tailsMsg) {
	    if (out != null) {
	        out.println("CMD:DEFEND_ANIM:" + isHeads + "|" + headsMsg + "|" + tailsMsg + " - Sent by: " + myPlayerId);
	    }
	}
	
	public List<cards> getLockedCards() {
	    return lockedCards;
	}
}