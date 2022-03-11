package com.codingame.game

data class BebraUnitInput(
  val location: BebraVector2,
  val isFriendly: Boolean,
  val creepType: CreepType?,
  val health: Int
)

data class BebraObstaclePerTurnInput(
  val obstacleId: Int,
  val gold: Int,
  val maxMineSize: Int,
  val structureType: Int,
  val owner: Int,
  val incomeRateOrHealthOrCooldown: Int,
  val attackRadiusOrCreepType: Int
)

data class BebraObstacleInput(
  val obstacleId: Int,
  val location: BebraVector2,
  val radius: Int,
  var gold: Int = -1,
  var maxMineSize: Int = -1,
  var structureType: Int = -1,                 // -1 = None, 0 = Mine, 1 = Tower, 2 = Barracks
  var owner: Int = -1,                         // 0 = Us, 1 = Enemy
  var incomeRateOrHealthOrCooldown: Int = -1,  // mine / tower / barracks
  var attackRadiusOrCreepType: Int = -1        // tower / barracks
) {
  fun applyUpdate(update: BebraObstaclePerTurnInput) {
    structureType = update.structureType
    gold = update.gold
    maxMineSize = update.maxMineSize
    owner = update.owner
    incomeRateOrHealthOrCooldown = update.incomeRateOrHealthOrCooldown
    attackRadiusOrCreepType = update.attackRadiusOrCreepType
  }
}

data class BebraAllInputs(
  val queenLoc: BebraVector2,
  val health: Int,
  val gold: Int,
  val touchedObstacleId: Int,
  val enemyQueenLoc: BebraVector2,
  val enemyHealth: Int,
  val obstacles: List<BebraObstacleInput>,
  val friendlyCreeps: List<BebraUnitInput>,
  val enemyCreeps: List<BebraUnitInput>
)