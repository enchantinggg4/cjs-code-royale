package com.codingame.game

import com.codingame.game.Constants.KING_HP
import com.codingame.gameengine.core.AbstractPlayer

class Player : AbstractPlayer() {
  override fun getExpectedOutputLines(): Int = 1
  lateinit var kingUnit: MyOwnedEntity
  lateinit var enemyPlayer: Player
  var inverted: Boolean = false

  private fun fixOwner(player: Player?) = when (player) { null -> -1; this -> 0; else -> 1 }

  fun printLocation(location: Vector2) {
    val (x,y) = location
    sendInputLine("${x.toInt()} ${y.toInt()}")
  }

  fun printObstacle(obstacle: Obstacle) {
    val (x,y) = obstacle.location
    val toks = listOf(x.toInt(),y.toInt(), obstacle.radius, obstacle.minerals)

    val struc = obstacle.structure

    val toks2 = when (struc) {
      is Mine -> listOf(0, fixOwner(struc.owner), struc.incomeRate, -1)
      is Tower -> listOf(1, fixOwner(struc.owner), struc.health, struc.attackRadius)
      is Barracks -> listOf(2, fixOwner(struc.owner), struc.progress, struc.creepType.ordinal)
      else -> listOf(-1, -1, -1, -1)
    }
    sendInputLine((toks + toks2).joinToString(" "))
  }


  val activeCreeps = mutableListOf<Creep>()

  fun allUnits() = activeCreeps + kingUnit

  var health = KING_HP

  fun checkKingHealth(): Boolean {
    kingUnit.entity.fillAlpha = 0.8 * health / KING_HP + 0.2
    if (health <= 0) kingUnit.entity.fillAlpha = 0.0
    return health > 0
  }

  var resources = 0
}