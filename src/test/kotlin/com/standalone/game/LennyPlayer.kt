package com.standalone.game

import java.io.InputStream
import java.io.PrintStream


import java.util.*
import kotlin.math.*

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

    fun isOccupied(): Boolean {
        return this.structureType != -1
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

    private fun readUnit() = UnitInput(
        Vector2(scanner.nextInt(), scanner.nextInt()), scanner.nextInt() == 0, {
            val type = scanner.nextInt()
            when (type) {
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

    init {
        while (true) {
            this.inputs = readInputs()
            stderr.println("read inputs")
//            fd()
            stdout.println("WAIT")
            stdout.println("TRAIN")
        }
    }

    private fun closestFree(): ObstacleInput {
        return inputs.obstacles.filter { !it.isOccupied() }.minByOrNull {
            it.location.distanceTo(inputs.queenLoc)
        }!!
    }


    fun decideAction() {

        val touched = inputs.touchedObstacleId

        if (touched != -1) {
            val touchedSite = inputs.obstacles.find { it.obstacleId == touched }!!
            if (!touchedSite.isOccupied()) {
                write("BUILD $touched BARRACKS-KNIGHT")
                return
            }
        }

        val closest = closestFree()
        write("MOVE ${closest.location.x} ${closest.location.y}")

    }


    fun fd() {
        decideAction()
        write("TRAIN")

    }


    fun write(s: String) {
        stderr.println("[WRITE] $s")
        stdout.println(s)
    }
}

//fun main(args: Array<String>) {
//    LennyPlayer(System.`in`, System.out, System.err)
//}