package com.standalone.game


import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import java.io.PrintStream
import java.util.*
import javax.imageio.ImageIO
import kotlin.math.*


val viewportX = 0..1920
val viewportY = 0..1000

object StructureType {
    const val MINE = 0
    const val TOWER = 1
    const val BARRACK = 2
}

data class CreepInstance(val type: CreepType)

fun Collection<ObstacleInput>.joinIds() = (if (isNotEmpty()) " " else "") + map { it.obstacleId }.joinToString(" ")

fun Map<CreepType, Int>.queue(): PriorityQueue<CreepInstance> {
    val q = PriorityQueue<CreepInstance> { a, b ->
        getValue(b.type) - getValue(a.type)
    }
    entries.forEach { entry ->
        repeat(entry.value) {
            q.add(CreepInstance(entry.key))
        }
    }

    return q
}

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
    var attackRadiusOrCreepType: Int = -1        // tower / barracks,
) {
    fun applyUpdate(update: ObstaclePerTurnInput) {
        structureType = update.structureType
        gold = update.gold
        maxMineSize = update.maxMineSize
        owner = update.owner
        incomeRateOrHealthOrCooldown = update.incomeRateOrHealthOrCooldown
        attackRadiusOrCreepType = update.attackRadiusOrCreepType
    }


    val range: Double
        get() = sqrt((incomeRateOrHealthOrCooldown * 1000 + radius) / PI)

    val isFullySaturated: Boolean
        get() = this.isMine && this.incomeRateOrHealthOrCooldown == maxMineSize

    val saturationRate
        get() = this.incomeRateOrHealthOrCooldown / maxMineSize.toDouble()

    val isTower: Boolean
        get() = this.structureType == StructureType.TOWER


    val isMine: Boolean
        get() = this.structureType == StructureType.MINE

    val isAllied
        get() = owner == 0

    val isEnemy
        get() = owner == 1

    val isNeutral
        get() = owner == -1

    val isBarrack: Boolean
        get() = structureType == StructureType.BARRACK

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

    fun canReplace(): Boolean {
        return this.structureType != 2 || this.structureType == 2 && this.incomeRateOrHealthOrCooldown == 0
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
    lateinit var input: AllInputs

    var tick = 0

    lateinit var closestCorner: Vector2


    val safetyWeight = 10.0


    val delimeter = 20

    val xGrid = 1920 / delimeter
    val yGrid = 1000 / delimeter


    val isRed
        get() = closestCorner.x < 1920 / 2

    /**
     * 4x3 example
     * [
     * [(0,0),(0,1),(0,2)],
     * [(1,0),(1,1),(1,2)],
     * [(2,0),(2,1),(2,2)],
     * [(3,0),(3,1),(3,2)],
     */
    private lateinit var dangerMatrix: Array<Array<Double>>
    private lateinit var protectionMatrix: Array<Array<Double>>

    init {
        initSafetyMatrix()
        while (true) {
            this.input = readInputs()
            initCorner()
            updateSafetyMatrix()
            fd()

            tick++
        }
    }

    private fun initSafetyMatrix() {
        dangerMatrix = Array(yGrid) {
            Array(xGrid) { 1.0 }
        }
        protectionMatrix = Array(yGrid) {
            Array(xGrid) { 1.0 }
        }
    }

    private fun applyGradient(
        matrix: Array<Array<Double>>, i: Int, j: Int, radius: Int, factor: Double, gradient: (Double) -> Double = {
            1.0 / it
        }
    ) {

        for (gi in -radius until radius) {
            for (gj in -radius until radius) {
                val ti = i + gi
                val tj = j + gj
                if (ti < 0 || ti >= xGrid || tj < 0 || tj >= yGrid) continue

                val distanceSq = sqrt((gi * gi + gj * gj).toDouble())
                val initialScore = matrix[tj][ti]
                matrix[tj][ti] = initialScore + gradient(distanceSq) * factor
            }
        }
    }


    private fun updateSafetyMatrix() {

        val knightDangerRadius = toGridLen(1000)
        val archerSafetyRadius = toGridLen(1000)

        // Initial filling with gradient
        for (i in 0 until xGrid) {
            for (j in 0 until yGrid) {
                val cords = toCords(i, j)
                val score = if (isRed) {
                    // e.g. X = 0 (most left) is the safest, so it needs to be lowest
                    cords.x / 1920 - 0.5
                } else {
                    // e.g. X = 1920(most right) is the safest, so it needs to be lowest
                    // 1800
                    0.5 - cords.x / 1920
                } * 12
                dangerMatrix[j][i] = score
                protectionMatrix[j][i] = 0.0
            }
        }

        input.friendlyCreeps.filter { it.creepType == CreepType.ARCHER }.forEach {
            // Aight now we gradient shiet
            val (i, j) = toGrid(it.location)
            applyGradient(protectionMatrix, i, j, archerSafetyRadius, -5.0) { distance ->
                1.0 / distance
            }
        }

        input.enemyCreeps.filter { it.creepType == CreepType.KNIGHT }.forEach {
            // Aight now we gradient shiet
            val (i, j) = toGrid(it.location)
            applyGradient(dangerMatrix, i, j, knightDangerRadius, 5.0) { distance ->
                20.0 / distance
            }
        }

        enemyTowers.forEach {
            val (i, j) = toGrid(it.location)
            applyGradient(dangerMatrix, i, j, toGridLen(it.range), 5.0) { distance ->
                5.0
            }
        }

        alliedTowers.forEach {
            val (i, j) = toGrid(it.location)
            applyGradient(protectionMatrix, i, j, toGridLen(it.range), 5.0) { distance ->
                if (distance < toGridLen(it.range))
                    2.0
                else
                    0.0
            }
        }

        input.obstacles.filter { it.isBarrackOf(CreepType.KNIGHT) && it.isEnemy }.forEach {
            val (i, j) = toGrid(it.location)
            applyGradient(dangerMatrix, i, j, toGridLen(800), 2.0) { distance ->
                2.0 / distance
            }
        }

        input.obstacles.filter { it.isBarrackOf(CreepType.ARCHER) && it.isAllied }.forEach {
            val trainProgress =
                (8 - if (it.incomeRateOrHealthOrCooldown == 0) 8 else it.incomeRateOrHealthOrCooldown) / 8.0

            val (i, j) = toGrid(it.location)
            applyGradient(protectionMatrix, i, j, toGridLen(1400), 2.0) { distance ->
                (1.0 * trainProgress) / distance
            }
        }
    }

    private fun toGridLen(dist: Double): Int = (dist / delimeter).toInt()
    private fun toGridLen(dist: Int): Int = (dist / delimeter)

    private fun toGrid(cords: Vector2): Pair<Int, Int> {
        val i = (cords.x / delimeter).toInt()
        val j = (cords.y / delimeter).toInt()
        return i to j
    }

    private fun toCords(x: Int, y: Int): Vector2 {
        return Vector2(x * delimeter, y * delimeter)
    }

    private fun getDangerLevel(cords: Vector2, bias: Double = 0.0): Double {
        val i = (cords.x / delimeter).toInt()
        val j = (cords.y / delimeter).toInt()
        val sm = dangerMatrix[j][i]
        return sm + bias
    }

    private fun findSafestSpot(): Vector2 {
        var prevSafety = Double.MIN_VALUE
        var prevCords = Vector2.Zero

        for (i in 0 until xGrid) {
            for (j in 0 until yGrid) {
                val safety = protectionMatrix[j][i]
                if (safety > prevSafety) {
                    prevSafety = safety
                    prevCords = toCords(i, j)
                }

            }
        }

        if (prevSafety == Double.MAX_VALUE) {
            stderr.println("Didn't find safe spot???")
        }
        return prevCords
    }


    private fun initCorner() {
        if (!this::closestCorner.isInitialized) {
            closestCorner = if (input.queenLoc.x < 1920 / 2) {
                if (input.queenLoc.y < 1000 / 2) {
                    Vector2(0, 0)
                } else {
                    Vector2(0, 1000)
                }
            } else {
                if (input.queenLoc.y < 1000 / 2) {
                    Vector2(1920, 0)
                } else {
                    Vector2(1920, 1000)
                }
            }
        }
    }

    // REGION accessors


    val alliedStructures
        get() = input.obstacles.filter { it.isAllied }

    val alliedTowers
        get() = alliedStructures.filter { it.isTower }

    val alliedBarracks
        get() = alliedStructures.filter { it.isBarrack }

    val alliedMines
        get() = alliedStructures.filter { it.isMine }


    val queenHealthPercent
        get() = input.health / 100.0

    val enemyQueenHealthPercent
        get() = input.enemyHealth / 100.0


    val knightCount get() = input.friendlyCreeps.count { it.creepType == CreepType.KNIGHT }
    val archerCount get() = input.friendlyCreeps.count { it.creepType == CreepType.ARCHER }
    val giantCount get() = input.friendlyCreeps.count { it.creepType == CreepType.GIANT }


    val enemyStructures
        get() = input.obstacles.filter { it.isEnemy }

    val enemyTowers
        get() = enemyStructures.filter { it.isTower }

    val enemyBarracks
        get() = enemyStructures.filter { it.isBarrack }

    val enemyMines
        get() = enemyStructures.filter { it.isMine }


    val income
        get() = alliedMines.sumOf { it.incomeRateOrHealthOrCooldown }


    val incomeWithCurrent
        get() = income + input.gold / 8

    val enemyIncome
        get() = enemyMines.sumOf { it.incomeRateOrHealthOrCooldown }

    val alliedArcherRacks
        get() = alliedBarracks.filter { it.isBarrackOf(CreepType.ARCHER) }
    val alliedKnightRacks
        get() = alliedBarracks.filter { it.isBarrackOf(CreepType.KNIGHT) }
    val alliedGiantRacks
        get() = alliedBarracks.filter { it.isBarrackOf(CreepType.GIANT) }


    fun canAfford(creepType: CreepType) {

    }

    /**
     * Returns unit priority map
     */
    val creepPriority: Map<CreepType, Int>
        get() {
            val priorityMap = hashMapOf<CreepType, Int>()
            // Giants
            val needGiant = enemyTowers.size > 3 && giantCount == 0
            priorityMap[CreepType.GIANT] = if (needGiant) 1 else 0

            // Knights are always in need
            priorityMap[CreepType.KNIGHT] = 2


            val currentKnightScore = input.enemyCreeps.count { it.creepType == CreepType.KNIGHT } / 3.0

            val enemyRacks = enemyBarracks.filter { it.creepType() == CreepType.KNIGHT }
            val knightPredictionScore = enemyRacks.sumOf {
                // 0 cd = can start training now
                // 7 cd = just started training
                if (it.incomeRateOrHealthOrCooldown > 0) {
                    (8.0 - max(it.incomeRateOrHealthOrCooldown, 4)) / 8
                } else {
                    0.0
                }
            }


            // e.g. 1 racks with cd 1
            // 7/8
            priorityMap[CreepType.ARCHER] =
                (1 + ceil(
                    knightPredictionScore / max(
                        1,
                        enemyRacks.size
                    )
                ) + currentKnightScore + enemyIncome / 6.0).roundToInt()


            return priorityMap
        }

    private val desiredRacks: Map<CreepType, Int>
        get() {
            // Here we need to include our finance info

            val buildMap = hashMapOf<CreepType, Int>()
            buildMap[CreepType.KNIGHT] = 0
            buildMap[CreepType.ARCHER] = 0
            buildMap[CreepType.GIANT] = 0

            var leftIncome = incomeWithCurrent

            val affordable = creepPriority.queue().takeWhile {
                val newIncome = leftIncome - it.type.cost / it.type.buildTime
                if (newIncome < 0) {
                    false
                } else {
                    leftIncome = newIncome
                    true
                }
            }

            affordable.forEach {
                buildMap[it.type] = buildMap[it.type]!! + 1
            }

            return buildMap
        }

    private val trainQueue: List<ObstacleInput>
        get() {
            val buildQ = arrayListOf<ObstacleInput>()

            val racksPool = alliedBarracks.toMutableList()

            var leftIncome = incomeWithCurrent

            // prioritized list of all we can afford
            val affordable = creepPriority.queue().takeWhile {
                val newIncome = leftIncome - it.type.cost / it.type.buildTime
                if (newIncome < 0) {
                    false
                } else {
                    leftIncome = newIncome
                    true
                }
            }.toMutableList()
            while (affordable.isNotEmpty()) {
                val poll = affordable.removeAt(0)

                val foundRacks = racksPool.find { it.creepType() == poll.type }
                if (foundRacks != null) {
                    racksPool.remove(foundRacks)
                    buildQ.add(foundRacks)
                } // else do nothing. We can't build it
            }

            return buildQ
        }


    // #REGION utils

    private fun getBuildDesirability(site: ObstacleInput): Double {
        return when {
            site.isOccupied() -> {
                when (site.structureType) {
                    StructureType.BARRACK -> {
                        0.8
                    }
                    StructureType.TOWER -> {
                        site.healthPercent()
                    }
                    StructureType.MINE -> {
                        // the more saturated it is the less its desirable to build on
                        site.saturationRate
                    }
                    else -> 1.0
                }
            }

            else -> 0.5
        }
    }

    private fun getTouchedSite(): ObstacleInput? {
        val touched = input.touchedObstacleId
        if (touched != -1) {
            return input.obstacles.find { it.obstacleId == touched }!!
        }
        return null
    }

    private fun closestSafestToQueen(threshold: Boolean = false, predicate: (ObstacleInput) -> Boolean = { true }) =
        input.obstacles.filter {
            if (threshold) {
                val safety = getDangerLevel(it.location)
                val safetyThreshold = 100

                safety < safetyThreshold && predicate(it)
            } else predicate(it)
        }.minByOrNull {
            val desirability = getBuildDesirability(it)
            it.location.distanceTo(input.queenLoc).toDouble * (getDangerLevel(
                it.location,
                bias = 1000.0
            ) * safetyWeight) * desirability
        }


    private fun tryBuild(obstacle: ObstacleInput, action: (site: ObstacleInput) -> Unit) {
        tryBuild(obstacle.obstacleId, action)
    }

    private fun tryBuild(siteId: Int, action: (site: ObstacleInput) -> Unit) {
        val touchedSite = getTouchedSite()
        if (touchedSite != null && touchedSite.obstacleId == siteId) {
            action(touchedSite)
        } else {
            val site = input.obstacles.find { it.obstacleId == siteId }!!
            write("MOVE ${site.cords()}")
        }
    }

    // #REGION high-level accessors

    private fun getNewMineCandidate(): ObstacleInput? {
        return input.obstacles.filter {
            when {
                it.isTower && it.isEnemy -> false
                it.isMine && it.isAllied && it.isFullySaturated -> false
                it.gold == 0 -> false

                it.isAllied && (it.isMine && it.isFullySaturated || it.isBarrack) -> false


                else -> true
            }
        }.sortedBy {
            it.location.distanceTo(input.queenLoc).toDouble * getDangerLevel(it.location, bias = 100.0)
        }.firstOrNull()
    }

    // 100%
    /**
     * knights
     * mine
     */
    private fun decideAction() {


        debug("CreepPriority: $creepPriority")
        debug("As Q: ${creepPriority.queue().toList()}")

        val dangerLevelPerKnightFactor = 2

        val queenDangerThreshold = 8 - queenHealthPercent * 5

        val dangerDistance = 400
//        val isInDanger = input.enemyCreeps.any { it.location.distanceTo(input.queenLoc).toDouble < dangerDistance }

        val queenDangerLevel = getDangerLevel(input.queenLoc)
        val isInDanger = queenDangerLevel > queenDangerThreshold

        // 16 income = po cd knights

        val closestFree = closestSafestToQueen { !it.isOccupied() }

        if (closestFree != null)
            debug("Closest free is: ${closestFree.obstacleId}. Safety there is ${getDangerLevel(closestFree.location)}")
        else
            debug("There is no safe closest here")

        val targetMine = getNewMineCandidate()


        debug("$desiredRacks")
        val safestSpot = findSafestSpot()

        when {
            isInDanger -> {
                // we need to build 2 tower close to middle
                // but towards corner
                debug("In danger")

                val dangerLevel =
                    input.enemyCreeps.count { it.creepType == CreepType.KNIGHT } / dangerLevelPerKnightFactor

                val candidates = alliedTowers.sortedBy { it.location.distanceTo(input.queenLoc).toDouble }

                when {
                    candidates.size < dangerLevel -> {
                        val toBuild = closestSafestToQueen(threshold = false) {
                            if (it.isBarrack && it.incomeRateOrHealthOrCooldown == 0)
                                true
                            else
                                !it.isTower
                        }!!

                        debug("Don't have enough towers for this danger, building more")
                        tryBuild(toBuild) {
                            tower()
                        }
                    }
                    else -> {
                        // we just move to safety

                        /**
                         * Find: X, Y
                         * For given N towers
                         *
                         *
                         */
                        when {
                            alliedTowers.isNotEmpty() -> {
//                                val needToGrowTower = alliedTowers.filter {
//                                    it.incomeRateOrHealthOrCooldown < 450
//                                }.minByOrNull {
//                                    it.incomeRateOrHealthOrCooldown
//                                }

                                val needToGrowTower: ObstacleInput? = null

                                if (needToGrowTower != null) {
                                    debug("We have a tower to grow!")
                                    tryBuild(needToGrowTower) {
                                        tower()
                                    }
                                } else {
                                    debug("Got enough towers, run!")
                                    val safety = findSafestSpot()
                                    val toGrow =
                                        closestSafestToQueen { it.isTower && it.isAllied && it.incomeRateOrHealthOrCooldown < 450 }
                                    if (toGrow != null) {
                                        tryBuild(toGrow) {
                                            tower()
                                        }
                                    } else
                                        write("MOVE ${safety.x.toInt()} ${safety.y.toInt()}")
                                }

                            }
                            else -> {
                                debug("Building more towers to protect")
                                tryBuild(closestSafestToQueen(threshold = false) { !(it.isAllied && it.isTower) }!!) {
                                    tower()
                                }
                            }
                        }


                    }

                }
            }

            closestFree == null -> {
                debug("Move to safest spot, no closest target")
                write("MOVE ${safestSpot.x.toInt()} ${safestSpot.y.toInt()}")
            }
            desiredRacks[CreepType.KNIGHT]!! > alliedKnightRacks.size -> {
                debug("Building knight")
                tryBuild(closestFree) {
                    knight()
                }
            }
            desiredRacks[CreepType.GIANT]!! > alliedGiantRacks.size -> {
                debug("Building giant")
                tryBuild(closestFree) {
                    giant()
                }
            }
            desiredRacks[CreepType.ARCHER]!! > alliedArcherRacks.size -> {
                debug("Building archer")
                tryBuild(closestFree) {
                    archer()
                }
            }
            targetMine != null -> {
                debug("Upgrading mine")
                tryBuild(targetMine) {
                    mine()
                }
            }
            else -> {
                debug("Building new mine")
                tryBuild(closestFree) {
                    mine()
                }
            }
        }

    }

    private fun fd() {

        if (System.getProperty("is_local_test") == "true")
            generateDebugSafetyImage()
        decideAction()
        write("TRAIN${trainQueue.joinIds()}")
    }


    fun tower() {
        write("BUILD ${input.touchedObstacleId} TOWER")
    }

    fun giant() {
        write("BUILD ${input.touchedObstacleId} BARRACKS-GIANT")
    }

    fun knight() {
        write("BUILD ${input.touchedObstacleId} BARRACKS-KNIGHT")
    }

    fun archer() {
        write("BUILD ${input.touchedObstacleId} BARRACKS-ARCHER")
    }

    fun mine() {
        write("BUILD ${input.touchedObstacleId} MINE")
    }

    private fun debug(out: Any) {
        stderr.println(out)
    }

    fun write(s: String) {
        stdout.println(s)
    }


    private fun generateDebugSafetyImage() {
        // only do red for now
        if (!isRed) return

        val tickCopy = tick
        val clr = if (!isRed) "blue" else "red"

        val width = xGrid
        val height = yGrid

        val output = BufferedImage(
            width, height,
            BufferedImage.TYPE_INT_RGB
        )


        val lowest = -50.0 //safetyMatrixCopy.flatten().minOf { it }
        val highest = +50.0 // safetyMatrixCopy.flatten().maxOf { it }

        for (i in 0 until xGrid) {
            for (j in 0 until yGrid) {
                val danger = dangerMatrix[j][i]
                val protection = protectionMatrix[j][i]

                val (mi, mj) = toGrid(input.queenLoc)
                val (ei, ej) = toGrid(input.enemyQueenLoc)

                val pixelColor = if (i == mi && j == mj) {
                    if (isRed) intArrayOf(255, 0, 255) else intArrayOf(0, 0, 255)
                } else if (i == ei && j == ej) {
                    if (isRed) intArrayOf(0, 0, 255) else intArrayOf(255, 0, 255)
                } else {
                    intArrayOf(
                        abs((danger / highest) * 255).toInt(),
                        abs(protection / highest * 255).toInt(),
                        0
                    )
                }
                output.raster.setPixel(i, j, pixelColor)
            }
        }



        ImageIO.write(
            output, "png",
            File("./debug_img/${tickCopy}_${clr}.png")
        )
    }
}

fun main(args: Array<String>) {
    LennyPlayer(System.`in`, System.out, System.err)
}