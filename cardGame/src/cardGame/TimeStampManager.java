package cardGame;

import java.io.*;
import java.util.*;

public class TimeStampManager {

    private static final String FILE_PATH = "user_data/user_timestamps.txt";

    // Ensure file exists
    private static void ensureFile() throws IOException {
        File f = new File(FILE_PATH);
        f.getParentFile().mkdirs();
        if (!f.exists()) f.createNewFile();
    }

    // Loads all timestamps into a Map
    public static Map<String, Long> loadAll() {
        Map<String, Long> map = new HashMap<>();

        try {
            ensureFile();
            try (Scanner sc = new Scanner(new File(FILE_PATH))) {
                while (sc.hasNextLine()) {
                    String line = sc.nextLine().trim();
                    if (line.isEmpty() || line.startsWith("====")) continue;

                    String[] parts = line.split("\\|");
                    if (parts.length == 2) {
                        map.put(parts[0].trim(), Long.parseLong(parts[1].trim()));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return map;
    }

    // Creates user timestamp if missing
    public static void createUserIfMissing(String username) {
        try {
            Map<String, Long> map = loadAll();

            if (map.containsKey(username)) return;

            ensureFile();
            try (FileWriter fw = new FileWriter(FILE_PATH, true)) {
                fw.write("=================================\n");
                fw.write(username + "|0\n");
                fw.write("=================================\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Saves/updates timestamp for a user
    public static void updateTimestamp(String username, long value) {
        try {
            Map<String, Long> map = loadAll();
            map.put(username, value);

            ensureFile();
            try (FileWriter fw = new FileWriter(FILE_PATH)) {
                for (String user : map.keySet()) {
                    fw.write("=================================\n");
                    fw.write(user + "|" + map.get(user) + "\n");
                    fw.write("=================================\n");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Retrieve a single user's timestamp
    public static long getTimestamp(String username) {
        Map<String, Long> map = loadAll();
        return map.getOrDefault(username, 0L);
    }
}
