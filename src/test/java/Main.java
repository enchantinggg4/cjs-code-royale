import com.codingame.gameengine.runner.GameRunner;
import com.lenny.game.LennyPlayer;
import com.nagiev.game.NagievPlayer;

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

        p.setProperty("is_local_test", "false");
//        System.setProperties(p);
        GameRunner gameRunner = new GameRunner();

        // Adds as many player as you need to test your game
        NvN(gameRunner);

        // gameRunner.addCommandLinePlayer("python3 /home/user/player.py");


        gameRunner.start();
    }

    private static void NvL(GameRunner gameRunner) {
        gameRunner.addAgent(NagievPlayer.class);
        gameRunner.addAgent(LennyPlayer.class);
    }

    private static void NvN(GameRunner gameRunner) {
        gameRunner.addAgent(NagievPlayer.class);
        gameRunner.addAgent(NagievPlayer.class);
    }
}
