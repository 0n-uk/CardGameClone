package cardGame;

import java.io.*;
import java.util.*;

public class probabilitor {

    // ----------------------------------------------------
    // Internal weighted entry container
    // ----------------------------------------------------
    private static class CardEntry {
        cards card;
        double weight;

        CardEntry(cards card, double weight) {
            this.card = card;
            this.weight = weight;
        }
    }

    private static final List<CardEntry> weightedPool = new ArrayList<>();
    
 // === TIER 2: Token Exclusion List ===
    // Any ID in this list will NEVER be pulled from a pack!
    private static final List<String> EXCLUDED_TOKEN_IDS = Arrays.asList(
        "01098", "01880", "01881", "01882", "01883", "01884", "01885", "01993"// Scrap Token
     
        // Add a comma and put your Sushi token ID here too if it becomes a real card!
    );

    // ----------------------------------------------------
    // Initialize: load cards → compute category + rarity weights
    // ----------------------------------------------------
 // ----------------------------------------------------
    // Initialize: load cards → compute category + rarity weights
    // ----------------------------------------------------
    public static void initialize() {
        Map<String, cards> allMap = cardLoader.loadAllCards();
        List<cards> allCards = new ArrayList<>(allMap.values());

        weightedPool.clear();

        for (cards c : allCards) {
            
            // === THE FIX: The Token Filter ===
            // If the card ID is in our blacklist, skip it entirely!
            if (EXCLUDED_TOKEN_IDS.contains(c.getCardID().trim())) {
                continue; // Moves on to the next card without adding this one to the pool
            }

            String category = determineCategory(c);
            int rarity = c.getRarity();

            double typeWeight = getCategoryWeight(category);
            double rarityWeight = getRarityWeight(rarity);

            double finalWeight = typeWeight * rarityWeight;

            weightedPool.add(new CardEntry(c, finalWeight));
        }
    }
    
    

    // ----------------------------------------------------
    // CATEGORY LOGIC
    // ----------------------------------------------------
    private static String determineCategory(cards c) {
        if (c.getSpecial().toLowerCase().contains("spell")) {
            return "Spell";
        }

        double avg = (c.getAtk() + c.getHp()) / 2.0;

        if (avg <= 6.0) return "Weak";
        else if (avg <= 8.0) return "Strong";
        else return "Powerful";
    }

    // ----------------------------------------------------
    // WEIGHTS
    // ----------------------------------------------------
    private static double getCategoryWeight(String type) {
        return switch (type) {
            case "Weak" -> 6.0;
            case "Strong" -> 3.0;
            case "Powerful" -> 1.5;
            case "Spell" -> 0.3;
            default -> 1.0;
        };
    }

    private static double getRarityWeight(int rarity) {
        return switch (rarity) {
            case 1 -> 6.0;
            case 2 -> 3.0;
            case 3 -> 1.0;
            default -> 1.0;
        };
    }

    // ----------------------------------------------------
    // Generate a pack
    // ----------------------------------------------------
    public static List<cards> generatePack(int count) {
        List<cards> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(weightedRandomCard());
        }
        savePackToFile(result);
        return result;
    }

    // ----------------------------------------------------
    // Weighted random selection
    // ----------------------------------------------------
    private static cards weightedRandomCard() {
        double totalWeight = 0;
        for (CardEntry e : weightedPool) totalWeight += e.weight;

        double r = Math.random() * totalWeight;

        for (CardEntry entry : weightedPool) {
            r -= entry.weight;
            if (r <= 0) return entry.card;
        }

        return weightedPool.get(weightedPool.size() - 1).card;
    }

    // ----------------------------------------------------
    // Save pack results to a text file
    // ----------------------------------------------------
    private static void savePackToFile(List<cards> pack) {
        try (PrintWriter out = new PrintWriter(new FileWriter("pack_results.txt", true))) {

            out.println("===== NEW PACK OPENED =====");
            out.println("Timestamp: " + new Date());

            for (cards c : pack) {
                String category = determineCategory(c);

                out.println(
                    c.getCardID() + " | " +
                    c.getName() + " | Category: " + category +
                    " | Rarity: " + c.getRarity() +
                    " | ATK: " + c.getAtk() +
                    " | HP: " + c.getHp()
                );
            }
            out.println(); // blank line for separation

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ----------------------------------------------------
    // MAIN method just generates a pack and writes to txt
    // ----------------------------------------------------
    public static void main(String[] args) {
        initialize();
        generatePack(8); // writes automatically to pack_results.txt
    }
}
