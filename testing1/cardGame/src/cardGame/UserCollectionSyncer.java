package cardGame;

import java.io.*;
import java.util.*;

/**
 * Keeps user_collection.txt in single-line boxed format:
 * =================================
 * username|cardID1,cardID2,...
 * =================================
 */
public class UserCollectionSyncer {

    private static final File BASE_DIR =
            new File(System.getProperty("user.dir"));

    private static final File USER_FILE =
            new File(BASE_DIR, "eclipse-workspace\\Personalstuff\\cardGame\\user_data\\users.txt");

    private static final File COLLECTION_FILE =
            new File(BASE_DIR, "eclipse-workspace\\Personalstuff\\cardGame\\user_data\\user_collection.txt");

    public static void sync() {

        Map<String, String> collections = new LinkedHashMap<>();

        // --- Step 1: Load existing collections ---
        if (COLLECTION_FILE.exists()) {
            try (Scanner sc = new Scanner(COLLECTION_FILE)) {
                while (sc.hasNextLine()) {
                    String line = sc.nextLine().trim();
                    if (line.isEmpty() || line.startsWith("===")) continue;

                    String[] parts = line.split("\\|", 2);
                    String username = parts[0].trim();
                    String cards = parts.length > 1 ? parts[1].trim() : "";
                    collections.put(username, cards);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // --- Step 2: Ensure all users exist ---
        if (USER_FILE.exists()) {
            try (Scanner sc = new Scanner(USER_FILE)) {
                while (sc.hasNextLine()) {
                    String line = sc.nextLine().trim();
                    if (line.isEmpty()) continue;

                    String[] parts = line.split("\\|", 2);
                    String username = parts[0].trim();
                    collections.putIfAbsent(username, "");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // --- Step 3: Rewrite file ---
        try (PrintWriter pw = new PrintWriter(new FileWriter(COLLECTION_FILE))) {
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
