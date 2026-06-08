package cardGame;

import java.io.*;
import java.util.*;

/**
 * CardModel - holds userCards, cardCounts, deckCounts and business logic.
 * This class now enforces deck limits by category (Weak/Strong/Powerful).
 */
public class CardModel {

    private final String username;
    private final List<cards> userCards = new ArrayList<>();
    private final Map<String, Integer> cardCounts = new HashMap<>();
    private final Map<String, Integer> deckCounts = new HashMap<>();
    private final Map<String, cards> allCards = new HashMap<>();

    // ----- Deck limits -----
    private static final int MAX_DECK_SIZE = 16;
    private static final int MAX_WEAK_CARDS = 8;
    private static final int MAX_STRONG_CARDS = 6;
    private static final int MAX_POWERFUL_CARDS = 2;

    public CardModel(String username) {
        this.username = username;
        loadUserCollection();
    }

    // ----- Accessors -----
    public List<cards> getUserCards() {
        return userCards;
    }

    public Map<String, Integer> getCardCounts() {
        return cardCounts;
    }

    public Map<String, Integer> getDeckCounts() {
        return deckCounts;
    }

    public void setDeckCounts(Map<String, Integer> newCounts) {
        deckCounts.clear();
        if (newCounts != null) deckCounts.putAll(newCounts);
    }

    public void clearDeck() {
        deckCounts.clear();
    }

    // ----- Business logic: add / remove -----
    public boolean addToDeck(String cardId) {
        // 1. Make sure the user owns this card and hasn't exceeded their owned amount
        int owned = cardCounts.getOrDefault(cardId, 0);
        int inDeck = deckCounts.getOrDefault(cardId, 0);
        if (inDeck >= owned) return false;

        cards c = allCards.get(cardId);
        if (c == null) return false;

        // 2. Check total deck size
        int currentTotal = deckCounts.values().stream().mapToInt(Integer::intValue).sum();
        if (currentTotal >= MAX_DECK_SIZE) return false;

        // 3. Check category limits
     // 3. Check category limits
        String category = getCategory(c);
        int currentInCategory = deckCounts.entrySet().stream()
                .filter(e -> {
                    // === THE FIX: Check if the card actually exists in the dictionary! ===
                    cards deckCard = allCards.get(e.getKey());
                    return deckCard != null && getCategory(deckCard).equals(category);
                })
                .mapToInt(Map.Entry::getValue)
                .sum();

        if ((category.equals("Weak") && currentInCategory >= MAX_WEAK_CARDS)
                || (category.equals("Strong") && currentInCategory >= MAX_STRONG_CARDS)
                || (category.equals("Powerful") && currentInCategory >= MAX_POWERFUL_CARDS)) {
            return false;
        }

        // 4. Add card
        deckCounts.put(cardId, inDeck + 1);
        return true;
    }

    public boolean removeFromDeck(String cardId) {
        int inDeck = deckCounts.getOrDefault(cardId, 0);
        if (inDeck <= 0) return false;
        if (inDeck == 1) deckCounts.remove(cardId);
        else deckCounts.put(cardId, inDeck - 1);
        return true;
    }

    public cards findCardById(String id) {
        for (cards c : userCards) {
            if (c.getCardID().equals(id)) return c;
        }
        return null;
    }

    // ----- Helper: determine category based on average stats -----
    private String getCategory(cards c) {
    	if (c == null) return "Unknown"; // === THE NPE SHIELD ===
        double avg = (c.getAtk() + c.getHp()) / 2.0;
        if (avg <= 6.0) return "Weak";
        else if (avg <= 8.0) return "Strong";
        else return "Powerful";
    }

    // ----- Load user collection (kept same behavior as original) -----
    private void loadUserCollection() {
        try {
            UserCollectionSyncer.sync();
            Map<String, cards> loadedCards = cardLoader.loadAllCards();
            List<String> userCardIDs = cardLoader.loadUserCollection(username);

            if (userCardIDs.isEmpty()) {
                cardLoader.registerUserCollection(username);
                userCardIDs = cardLoader.loadUserCollection(username);
            }

            for (String id : userCardIDs) {
                cards base = loadedCards.get(id.trim());
                if (base != null) {
                    String imagePath = "/images/" + id.trim() + ".png";
                    cards c = new cards(base.getName(), base.getSpecial(), base.getAtk(), base.getHp(), id.trim(),
                            imagePath, base.getRarity());
                    userCards.add(c);
                    cardCounts.put(c.getCardID(), cardCounts.getOrDefault(c.getCardID(), 0) + 1);
                    allCards.put(c.getCardID(), c); // keep for easy lookup
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
