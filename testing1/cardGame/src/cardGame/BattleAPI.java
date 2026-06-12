package cardGame;

import java.util.List;
import java.util.Random;


public interface BattleAPI {
	java.util.List<BattleCard> getEnemyCards();
	
	// === NEW: Card Transformation ===
    void transformCard(BattleCard target, String newCardId);
    boolean summonTokenToField(String cardId, BattleCard caster);
	
	// === NEW: Allow GUI to count locks for identical clones! ===
	List<cards> getLockedCards();
	
	// === NEW: Network Broadcasting for Defensive Tricks ===
	void broadcastDefensiveCoinflip(boolean isHeads, String headsMsg, String tailsMsg);
	
	// === NEW: Trigger Event Hook ===
		boolean triggerEvent(BattleCard caster, boolean isLocalCaster, String... eventKeywords);
		public java.util.Random getSpecialRng();
	
	boolean isCasterPlayer1(BattleCard caster);
	BattleCard[][] getField1();
	BattleCard[][] getField2();
	
	
    // --- UI & Game State ---
    void updateBoard();
    void showToast(String message);
    Random getSharedRng(); // Essential so both players get the same coinflip results!

    // --- Targeting (Translates 2D arrays to simple Lists for specials) ---
    List<BattleCard> getTargetableEnemies(boolean isLocalPlayerCasting);
    List<BattleCard> getFriendlyField(boolean isLocalPlayerCasting);

    // --- Actions ---
    void destroyCard(BattleCard target);
    void returnToHand(BattleCard target, boolean isLocalPlayerCasting);
    void modifyHp(BattleCard target, int amount);
 // The new generic status trackers
    void addStatus(BattleCard target, String status, int duration);
    boolean hasStatus(BattleCard target, String status);
    void removeStatus(BattleCard target, String status);
    boolean attack(BattleCard attacker, BattleCard target);
 // === NEW: Fetch the number off a sticky note! ===
    int getStatusModifier(BattleCard target, String statusPrefix);
 // === NEW: Check the board for radiating field effects! ===
    int getAuraModifier(BattleCard target, String auraPrefix);
    
 // === NEW: Spell Memory for the Pirate Parrot ===
    void setLastCastSpecial(String rawCommand);
    String getLastCastSpecial();
    
 // === NEW: Specific Graveyard Mechanics ===
    List<cards> getGraveyard(); // Lets the GUI see the graveyard
    cards pluckFromGraveyard(String cardId); // Plucks a specific card
 // === NEW: Graveyard and Summoning Mechanics ===
    cards popFromGraveyard();
    boolean summonToField(cards baseCard, BattleCard caster);
 // Change this in BattleAPI.java
    boolean summonToSpecificSlot(cards baseCard, boolean isLocalPlayer, int row, int col);
    void forceToHand(cards baseCard, BattleCard caster);
 // === NEW: Steal from Enemy Deck ===
    void stealEnemyDraw(BattleCard caster, int amount);
 // === NEW: UI Callbacks ===
    void openGraveyardUI(BattleCard caster);
 // === NEW: UI Callbacks ===
    void triggerSpecialCoinflip(boolean isHeads, String headsMsg, String tailsMsg, Runnable onComplete);
 // === NEW: Forced Card Draw ===
 // === UPDATED: Pass the caster so we can check their dog tags! ===
    void forceDraw(BattleCard caster, int amount);
 // === NEW: Board Wipes & Healing ===
    void healPlayerField(boolean isPlayer1);
 // === NEW: Area of Effect Damage ===
 // === THE FIX: Pass the actual card doing the damage! ===
    void damageEnemyField(BattleCard attacker, int damageAmount);
    void nukeField();
 // === NEW: Network Broadcasting ===
    void broadcastSpecialExecution(BattleCard caster, List<BattleCard> targets, String specialCode);
    
 // === NEW: Card Stealing & Placement Mechanics ===
    void changeCardOwnership(BattleCard target, boolean isLocalCaster);
    void forcePlacementMode(String cardId);
    cards pluckFromHand(String cardId, boolean isLocalPlayer);
    
    void triggerAutoSelect(cards targetCard, boolean isLocalPlayer);
    void triggerGraveyardAutoPlace(BattleCard caster, int targetRow, int targetCol);
 // === NEW: Card Movement Mechanics ===
 	void bounceCardToHand(BattleCard target, boolean isP1);
 	void reviveSelf(BattleCard target, boolean isP1);
 	void finalizeDeath(BattleCard target, boolean isP1);
 // === NEW: Defense Interceptor Mechanics ===
 // === NEW: Defense Interceptor Mechanics ===
    void triggerDefensiveCoinflip(boolean isHeads, String headsMsg, String tailsMsg, Runnable onComplete);
    BattleCard processDefensiveTricks(BattleCard attacker, BattleCard target, String commandStr);
    BattleCard doConfuseRedirect(BattleCard attacker, BattleCard target);
    }