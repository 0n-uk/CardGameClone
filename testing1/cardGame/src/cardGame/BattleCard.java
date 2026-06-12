package cardGame;

public class BattleCard {

	// Put this near your other variables at the top
		private boolean ownerIsP1 = false;
    private cards baseCard;   // original card
    private int atk;
    private int hp;
    private int actions;
    private boolean isDying = false;
    private boolean deathProcessed = false;
 // === NEW: Targeted Indicator ===
    private boolean isBeingTargeted = false;

    public void setDeathProcessed(boolean processed) { this.deathProcessed = processed; }
    public boolean isDeathProcessed() { return deathProcessed; }
    
 // Put this near your other variables
    private boolean specialDisabled = false;
 // === NEW: Stolen Special Memory ===
    private String overriddenSpecial = null;
    

    public void setOverriddenSpecial(String special) { this.overriddenSpecial = special; }
    public String getOverriddenSpecial() { return this.overriddenSpecial; }

 
    
    private int row = -1;
    private int col = -1;
 // === NEW: Dodge Mechanic ===
    private int dodges = 0;

    public BattleCard(cards baseCard) {
        this.baseCard = baseCard;
        this.atk = baseCard.getAtk();
        this.hp = baseCard.getHp();
        this.actions = 1; // 1 attack per turn
        
    }
    
    public void setCoords(int r, int c) { this.row = r; this.col = c; }
    public int getRow() { return row; }
    public int getCol() { return col; }
 // Put these anywhere inside the class
 	public void setOwnerIsP1(boolean isP1) { this.ownerIsP1 = isP1; }
 	public boolean isOwnerP1() { return this.ownerIsP1; }

    // ======================
    // Getters
    // ======================
    
  
    // Add the getters/setters anywhere inside
    public void setSpecialDisabled(boolean disabled) { this.specialDisabled = disabled; }
    public boolean isSpecialDisabled() { return specialDisabled; }
    public String getName() {
        return baseCard.getName();
    }

    public int getAtk() {
        return this.atk; // <--- THE FIX: Return the changing stat, not the static base card!
    }

    public int getHp() {
        return hp;
    }

    public int getActions() {
        return actions;
    }

    public cards getBaseCard() {
        return baseCard;
    }
    
    public int getDodges() {
        return dodges;
    }

    // ======================
    // Setters / Modifiers
    // ======================
    
    public void addDodges(int amount) {
        this.dodges += amount;
    }

    public void setHp(int hp) {
        this.hp = hp;
    }
 // === NEW: Allow the engine to dynamically buff/debuff attack! ===
 	public void setAtk(int newAtk) {
         // Prevent ATK from ever dropping below 0 (so healing doesn't accidentally heal the enemy!)
 		if (newAtk < 0) {
 			newAtk = 0;
 		}
 		this.atk = newAtk; // Change 'this.atk' to whatever your specific variable is named!
 	}

    public void useAction() {
        if (actions > 0) {
            actions--;
        }
    }
    
    public void setActions(int actions) {
        this.actions = actions;
    }
    
 // This method checks if the card CAN dodge, and if so, safely burns 1 charge!
    public boolean consumeDodge() {
        if (dodges > 0) {
            dodges--;
            return true; 
        }
        return false;
    }
    
    public boolean isBeingTargeted() { return isBeingTargeted; }
    public void setBeingTargeted(boolean targeted) { this.isBeingTargeted = targeted; }

    public void resetActions() {
        actions = 1;
    }

    @Override
    public String toString() {
        return baseCard.getName() + " (ATK: " + getAtk() + ", HP: " + hp + ")";
    }
    
 // === NEW: Transform Mechanic ===
    public void transformInto(cards newBaseCard) {
        this.baseCard = newBaseCard;
        
        // Reset stats to the new card's default blueprint
        this.atk = newBaseCard.getAtk();
        this.hp = newBaseCard.getHp();
        
        // Wipe out temporary states
        this.dodges = 0;
        this.specialDisabled = false;
        this.overriddenSpecial = null;
    }
    
 // Drop these two methods anywhere inside the class
 	public boolean isDying() { return isDying; }
 	public void setDying(boolean isDying) { this.isDying = isDying; }
}