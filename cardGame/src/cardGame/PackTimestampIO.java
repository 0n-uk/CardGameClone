package cardGame;

import java.io.*;
import java.util.*;

public class PackTimestampIO {

    private static final String FILE_PATH = "eclipse-workspace\\Personalstuff\\cardGame\\user_data\\user_timestamps.txt";
    private final String username;
    private Map<String, Long> timestamps;

    public PackTimestampIO(String username) {
        this.username = username;
        loadAll();
    }

    // ----------------------------------------
    // Load entire file into memory
    // ----------------------------------------
    private void loadAll() {
        timestamps = new HashMap<>();
        File file = new File(FILE_PATH);

        if (!file.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.equals("=================================")) {
                    String data = br.readLine();
                    br.readLine(); // skip closing ===== line

                    if (data != null && data.contains("|")) {
                        String[] parts = data.split("\\|");
                        String user = parts[0];
                        long time = Long.parseLong(parts[1]);
                        timestamps.put(user, time);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ----------------------------------------
    // Save everything back to the file
    // ----------------------------------------
    private void saveAll() {
        try (PrintWriter out = new PrintWriter(new FileWriter(FILE_PATH))) {

            for (var entry : timestamps.entrySet()) {
                out.println("=================================");
                out.println(entry.getKey() + "|" + entry.getValue());
                out.println("=================================");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ----------------------------------------
    // Get timestamp for THIS user
    // ----------------------------------------
    public long loadTimestamp() {
        return timestamps.getOrDefault(username, 0L);
    }

    // ----------------------------------------
    // Save timestamp for THIS user
    // ----------------------------------------
    public void saveTimestamp(long ts) {
        timestamps.put(username, ts);
        saveAll();
    }
}
