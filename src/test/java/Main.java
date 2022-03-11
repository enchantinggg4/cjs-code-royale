import com.codingame.gameengine.runner.GameRunner;
import com.standalone.game.LennyPlayer;

public class Main {
    public static void main(String[] args) {

        GameRunner gameRunner = new GameRunner();

        // Adds as many player as you need to test your game
        gameRunner.addAgent(Level1Player.class);
        gameRunner.addAgent(LennyPlayer.class);

        // gameRunner.addCommandLinePlayer("python3 /home/user/player.py");

        gameRunner.start();
    }
}
