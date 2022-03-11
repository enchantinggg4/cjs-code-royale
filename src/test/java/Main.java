import com.codingame.gameengine.runner.GameRunner;
import com.standalone.game.LennyPlayer;

import java.util.Properties;

public class Main {
    public static void main(String[] args) {

        Properties p = System.getProperties();
        p.setProperty("league.level", "3");
//        System.setProperties(p);
        GameRunner gameRunner = new GameRunner();

        // Adds as many player as you need to test your game
        gameRunner.addAgent(LennyPlayer.class);
        gameRunner.addAgent(LennyPlayer.class);

        // gameRunner.addCommandLinePlayer("python3 /home/user/player.py");

        gameRunner.start();
    }
}
