package cardGame;

import java.util.List;

public class SpecialsLibrary {

    // ==========================================
	// 1. THE PARSER (Reads the string and routes it)
    // ==========================================
	public static void parseAndExecute(BattleAPI api, String specialCode, boolean isLocalPlayerCasting, BattleCard caster, List<BattleCard> targets) {
		System.out.println("\n--- SPECIAL ACTIVATED ---");

		// Clean any stray spaces or accidental leading pipes
		specialCode = specialCode.trim();
		if (specialCode.startsWith("|")) {
			specialCode = specialCode.substring(1).trim();
		}
		
		// If the string accidentally starts with the Card ID, slice it off!
		String[] safeCheck = specialCode.split("\\|");
		if (safeCheck[0].trim().matches("\\d+")) { 
			specialCode = specialCode.substring(specialCode.indexOf("|") + 1).trim();
		}

		// Scrub the network tags before saving!
		if (specialCode != null && !specialCode.toLowerCase().contains("mimicspecial")) {
			String pureMemory = specialCode.replaceAll("\\|?\\s*COIN:HEADS", "")
										   .replaceAll("\\|?\\s*COIN:TAILS", "").trim();
			api.setLastCastSpecial(pureMemory);
		}
		
		System.out.println("Cleaned Code Ready for Engine: [" + specialCode + "]");

		

		// === THE PARSER FIX: Dynamically step over legacy badges! ===
		// === THE PARSER FIX: Dynamically step over legacy badges! ===
		String[] parts = specialCode.split("\\|");
		if (parts.length < 2) return;

		int index = 0;

		// 1. Activation Type (CHOSEN, ACTIVE, PASSIVE)
		String activationType = parts[index].trim();
		index++;

		// 2. Ignore the legacy target badge ("01", "02") if it exists!
		if (index < parts.length && parts[index].trim().matches("\\d+")) {
		    index++;
		}

		// 3. Trigger (CF, NONE, ON_PLAY, etc.)
		String trigger = (index < parts.length) ? parts[index].trim() : "NONE";
		index++;

		// === THE FIX: Step over the rogue "CF" column (Used by Arsonist) ===
		boolean hasExtraCF = false;
		if (index < parts.length && parts[index].trim().equals("CF")) {
		    hasExtraCF = true;
		    index++; // Step over it so if_1 and if_2 align correctly!
		}

		// 4. if_1 (Action on Success)
		String if1 = (index < parts.length) ? parts[index].replaceAll("(?i)if_1:", "").trim() : "null";
		index++;

		// 5. if_2 (Action on Fail)
		String if2 = (index < parts.length) ? parts[index].replaceAll("(?i)if_2:", "").trim() : "null";

		// Deduct action point
		if (caster != null && !activationType.toUpperCase().contains("PASSIVE")) {
		    caster.useAction(); 
		}

		// === THE NEW INDICATOR: Stamp the targets BEFORE the coinflip! ===
		if (targets != null && !targets.isEmpty()) {
		    for (BattleCard t : targets) {
		        t.setBeingTargeted(true);
		    }
		    api.updateBoard(); // Force the GUI to instantly draw the stickers!
		}

		// --- Evaluate Trigger ---
		// Now we check hasExtraCF to guarantee the Arsonist triggers the UI!
		boolean requiresCoinflip = trigger.equals("CF") || hasExtraCF || (!if2.equals("null") && !if2.isEmpty());
       
            
         // Check if the network forced an outcome!
            if (requiresCoinflip) {
                boolean isHeads;
                
                // Check if the network forcedF an outcome!
                if (specialCode.contains("COIN:HEADS")) {
                    isHeads = true;
                } else if (specialCode.contains("COIN:TAILS")) {
                    isHeads = false;
                } else {
                    
                    // === THE EXORCISM ===
                    // We use the Glass Box RNG. Nothing else in the game is allowed 
                    // to touch this, so a desync is physically impossible!
                    isHeads = api.getSpecialRng().nextBoolean();
                }
                
            
            String cardId = caster != null ? caster.getBaseCard().getCardID() : "";

            // === YOUR BRILLIANT FLAG FIX ===
            // Placed completely outside the if/else so it always checks the final result!
            if (!isHeads && (cardId.equals("01081"))) {
                if (caster != null) caster.setSpecialDisabled(true);
                System.out.println("LIBRARY: " + caster.getBaseCard().getName() + " failed its death save. Special permanently disabled!");
            }
            
            String headsMsg = "Success! Executing Special...";
            String tailsMsg = "Failed! Nothing happens.";
            
            // Scalable Switch Statement!
            switch (cardId) {
          
                case "01001": 
                    headsMsg = "Success! Target eliminated!";
                    tailsMsg = "Failed! Gun jammed.";
                    break;
                case "01005": 
                    headsMsg = "Heads! Gains +3 ATK!";
                    tailsMsg = "Tails! Gains +3 HP!";
                    break;      
                case "01009":
                    headsMsg = "Drawing 2 cards from deck!";
                    tailsMsg = "Opening Graveyard!";
                    break;
                case "01011": 
                    headsMsg = "Melody successful! Enemy converted!";
                    tailsMsg = "Melody fell flat!";
                    break;
                case "01015": // (NOTE: T-Bot will rarely hit this block because it's an ON_DEFEND interrupt, but it's safe here!)
                	headsMsg = "HEADS! dmg is dealt to T-BOT";
                	tailsMsg = "TAILS! A random card is dmged";
                    break;
                case "01018": //classy ufo
                	headsMsg = "HEADS! Immune for 5 rounds";
                	tailsMsg = "TAILS! the shield faltered";
                    break;
                case "01019": // Lucky Gun Slinger
                    headsMsg = "HEADS! Wild barrage! 2x damage to all enemies!";
                    tailsMsg = "TAILS! Gun backfired! Slinger is destroyed!";
                    break;
                case "01021": // Ice Spirit
                    headsMsg = "HEADS! Freeze ball! Target is Frozen for 3 rounds!";
                    tailsMsg = "TAILS! Freeze ball missed!";
                    break;
                case "01081": // Midnight Wolf
                    headsMsg = "HEADS! Midnight Wolf escapes to your hand!";
                    tailsMsg = "TAILS! The Wolf fades to the graveyard.";
                    break;
                case "01082": // The Arsonist
                    headsMsg = "HEADS! KABOOM! The Arsonist detonates!";
                    tailsMsg = "TAILS! The fuse went out. No explosion.";
                    break;
                case "01083": 
                    headsMsg = "Immune to atk's for 5 rounds";
                    tailsMsg = "Returns to hand!";
                    break;
                case "01085": // Twin Pyramid Specter
                    headsMsg = "HEADS! Pyramid Power! +3 ATK for 3 rounds!";
                    tailsMsg = "TAILS! Pyramid Shield! +3 HP for 3 rounds!";
                    break;
                case "01088": // Coco-Mage
                    headsMsg = "HEADS! COCO-LOCO! Targets transformed into Coco-Goons!";
                    tailsMsg = "TAILS! The magic fizzled!";
                    break;
                case "01089": // Stone Giant
                    headsMsg = "HEADS! The Giant enrages! +8 ATK!";
                    tailsMsg = "TAILS! The Giant hardens! +8 HP, +Armor, but cannot attack!";
                    break;
                case "01090": // Sea Wizard
                    headsMsg = "HEADS! Watery Bind! Target is Pacified!";
                    tailsMsg = "TAILS! Aqua Shield! Wizard is Untargetable for 5 rounds!";
                    break;
                case "01093": // The Street Conjurer
                    headsMsg = "HEADS! Conjuring success! Stole a card from the enemy deck!";
                    tailsMsg = "TAILS! Minor trick. Drew a card from your own deck.";
                    break;
                case "01094": // Dark Necromancer Magician
                    headsMsg = "HEADS! Dark ritual succeeds! Select a card to revive.";
                    tailsMsg = "TAILS! The magic fizzled. No one was revived.";
                    break;
                case "01163": // Flame Dragon
                    headsMsg = "HEADS! The Dance of Fire commences!";
                    tailsMsg = "TAILS! The Spirits of fire rejected your display...";
                    break;
                    
                 //pod evolution tree messages   
                case "01992": // Pod
                    headsMsg = "HEADS! The Pod sprouts into a Small Ent!";
                    tailsMsg = "TAILS! The Pod blooms into a Small Bushy!";
                    break;
                case "01880": // Small Bushy
                    headsMsg = "HEADS! The Bushy overgrows into a Large Bushy!";
                    tailsMsg = "TAILS! The Bushy mutates into a Thorny Bushy!";
                    break;
                case "01881": // Small Ent
                    headsMsg = "HEADS! Roots take hold! Evolves into a Great Ent!";
                    tailsMsg = "TAILS! Nature's blessing! Evolves into a Druid!";
                    break;
            }
            
String executionPath = isHeads ? if1 : if2;
            
            // BRING BACK THE COINFLIP UI!
            api.triggerCoinflip(isHeads, headsMsg, tailsMsg, () -> {
                executeChainedCommands(api, executionPath, isLocalPlayerCasting, caster, targets);
            });
        }
         // === THE FIX: The Guaranteed Execution Block! ===
            else {
                // If no coinflip is needed, we instantly execute the guaranteed if_1 path!
                executeChainedCommands(api, if1, isLocalPlayerCasting, caster, targets);
            }
    }
	
	// === NEW HELPER: Universal Special Fetcher ===
	public static String getFullCommandString(BattleAPI api, BattleCard card) {
	    if (card == null || api == null) return null;
	    
	    String innateCommand = null;
	    if (api instanceof BattleSystem) {
	        innateCommand = ((BattleSystem)api).allSpecials.get(card.getBaseCard().getCardID());
	    }
	    
	    String stolenCommand = card.getOverriddenSpecial();
	    
	    // Stitch them together so the card has BOTH abilities simultaneously!
	    if (innateCommand != null && stolenCommand != null) {
	        return innateCommand + " ++ " + stolenCommand;
	    } else if (stolenCommand != null) {
	        return stolenCommand;
	    }
	    
	    return innateCommand;
	}
    
 // === NEW: THE MULTI-STRING EXTRACTOR ===
 	public static String extractSegment(String rawCommand, String keyword) {
 		if (rawCommand == null || rawCommand.trim().isEmpty() || rawCommand.equals("NONE")) return null;
 		
 		// Split the string into pieces wherever there is a "++"
 		String[] segments = rawCommand.split("\\+\\+");
 		
 		for (String segment : segments) {
 			if (segment.toUpperCase().contains(keyword.toUpperCase())) {
 				
 				// Clean up any stray spaces or accidental leading pipes
 				String cleanSeg = segment.trim();
 				if (cleanSeg.startsWith("|")) cleanSeg = cleanSeg.substring(1).trim();
 				
 				return cleanSeg; 
 			}
 		}
 		return null; // Keyword not found in any segment!
 	}

    // ==========================================
    // 1.5 NEW HELPER METHOD (Runs after the coin finishes spinning!)
    // ==========================================
 // ==========================================
    // 1.5 NEW HELPER METHOD (Runs after the coin finishes spinning!)
    // ==========================================
private static void executeChainedCommands(BattleAPI api, String executionPath, boolean isLocalPlayerCasting, BattleCard caster, List<BattleCard> targets) {
        
        // === THE OMNI-DODGE INTERCEPTOR ===
        // We filter the targets list BEFORE any spells are actually executed!
        List<BattleCard> finalTargets = targets;
        
        if (targets != null && !targets.isEmpty()) {
            finalTargets = new java.util.ArrayList<>();
            for (BattleCard t : targets) {
                // 1. Check if the spell is hostile (Caster is on a different team than the target)
                boolean isHostile = (caster != null && api.isCasterPlayer1(caster) != api.isCasterPlayer1(t));
                
                // 2. If it's a hostile spell, and they have the Omni-Dodge sticky note, AND they have charges left...
                if (isHostile && api.hasStatus(t, "OMNIDODGE") && t.getDodges() > 0) {
                    t.consumeDodge(); // Burn a charge!
                    System.out.println("LIBRARY: *SWOOSH!* " + t.getBaseCard().getName() + " omni-dodged the hostile special! (" + t.getDodges() + " dodges remaining)");
                    api.showToast(t.getBaseCard().getName() + " flipped out of the way of the special!");
                    
                    // If they just burned their very last dodge, rip the sticky note off!
                    if (t.getDodges() <= 0) {
                        api.removeStatus(t, "OMNIDODGE");
                    }
                    continue; // SKIP adding them to the final hit-list!
                }
                
                // 3. Target didn't dodge, add them to the official crosshairs!
                finalTargets.add(t);
            }
        }
        
        // Wrap the execution so we don't early return and skip the cleanup!
        if (!executionPath.equals("null") && !executionPath.isEmpty()) {

            // === CONDITIONAL CHAIN (->) ===
            if (executionPath.contains("->")) {
                String[] actions = executionPath.split("->");
                boolean previousActionSucceeded = true;
                
                for (String actionString : actions) {
                    if (previousActionSucceeded) {
                        String[] parallelActions = actionString.split("\\+");
                        boolean allSucceeded = true;
                        
                        for (String pAction : parallelActions) {
                            System.out.println("Executing Conditional Action: " + pAction.trim());
                            
                            // === THE FIX: Pass finalTargets instead of targets! ===
                            boolean result = decodeSingleAction(api, pAction.trim(), isLocalPlayerCasting, caster, finalTargets);
                            if (!result) allSucceeded = false;
                        }
                        previousActionSucceeded = allSucceeded;
                        
                    } else {
                        System.out.println("Execution chain broken! Skipping: " + actionString.trim());
                        break;
                    }
                }
            }
            // === INDEPENDENT CHAIN (+) ===
            else {
                String[] actions = executionPath.split("\\+");
                for (String actionString : actions) {
                    System.out.println("Executing Action: " + actionString.trim());
                    
                    // === THE FIX: Pass finalTargets instead of targets! ===
                    decodeSingleAction(api, actionString.trim(), isLocalPlayerCasting, caster, finalTargets);
                }
            }
        } else {
            System.out.println("Path is null. Special ends peacefully.");
        }
        
        // === THE CLEANUP: Wipe the stickers off the cards! ===
        if (targets != null) {
            for (BattleCard t : targets) {
                t.setBeingTargeted(false);
            }
        }
        
        api.updateBoard(); 
        System.out.println("--- SPECIAL FINISHED ---\n");
    }

    // ==========================================
    // 2. THE DECODER (Extracts brackets like [2] or [01003_1_3])
    // ==========================================
    private static boolean decodeSingleAction(BattleAPI api, String actionString, boolean isLocalPlayerCasting, BattleCard caster, List<BattleCard> targets) {
        String baseAction = actionString.trim();
        int param = 1; 
        String rawParam = "";

        // 1. Check for standard brackets [X]
        if (baseAction.contains("[") && baseAction.contains("]")) {
            int start = baseAction.indexOf("[");
            int end = baseAction.indexOf("]");
            rawParam = baseAction.substring(start + 1, end).trim();
            try { param = Integer.parseInt(rawParam); } catch (Exception e) {}
            baseAction = baseAction.substring(0, start).trim(); 
        }
        // 2. === NEW: Check for generalized parentheses (X) ===
        else if (baseAction.contains("(") && baseAction.contains(")")) {
            int start = baseAction.indexOf("(");
            int end = baseAction.indexOf(")");
            rawParam = baseAction.substring(start + 1, end).trim();
            try { param = Integer.parseInt(rawParam); } catch (Exception e) {}
            baseAction = baseAction.substring(0, start).trim(); 
        }

        return executeEffect(api, baseAction, param, rawParam, isLocalPlayerCasting, caster, targets);
    }
    // ==========================================
    // 3. THE EFFECT SWITCHBOARD
    // ==========================================
    private static boolean executeEffect(BattleAPI api, String action, int param, String rawParam, boolean isLocalPlayer, BattleCard caster, List<BattleCard> targets) {
        switch (action.toLowerCase()) {
            case "instakill":
                return doInstakill(api, param, isLocalPlayer, targets);
                
             // === NEW: Transform / Polymorph ===
             // === TIER 2: Transform / Polymorph (Upgraded for Self-Evolution!) ===
            case "transform":
                java.util.List<BattleCard> transformTargets = (targets != null && !targets.isEmpty()) ? targets : java.util.Collections.singletonList(caster);
                for (BattleCard t : transformTargets) {
                    if (t != null) {
                        api.transformCard(t, rawParam.trim());
                        System.out.println("LIBRARY: Transformed into " + rawParam.trim() + "!");
                    }
                }
                return true;
             // === TIER 2: Evolution Timer ===
            case "apply_growth": {
                // _TURN ensures it ticks down exactly like the Watch Tower bomb!
                api.addStatus(caster, "GROWTH_" + param + "_TURN", param);
                System.out.println("LIBRARY: " + caster.getBaseCard().getName() + " is growing! It will evolve in " + param + " turns.");
                return true;
            }
                
            case "return2hand":
                return doReturnToHand(api, isLocalPlayer, caster);
                
            case "untargetable":
                api.addStatus(caster, "UNTARGETABLE", param);
                return true;
                
            case "reduceto":
                api.addStatus(caster, "REDUCETO_" + param, 99);
                return true;
             // === NEW: Ticking Time Bomb ===
            case "timebomb":
                String[] tbParts = rawParam.split("_");
                int bombDamage = Integer.parseInt(tbParts[0]);
                int bombDuration = Integer.parseInt(tbParts[1]);
                
                // === THE FIX: Add the "_TURN" tag to the end of the sticky note! ===
                api.addStatus(caster, "TIMEBOMB_" + bombDamage + "_" + bombDuration + "_TURN", bombDuration);
                
                System.out.println("LIBRARY: " + caster.getBaseCard().getName() + " armed a time bomb! It will fire in " + bombDuration + " turns!");
                return true;
            case "reduceby":
                api.addStatus(caster, "REDUCEBY_" + param, 99);
                return true;
                
            case "auraatkdown":
                api.addStatus(caster, "AURAATKDOWN_" + param, 99);
                return true;
                
             // === NEW: Temporary ATK Buff ===
            case "tempatk":
                String[] atkParts = rawParam.split("_");
                int atkAmount = Integer.parseInt(atkParts[0]);
                int atkDuration = Integer.parseInt(atkParts[1]);
                
                caster.setAtk(caster.getAtk() + atkAmount);
                api.addStatus(caster, "TEMPATK_" + atkAmount, atkDuration);
                System.out.println("LIBRARY: " + caster.getBaseCard().getName() + " temporarily gained +" + atkAmount + " ATK for " + atkDuration + " rounds!");
                return true;
                
            // === NEW: Temporary HP Buff ===
            case "temphp":
                String[] hpParts = rawParam.split("_");
                int hpAmount = Integer.parseInt(hpParts[0]);
                int hpDuration = Integer.parseInt(hpParts[1]);
                
                caster.setHp(caster.getHp() + hpAmount);
                api.addStatus(caster, "TEMPHP_" + hpAmount, hpDuration);
                System.out.println("LIBRARY: " + caster.getBaseCard().getName() + " temporarily gained +" + hpAmount + " HP for " + hpDuration + " rounds!");
                return true;  
                
             // === NEW: Permanently Disable Special ===
            case "disable_special":
                if (caster != null) {
                    caster.setSpecialDisabled(true);
                    System.out.println("LIBRARY: " + caster.getBaseCard().getName() + " had its special ability permanently exhausted!");
                }
                return true;
             // === NEW: Enforce Summoning Sickness ===
            case "exhaust_action":
                if (caster != null) {
                    caster.setActions(0);
                    System.out.println("LIBRARY: " + caster.getBaseCard().getName() + " exhausted its actions for this turn!");
                }
                return true;
                
            case "drawcards":
                api.forceDraw(caster, param);
                System.out.println("LIBRARY: Drew " + param + " cards directly to hand!");
                return true;
             // === NEW: Steal from Enemy Deck ===
            case "stealenemydraw":
                api.stealEnemyDraw(caster, param);
                System.out.println("LIBRARY: Stole " + param + " card(s) from the enemy deck directly into hand!");
                return true;
                
            case "healfieldtomax":
                // === THE FIX: Use the Dog Tags instead of the local screen identity! ===
                api.healPlayerField(api.isCasterPlayer1(caster));
                System.out.println("LIBRARY: Healed allied field to MAX HP!");
                return true;
                
            case "nukefield":
                api.nukeField();
                System.out.println("LIBRARY: NUKED THE ENTIRE FIELD!");
                return true;
                
            case "addatk":
                caster.setAtk(caster.getAtk() + param);
                System.out.println("LIBRARY: Added " + param + " ATK to " + caster.getBaseCard().getName());
                return true;
                
            case "addhp":
                caster.setHp(caster.getHp() + param);
                System.out.println("LIBRARY: Added " + param + " HP to " + caster.getBaseCard().getName());
                return true;
            
            case "stealcard":
                return doStealCard(api, param, isLocalPlayer, targets);

            case "damageenemyfield":
                // === THE FIX: Pass the physical card! ===
                api.damageEnemyField(caster, param);
                System.out.println("LIBRARY: " + caster.getBaseCard().getName() + " dealt " + param + " damage to the enemy field!");
                return true;
                
            case "adddodges": {
                List<BattleCard> dodgeTargets = (targets != null && !targets.isEmpty()) ? targets : java.util.Collections.singletonList(caster);
                for (BattleCard targetCard : dodgeTargets) {
                    if (targetCard != null) {
                        targetCard.addDodges(param);
                        System.out.println("LIBRARY: " + targetCard.getName() + " gained " + param + " dodges!");
                    }
                }
                return true;
            } // <--- Added closing brace
            
            // === NEW: Omni-Dodge (Dodges physical AND special attacks!) ===
            case "omni_dodge": {
                List<BattleCard> dodgeTargets = (targets != null && !targets.isEmpty()) ? targets : java.util.Collections.singletonList(caster);
                for (BattleCard targetCard : dodgeTargets) {
                    if (targetCard != null) {
                        targetCard.addDodges(param);
                        // 99 means the permission slip is permanent until the charges run out!
                        api.addStatus(targetCard, "OMNIDODGE", 99); 
                        System.out.println("LIBRARY: " + targetCard.getBaseCard().getName() + " gained " + param + " OMNI-DODGES!");
                    }
                }
                return true;
            } // <--- Added closing brace
            case "selectrevivetofield":
                if (isLocalPlayer) {
                    api.openGraveyardUI(caster);
                }
                return true;
                
            case "martyr_revive":
                // === THE FIX: Lock the opponent's screen so they don't throw Game Over while waiting! ===
                if (api instanceof BattleSystem) {
                    ((BattleSystem)api).isGraveyardAutoPlaceMode = true;
                }
                
                // Only pop the UI for the player who actually owned the Martyr!
                if (isLocalPlayer) {
                    api.triggerGraveyardAutoPlace(caster, caster.getRow(), caster.getCol());
                }
                return true;
                
            case "mimicspecial":
                String lastSpell = api.getLastCastSpecial();
                if (lastSpell != null && !lastSpell.isEmpty()) {
                    System.out.println("LIBRARY: *SQUAWK!* Pirate Parrot is mimicking: " + lastSpell);
                    parseAndExecute(api, lastSpell, isLocalPlayer, caster, targets);
                    return true;
                } else {
                    System.out.println("LIBRARY: *Squawk?* Nothing to mimic yet!");
                    return false;
                }
                
            case "apply_mystic_aura": {
                // 99 duration means it acts as a permanent aura until the dragon dies
                api.addStatus(caster, "MYSTICAURA_" + param, 99);
                System.out.println("LIBRARY: Mystic Dragon activated its permanent aura!");
                return true;
            }
                
            case "sweepatkmult":
                // param is the multiplier (e.g., the '2' in sweepatkmult[2])
                int multiDamage = caster.getAtk() * param;
                
                boolean isCasterP1 = api.isCasterPlayer1(caster);
                BattleCard[][] enemyField = isCasterP1 ? api.getField2() : api.getField1();

                System.out.println("LIBRARY: " + caster.getBaseCard().getName() + " unleashes a wild barrage for " + multiDamage + " damage!");

                // Nuke the enemy side!
                for (int r = 0; r < 2; r++) {
                    for (int c = 0; c < 4; c++) {
                        BattleCard victim = enemyField[r][c];
                        if (victim != null && victim.getHp() > 0) {
                            
                            // 1. Fetch the target's specific dictionary string
                            String targetId = victim.getBaseCard().getCardID();
                            String targetCommandStr = ((BattleSystem)api).allSpecials.get(targetId);

                            // === THE FIX: Route the sweep damage through the Defensive Switchboard FIRST! ===
                            // This fires the async coinflip, and the callback resolves the actual damage!
                            handleDefensiveTrick(api, caster, victim, targetCommandStr, isLocalPlayer, finalTarget -> {
                                if (finalTarget != null && finalTarget.getHp() > 0) {
                                    
                                    int damageDealt = applyDamageModifiers((BattleSystem) api, caster, finalTarget, multiDamage);
                                    
                                    if (damageDealt > 0) {
                                        finalTarget.setHp(finalTarget.getHp() - damageDealt);
                                        if (finalTarget.getHp() <= 0) {
                                            api.destroyCard(finalTarget);
                                        }
                                    }
                                    api.updateBoard(); // Update the visuals after each individual hit resolves!
                                }
                            });
                        }
                    }
                }
                return true;

            case "suicide":
                // Instantly destroys the caster (used for the Slinger's backfire!)
                System.out.println("LIBRARY: " + caster.getBaseCard().getName() + " was destroyed by its own effect!");
                api.destroyCard(caster);
                return true;

            case "defensebuff":
                // Assuming row 1 is your back/defense row!
                // Because we stamped coordinates in placeCard, the engine knows where it is!
                if (caster.getRow() == 1) { 
                    caster.setHp(caster.getHp() + param);
                    System.out.println("LIBRARY: " + caster.getBaseCard().getName() + " placed in Defense Mode! Bones hardened for +" + param + " HP!");
                } else {
                    System.out.println("LIBRARY: Placed in Attack Mode. Bones did not harden.");
                }
                return true;
                
             // === NEW: Friendly Target Verification ===
            
             // === NEW: Friendly Target Verification ===
            case "target_any": // <--- THE FIX: Add target_any here!
            case "target_ally":
            case "target":
                // === THE FAILSAFE: If no targets are provided, the spell breaks! ===
                if (targets == null || targets.isEmpty()) {
                    System.out.println("LIBRARY: Target requirement not met! Spell chain broken.");
                    return false; // This stops the '->' arrow from continuing!
                }
                return true;

            // === NEW: Bounce Targeted Ally ===
            case "return_target_2_hand":
                if (targets != null) {
                    for (BattleCard t : targets) {
                        if (t != null) {
                            System.out.println("LIBRARY: Bouncing targeted ally " + t.getBaseCard().getName() + " to hand!");
                            api.returnToHand(t, isLocalPlayer); // returnToHand automatically applies summoning sickness!
                        }
                    }
                }
                return true;
                
            // === FIXED: Ghouly Face ===
            case "debuffatk":
                // Add a null-armor check just to be absolutely bulletproof!
                if (targets == null) return false; 
                
                for (BattleCard target : targets) {
                    if (target != null) {
                        target.setAtk(target.getAtk() - param);
                        System.out.println("LIBRARY: " + target.getBaseCard().getName() + " was spooked! Lost " + param + " ATK!");
                    }
                }
                return true;

             // === NEW: French Lips (Targeted Multi-Strike) ===
            case "strike":
                int damage = caster.getAtk(); 
                for (BattleCard target : targets) {
                    if (target != null && target.getHp() > 0) {
                        // 1. Fetch the target's specific dictionary string
                        String targetId = target.getBaseCard().getCardID();
                        String targetCommandStr = ((BattleSystem)api).allSpecials.get(targetId);

                        // 2. Route through the Defensive Switchboard BEFORE applying damage!
                        // This fires the async coinflip, and the callback resolves the actual damage!
                        handleDefensiveTrick(api, caster, target, targetCommandStr, isLocalPlayer, finalTarget -> {
                            if (finalTarget != null && finalTarget.getHp() > 0) {
                                int damageDealt = applyDamageModifiers((BattleSystem) api, caster, finalTarget, damage);
                                if (damageDealt > 0) {
                                    finalTarget.setHp(finalTarget.getHp() - damageDealt);
                                    if (finalTarget.getHp() <= 0) {
                                        api.destroyCard(finalTarget);
                                    }
                                }
                                api.updateBoard(); // Update the visuals after each individual hit resolves!
                            }
                        });
                    }
                }
                return true;

            // === NEW: Orc Warrior (Action Refresh) ===
            case "refresh_actions":
                // Give the card its attack phase back!
                caster.setActions(caster.getActions() + param);
                api.showToast(caster.getBaseCard().getName() + " goes into a frenzy and attacks again!");
                System.out.println("LIBRARY: Refreshed " + param + " actions for " + caster.getBaseCard().getName());
                return true;
          
    		

            case "revivespecifictofield":
                String[] parts = rawParam.split("_");
                String targetId = parts[0];
                
                // 1. Look in the graveyard first
                cards specificCard = api.pluckFromGraveyard(targetId);
                
                // === THE HIJACK: If it's not there, it must be a stolen card in the hand! ===
                if (specificCard == null) {
                    specificCard = api.pluckFromHand(targetId, isLocalPlayer);
                }
                
                if (specificCard != null) {
                    boolean success = false;
                    if (parts.length == 3) {
                        int r = Integer.parseInt(parts[1]);
                        int c = Integer.parseInt(parts[2]);
                        success = api.summonToSpecificSlot(specificCard, isLocalPlayer, r, c);
                    } else {
                        success = api.summonToField(specificCard, caster);
                    }
                    
                    if (!success) {
                        api.returnToHand(caster, isLocalPlayer); 
                    }
                    System.out.println("LIBRARY: Revived/Placed " + specificCard.getName() + " to field!");
                    
                    // === THE UNLOCK FIX ===
                    if (api instanceof BattleSystem) {
                        ((BattleSystem)api).isGraveyardAutoPlaceMode = false;
                    }
                    return true; 
                }
                
                // Failsafe unlock
                if (api instanceof BattleSystem) {
                    ((BattleSystem)api).isGraveyardAutoPlaceMode = false;
                }
                return false;
                
            // === NEW: Handles the player hitting "Cancel" on the Graveyard! ===
            case "cancel_revive":
                if (api instanceof BattleSystem) {
                    ((BattleSystem)api).isGraveyardAutoPlaceMode = false;
                }
                api.updateBoard(); // Triggers the Game Over check!
                return true;
          
        
            case "reflectshield":
                // Adds the shield to your sticky note system!
                // param is the number of rounds (e.g., the '5' in reflectshield[5])
                api.addStatus(caster, "REFLECT", param);
                System.out.println("LIBRARY: " + caster.getBaseCard().getName() + " engaged Reflect Shield for " + param + " rounds!");
                return true;
                
            case "bounce_to_hand": 
                // We just ask the API directly who the caster belongs to!
                api.bounceCardToHand(caster, api.isCasterPlayer1(caster));
                System.out.println("LIBRARY: Bounced back to hand!");
                return true;
                
            case "revive_self": 
                api.reviveSelf(caster, api.isCasterPlayer1(caster));
                System.out.println("LIBRARY: Revived itself!");
                return true;
                
            case "donothing": 
                api.finalizeDeath(caster, api.isCasterPlayer1(caster));
                System.out.println("LIBRARY: Coinflip failed. Sent to graveyard.");
                return true;
                
             // === NEW: Pacify (Cannot Attack) ===
             // === UPGRADED: Pacify (Now supports targeted enemies!) ===
            case "pacify":
                // If a target was clicked, pacify them! Otherwise, pacify the caster (Stone Giant).
                List<BattleCard> pacifyTargets = (targets != null && !targets.isEmpty()) ? targets : java.util.Collections.singletonList(caster);
                
                for (BattleCard pTarget : pacifyTargets) {
                    if (pTarget != null) {
                        api.addStatus(pTarget, "PACIFIED", param);
                        System.out.println("LIBRARY: " + pTarget.getBaseCard().getName() + " was pacified and cannot attack!");
                    }
                }
                return true;
                
             // === NEW: Cleanse Pacify on Death ===
            case "unpacify_enemies":
                // Find out which side of the field the enemies are on!
                boolean isP1 = api.isCasterPlayer1(caster);
                BattleCard[][] enemyFieldForWizard = isP1 ? api.getField2() : api.getField1();
                
                // Sweep the enemy field and rip off any PACIFIED sticky notes!
                for (int r = 0; r < 2; r++) {
                    for (int c = 0; c < 4; c++) {
                        if (enemyFieldForWizard[r][c] != null) {
                            api.removeStatus(enemyFieldForWizard[r][c], "PACIFIED");
                        }
                    }
                }
                System.out.println("LIBRARY: The Sea Wizard's spell broke! Enemies are no longer pacified.");
                return true;
    
             // === NEW: Arsonist Abilities ===
             // === TIER 2 UPGRADE: Variable Burn Duration ===
            case "apply_burn": {
                if (targets != null) {
                    // Check if the parameter includes a duration (e.g., apply_burn[2_4])
                    int burnDmg = param;
                    int duration = 99; // Defaults to permanent if no duration is specified
                    
                    if (rawParam.contains("_")) {
                        String[] burnParts = rawParam.split("_");
                        burnDmg = Integer.parseInt(burnParts[0]);
                        duration = Integer.parseInt(burnParts[1]);
                    }
                    
                    for (BattleCard t : targets) {
                        if (t != null) {
                            api.addStatus(t, "BURN_" + burnDmg + "_TURN", duration);
                            System.out.println("LIBRARY: " + t.getBaseCard().getName() + " caught on fire for " + duration + " rounds!");
                        }
                    }
                }
                return true;
            }
            case "nuke_enemy_field": {
                // 999 damage punches through almost any shield and wipes the field!
                api.damageEnemyField(caster, 999);
                System.out.println("LIBRARY: The Arsonist detonated and blew up the enemy field!");
                return true;
            }
         // === NEW: Mystic Dragon (Dynamic Action Refresh) ===
         // === TIER 2: Mystic Dragon (Dynamic Action Refresh) ===
            case "refresh_actions_per_ally": {
                boolean p1Mystic = api.isCasterPlayer1(caster);
                int allyCount = 0;
                
                // Count how many cards are on the player's field
                for (BattleCard ally : api.getFriendlyField(p1Mystic)) {
                    // THE FIX: Ensure the ally exists AND is not the Dragon itself!
                    if (ally != null && ally != caster) { 
                        allyCount++;
                    }
                }
                
                caster.setActions(caster.getActions() + (allyCount * param));
                System.out.println("LIBRARY: Mystic Dragon gained " + (allyCount * param) + " extra attacks!");
                api.showToast("Mystic Conjunction! +" + (allyCount * param) + " attacks!");
                return true;
            }
         // === TIER 2: Silver Scaled Dragon (Dynamic Armor Upgrade) ===
            case "upgrade_reduceby": {
                int currentBlock = api.getStatusModifier(caster, "REDUCEBY");
                if (currentBlock != -1) {
                    
                    // === THE FIX: Calculate the new block and cap it at 10! ===
                    int newBlock = Math.min(10, currentBlock + param);
                    
                    // Rip off the old sticky note and apply the upgraded one!
                    api.removeStatus(caster, "REDUCEBY_" + currentBlock);
                    api.addStatus(caster, "REDUCEBY_" + newBlock, 99);
                    
                    // Only show the popup if it actually upgraded
                    if (currentBlock < 10) {
                        System.out.println("LIBRARY: Silver Scales hardened! Now blocks " + newBlock + " damage!");
                        api.showToast("Silver Skin hardened! -" + newBlock + " damage taken!");
                    } else {
                        System.out.println("LIBRARY: Silver Scales are already at maximum hardness (10)!");
                    }
                } else {
                    // Failsafe in case the original armor got stripped
                    api.addStatus(caster, "REDUCEBY_" + param, 99); 
                }
                return true;
            }
         // === TIER 2: Thief of Souls (Eerie Elf) ===
            case "steal_special": {
                if (targets != null && !targets.isEmpty()) {
                    BattleCard victim = targets.get(0);
                    String victimId = victim.getBaseCard().getCardID();
                    
                    String victimCommand = null;
                    if (api instanceof BattleSystem) {
                        victimCommand = ((BattleSystem)api).allSpecials.get(victimId);
                    }

                    // === THE FIX: Added !api.hasStatus(victim, "SILENCED") to the check! ===
                    if (victimCommand != null && !victimCommand.isEmpty() && !victimCommand.equals("NONE") 
                        && !victimCommand.contains("steal_special") && !api.hasStatus(victim, "SILENCED")) {
                        
                        // 1. The Elf permanently memorizes the victim's dictionary string
                        caster.setOverriddenSpecial(victimCommand);
                        
                        // 2. The Victim is silenced so they "lose" the ability (The Steal!)
                        api.addStatus(victim, "SILENCED", 99);
                        
                        System.out.println("LIBRARY: Eerie Elf stole the ability from " + victim.getBaseCard().getName() + "!");
                        api.showToast("Stole " + victim.getBaseCard().getName() + "'s Special!");
                        
                        // 3. Immediately trigger ON_PLAY passives just in case we stole an Aura!
                        if (api instanceof BattleSystem) {
                            ((BattleSystem)api).triggerEvent(caster, isLocalPlayer, "ON_PLAY");
                        }
                    } else {
                        api.showToast("Target has no valid special to steal!");
                    }
                }
                return true;
            }
            case "field_burn": {
                String[] fbParts = rawParam.split("_");
                int burnDmg = Integer.parseInt(fbParts[0]);
                int burnRounds = Integer.parseInt(fbParts[1]);
                
                // We multiply rounds by 2 so it lasts the exact amount of "Turns" requested!
                int burnTurns = burnRounds * 2; 
                
                // Glues the sticky note to the Arsonist's GHOST in the graveyard!
                api.addStatus(caster, "FIELDBURN_" + burnDmg + "_TURN", burnTurns);
                System.out.println("LIBRARY: Field engulfed in flames for " + burnRounds + " full rounds!");
                return true;
            }
         // === TIER 2: Targeted Heal ===
            case "heal_target": {
                if (targets != null) {
                    for (BattleCard t : targets) {
                        if (t != null) {
                            // Heal, but don't accidentally "overheal" past the card's original max HP!
                            int maxHp = t.getBaseCard().getHp();
                            int newHp = Math.min(t.getHp() + param, maxHp);
                            t.setHp(newHp);
                            System.out.println("LIBRARY: Healed " + t.getBaseCard().getName() + " by " + param + " HP!");
                        }
                    }
                }
                return true;
            }

         // === TIER 2: Bushy Triggers (Moved from Switchboard!) ===
            case "berry_drop": {
                boolean p1 = api.isCasterPlayer1(caster);
                java.util.List<BattleCard> allies = api.getFriendlyField(p1);
                java.util.List<BattleCard> injuredAllies = new java.util.ArrayList<>();
                
                for (BattleCard ally : allies) {
                    if (ally != null && ally.getHp() < ally.getBaseCard().getHp()) {
                        injuredAllies.add(ally);
                    }
                }
                
                if (!injuredAllies.isEmpty()) {
                    java.util.Collections.shuffle(injuredAllies, api.getSharedRng());
                    BattleCard luckyAlly = injuredAllies.get(0);
                    luckyAlly.setHp(Math.min(luckyAlly.getHp() + param, luckyAlly.getBaseCard().getHp()));
                    
                    api.showToast(caster.getBaseCard().getName() + " dropped a berry! Healed " + luckyAlly.getBaseCard().getName() + "!");
                    api.updateBoard();
                }
                return true;
            }
            
            case "vine_thorns": {
                api.showToast(caster.getBaseCard().getName() + "'s thorns struck back!");
                if (targets != null && !targets.isEmpty()) {
                    BattleCard attackerCard = targets.get(0);
                    int damageBack = caster.getAtk(); 
                    attackerCard.setHp(attackerCard.getHp() - damageBack);
                    
                    if (attackerCard.getHp() <= 0) {
                        api.destroyCard(attackerCard);
                    }
                    api.updateBoard();
                }
                return true;
            }
            // === TIER 2: AoE Field Buff ===
         // === TIER 2: AoE Field Buff (FIXED) ===
         // === TIER 2: AoE Field Buff (FIXED) ===
         // === TIER 2: AoE Field Buff (FIXED) ===
            case "buff_allies": {
                // THE FIX: Renamed 'isP1' to 'isTorchOwnerP1' to avoid duplicate variable errors!
                boolean isTorchOwnerP1 = api.isCasterPlayer1(caster);
                BattleCard[][] myField = isTorchOwnerP1 ? api.getField1() : api.getField2();
                
                for (int r = 0; r < 2; r++) {
                    for (int c = 0; c < 4; c++) {
                        BattleCard ally = myField[r][c];
                        
                        // THE FIX: Ensure the ally exists AND is not the card casting the spell!
                        if (ally != null && ally != caster) {
                            // Permanently raise base stats!
                            ally.setAtk(ally.getAtk() + param);
                            ally.setHp(ally.getHp() + param);
                            System.out.println("LIBRARY: " + ally.getBaseCard().getName() + " was buffed by +" + param + "/+" + param + "!");
                        }
                    }
                }
                return true;
            }
            
         // === TIER 2: Auto-Heal Random Allies (Raging Fish) ===
            case "heal_random_allies": {
                boolean fish1 = api.isCasterPlayer1(caster);
                List<BattleCard> allies = api.getFriendlyField(fish1);
                List<BattleCard> injuredAllies = new java.util.ArrayList<>();
                
                // Find everyone who is missing health
                for (BattleCard ally : allies) {
                    if (ally != null && ally.getHp() < ally.getBaseCard().getHp()) {
                        injuredAllies.add(ally);
                    }
                }
                
                // Shuffle the injured list using the Synced Network RNG!
                // This guarantees both computers pick the exact same 2 cards to heal.
                java.util.Collections.shuffle(injuredAllies, api.getSharedRng());
                
                int healedCount = 0;
                for (BattleCard ally : injuredAllies) {
                    if (healedCount >= param) break; // Stop after we hit the limit (2)
                    
                    ally.setHp(ally.getBaseCard().getHp());
                    System.out.println("LIBRARY: Sushi healed " + ally.getBaseCard().getName() + " to MAX HP!");
                    healedCount++;
                }
                return true;
            }
            
         // === TIER 2: Silence (Cannot Use Specials) ===
            case "silence": {
                // If they clicked a target, freeze the target. Otherwise, freeze the caster.
                java.util.List<BattleCard> silenceTargets = (targets != null && !targets.isEmpty()) ? targets : java.util.Collections.singletonList(caster);
                
                for (BattleCard t : silenceTargets) {
                    if (t != null) {
                        api.addStatus(t, "SILENCED", param);
                        System.out.println("LIBRARY: " + t.getBaseCard().getName() + " was silenced/frozen and cannot use specials!");
                    }
                }
                return true;
            }
         // === TIER 2: Druid's Nature Blessing ===
            case "apply_nature_blessing": {
                api.addStatus(caster, "NATUREBLESSING_" + param + "_TURN", 1);
                System.out.println("LIBRARY: Druid's Blessing is active!");
                return true;
            }
         // === TIER 2: Token Spawner (Upgraded for Manual Placement!) ===
            case "summon_token": {
                String[] tokenParts = rawParam.split("_");
                int count = 1;
                String tokenId = rawParam; 
                
                if (tokenParts.length > 1) {
                    count = Integer.parseInt(tokenParts[0]);
                    tokenId = tokenParts[1];
                }

                // 1. The Failsafe: Check available empty slots!
                boolean p1 = api.isCasterPlayer1(caster);
                BattleCard[][] myField = p1 ? api.getField1() : api.getField2();
                
                int emptySlots = 0;
                for (int r = 0; r < 2; r++) {
                    for (int c = 0; c < 4; c++) {
                        if (myField[r][c] == null) emptySlots++;
                    }
                }

                if (emptySlots == 0) {
                    System.out.println("LIBRARY: Field is full! Token spawning halted.");
                    api.showToast("Field is full! Cannot summon tokens.");
                    return true;
                }

                // 2. The Opponent stops here! Only the local player gets the UI!
                if (!isLocalPlayer) return true;

                // Cap the count to the available slots so the GUI doesn't soft-lock!
                int finalCount = Math.min(count, emptySlots);

                // 3. Put the GUI into Revive Placement Mode!
                BattleGUI.isRevivePlacementMode = true;
                BattleGUI.pendingReviveId = "TOKEN_" + finalCount + "_" + tokenId.trim();
                BattleGUI.currentReviveCaster = caster;
                
                api.showToast("Select a slot to deploy your token!");
                return true;
            }

            // === TIER 2: Token Manual Execution (Step 2: Network Sync) ===
            case "token_execute": {
                // Format: token_execute[01991_r_c]
                String[] execParts = rawParam.split("_");
                String summonId = execParts[0].trim();
                int r = Integer.parseInt(execParts[1]);
                int c = Integer.parseInt(execParts[2]);
                
                // Deploy the token to the exact slot the user clicked!
                if (api instanceof BattleSystem) {
                    BattleSystem bs = (BattleSystem) api;
                    cards baseCard = bs.allCards.get(summonId);
                    if (baseCard != null) {
                        boolean success = bs.summonToSpecificSlot(baseCard, isLocalPlayer, r, c);
                        if (success) {
                            System.out.println("LIBRARY: Manually deployed token " + baseCard.getName() + " to Row " + r + " Col " + c);
                            bs.updateBoard();
                        }
                    }
                }
                return true;
            }
         // === TIER 2: The Recurring Spawner (Scrap Bot) ===
            case "apply_spawner": {
                // Format: apply_spawner[01991_3] -> Spawns card 01991 every 3 turns
                String[] spawnParts = rawParam.split("_");
                String tokenId = spawnParts[0];
                int frequency = Integer.parseInt(spawnParts[1]);

                // We use the same timer logic as the Evolution and Watch Tower!
                api.addStatus(caster, "SPAWNER_" + tokenId + "_" + frequency + "_TURN", frequency);
                System.out.println("LIBRARY: Factory online! Will manufacture " + tokenId + " every " + frequency + " turns.");
                return true;
            }

            // === TIER 2: Token-Scaling Damage (Battle Bot) ===
            case "token_bonus_strike": {
                // Format: token_bonus_strike[01991_1] -> +1 damage for every 01991 token on the field
                String[] bonusParts = rawParam.split("_");
                String tokenId = bonusParts[0];
                int dmgPerToken = Integer.parseInt(bonusParts[1]);

                boolean p1 = api.isCasterPlayer1(caster);
                java.util.List<BattleCard> myField = api.getFriendlyField(p1);
                
                // Count how many specific tokens are on our side of the board
                int tokenCount = 0;
                for (BattleCard ally : myField) {
                    if (ally != null && ally.getBaseCard().getCardID().trim().equals(tokenId)) {
                        tokenCount++;
                    }
                }

                // If we have scraps and we successfully hit a target, apply the bonus damage!
                if (tokenCount > 0 && targets != null) {
                    int bonusDmg = tokenCount * dmgPerToken;
                    for (BattleCard t : targets) {
                        if (t != null && t.getHp() > 0) {
                            t.setHp(t.getHp() - bonusDmg);
                         // === THE NEW VERIFICATION PRINT ===
                            // Calculate the combined damage just for the terminal readout!
                            int totalCombinedDamage = caster.getAtk() + bonusDmg;
                            
                            System.out.println("LIBRARY: " + caster.getBaseCard().getName() + 
                                               " dealt " + totalCombinedDamage + " damage to " + 
                                               t.getBaseCard().getName() + 
                                               " (+" + bonusDmg + " Scrap Bonus!)");
                            
                            // Check if the bonus damage was a fatal blow!
                            if (t.getHp() <= 0) {
                                api.destroyCard(t);
                            }
                        }
                    }
                    api.updateBoard(); // Force the GUI to update the health bars!
                }
                return true;
            }

         // === TIER 2: Constructor Menu (Step 1: The Local Choice) ===
            case "constructor_menu": {
                // Format: constructor_menu[01991_01025_01026_01030]

                // 1. Destroy the selected targets on BOTH screens instantly!
                if (targets != null) {
                    for (BattleCard t : targets) {
                        api.destroyCard(t);
                    }
                    api.updateBoard(); 
                }

                // 2. The Opponent stops here! Only the local player sees the menu!
                if (!isLocalPlayer) return true; 

                // 3. Build the Dropdown Menu
                if (api instanceof BattleSystem) {
                    BattleSystem bs = (BattleSystem) api;
                    String[] prt = rawParam.split("_");
                    
                    class CardOption {
                        cards c;
                        CardOption(cards c) { this.c = c; }
                        public String toString() { return c.getName() + " (ATK: " + c.getAtk() + " | HP: " + c.getHp() + ")"; }
                    }

                    java.util.List<CardOption> optionsList = new java.util.ArrayList<>();
                    // We start at i=1 because index 0 is the sacrifice ID (01991)
                    for (int i = 1; i < prt.length; i++) {
                        cards opt = bs.allCards.get(prt[i].trim());
                        if (opt != null) optionsList.add(new CardOption(opt));
                    }

                    CardOption[] options = optionsList.toArray(new CardOption[0]);
                    CardOption chosen = (CardOption) javax.swing.JOptionPane.showInputDialog(
                            null,
                            "Factory is ready! Select a bot to manufacture:",
                            "Constructor Bot",
                            javax.swing.JOptionPane.QUESTION_MESSAGE,
                            null,
                            options,
                            options[0]
                    );

                    if (chosen == null) return true; // The user clicked "Cancel"

                    // 4. Enter placement mode
                    BattleGUI.isRevivePlacementMode = true;
                    BattleGUI.pendingReviveId = "BOT_" + chosen.c.getCardID();
                    BattleGUI.currentReviveCaster = caster;
                    
                    api.showToast("Manufactured! Click an empty slot to deploy it.");
                }
                return true;
            }

            // === TIER 2: Constructor Execute (Step 2: The Network Sync) ===
            case "constructor_execute": {
                // Format: constructor_execute[01100_r_c]
                String[] execParts = rawParam.split("_");
                String summonId = execParts[0].trim();
                int r = Integer.parseInt(execParts[1]);
                int c = Integer.parseInt(execParts[2]);
                
                // Deploy the chosen bot to the specific slot!
                if (api instanceof BattleSystem) {
                    BattleSystem bs = (BattleSystem) api;
                    cards baseCard = bs.allCards.get(summonId);
                    if (baseCard != null) {
                        boolean success = bs.summonToSpecificSlot(baseCard, isLocalPlayer, r, c);
                        if (success) {
                            System.out.println("LIBRARY: Sacrificed scraps to manufacture " + baseCard.getName() + "!");
                            bs.updateBoard();
                        }
                    }
                }
                return true;
            }
                
         // === TIER 2: Water Bubble Shield (01022) ===
            case "water_shield_up": {
                // === THE FIX: Glue the Aura to the Spirit so future cards can see it! ===
                // 99 means it lasts permanently until the Spirit dies!
                api.addStatus(caster, "WATERSHIELD_" + param, 99);

                boolean p1 = api.isCasterPlayer1(caster);
                java.util.List<BattleCard> myField = api.getFriendlyField(p1);
                
                for (BattleCard ally : myField) {
                    if (ally != null) {
                        ally.setHp(ally.getHp() + param);
                    }
                }
                System.out.println("LIBRARY: Water Spirit granted + " + param + " HP to all allies!");
                api.updateBoard();
                return true;
            }
            
            case "water_shield_down": {
                boolean p1 = api.isCasterPlayer1(caster);
                java.util.List<BattleCard> myField = api.getFriendlyField(p1);
                
                for (BattleCard ally : myField) {
                    if (ally != null) {
                        ally.setHp(ally.getHp() - param);
                        
                        // If losing the shield causes their HP to drop to 0, they drown!
                        if (ally.getHp() <= 0 && !ally.isDying()) {
                            api.destroyCard(ally);
                        }
                    }
                }
                System.out.println("LIBRARY: Water Spirit died! The protective bubble popped.");
                api.updateBoard();
                return true;
            }

            // === TIER 2: Marked for Death (01028) ===
            case "mark_or_execute": {
                if (targets != null) {
                    for (BattleCard t : targets) {
                        if (t != null && t.getHp() > 0) {
                            // If the target ALREADY has the mark, execute them for double damage!
                            if (api.hasStatus(t, "MARKED")) {
                                int bonusDmg = caster.getAtk(); // Deal base damage AGAIN to equal 2x!
                                t.setHp(t.getHp() - bonusDmg);
                                
                                System.out.println("LIBRARY: Arctic Fox triggered MARKED! Dealt " + bonusDmg + " BONUS damage!");
                                api.removeStatus(t, "MARKED"); // Consume the mark
                                
                                if (t.getHp() <= 0) api.destroyCard(t);
                            } 
                            // If they don't have the mark, apply it for next time!
                            else {
                                api.addStatus(t, "MARKED", 99);
                                System.out.println("LIBRARY: Arctic Fox applied MARKED FOR DEATH to " + t.getBaseCard().getName() + "!");
                                api.showToast(t.getBaseCard().getName() + " is Marked for Death!");
                            }
                        }
                    }
                    api.updateBoard();
                }
                return true;
            }

            // === TIER 2: Assimilate Stats (01029) ===
            case "assimilate": {
                if (targets != null && !targets.isEmpty()) {
                    BattleCard target = targets.get(0);
                    
                    int bonusAtk = target.getBaseCard().getAtk();
                    int bonusHp = target.getBaseCard().getHp();
                    
                    caster.setAtk(caster.getAtk() + bonusAtk);
                    caster.setHp(caster.getHp() + bonusHp);
                    
                    System.out.println("LIBRARY: Formless Beast absorbed stats! Gained +" + bonusAtk + "/+" + bonusHp);
                    api.showToast("Formless Beast assimilated " + target.getBaseCard().getName() + "'s stats!");
                    api.updateBoard();
                }
                return true;
            }

            // === TIER 2: Camelcorn Positional Healing (01031) ===
            case "apply_camel_heal": {
                // We set the duration to 1 so it triggers every single turn!
                api.addStatus(caster, "CAMELHEAL_" + param + "_TURN", 1);
                System.out.println("LIBRARY: Camelcorn's positional healing aura is active!");
                return true;
            
            }
            case "freeze": {
                // Freeze the target for X turns (they can't attack or use abilities, but they can still be targeted and hit with damage)
                List<BattleCard> freezeTargets = (targets != null && !targets.isEmpty()) 
                ? targets 
                : java.util.Collections.singletonList(caster);
                
                for (BattleCard t : freezeTargets) {
                    if (t != null) {
                        api.addStatus(t, "FREEZE", param);
                        System.out.println("LIBRARY: " + t.getBaseCard().getName() + " is frozen for " + param + " turns!");
                    }
                }
                return true;
            }
                
            default:
                System.out.println("Abort: Unknown special action -> " + action);
                return true;
        }
    }

    // ==========================================
    // 4. THE ACTUAL ABILITIES
    // ==========================================
    private static boolean doInstakill(BattleAPI api, int maxTargets, boolean isLocalPlayer, List<BattleCard> targets) {
        if (targets == null || targets.isEmpty()) {
            System.out.println("Instakill failed: No targets were passed from GUI.");
            return false; // Action failed
        }
        
        int killed = 0;
        for (BattleCard t : targets) {
            if (killed >= maxTargets) break; 
            
            // Check if the target has a dodge charge to burn!
            if (t.consumeDodge()) {
                System.out.println("LIBRARY: *SWOOSH!* " + t.getBaseCard().getName() + " dodged the instakill! (" + t.getDodges() + " dodges remaining)");
                continue; 
            }

            System.out.println("Destroying card: " + t.getBaseCard().getName());
            api.destroyCard(t);
            killed++;
        }
        
        // Return true if we successfully killed at least one target
        return killed > 0;
    }
    
    private static boolean doStealCard(BattleAPI api, int maxTargets, boolean isLocalPlayer, List<BattleCard> targets) {
        if (targets == null || targets.isEmpty()) {
            System.out.println("Steal failed: No targets were passed.");
            return false;
        }
        
        int stolen = 0;
        for (BattleCard t : targets) {
            if (stolen >= maxTargets) break; 
            
            cards stolenBaseCard = t.getBaseCard();
            
            // 1. Move it to the hand safely
            api.changeCardOwnership(t, isLocalPlayer);
            
            // 2. THE FIX: Force the player to place it!
            if (isLocalPlayer) {
                api.forcePlacementMode(stolenBaseCard.getCardID());
            }
            
            stolen++;
        }
        return stolen > 0;
    }
    
    private static boolean doReturnToHand(BattleAPI api, boolean isLocalPlayer, BattleCard caster) {
        if (caster != null) {
            System.out.println("Returning " + caster.getBaseCard().getName() + " to hand.");
            api.returnToHand(caster, isLocalPlayer);
            return true; // Action succeeded
        }
        return false; // Action failed
    }

    // ==========================================
    // 5. THE RULES ENGINE FOR TARGETING
    // ==========================================
    public static java.util.List<BattleCard> getValidTargets(BattleAPI api, boolean isSpecialAttack) {
        java.util.List<BattleCard> allEnemies = api.getEnemyCards();
        
        // Rule 1: Specials bypass standard target immunity!
        if (isSpecialAttack) {
            return allEnemies; 
        }

        // Rule 2: Normal attacks cannot hit cards with the UNTARGETABLE sticky note
        java.util.List<BattleCard> attackable = new java.util.ArrayList<>();
        for (BattleCard bc : allEnemies) {
            if (!api.hasStatus(bc, "UNTARGETABLE")) {
                attackable.add(bc);
            }
        }
        return attackable;
    }

    // ==========================================
    // 6. THE RULES ENGINE FOR DAMAGE MODIFIERS
    // ==========================================
    public static int applyDamageModifiers(BattleSystem api, BattleCard attacker, BattleCard target, int baseDamage) {
        int finalDamage = baseDamage;
        
        // === THE SAFETY NET: Prevent crash if the damage comes from a dead card! ===
        String attackerName = (attacker != null) ? attacker.getBaseCard().getName() : "A Field Effect";
        System.out.println("RULES ENGINE: " + attackerName + " is hitting " + target.getBaseCard().getName() + " for " + baseDamage + " damage.");

        // === STEP 0: Invincibility / Untargetable ===
        if (api.hasStatus(target, "UNTARGETABLE")) { 
            System.out.println("RULES ENGINE: " + target.getBaseCard().getName() + " is immune! Attack completely deflected.");
            return 0; // Short-circuit and exit early!
        }
        
     // === STEP 0.5: Dodge Mechanics ===
        if (target.consumeDodge()) {
            System.out.println("RULES ENGINE: *SWOOSH!* " + target.getBaseCard().getName() + " dodged the attack! (" + target.getDodges() + " dodges remaining)");
            
            // Clean up the Omni-Dodge sticky note if the final charge was burned by a physical punch!
            if (target.getDodges() <= 0 && api.hasStatus(target, "OMNIDODGE")) {
                api.removeStatus(target, "OMNIDODGE");
            }
            
            return 0;
        }

        // === STEP 1: Radiating Auras ===
        int auraAtkDown = api.getAuraModifier(target, "AURAATKDOWN");
        if (auraAtkDown > 0) {
            finalDamage -= auraAtkDown;
            System.out.println("RULES ENGINE: Buffo's aura weakened the attack by " + auraAtkDown + "!");
        }

        // === STEP 2: Reduce BY a specific number ===
        int reduceByVal = api.getStatusModifier(target, "REDUCEBY");
        if (reduceByVal != -1) { 
            finalDamage -= reduceByVal;
            System.out.println("RULES ENGINE: Armor absorbed " + reduceByVal + " damage!");
        }

        // === STEP 3: Reduce TO a specific number ===
        int reduceToVal = api.getStatusModifier(target, "REDUCETO");
        if (reduceToVal != -1) { 
            finalDamage = reduceToVal;
            System.out.println("RULES ENGINE: Damage crushed TO " + reduceToVal + "!");
        }

        // === THE FLOOR: Prevent negative damage (healing) ===
        if (finalDamage < 0) {
            finalDamage = 0;
            System.out.println("RULES ENGINE: Damage fully negated! Final damage is 0.");
        }

        return finalDamage;
    }
    
 // === NEW: Generic Status Tick Handler ===
 	// This fires every single time the clock ticks down on an active status.
 	public static void handleStatusTick(BattleAPI api, BattleCard target, String statusKey) {
 		
 		// 1. Standard Burn (Single Target DoT)
 		if (statusKey.startsWith("BURN_")) {
 			int dmg = Integer.parseInt(statusKey.split("_")[1]);
 			System.out.println("LIBRARY: " + target.getBaseCard().getName() + " suffered " + dmg + " burn damage!");
 			
 			// Use modifyHp because it safely handles negative numbers AND checks for death!
 			api.modifyHp(target, -dmg); 
 			api.updateBoard();
 		} 
 		
 		// 2. Field Burn (Area of Effect DoT)
 		else if (statusKey.startsWith("FIELDBURN_")) {
 			int dmg = Integer.parseInt(statusKey.split("_")[1]);
 			System.out.println("LIBRARY: The field burns for " + dmg + " damage!");
 			
 			// Because the ghost still has its dog tags, damageEnemyField knows exactly where to aim!
 			api.damageEnemyField(target, dmg);
 			api.updateBoard();
 		}
 	}
    
    private static boolean doMartyrRevive(BattleAPI api, boolean isLocalPlayer, BattleCard caster) {
        // 1. Only the player whose card died gets the popup
        if (!isLocalPlayer) return true; 

        java.util.List<cards> grave = api.getGraveyard();
        if (grave == null || grave.isEmpty()) {
            System.out.println("LIBRARY: Graveyard is empty. Martyr's ability fizzles.");
            return false; 
        }

        // 2. Pop the Graveyard UI locally! 
        cards[] graveArray = grave.toArray(new cards[0]);
        cards chosenCard = (cards) javax.swing.JOptionPane.showInputDialog(null,
                "The Martyr fell! Select a card to take its place:", "Graveyard Search",
                javax.swing.JOptionPane.QUESTION_MESSAGE, null, graveArray,
                graveArray[graveArray.length - 1]
        );

        if (chosenCard == null) return false;

        // 3. Extract the dying card's coordinates
        int r = caster.getRow(); 
        int c = caster.getCol();

        // 4. === THE FIX: Route it through your existing, battle-tested network helper! ===
        // This safely builds the string, broadcasts it, and executes it locally.
        executeSlotAction(api, "REVIVE", chosenCard.getCardID(), r, c, caster, isLocalPlayer);
        
        return true;
    }
    // === NEW: The Ultimate Badge Translator ===
    public static String getRequiredBadge(BattleAPI api, String rawCode) {
        if (rawCode == null || rawCode.trim().isEmpty()) return "00";

        String[] parts = rawCode.split("\\|");
        boolean isChosen = parts[0].trim().equalsIgnoreCase("CHOSEN");
        int offset = isChosen ? 1 : 0;

        if (parts.length < (2 + offset)) return "00";

        String defaultBadge = parts[offset].trim();

        if (rawCode.toLowerCase().contains("mimicspecial")) {
            String lastSpell = api.getLastCastSpecial();
            
            if (lastSpell != null && lastSpell.toUpperCase().contains("CHOSEN")) {
                String[] memoryParts = lastSpell.split("\\|");
                boolean lastIsChosen = memoryParts[0].trim().equalsIgnoreCase("CHOSEN");
                int lastOffset = lastIsChosen ? 1 : 0;
                
                if (memoryParts.length > lastOffset) {
                    return memoryParts[lastOffset].trim();
                }
            }
        }

        return defaultBadge;
    }
    

 // === 1. Slot Action Builder ===
    public static void executeSlotAction(BattleAPI api, String actionCategory, String targetId, int row, int col, BattleCard caster, boolean isLocalPlayer) {
        String rawCommand = "";
        
        if (actionCategory.equals("REVIVE")) {
            // === THE HIJACK: Is this a Constructor Bot payload? ===
            if (targetId.startsWith("BOT_")) {
                String pureId = targetId.replace("BOT_", "");
                rawCommand = "constructor_execute[" + pureId + "_" + row + "_" + col + "]";
            } 
            // === THE HIJACK 2: Is this a Multi-Token placement? ===
            else if (targetId.startsWith("TOKEN_")) {
                String[] tParts = targetId.split("_");
                int countLeft = Integer.parseInt(tParts[1]);
                String tId = tParts[2];
                
                rawCommand = "token_execute[" + tId + "_" + row + "_" + col + "]";
                
                // If there are more tokens to place, queue the GUI to instantly pop back open!
                countLeft--;
                if (countLeft > 0) {
                    final int newCount = countLeft;
                    // We use invokeLater so it turns the mode back ON after BattleGUI tries to turn it OFF!
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        BattleGUI.isRevivePlacementMode = true;
                        BattleGUI.pendingReviveId = "TOKEN_" + newCount + "_" + tId;
                        BattleGUI.currentReviveCaster = caster; // Keep the original caster
                        api.showToast("Place " + newCount + " more token(s)!");
                    });
                }
            } else {
                rawCommand = "revivespecifictofield[" + targetId + "_" + row + "_" + col + "]";
            }
        }
        
        String finalCommand = "PASSIVE | NONE | if_1: " + rawCommand + " | if_2: null";
        
        api.broadcastSpecialExecution(caster, null, finalCommand);
        parseAndExecute(api, finalCommand, isLocalPlayer, caster, null);
    }

    // === 2. Graveyard UI Checker ===
    public static boolean requiresGraveyardSelection(String rawCode) {
        return rawCode != null && rawCode.toLowerCase().contains("selectrevivetofield");
    }

    // === 3. Targeted Revive Builder ===
    public static String buildTargetedReviveCommand(String chosenCardId) {
        return "PASSIVE | NONE | if_1: revivespecifictofield[" + chosenCardId + "] | if_2: null";
    }

    // === NEW: The Shapeshifter Tool ===
    public static String getEffectiveCommand(BattleAPI api, String rawCode) {
        if (rawCode != null && rawCode.toLowerCase().contains("mimicspecial")) {
            String lastSpell = api.getLastCastSpecial();
            
            if (lastSpell != null && !lastSpell.isEmpty() && 
            		   (lastSpell.toUpperCase().contains("CHOSEN") || lastSpell.toUpperCase().contains("ACTIVE"))) {
            		    return lastSpell; 
            		}
        }
        return rawCode; 
    }
 // ==========================================
 	// 2. THE DEFENSE SWITCHBOARD (Pre-Attack Interrupts)
 	// ==========================================
 // 1. Existing signature for the GUI (Normal Attacks) -> Always broadcasts!
  	public static void handleDefensiveTrick(BattleAPI api, BattleCard attacker, BattleCard target, String commandStr, java.util.function.Consumer<BattleCard> onResolve) {
  		handleDefensiveTrick(api, attacker, target, commandStr, true, onResolve);
  	}

  	// 2. New Overloaded version for Specials -> Uses the isLocalAttack flag to prevent duplicate network echoing!
public static void handleDefensiveTrick(BattleAPI api, BattleCard attacker, BattleCard target, String commandStr, boolean isLocalAttack, java.util.function.Consumer<BattleCard> onResolve) {
  		
  		if (api.hasStatus(target, "REFLECT")) { 
  			System.out.println("SHIELD ACTIVE! " + target.getBaseCard().getName() + " reflects the attack back!");
  			onResolve.accept(attacker);
  			return; 
  		}

String defendTrick = extractSegment(commandStr, "ON_DEFEND");
  		
  		// === THE FIX: Kick post-hit effects out of the switchboard! ===
  		if (defendTrick == null || defendTrick.contains("berry_drop") || defendTrick.contains("vine_thorns")) {
  			onResolve.accept(target); 
  			return;
  		}
  		
  		
  		// --- T-BOT'S REDIRECT ---
  		if (defendTrick.contains("confuse_redirect")) {
  			boolean isHeads = api.getSharedRng().nextBoolean();
  			
  			String headsMsg = "HEADS! T-BOT takes the hit!";
  			String tailsMsg = "TAILS! Redirected to a teammate!";
  			
  			if (isHeads) {
  				if (isLocalAttack) api.broadcastDefensiveCoinflip(true, headsMsg, tailsMsg); 
  				api.triggerCoinflip(true, headsMsg, tailsMsg, () -> onResolve.accept(target));
  			} else {
  				BattleCard newTarget = calculateRedirectTarget(api, attacker, target);
  				
  				if (newTarget == attacker) {
  					tailsMsg = "TAILS! Attack backfired onto " + newTarget.getBaseCard().getName() + "!";
  				} else if (newTarget != target) {
  					tailsMsg = "TAILS! Redirected to " + newTarget.getBaseCard().getName() + "!";
  				} else {
  					tailsMsg = "TAILS! T-BOT takes the hit!"; 
  				}
  				
  				if (isLocalAttack) api.broadcastDefensiveCoinflip(false, headsMsg, tailsMsg); 
  				api.triggerCoinflip(false, headsMsg, tailsMsg, () -> onResolve.accept(newTarget));
  			}
  		} 
		/*
		 * // --- NEW: BERRY DROP (Heals random injured ally) --- else if
		 * (defendTrick.toLowerCase().contains("berry_drop")) { int healAmount =
		 * defendTrick.contains("6") ? 6 : 3; // Checks if it's the Small or Large Bushy
		 * 
		 * boolean isP1 = api.isCasterPlayer1(target); java.util.List<BattleCard> allies
		 * = api.getFriendlyField(isP1); java.util.List<BattleCard> injuredAllies = new
		 * java.util.ArrayList<>();
		 * 
		 * for (BattleCard ally : allies) { if (ally != null && ally.getHp() <
		 * ally.getBaseCard().getHp()) { injuredAllies.add(ally); } }
		 * 
		 * if (!injuredAllies.isEmpty()) { java.util.Collections.shuffle(injuredAllies,
		 * api.getSharedRng()); BattleCard luckyAlly = injuredAllies.get(0);
		 * luckyAlly.setHp(Math.min(luckyAlly.getHp() + healAmount,
		 * luckyAlly.getBaseCard().getHp()));
		 * 
		 * api.showToast(target.getBaseCard().getName() + " dropped a berry! Healed " +
		 * luckyAlly.getBaseCard().getName() + "!");
		 * 
		 * // === THE FIX: Force the GUI to instantly update the health bars! ===
		 * api.updateBoard(); }
		 * 
		 * onResolve.accept(target); // Proceed with the incoming attack! }
		 * 
		 * // --- NEW: VINE THORNS (Damages attacker) --- else if
		 * (defendTrick.toLowerCase().contains("vine_thorns")) {
		 * api.showToast(target.getBaseCard().getName() + "'s thorns struck back!");
		 * 
		 * // Deal the Bushy's attack damage back to the attacker! int damageBack =
		 * target.getAtk(); attacker.setHp(attacker.getHp() - damageBack);
		 * 
		 * if (attacker.getHp() <= 0) { api.destroyCard(attacker); }
		 * 
		 * // === THE FIX: Force the GUI to instantly update the health bars! ===
		 * api.updateBoard();
		 * 
		 * onResolve.accept(target); // Proceed with the incoming attack! }
		 */
  		// --- NEW: REACTIVE DODGE ROLL ---
  		else if (defendTrick.toLowerCase().contains("adddodges")) {
  			boolean isHeads = api.getSharedRng().nextBoolean();
  			
  			String headsMsg = "HEADS! Quick reflexes! Attack dodged!";
  			String tailsMsg = "TAILS! Too slow! Takes the hit.";
  			
  			if (isHeads) {
  				if (isLocalAttack) api.broadcastDefensiveCoinflip(true, headsMsg, tailsMsg); 
  				System.out.println("Dodged");
  				api.triggerCoinflip(true, headsMsg, tailsMsg, () -> {
  				    // Give them the dodge charge right before the punch lands!
  				    target.addDodges(1); 
  				    onResolve.accept(target);
  				});
  			} else {
  				if (isLocalAttack) api.broadcastDefensiveCoinflip(false, headsMsg, tailsMsg); 
  				api.triggerCoinflip(false, headsMsg, tailsMsg, () -> onResolve.accept(target));
  			}
  		}
  		else {
  			onResolve.accept(target);
  		}
  		
  	}

//=== NEW: Generic Expiring Status Handler ===
	// Returns the new duration (0 means throw it away, >0 means reset the clock!)
	public static int handleExpiringStatus(BattleAPI api, BattleCard target, String statusKey) {
		
		// 1. --- The Watch Tower's Time Bomb ---
		if (statusKey.startsWith("TIMEBOMB_")) {
			String[] parts = statusKey.split("_");
			int dmg = Integer.parseInt(parts[1]);
			int resetTime = Integer.parseInt(parts[2]);
			
			String cardName = target.getBaseCard().getName();
			System.out.println("LIBRARY: THE DETONATOR FIRES! " + cardName + " deals " + dmg + " to the enemy field!");
			api.showToast(cardName + " FIRES! " + dmg + " damage to the enemy field!");
			
			boolean isP1 = api.isCasterPlayer1(target);
			// === THE FIX: Pass the physical card! ===
						api.damageEnemyField(target, dmg);
						
						return resetTime;
		} 
		// 1.6 --- Evolution / Growth Timers ---
        else if (statusKey.startsWith("GROWTH_")) {
            System.out.println("LIBRARY: " + target.getBaseCard().getName() + " is evolving!");
            api.showToast(target.getBaseCard().getName() + " is evolving!");

            // === THE FIX: Use the built-in triggerEvent! ===
            // This tells the engine to automatically find the "ON_GROWTH" coinflip in cards.txt and execute it!
            boolean isP1 = api.isCasterPlayer1(target);
            api.triggerEvent(target, isP1, "ON_GROWTH");
            
            return 0; // The timer fades away because the card evolved!
        }
		// 1.7 --- Token Spawners (Scrap Bot) ---
     // 1.7 --- Token Spawners (Scrap Bot) ---
        else if (statusKey.startsWith("SPAWNER_")) {
            String[] parts = statusKey.split("_");
            String tokenId = parts[1];
            int resetTime = Integer.parseInt(parts[2]);

            // 1. The Failsafe: Check available empty slots!
            boolean isP1 = api.isCasterPlayer1(target);
            BattleCard[][] myField = isP1 ? api.getField1() : api.getField2();

            int emptySlots = 0;
            for (int r = 0; r < 2; r++) {
                for (int c = 0; c < 4; c++) {
                    if (myField[r][c] == null) emptySlots++;
                }
            }

            if (emptySlots == 0) {
                System.out.println("LIBRARY: Field is full! " + target.getBaseCard().getName() + " could not spawn a token.");
                return resetTime; // Restart clock, try again later!
            }

            // 2. Trigger Manual Placement (Only for the local owner!)
            if (api instanceof BattleSystem) {
                BattleSystem bs = (BattleSystem) api;
                boolean isLocalOwner = bs.doIOwnThisCard(target);

                if (isLocalOwner) {
                    BattleGUI.isRevivePlacementMode = true;
                    BattleGUI.pendingReviveId = "TOKEN_1_" + tokenId;
                    BattleGUI.currentReviveCaster = target;

                    api.showToast("Factory Output! Click an empty slot to place your Scrap.");
                }
            }

            // Returning the resetTime tells the engine to restart the clock!
            return resetTime; 
        }
		// 1.8 --- Druid's Nature Blessing ---
        else if (statusKey.startsWith("NATUREBLESSING_")) {
            int buffAmount = Integer.parseInt(statusKey.split("_")[1]);
            boolean isP1 = api.isCasterPlayer1(target);
            java.util.List<BattleCard> allies = api.getFriendlyField(isP1);
            
            // The Plant Faction IDs (Used to ensure it only buffs plants)
            java.util.List<String> plantIds = java.util.Arrays.asList(
                "01880", "01881", "01882", "01883", "01884", "01885", "01992"
            );
            
            for (BattleCard ally : allies) {
                if (ally != null && plantIds.contains(ally.getBaseCard().getCardID().trim())) {
                    ally.setAtk(ally.getAtk() + buffAmount);
                    ally.setHp(ally.getHp() + buffAmount); 
                }
            }
            
            api.showToast(target.getBaseCard().getName() + " blessed the plants!");
            System.out.println("LIBRARY: Plants gained +" + buffAmount + "/+" + buffAmount + "!");
            
            return 1; // Returning 1 resets the timer so it fires EVERY turn!
        }
		
		// 1.9 --- Camelcorn Positional Healing ---
        else if (statusKey.startsWith("CAMELHEAL_")) {
            int healAmt = Integer.parseInt(statusKey.split("_")[1]);
            
            boolean isP1 = api.isCasterPlayer1(target);
            BattleCard[][] myField = isP1 ? api.getField1() : api.getField2();

            // Check if the Camelcorn is in the Defense Row (Row 1)
            if (target.getRow() == 1) {
                int myCol = target.getCol(); // Find exactly what column it is in
                BattleCard frontCard = myField[0][myCol]; // Look at the Attack Row (Row 0) in the exact same column!
                
                // If there is a card in front of it, and it's injured, heal it!
                if (frontCard != null && frontCard.getHp() < frontCard.getBaseCard().getHp()) {
                    int maxHp = frontCard.getBaseCard().getHp();
                    frontCard.setHp(Math.min(frontCard.getHp() + healAmt, maxHp));
                    
                    System.out.println("LIBRARY: Camelcorn healed " + frontCard.getBaseCard().getName() + " for " + healAmt + " HP!");
                    api.showToast("Camelcorn healed the card it is defending!");
                    
                    if (api instanceof BattleSystem) {
                        ((BattleSystem) api).updateBoard();
                    }
                }
            }
            
            return 1; // Returning 1 resets the clock so it fires again next turn!
        }
		
		
		// 2. --- Temporary ATK Buffs ---
		else if (statusKey.startsWith("TEMPATK_")) {
			int amount = Integer.parseInt(statusKey.split("_")[1]);
			target.setAtk(target.getAtk() - amount);
			System.out.println("LIBRARY: Temporary ATK buff faded from " + target.getBaseCard().getName());
			return 0; // Fade away
		} 
		
		// 3. --- Temporary HP Buffs ---
		else if (statusKey.startsWith("TEMPHP_")) {
			int amount = Integer.parseInt(statusKey.split("_")[1]);
			target.setHp(target.getHp() - amount);
			System.out.println("LIBRARY: Temporary HP buff faded from " + target.getBaseCard().getName());
			
			if (target.getHp() <= 0 && !target.isDying()) {
				api.destroyCard(target);
			}
			return 0; // Fade away
		}
		
		// Default: If we don't recognize it, just throw it away!
		return 0;
	}

 	// Helper method to find a random teammate of the attacker
 // Helper method to find a random teammate of the attacker
 	private static BattleCard calculateRedirectTarget(BattleAPI api, BattleCard attacker, BattleCard originalTarget) {
 		// Find the attacker's side of the board using the newly wired API!
 		boolean isAttackerP1 = api.isCasterPlayer1(attacker);
 		BattleCard[][] targetField = isAttackerP1 ? api.getField1() : api.getField2();
 		
 		java.util.List<BattleCard> validVictims = new java.util.ArrayList<>();

 		// Scan the 2x4 grid
 		for (int r = 0; r < 2; r++) {
 			for (int c = 0; c < 4; c++) {
 				BattleCard potentialVictim = targetField[r][c];
 				
 				// Add to the list if it's a real card AND it is NOT the attacker themselves!
 				if (potentialVictim != null && potentialVictim != attacker) {
 					validVictims.add(potentialVictim);
 				}
 			}
 		}

 		if (validVictims != null && !validVictims.isEmpty()) {
			// === The networked RNG you just added! ===
			int randomIndex = api.getSharedRng().nextInt(validVictims.size());
			return validVictims.get(randomIndex);
		}

		// === THE FIX: The Failsafe Return ===
		// If there is no one else to throw the bomb at, return the original target!
		return originalTarget;
 	
 	}
}