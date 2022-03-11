import com.codingame.game.BasePlayer
import com.codingame.game.BebraObstacleInput
import java.io.InputStream
import java.io.PrintStream

class Level1Player(stdin: InputStream, stdout: PrintStream, stderr: PrintStream) : BasePlayer(stdin, stdout, stderr) {

  private fun myBarracks(): List<BebraObstacleInput> = obstacles.filter { it.owner == 0 && it.structureType == 2 }

  init {

    while (true) {
      val (queenLoc, _, _, touch, _, _, obstacles, _, _) = readInputs()
      //      stderr.println("Touching: $touch")
      fun getQueenAction(): String {

        val queenTarget = obstacles
          .filter { it.owner == -1 }
          .minByOrNull { it.location.distanceTo(queenLoc) } ?: return "WAIT"

        val needsKnight = obstacles.count { it.structureType == 2 && it.owner == 0 && it.attackRadiusOrCreepType == 0 } < 2
        val needsArcher = !obstacles.any { it.structureType == 2 && it.owner == 0 && it.attackRadiusOrCreepType == 1 }
//        val needsGiant = !obstacles.any { it.structureType == 2 && it.owner == 0 && it.attackRadiusOrCreepType == 2 }

        return when {
          needsKnight -> "BUILD ${queenTarget.obstacleId} BARRACKS-KNIGHT"
          needsArcher -> "BUILD ${queenTarget.obstacleId} BARRACKS-ARCHER"
//          needsGiant -> "BUILD ${queenTarget.obstacleId} BARRACKS-GIANT"
//          else -> "BUILD ${queenTarget.obstacleId} TOWER"
          else -> "WAIT"
        }
      }

      try {
        stdout.println(getQueenAction())
        stdout.println("TRAIN${myBarracks().joinToString("") { " " + it.obstacleId }}")
      } catch (ex: Exception) {
        ex.printStackTrace(stderr)
        throw ex
      }

    }
  }
}
