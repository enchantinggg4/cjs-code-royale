import com.codingame.gameengine.runner.GameRunner;
import com.standalone.game.LennyPlayer;

import java.io.File;
import java.util.Properties;

public class Main {
    public static void main(String[] args) {

        File rootDir = new File("./debug_img");
        if (rootDir.exists()) {
            for (File f : rootDir.listFiles())
                f.delete();
        }
        new File("./debug_img").mkdir();
        Properties p = System.getProperties();
        p.setProperty("league.level", "3");

        p.setProperty("is_local_test", "true");
//        System.setProperties(p);
        GameRunner gameRunner = new GameRunner();

        // Adds as many player as you need to test your game
        gameRunner.addAgent(LennyPlayer.class);
        gameRunner.addAgent(LennyPlayer.class);

        // gameRunner.addCommandLinePlayer("python3 /home/user/player.py");


        gameRunner.start();
    }
}
