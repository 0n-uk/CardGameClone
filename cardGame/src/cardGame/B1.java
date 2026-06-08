package cardGame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class main {

	 public static void main(String[] args) {
	        // Set a consistent look and feel
	        try {
	            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
	        } catch (Exception ex) {
	            // Ignore, proceed with default
	        }

	        // Launch the LandingPage GUIs
			    SwingUtilities.invokeLater(() -> new BattleGUI("Host", B, true));
                SwingUtilities.invokeLater(() -> new BattleGUI(B, "Opponent", true));
	    }
	}
