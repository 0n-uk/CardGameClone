package cardGame;

import java.io.*;
import java.util.*;

public class cardLoader {

    // ----- JAR resources (READ-ONLY) -----
    private static final String ALL_CARDS_RESOURCE = "/data/cards.txt";
    private static final String IMAGES_RESOURCE_FOLDER = "/images/";

    // ----- External user data (READ/WRITE) -----
    private static final String USER_COLLECTIONS_FILE = "eclipse-workspace\\Personalstuff\\cardGame\\user_data\\user_collection.txt";
    private static final String SPECIALS_RESOURCE = "/data/specials.txt";

    /**
     * Load all cards from cards.txt inside the JAR
     */
    
    public static Map<String, String> loadSpecials() {
        Map<String, String> specialsMap = new HashMap<>();

        try (InputStream is = cardLoader.class.getResourceAsStream(SPECIALS_RESOURCE)) {
            if (is == null) {
                System.out.println("Warning: specials.txt not found in JAR resources");
                return specialsMap; // Return empty map so game doesn't crash
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                // Skip empty lines or comments
                if (line.isEmpty() || line.startsWith("#")) continue;

                // Split exactly by the first pipe to separate ID from the rest of the command string
                String[] parts = line.split("\\|", 2); 
                if (parts.length >= 2) {
                    String cardId = parts[0].trim();
                    String commands = parts[1].trim();
                    
                    specialsMap.put(cardId, commands);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return specialsMap;
    }
    public static Map<String, cards> loadAllCards() {
        Map<String, cards> allCards = new HashMap<>();

        try (InputStream is = cardLoader.class.getResourceAsStream(ALL_CARDS_RESOURCE)) {

            if (is == null) {
                throw new RuntimeException("cards.txt not found in JAR resources");
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\|");
                if (parts.length >= 5) {
                    String id = parts[0].trim();
                    String name = parts[1].trim();
                    String special = parts[2].trim();
                    int atk = Integer.parseInt(parts[3].trim());
                    int hp = Integer.parseInt(parts[4].trim());

                    int rarity = parts.length >= 6
                            ? Integer.parseInt(parts[5].trim())
                            : 1;

                    String imagePath = IMAGES_RESOURCE_FOLDER + id + ".png";

                    allCards.put(id,
                            new cards(name, special, atk, hp, id, imagePath, rarity));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return allCards;
    }

    /**
     * Load a user's collection from external user_data file
     */
    public static List<String> loadUserCollection(String username) {
        List<String> collection = new ArrayList<>();
        File file = new File(USER_COLLECTIONS_FILE);
        if (!file.exists()) return collection;

        try (Scanner sc = new Scanner(file)) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if (line.isEmpty() || line.startsWith("===")) continue;

                if (line.contains("|")) {
                    String[] parts = line.split("\\|", 2);
                    String userInFile = parts[0].trim();
                    String cardsPart = parts.length > 1 ? parts[1].trim() : "";

                    if (username.equalsIgnoreCase(userInFile) && !cardsPart.isEmpty()) {
                        String[] cardIDs = cardsPart.split(",");
                        for (String id : cardIDs) {
                            if (!id.trim().isEmpty()) {
                                collection.add(id.trim());
                            }
                        }
                        break;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return collection;
    }

    /**
     * Register a new user by syncing collections
     */
    public static void registerUserCollection(String username) {
        UserCollectionSyncer.sync();
    }

    /**
     * Save or update a user's collection
     */
    public static void saveUserCollection(String username, List<String> newCardIDs) {
        File file = new File(USER_COLLECTIONS_FILE);
        Map<String, String> collections = new LinkedHashMap<>();

        // Load existing
        if (file.exists()) {
            try (Scanner sc = new Scanner(file)) {
                while (sc.hasNextLine()) {
                    String line = sc.nextLine().trim();
                    if (line.isEmpty() || line.startsWith("===")) continue;

                    if (line.contains("|")) {
                        String[] parts = line.split("\\|", 2);
                        collections.put(parts[0].trim(),
                                parts.length > 1 ? parts[1].trim() : "");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Merge cards
        String existingCards = collections.getOrDefault(username, "");
        List<String> merged = new ArrayList<>();

        if (!existingCards.isEmpty()) {
            merged.addAll(Arrays.asList(existingCards.split(",")));
        }
        merged.addAll(newCardIDs);

        collections.put(username, String.join(",", merged));

        // Ensure all users exist
        File userFile = new File("user_data/users.txt");
        if (userFile.exists()) {
            try (Scanner sc = new Scanner(userFile)) {
                while (sc.hasNextLine()) {
                    String line = sc.nextLine().trim();
                    if (line.isEmpty()) continue;
                    String userFromFile = line.split("\\|", 2)[0].trim();
                    collections.putIfAbsent(userFromFile, "");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Rewrite boxed format
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            for (Map.Entry<String, String> entry : collections.entrySet()) {
                pw.println("=================================");
                pw.println(entry.getKey() + "|" + entry.getValue());
                pw.println("=================================");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
