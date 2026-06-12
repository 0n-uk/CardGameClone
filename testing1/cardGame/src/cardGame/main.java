package cardGame;

import java.io.IOException;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class main {

    public static void main(String[] args) throws IOException {


        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ex) {
            // Ignore, proceed with default
        }

        // Start the server in the background
        new Thread(() -> ChatServer.main(new String[0])).start();

        // Launch host instance (Player 1)
        SwingUtilities.invokeLater(() -> new BattleGUI("B", "B", true));

        // Give the server a moment to start, then launch the joining instance (Player 2)
        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            SwingUtilities.invokeLater(() -> new BattleGUI("B", "B", false));
        }).start();
    }

}
	//comments there were from the previous project that I built the login and hub page w/
