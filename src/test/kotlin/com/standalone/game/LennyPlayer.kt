package com.standalone.game

import java.io.InputStream
import java.io.PrintStream


import java.util.*
import kotlin.math.*


val viewportX = 0..1920
val viewportY = 0..1000

object Constants {

    const val KNIGHT_SID = 0
    const val ARCHER_SID = 1
    const val GIANT_SID = 2

    const val STARTING_GOLD = 100

    const val QUEEN_SPEED = 60
    const val TOWER_HP_INITIAL = 200
    const val TOWER_HP_INCREMENT = 100
    const val TOWER_HP_MAXIMUM = 800
    const val TOWER_CREEP_DAMAGE_MIN = 3
    const val TOWER_CREEP_DAMAGE_CLIMB_DISTANCE = 200
    const val TOWER_QUEEN_DAMAGE_MIN = 1
    const val TOWER_QUEEN_DAMAGE_CLIMB_DISTANCE = 200
    const val TOWER_MELT_RATE = 4
    const val TOWER_COVERAGE_PER_HP = 1000

    const val GIANT_BUST_RATE = 80

    const val OBSTACLE_GAP = 90
    val OBSTACLE_RADIUS_RANGE = 60..90
    val OBSTACLE_GOLD_RANGE = 200..250
    val OBSTACLE_MINE_BASESIZE_RANGE = 1..3
    const val OBSTACLE_GOLD_INCREASE = 50
    const val OBSTACLE_GOLD_INCREASE_DISTANCE_1 = 500
    const val OBSTACLE_GOLD_INCREASE_DISTANCE_2 = 200
    val OBSTACLE_PAIRS = 6..12

    const val KNIGHT_DAMAGE = 1
    const val ARCHER_DAMAGE = 2
    const val ARCHER_DAMAGE_TO_GIANTS = 10

    const val QUEEN_RADIUS = 30
    const val QUEEN_MASS = 10000
    val QUEEN_HP = 5..20
    const val QUEEN_HP_MULT = 5   // i.e. 25..100 by 5
    const val QUEEN_VISION = 300

    val WORLD_WIDTH = viewportX.last - viewportX.first
    val WORLD_HEIGHT = viewportY.last - viewportY.first

    const val TOUCHING_DELTA = 5
    const val WOOD_FIXED_INCOME = 10
}

data class Distance(private val squareDistance: Double) : Comparable<Distance> {
    override fun compareTo(other: Distance) = squareDistance.compareTo(other.squareDistance)
    operator fun compareTo(compareDist: Double) = squareDistance.compareTo(compareDist * compareDist)
    operator fun compareTo(compareDist: Int) = squareDistance.compareTo(compareDist * compareDist)
    val toDouble by lazy { sqrt(squareDistance) }
}

data class Vector2(val x: Double, val y: Double) {
    private val lengthSquared by lazy { x * x + y * y }
    val length by lazy { Distance(lengthSquared) }
    val isNearZero by lazy { Math.abs(x) < 1e-12 && Math.abs(y) < 1e-12 }
    val normalized: Vector2 by lazy {
        val len = length
        when {
            len < 1e-6 -> Vector2(1, 0)
            else -> Vector2(x / len.toDouble, y / len.toDouble)
        }
    }
    val angle by lazy { Math.atan2(y, x) }

    constructor(x: Int, y: Int) : this(x.toDouble(), y.toDouble())

    operator fun minus(other: Vector2) = Vector2(x - other.x, y - other.y)
    operator fun plus(other: Vector2) = Vector2(x + other.x, y + other.y)
    operator fun times(other: Double) = Vector2(x * other, y * other)
    operator fun div(other: Double) = when (other) {
        0.0 -> throw IllegalArgumentException("Division by zero")
        else -> Vector2(x / other, y / other)
    }

    operator fun unaryMinus() = Vector2(-x, -y)
    fun resizedTo(newLength: Double) = normalized * newLength
    fun distanceTo(other: Vector2) = (this - other).length
    fun dot(other: Vector2) = x * other.x + y * other.y
    fun compareDirection(other: Vector2) =
        normalized.dot(other.normalized)   /* 1 == same direction, -1 == opposite direction */

    fun projectInDirectionOf(other: Vector2) = when {
        other.isNearZero -> throw IllegalArgumentException("cannot project in direction of zero")
        else -> other * (this.dot(other) / other.dot(other))
    }

    fun rejectInDirectionOf(other: Vector2) = this - projectInDirectionOf(other)
    fun towards(other: Vector2, maxDistance: Double) =
        if (distanceTo(other) < maxDistance) other
        else this + (other - this).resizedTo(maxDistance)

    fun clampWithin(minX: Double, maxX: Double, minY: Double, maxY: Double): Vector2 {
        val nx = when {
            x < minX -> minX; x > maxX -> maxX; else -> x
        }
        val ny = when {
            y < minY -> minY; y > maxY -> maxY; else -> y
        }
        return Vector2(nx, ny)
    }

    override fun toString(): String = "(${Math.round(x)}, ${Math.round(y)})"
    fun snapToIntegers(): Vector2 = Vector2(x.roundToInt(), y.roundToInt())

    companion object {
        val Zero = Vector2(0, 0)
        fun random(theRandom: Random, maxX: Int, maxY: Int) = Vector2(theRandom.nextInt(maxX), theRandom.nextInt(maxY))
        fun randomCircle(theRandom: Random, maxRadius: Int): Vector2 {
            val ang = theRandom.nextDouble() * PI * 2
            val radius = theRandom.nextDouble() * maxRadius
            return Vector2(cos(ang) * radius, sin(ang) * radius)
        }
    }
}

enum class CreepType(
    val count: Int, val cost: Int, val speed: Int, val range: Int, val radius: Int,
    val mass: Int, val hp: Int, val buildTime: Int, val assetName: String
) {
    KNIGHT(4, 80, 100, 0, 20, 400, 30, 5, "Unite_Fantassin"),
    ARCHER(2, 100, 75, 200, 25, 900, 45, 8, "Unite_Archer"),
    GIANT(1, 140, 50, 0, 40, 2000, 200, 10, "Unite_Siege")
}

data class UnitInput(
    val location: Vector2,
    val isFriendly: Boolean,
    val creepType: CreepType?,
    val health: Int
)

data class ObstaclePerTurnInput(
    val obstacleId: Int,
    val gold: Int,
    val maxMineSize: Int,
    val structureType: Int,
    val owner: Int,
    val incomeRateOrHealthOrCooldown: Int,
    val attackRadiusOrCreepType: Int
)

data class ObstacleInput(
    val obstacleId: Int,
    val location: Vector2,
    val radius: Int,
    var gold: Int = -1,
    var maxMineSize: Int = -1,
    var structureType: Int = -1,                 // -1 = None, 0 = Mine, 1 = Tower, 2 = Barracks
    var owner: Int = -1,                         // 0 = Us, 1 = Enemy
    var incomeRateOrHealthOrCooldown: Int = -1,  // mine / tower / barracks
    var attackRadiusOrCreepType: Int = -1        // tower / barracks
) {
    fun applyUpdate(update: ObstaclePerTurnInput) {
        structureType = update.structureType
        gold = update.gold
        maxMineSize = update.maxMineSize
        owner = update.owner
        incomeRateOrHealthOrCooldown = update.incomeRateOrHealthOrCooldown
        attackRadiusOrCreepType = update.attackRadiusOrCreepType
    }

    val isFullySaturated: Boolean
        get() = this.isMine && this.incomeRateOrHealthOrCooldown == maxMineSize

    val isTower: Boolean
        get() = this.structureType == 1


    val isMine: Boolean
        get() = this.structureType == 0

    val isAllied
        get() = owner == 0

    val isNeutral
        get() = owner == -1

    fun isBarrackOf(type: CreepType): Boolean {
        return this.structureType == 2 && this.creepType() == type
    }

    fun creepType(): CreepType? {
        if (attackRadiusOrCreepType > 2) {
            return null
        }
        return CreepType.values()[attackRadiusOrCreepType]
    }


    fun isOccupied(): Boolean {
        return this.structureType != -1
    }

    fun cords(): String {
        return "${location.x.toInt()} ${location.y.toInt()}"
    }

    fun healthPercent(): Double {
        return this.incomeRateOrHealthOrCooldown / Constants.TOWER_HP_MAXIMUM.toDouble()
    }
}

data class AllInputs(
    val queenLoc: Vector2,
    val health: Int,
    val gold: Int,
    val touchedObstacleId: Int,
    val enemyQueenLoc: Vector2,
    val enemyHealth: Int,
    val obstacles: List<ObstacleInput>,
    val friendlyCreeps: List<UnitInput>,
    val enemyCreeps: List<UnitInput>
)

abstract class BasePlayer(stdin: InputStream, val stdout: PrintStream, val stderr: PrintStream) {
    private val scanner = Scanner(stdin)

    var obstacles = listOf<ObstacleInput>()

    private fun readObstacleInit() = ObstacleInput(
        scanner.nextInt(),
        Vector2(scanner.nextInt(), scanner.nextInt()),
        scanner.nextInt()
    )//.also { stderr.println("Read obstacle: $it")}

    private fun readObstaclePerTurn() = ObstaclePerTurnInput(
        scanner.nextInt(), scanner.nextInt(), scanner.nextInt(), scanner.nextInt(),
        scanner.nextInt(), scanner.nextInt(), scanner.nextInt()
    )

    @Suppress("RedundantLambdaOrAnonymousFunction")
    private fun readUnit() = UnitInput(
        Vector2(scanner.nextInt(), scanner.nextInt()), scanner.nextInt() == 0, {
            when (val type = scanner.nextInt()) {
                -1 -> null
                else -> CreepType.values()[type]
            }
        }(), scanner.nextInt()
    )//.also { stderr.println("Read creep: $it")}


    init {
        obstacles = (0 until scanner.nextInt()).map { readObstacleInit() }
    }

    protected fun readInputs(): AllInputs {
        val gold = scanner.nextInt()
        val touchedObstacleId = scanner.nextInt()
        val obstacles = (0 until obstacles.size).map { applyObstacleUpdate(readObstaclePerTurn()) }
        val units = (0 until scanner.nextInt()).map { readUnit() }
        return AllInputs(
            units.single { it.isFriendly && it.creepType == null }.let { it.location },
            units.single { it.isFriendly && it.creepType == null }.let { it.health },
            gold,
            touchedObstacleId,
            units.single { !it.isFriendly && it.creepType == null }.let { it.location },
            units.single { !it.isFriendly && it.creepType == null }.let { it.health },
            obstacles,
            units.filter { it.isFriendly && it.creepType != null },
            units.filter { !it.isFriendly && it.creepType != null }
        )
    }//.also { stderr.println("Read inputs: $it")}

    private fun applyObstacleUpdate(update: ObstaclePerTurnInput): ObstacleInput {
        val matchingObstacle = obstacles.find { it.obstacleId == update.obstacleId }!!
        matchingObstacle.applyUpdate(update)
        return matchingObstacle
    }
}


class LennyPlayer(stdin: InputStream, stdout: PrintStream, stderr: PrintStream) : BasePlayer(stdin, stdout, stderr) {
    lateinit var inputs: AllInputs


    lateinit var closestCorner: Vector2

    init {

        while (true) {
            this.inputs = readInputs()
            initCorner()
            fd()
        }
    }

    private fun initCorner() {
        if (!this::closestCorner.isInitialized) {
            closestCorner = if (inputs.queenLoc.x < 1920 / 2) {
                if (inputs.queenLoc.y < 1000 / 2) {
                    Vector2(0, 0)
                } else {
                    Vector2(0, 1000)
                }
            } else {
                if (inputs.queenLoc.y < 1000 / 2) {
                    Vector2(1920, 0)
                } else {
                    Vector2(1920, 1000)
                }
            }
        }
    }

    private fun closestFree(): ObstacleInput {
        return inputs.obstacles.filter { !it.isAllied }.minByOrNull {
            it.location.distanceTo(inputs.queenLoc)
        }!!
    }

    private fun closestAnd(predicate: (ObstacleInput) -> Boolean): ObstacleInput? = inputs.obstacles.filter {
        !it.isAllied && predicate(it)
    }.minByOrNull { it.location.distanceTo(inputs.queenLoc) }


    fun getTouchedSite(): ObstacleInput? {
        val touched = inputs.touchedObstacleId
        if (touched != -1) {
            return inputs.obstacles.find { it.obstacleId == touched }!!
        }
        return null
    }


    private fun tryBuild(siteId: Int, action: (site: ObstacleInput) -> Unit) {
        val touchedSite = getTouchedSite()
        if (touchedSite != null && touchedSite.obstacleId == siteId) {
            action(touchedSite)
        } else {
            val site = inputs.obstacles.find { it.obstacleId == siteId }!!
            write("MOVE ${site.cords()}")
        }
    }


    fun mineStrategy() {
        // archer -> mine -> lvlup mine -> knights on enemy mines

        val archers = inputs.obstacles.filter { it.isAllied && it.isBarrackOf(CreepType.ARCHER) }
        val knights = inputs.obstacles.filter { it.isAllied && it.isBarrackOf(CreepType.KNIGHT) }
        val mines = inputs.obstacles.filter { it.isAllied && it.isMine }

        val towers = inputs.obstacles.filter { it.isAllied && it.isTower }


        val closestEnemyKnight = inputs.enemyCreeps.filter { it.creepType == CreepType.KNIGHT }.minByOrNull {
            it.location.distanceTo(inputs.queenLoc).toDouble
        }

        // 5 100
        // 1 5
        // 3 66

        // 66/3 = 22
        // 100/5 = 20
        // 5/1 = 5

        fun isMineSafe(mine: ObstacleInput): Boolean {
            val myDistance = inputs.queenLoc.distanceTo(mine.location)
            val closestEnemyKnight = inputs.enemyCreeps.filter { it.creepType == CreepType.KNIGHT }.minByOrNull {
                it.location.distanceTo(mine.location).toDouble
            }

            val enemyQueenDistance = inputs.enemyQueenLoc.distanceTo(mine.location)

            if (closestEnemyKnight != null) {
                val dst = closestEnemyKnight.location.distanceTo(mine.location)
                // we are going to get intercepted, not good
                if (dst.toDouble - myDistance.toDouble < 100) return false
            }

            if (abs(enemyQueenDistance.toDouble - myDistance.toDouble) < 300) {
                // queen is going to intercept
                return false
            }


            return true
        }

        val reservedMine =
            inputs.obstacles.filter {

                // when we can override allies?
                if (!isMineSafe(it)) return@filter false

                // its a mine and we know its not saturated

                if (it.isMine) {
                    if (!it.isFullySaturated) {
                        stderr.println("Oh ${it.obstacleId} is not fully saturated! ${it.incomeRateOrHealthOrCooldown} != ${it.maxMineSize}")
                        return@filter true
                    } else {
                        return@filter false
                    }
                }

                val closestKnight = inputs.enemyCreeps.filter { it.creepType == CreepType.KNIGHT }.sortedBy {
                    it.location.distanceTo(it.location).toDouble
                }.firstOrNull()

                val isSafeTower =
                    if (closestKnight != null) closestKnight.location.distanceTo(it.location).toDouble > 500
                    else true

                if (it.isTower && isSafeTower) return@filter true
                else (it.gold == -1 || it.gold > 0)


            }.sortedByDescending {
                val potential = max(1, it.maxMineSize) + max(it.gold, 0)
                val score = potential / it.location.distanceTo(closestCorner).toDouble


                score
            }.firstOrNull()

        stderr.println("${reservedMine?.obstacleId} best candidate")

        val closestFree = closestAnd { it.obstacleId != reservedMine?.obstacleId }


        val myMine = mines.firstOrNull()

        val desiredTowerLocation = inputs.obstacles.minByOrNull {
            it.location.distanceTo(closestCorner).toDouble
        }!!

        val closestTower = towers.minByOrNull { it.location.distanceTo(inputs.queenLoc).toDouble }


        when {
            closestEnemyKnight != null && closestEnemyKnight.location.distanceTo(inputs.queenLoc).toDouble < 300 -> {
                // we panik!
                val literalClosest = inputs.obstacles.minByOrNull { it.location.distanceTo(inputs.queenLoc).toDouble }!!
                stderr.println("I panick!!! want to make tower ${literalClosest.obstacleId}")
                tryBuild(literalClosest.obstacleId) {
                    tower()
                }
            }

            closestFree == null -> write("MOVE ${closestCorner.x.toInt()} ${closestCorner.y.toInt()}")
            archers.isEmpty() -> {
                tryBuild(closestFree.obstacleId) {
                    archer()
                }
            }
            knights.isNotEmpty() && reservedMine != null -> {
                tryBuild(reservedMine.obstacleId) {
                    mine()
                }
            }
            myMine != null && myMine.incomeRateOrHealthOrCooldown < myMine.maxMineSize -> {
                // UPGRADE
                tryBuild(myMine.obstacleId) {
                    mine()
                }
            }
            knights.isEmpty() -> {
                tryBuild(closestFree.obstacleId) {
                    knight()
                }
            }
            !desiredTowerLocation.isTower -> {
                tryBuild(desiredTowerLocation.obstacleId) {
                    tower()
                }
            }
            closestTower != null -> {
                write("MOVE ${closestTower.cords()}")
            }
            else -> {
                // write("MOVE ${closestCorner.x.toInt()} ${closestCorner.y.toInt()}")
                val literalClosest = inputs.obstacles.minByOrNull { it.location.distanceTo(inputs.queenLoc).toDouble }!!
                stderr.println("I want to make tower ${literalClosest.obstacleId}")
                tryBuild(literalClosest.obstacleId) {
                    tower()
                }
            }

        }


    }


    fun decideAction() {
        return mineStrategy()
        val archers = inputs.obstacles.filter { it.isAllied && it.isBarrackOf(CreepType.ARCHER) }
        val knights = inputs.obstacles.filter { it.isAllied && it.isBarrackOf(CreepType.KNIGHT) }

        val towers = inputs.obstacles.filter { it.isAllied && it.isTower }

        val closestFree = closestFree()

        when {
            archers.isEmpty() -> {
                tryBuild(closestFree.obstacleId) {
                    archer()
                }
            }
            towers.size < 2 -> {
                // towers
                tryBuild(closestFree.obstacleId) {
                    tower()
                }
            }
            knights.isEmpty() -> {
                tryBuild(closestFree.obstacleId) {
                    knight()
                }
            }
            else -> write("MOVE ${closestCorner.x.toInt()} ${closestCorner.y.toInt()}")

        }


    }

    fun fd() {
        decideAction()

        val archers = inputs.obstacles.filter { it.isAllied && it.isBarrackOf(CreepType.ARCHER) }
        val knights = inputs.obstacles.filter { it.isAllied && it.isBarrackOf(CreepType.KNIGHT) }

        val archersCount = inputs.friendlyCreeps.count { it.creepType == CreepType.ARCHER }
        val knightCount = inputs.friendlyCreeps.count { it.creepType == CreepType.KNIGHT }

        if (archersCount > knightCount && knights.isNotEmpty())
            write("TRAIN ${knights.first().obstacleId}")
        else if (knightCount >= archersCount && archers.isNotEmpty())
            write("TRAIN ${archers.first().obstacleId}")
        else
            write("TRAIN")
    }


    fun tower() {
        write("BUILD ${inputs.touchedObstacleId} TOWER")
    }

    fun giant() {
        write("BUILD ${inputs.touchedObstacleId} BARRACKS-GIANT")
    }

    fun knight() {
        write("BUILD ${inputs.touchedObstacleId} BARRACKS-KNIGHT")
    }

    fun archer() {
        write("BUILD ${inputs.touchedObstacleId} BARRACKS-ARCHER")
    }

    fun mine() {
        write("BUILD ${inputs.touchedObstacleId} MINE")
    }

    fun write(s: String) {
        stdout.println(s)
    }
}

fun main(args: Array<String>) {
    LennyPlayer(System.`in`, System.out, System.err)
}