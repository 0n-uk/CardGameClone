package cardGame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class main {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ex) {
            // Ignore, proceed with default
        }

        // Start the server in the background
        new Thread(() -> ChatServer.main(new String[0])).start();

        // Launch host instance (Player 1)
        SwingUtilities.invokeLater(() -> new BattleGUI("UserB", "UserB", true));

        // Give the server a moment to start, then launch the joining instance (Player 2)
        new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            SwingUtilities.invokeLater(() -> new BattleGUI("UserB", "UserB", false));
        }).start();
    }
}
