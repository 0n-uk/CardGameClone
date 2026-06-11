package cardGame;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JOptionPane;

/**
 * DeckIO - handles deck save/load/delete and selection dialogs.
 * Preserves original file format: user_data/user_deck.txt entries structured as:
 *   username|deckName|id1,id2,id3
 *
 * This preserves compatibility with existing files you already have.
 */
public class DeckIO {

    private final String username;
    private final File decksFile;
    private static final int MAX_DECK_SIZE = 20; // <-- 20 card limit
    private static final int MAX_DUPLICATES = 2; // <-- Limit 2 copies per card

    public DeckIO(String username) {
        this.username = username;
        File folder = new File("eclipse-workspace\\Personalstuff\\cardGame\\user_data");
        if (!folder.exists()) folder.mkdirs();
        this.decksFile = new File(folder, "user_deck.txt");
    }

    public boolean doesDeckExist(String deckName) {
        if (!decksFile.exists()) return false;
        try (BufferedReader reader = new BufferedReader(new FileReader(decksFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("=")) continue;
                String[] parts = line.split("\\|");
                if (parts.length == 3) {
                    String user = parts[0].trim();
                    String name = parts[1].trim();
                    if (user.equals(username) && name.equals(deckName)) return true;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Save deckCounts to the decks file under username|deckName|id1,id2,id3 format.
     * deckCounts map is converted to a repeated-id list to match original serialization.
     */
    public void saveDeck(String deckName, Map<String, Integer> deckCounts) {
        Map<String, List<String>> allDecks = new LinkedHashMap<>();

        // Load existing decks (skip dividers)
        if (decksFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(decksFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("=")) continue;
                    String[] parts = line.split("\\|");
                    if (parts.length != 3) continue;
                    allDecks.put(parts[0].trim() + "|" + parts[1].trim(), Arrays.asList(parts[2].split(",")));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Build the list representation of provided deckCounts
        List<String> deckIds = new ArrayList<>();
        if (deckCounts != null) {
            for (Map.Entry<String, Integer> entry : deckCounts.entrySet()) {
                String cardId = entry.getKey();
                int count = entry.getValue();
                for (int i = 0; i < count; i++) deckIds.add(cardId);
            }
        }

        allDecks.put(username + "|" + deckName, deckIds);

        // Write back with dividers (same layout as your original)
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(decksFile))) {
            for (Map.Entry<String, List<String>> entry : allDecks.entrySet()) {
                writer.write("=================================");
                writer.newLine();
                writer.write(entry.getKey() + "|" + String.join(",", entry.getValue()));
                writer.newLine();
                writer.write("=================================");
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Load a deck and return the list of card IDs in that deck (repeated IDs preserved).
     * Caller should convert to counts if needed.
     */
    public List<String> loadDeck(String deckName) {
        List<String> ids = new ArrayList<>();
        if (!decksFile.exists()) return ids;
        try (BufferedReader reader = new BufferedReader(new FileReader(decksFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("=")) continue;
                String[] parts = line.split("\\|");
                if (parts.length != 3) continue;
                String user = parts[0].trim();
                String name = parts[1].trim();
                if (!user.equals(username) || !name.equals(deckName)) continue;
                String[] cardIds = parts[2].split(",");
                for (String cid : cardIds) {
                    if (!cid.trim().isEmpty()) ids.add(cid.trim());
                }
                break; // only first matching
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ids;
    }

    public void deleteDeck(String deckName) {
        if (!decksFile.exists()) {
            JOptionPane.showMessageDialog(null, "No decks found to delete.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<String> lines = new ArrayList<>();
        boolean found = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(decksFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("=")) continue;
                String[] parts = line.split("\\|");
                if (parts.length != 3) {
                    lines.add(line);
                    continue;
                }
                String user = parts[0].trim();
                String name = parts[1].trim();
                if (user.equals(username) && name.equals(deckName)) {
                    found = true;
                } else {
                    lines.add(line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        if (!found) {
            JOptionPane.showMessageDialog(null, "Deck \"" + deckName + "\" not found.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(decksFile))) {
            for (String l : lines) {
                writer.write("=================================");
                writer.newLine();
                writer.write(l);
                writer.newLine();
                writer.write("=================================");
                writer.newLine();
            }
            JOptionPane.showMessageDialog(null, "Deck \"" + deckName + "\" deleted successfully!", "Deleted",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String showDeckSelectionDialog() {
        if (!decksFile.exists()) {
            JOptionPane.showMessageDialog(null, "No decks found.", "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
        Set<String> deckNames = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(decksFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length != 3) continue;
                String user = parts[0].trim();
                String name = parts[1].trim();
                String cardsPart = parts[2].trim();
                if (user.equals(username) && !cardsPart.isEmpty()) deckNames.add(name);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (deckNames.isEmpty()) {
            JOptionPane.showMessageDialog(null, "No decks found.", "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
        Object selected = JOptionPane.showInputDialog(null, "Select a deck to load:", "Load Deck",
                JOptionPane.PLAIN_MESSAGE, null, deckNames.toArray(), null);
        return selected != null ? selected.toString() : null;
    }
    public boolean addCardToDeck(String deckName, String cardId) {
        List<String> deckIds = loadDeck(deckName);

        // Check overall deck size limit
        if (deckIds.size() >= MAX_DECK_SIZE) {
            JOptionPane.showMessageDialog(null,
                    "Cannot add more than " + MAX_DECK_SIZE + " cards to a deck!",
                    "Deck Full",
                    JOptionPane.INFORMATION_MESSAGE);
            return false;
        }

        // Count duplicates of this card
        int currentCopies = 0;
        for (String id : deckIds) {
            if (id.equals(cardId)) currentCopies++;
        }

        if (currentCopies >= MAX_DUPLICATES) {
            JOptionPane.showMessageDialog(null,
                    "You can only have " + MAX_DUPLICATES + " copies of card " + cardId + " in a deck!",
                    "Duplicate Limit Reached",
                    JOptionPane.INFORMATION_MESSAGE);
            return false;
        }

        // Add card
        deckIds.add(cardId);

        // Convert list back to counts
        Map<String, Integer> deckCounts = new HashMap<>();
        for (String id : deckIds) {
            deckCounts.put(id, deckCounts.getOrDefault(id, 0) + 1);
        }

        saveDeck(deckName, deckCounts);
        return true;
    }


    public String showDeckDeletionDialog() {
        return showDeckSelectionDialog();
    }
}
