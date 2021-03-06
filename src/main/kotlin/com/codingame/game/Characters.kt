package com.codingame.game

import anims.Anim
import anims.AnimModule
import com.codingame.game.Constants.GIANT_BUST_RATE
import com.codingame.game.Constants.KNIGHT_DAMAGE
import com.codingame.game.Constants.QUEEN_HP
import com.codingame.game.Constants.QUEEN_MASS
import com.codingame.game.Constants.QUEEN_RADIUS
import com.codingame.game.Constants.QUEEN_SPEED
import com.codingame.game.Constants.ARCHER_DAMAGE
import com.codingame.game.Constants.ARCHER_DAMAGE_TO_GIANTS
import com.codingame.game.Constants.TOUCHING_DELTA
import com.codingame.gameengine.core.GameManager
import com.codingame.gameengine.module.entities.Curve
import com.codingame.gameengine.module.entities.Entity
import com.codingame.gameengine.module.entities.GraphicEntityModule
import tooltipModule.TooltipModule
import java.lang.UnsupportedOperationException
import java.util.*

lateinit var theEntityManager: GraphicEntityModule
lateinit var theTooltipModule: TooltipModule
lateinit var theGameManager: GameManager<Player>
lateinit var theAnimModule: AnimModule
lateinit var theRandom: Random

val viewportX = 0..1920
val viewportY = 0..1000

var Anim.location: Vector2
  get() = throw UnsupportedOperationException()
  set(value) {
    params["x"] = value.x
    params["y"] = value.y
  }

var <T : Entity<*>?> Entity<T>.location: Vector2
  get() = Vector2(x - viewportX.first, y - viewportY.first)

  set(value) {
    x = (value.x + viewportX.first).toInt()
    y = (value.y + viewportY.first).toInt()
  }

abstract class FieldObject {
  abstract var location: Vector2
  abstract var radius: Int
  abstract val mass: Int   // 0 := immovable
}

abstract class Unit(val owner: Player) : FieldObject() {
  abstract fun damage(damageAmount: Int)

  protected val tokenCircle = theEntityManager.createSprite()
    .setImage(if (owner.isSecondPlayer) "Unite_Base_Bleu" else "Unite_Base_Rouge")
    .setAnchor(0.5)
    .setZIndex(40)!!   // TODO: set to some kind of increasing ID

  protected val characterSprite = theEntityManager.createSprite()
    .setZIndex(41)
    .setScale(1.2)
    .setAnchor(0.5)!!

  protected val tokenGroup = theEntityManager.createGroup(tokenCircle, characterSprite)

  override var location: Vector2 = Vector2.Zero
    set(value) {
      if (value == Vector2.Zero) return
      field = value

      tokenGroup.location = value
    }

  abstract val maxHealth:Int
  open var health:Int = 0
    set(value) {
      field = value
      if (value < 0) field = 0
      theTooltipModule.updateExtraTooltipText(tokenCircle, "Health: $health")
    }

  fun commitState(time: Double) {
    theEntityManager.commitEntityState(time, tokenCircle, characterSprite, tokenGroup)
  }
}

class Queen(owner: Player) : Unit(owner) {
  override val mass = QUEEN_MASS
  override var radius = QUEEN_RADIUS
  override val maxHealth = Leagues.queenHp

  init {
    characterSprite.image = "Unite_Reine"
    theTooltipModule.registerEntity(tokenGroup, mapOf("type" to "Queen"))
    tokenCircle.baseWidth = radius*2
    tokenCircle.baseHeight = radius*2
    characterSprite.baseWidth = radius*2
    characterSprite.baseHeight = radius*2
  }

  fun moveTowards(target: Vector2) {
    location = location.towards(target, QUEEN_SPEED.toDouble())
  }

  override fun damage(damageAmount: Int) {
    if (damageAmount <= 0) return
    owner.health -= damageAmount
  }

}

abstract class Creep(
  owner: Player,
  val creepType: CreepType
) : Unit(owner) {

  protected val speed: Int = creepType.speed
  val attackRange: Int = creepType.range
  override val mass: Int = creepType.mass
  override val maxHealth = creepType.hp
  final override var radius = creepType.radius

  open fun finalizeFrame() { }

  override fun damage(damageAmount: Int) {
    if (damageAmount <= 0) return   // no accidental healing!

    health -= damageAmount
    theTooltipModule.updateExtraTooltipText(tokenCircle, "Health: $health")
  }

  abstract fun dealDamage()
  abstract fun move(frames: Double)

  final override var health: Int
    get() { return super.health }
    set(value) {
      super.health = value
      if (super.health == 0) {
        characterSprite.alpha = 0.0
        tokenCircle.alpha = 0.0
      } else {
        tokenCircle.alpha = 0.8 * health / maxHealth + 0.2
      }
    }

  init {
    health = creepType.hp

    tokenCircle.baseWidth = radius*2
    tokenCircle.baseHeight = radius*2
    characterSprite.image = creepType.assetName
    characterSprite.baseWidth = radius*2
    characterSprite.baseHeight = radius*2

    theTooltipModule.registerEntity(tokenGroup,mapOf("type" to creepType.toString()))
  }
}

class GiantCreep(
  owner: Player,
  creepType: CreepType,
  private val obstacles: List<Obstacle>
) : Creep(owner, creepType) {
  override fun move(frames: Double)  {
    obstacles
      .filter { it.structure != null && it.structure is Tower && (it.structure as Tower).owner == owner.enemyPlayer }
      .minByOrNull { it.location.distanceTo(location) }
      ?.let {
        location = location.towards(it.location, speed.toDouble() * frames)
      }
  }

  override fun dealDamage() {
    obstacles
      .firstOrNull {
        val struc = it.structure
        struc is Tower
          && struc.owner == owner.enemyPlayer
          && it.location.distanceTo(location) < radius + it.radius + TOUCHING_DELTA
      }?.also {
        (it.structure as Tower).health -= GIANT_BUST_RATE
        val creepToTower = it.location - location
        characterSprite.location = creepToTower.resizedTo(radius.toDouble())
        theEntityManager.commitEntityState(0.2, characterSprite)
        characterSprite.location = Vector2(0,0)
        theEntityManager.commitEntityState(1.0, characterSprite)
      }
  }
}

class KnightCreep(owner: Player, creepType: CreepType)
  : Creep(owner, creepType) {

  private var lastLocation: Vector2? = null
  private var attacksThisTurn: Boolean = false

  override fun finalizeFrame() {
    val last = lastLocation

    if (last != null) {
      val movementVector = when {
        last.distanceTo(location) > 30 && !attacksThisTurn -> location - last
        else -> owner.enemyPlayer.queenUnit.location - location
      }
      characterSprite.rotation = movementVector.angle
    }

    lastLocation = location
  }

  override fun move(frames: Double)  {
    val enemyQueen = owner.enemyPlayer.queenUnit
    // move toward enemy queen, if not yet in range
    if (location.distanceTo(enemyQueen.location) > radius + enemyQueen.radius + attackRange)
      location = location.towards((enemyQueen.location + (location - enemyQueen.location).resizedTo(3.0)), speed.toDouble() * frames)
  }

  override fun dealDamage() {
    attacksThisTurn = false
    val enemyQueen = owner.enemyPlayer.queenUnit
    if (location.distanceTo(enemyQueen.location) < radius + enemyQueen.radius + attackRange + TOUCHING_DELTA) {
      attacksThisTurn = true
      characterSprite.setAnchorX(0.5, Curve.IMMEDIATE)
      theEntityManager.commitEntityState(0.4, characterSprite)
      characterSprite.anchorX = 0.2
      theEntityManager.commitEntityState(0.7, characterSprite)
      characterSprite.anchorX = 0.5
      theEntityManager.commitEntityState(1.0, characterSprite)
      owner.enemyPlayer.health -= KNIGHT_DAMAGE
    }
  }
}

class ArcherCreep(owner: Player, creepType: CreepType)
  : Creep(owner, creepType){

  private var lastLocation: Vector2? = null

  var attackTarget: Creep? = null
  val color = if (owner.isSecondPlayer) "Bleu" else "Rouge"
  private val projectile = theEntityManager.createSprite()!!
    .setZIndex(60)
    .setImage("Fleche_$color")
    .setVisible(false)
    .setAnchorX(1.0).setAnchorY(0.5)

  override fun finalizeFrame() {
    val target = findTarget() ?: owner.enemyPlayer.queenUnit

    val last = lastLocation

    if (last != null) {
      val movementVector = when {
        last.distanceTo(location) > 30 -> location - last
        else -> target.location - location
      }
      characterSprite.rotation = Math.atan2(movementVector.y, movementVector.x)
    }

    lastLocation = location

    val localAttackTarget = attackTarget
    if (localAttackTarget != null) {
      characterSprite.anchorX = 0.8
      theEntityManager.commitEntityState(0.3, characterSprite)
      characterSprite.anchorX = 0.5
      theEntityManager.commitEntityState(0.4, characterSprite)

      projectile.setRotation((localAttackTarget.location - location).angle, Curve.IMMEDIATE)
      projectile.isVisible = true
      projectile.setX(location.x.toInt() + viewportX.first, Curve.NONE)
      projectile.setY(location.y.toInt() + viewportY.first, Curve.NONE)
      theEntityManager.commitEntityState(0.4, projectile)
      projectile.setX(localAttackTarget.location.x.toInt() + viewportX.first, Curve.EASE_IN_AND_OUT)
      projectile.setY(localAttackTarget.location.y.toInt() + viewportY.first, Curve.EASE_IN_AND_OUT)
      theEntityManager.commitEntityState(0.99, projectile)
      projectile.isVisible = false
      theEntityManager.commitEntityState(1.0, projectile)
    }
  }

  override fun move(frames: Double) {
    val target = findTarget() ?: owner.queenUnit
    // move toward target, if not yet in range
    if (location.distanceTo(target.location) > radius + target.radius + attackRange)
      location = location.towards((target.location + (location - target.location).resizedTo(3.0)), speed.toDouble() * frames)
  }

  override fun dealDamage() {
    attackTarget = null
    val target = findTarget() ?: return
    if (location.distanceTo(target.location) < radius + target.radius + attackRange + TOUCHING_DELTA) {
      target.damage(if (target is GiantCreep) ARCHER_DAMAGE_TO_GIANTS else ARCHER_DAMAGE)
      attackTarget = target
    }
  }

  private fun findTarget(): Creep? {
    return owner.enemyPlayer.activeCreeps
      .minByOrNull { it.location.distanceTo(location) }
  }
}

