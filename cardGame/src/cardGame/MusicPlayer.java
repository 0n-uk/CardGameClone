package cardGame;

import java.io.File;
import javax.sound.sampled.*;

public class MusicPlayer {

    private static Clip clip;

    public static void play() {
        if (clip != null) {
            if (!clip.isRunning()) clip.loop(Clip.LOOP_CONTINUOUSLY);
            return;
        }
        try {
            AudioInputStream audio = AudioSystem.getAudioInputStream(new File("main_hub_theme.wav"));
            clip = AudioSystem.getClip();
            clip.open(audio);
            clip.loop(Clip.LOOP_CONTINUOUSLY);
        } catch (Exception ignored) {}
    }
    public static void play(String filename) {
        if (clip != null) {
            if (!clip.isRunning()) clip.loop(Clip.LOOP_CONTINUOUSLY);
            return;
        }
        try {
            AudioInputStream audio = AudioSystem.getAudioInputStream(new File("eclipse-workspace\\Personalstuff\\cardGame\\resources\\toons\\" + filename));
            clip = AudioSystem.getClip();
            clip.open(audio);
            clip.loop(Clip.LOOP_CONTINUOUSLY);
        } catch (Exception ignored) {}
    }

    public static void stop() {
        if (clip != null && clip.isRunning()) clip.stop();
    }

    static boolean isPlaying() {
        return clip != null && clip.isRunning();
    }
}
