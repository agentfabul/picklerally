package com.picklerally.app.game

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.random.Random

enum class CharacterId(val displayName: String) {
    RIO("Rio"),
    LOLA("Lola"),
    BERT("Bert"),
    DILL("Dill"),
    MAYA("Maya"),
    KAI("Kai"),
    ZOE("Zoe"),
    GUS("Gus"),
    NINA("Nina"),
    DUKE("Duke");

    companion object {
        fun fromStringOrDefault(raw: String?, fallback: CharacterId = RIO): CharacterId =
            entries.firstOrNull { it.name == raw } ?: fallback
    }
}

data class CharacterPalette(
    val id: CharacterId,
    val name: String,
    val tagline: String,
    val description: String,
    val skin: Color,
    val shirt: Color,
    val headwear: Color,
    val paddle: Color,
    val hair: Color,
    val hasGlasses: Boolean = false,
    val hasBeard: Boolean = false,
    val isPickleMascot: Boolean = false,
    val isUnlockable: Boolean = false,
    val bigBuild: Boolean = false,
    val tankTop: Boolean = false,
    val bodyRadiusFt: Float = 1.12f,
)

val ROSTER_ORDER: List<CharacterId> = listOf(
    CharacterId.RIO,
    CharacterId.LOLA,
    CharacterId.BERT,
    CharacterId.DILL,
    CharacterId.MAYA,
    CharacterId.KAI,
    CharacterId.ZOE,
    CharacterId.GUS,
    CharacterId.NINA,
    CharacterId.DUKE,
)

val CHARACTER_PALETTES: Map<CharacterId, CharacterPalette> = mapOf(
    CharacterId.RIO to CharacterPalette(
        id = CharacterId.RIO,
        name = "Rio",
        tagline = "Speedy rookie",
        description = "Orange headband, blue shirt",
        skin = Color(0xFFF3C69F),
        shirt = Color(0xFF2563EB),
        headwear = Color(0xFFF97316),
        paddle = Color(0xFF84CC16),
        hair = Color(0xFF4B2E1E),
    ),
    CharacterId.LOLA to CharacterPalette(
        id = CharacterId.LOLA,
        name = "Lola",
        tagline = "Dink master",
        description = "Pink visor, gray hair, glasses",
        skin = Color(0xFFF0C2A2),
        shirt = Color(0xFF7C3AED),
        headwear = Color(0xFFEC4899),
        paddle = Color(0xFF38BDF8),
        hair = Color(0xFF94A3B8),
        hasGlasses = true,
    ),
    CharacterId.BERT to CharacterPalette(
        id = CharacterId.BERT,
        name = "Bert",
        tagline = "Power driver",
        description = "Big build, beard, green tank top",
        skin = Color(0xFFE9B68E),
        shirt = Color(0xFF16A34A),
        headwear = Color(0xFF78350F),
        paddle = Color(0xFFF59E0B),
        hair = Color(0xFF78350F),
        hasBeard = true,
        bigBuild = true,
        tankTop = true,
        bodyRadiusFt = 1.28f,
    ),
    CharacterId.DILL to CharacterPalette(
        id = CharacterId.DILL,
        name = "Dill",
        tagline = "Pickle mascot",
        description = "Unlockable pickle mascot, red headband",
        skin = Color(0xFF65A30D),
        shirt = Color(0xFF4D7C0F),
        headwear = Color(0xFFEF4444),
        paddle = Color(0xFFFACC15),
        hair = Color(0xFF3F6212),
        isPickleMascot = true,
        isUnlockable = true,
    ),
    CharacterId.MAYA to CharacterPalette(
        id = CharacterId.MAYA,
        name = "Maya",
        tagline = "Spin specialist",
        description = "Teal visor, purple shirt, black ponytail",
        skin = Color(0xFFC68642),
        shirt = Color(0xFF9333EA),
        headwear = Color(0xFF14B8A6),
        paddle = Color(0xFFFACC15),
        hair = Color(0xFF111827),
    ),
    CharacterId.KAI to CharacterPalette(
        id = CharacterId.KAI,
        name = "Kai",
        tagline = "Kitchen king",
        description = "Yellow headband, red shirt, glasses",
        skin = Color(0xFF8D5524),
        shirt = Color(0xFFDC2626),
        headwear = Color(0xFFFACC15),
        paddle = Color(0xFF22D3EE),
        hair = Color(0xFF1F2937),
        hasGlasses = true,
    ),
    CharacterId.ZOE to CharacterPalette(
        id = CharacterId.ZOE,
        name = "Zoe",
        tagline = "Lob queen",
        description = "White headband, pink shirt, red hair",
        skin = Color(0xFFFFDBAC),
        shirt = Color(0xFFEC4899),
        headwear = Color(0xFFF8FAFC),
        paddle = Color(0xFFA3E635),
        hair = Color(0xFFB91C1C),
    ),
    CharacterId.GUS to CharacterPalette(
        id = CharacterId.GUS,
        name = "Gus",
        tagline = "Veteran grinder",
        description = "Gray beard, navy polo, green headband",
        skin = Color(0xFFE0AC69),
        shirt = Color(0xFF1E3A8A),
        headwear = Color(0xFF15803D),
        paddle = Color(0xFFF97316),
        hair = Color(0xFF9CA3AF),
        hasBeard = true,
    ),
    CharacterId.NINA to CharacterPalette(
        id = CharacterId.NINA,
        name = "Nina",
        tagline = "Net rusher",
        description = "Black headband, orange shirt, glasses",
        skin = Color(0xFFF1C27D),
        shirt = Color(0xFFEA580C),
        headwear = Color(0xFF0F172A),
        paddle = Color(0xFFEC4899),
        hair = Color(0xFF3B2314),
        hasGlasses = true,
    ),
    CharacterId.DUKE to CharacterPalette(
        id = CharacterId.DUKE,
        name = "Duke",
        tagline = "Hard hitter",
        description = "Big build, purple tank top, gold headband",
        skin = Color(0xFF5C3A21),
        shirt = Color(0xFF7C3AED),
        headwear = Color(0xFFEAB308),
        paddle = Color(0xFFF43F5E),
        hair = Color(0xFF0B0B0B),
        bigBuild = true,
        tankTop = true,
        bodyRadiusFt = 1.28f,
    ),
)

fun characterPalette(id: CharacterId): CharacterPalette =
    CHARACTER_PALETTES[id] ?: CHARACTER_PALETTES.getValue(CharacterId.RIO)

enum class ShotType {
    DINK,
    DRIVE,
    LOB,
    SERVE,
}

enum class NetCrossOutcome {
    NONE,
    CLEARED,
    HIT_NET,
}

enum class RallyPhase {
    WAITING_SERVE,
    RALLY_LIVE,
    POINT_PAUSE,
    MATCH_OVER,
}

enum class PlayerHitState {
    NONE,
    READY,
    WAIT_BOUNCE,
    KITCHEN,
    TOO_HIGH,
}

enum class BotDifficulty(val label: String) {
    EASY("Easy"),
    NORMAL("Normal"),
    HARD("Hard"),
    PRO("Pro"),
}

data class BotProfile(
    val label: String,
    val speedFtS: Float,
    val reachScale: Float,
    val aimAwayChance: Float,
    val sidelineMarginFt: Float,
)

val BOT_PROFILES: Map<BotDifficulty, BotProfile> = mapOf(
    BotDifficulty.EASY to BotProfile("Easy", 10.5f, 0.8f, 0.35f, 3.2f),
    BotDifficulty.NORMAL to BotProfile("Normal", 14.2f, 0.95f, 1.0f, 2.0f),
    BotDifficulty.HARD to BotProfile("Hard", 16.5f, 1.0f, 1.0f, 1.6f),
    BotDifficulty.PRO to BotProfile("Pro", 18.5f, 1.05f, 1.0f, 1.2f),
)

fun botErrorChance(difficulty: BotDifficulty = BotDifficulty.NORMAL): Float = when (difficulty) {
    BotDifficulty.EASY -> 0.24f
    BotDifficulty.HARD -> 0.05f
    BotDifficulty.PRO -> 0.02f
    BotDifficulty.NORMAL -> 0.12f
}

data class MatchFormatConfig(
    val pointsToWin: Int = 11,
    val bestOf: Int = 1,
    val difficulty: BotDifficulty = BotDifficulty.NORMAL,
)

data class BallState(
    var x: Float = 14f,
    var y: Float = 10f,
    var z: Float = 2.4f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var vz: Float = 0f,
    var active: Boolean = false,
    var bouncesOnCurrentSide: Int = 0,
    var lastHitterSide: CourtSide? = null,
    var crossedNetSinceHit: Boolean = false,
)

data class StepEvents(
    val bounced: Boolean = false,
    val bounceZSpeed: Float = 0f,
    val bounceX: Float = 0f,
    val bounceY: Float = 0f,
    val netOutcome: NetCrossOutcome = NetCrossOutcome.NONE,
)

data class SwipeVector(
    val dxFt: Float,
    val dyFt: Float,
    val durationSec: Float,
)

data class PlayerEntity(
    val side: CourtSide,
    var characterId: CharacterId,
    var x: Float,
    var y: Float,
    var targetX: Float,
    var targetY: Float,
    var swingTimer: Float = 0f,
    var lastSwingType: ShotType = ShotType.DRIVE,
    var displayName: String? = null,
)

data class HitFeedback(
    val hitterSide: CourtSide,
    val shotType: ShotType,
    val timingAccuracy: Float,
    val hitX: Float,
    val hitY: Float,
)

data class SimTickOutput(
    val stepEvents: StepEvents,
    val hitFeedback: HitFeedback? = null,
    val pointResolution: PointResolution? = null,
)

data class ShotOutcome(
    val hit: HitFeedback? = null,
    val fault: PointResolution? = null,
)

data class ShotVelocity(
    val vx: Float,
    val vy: Float,
    val vz: Float,
)

data class BotReturnShot(
    val shotType: ShotType,
    val vx: Float,
    val vy: Float,
    val vz: Float,
    val targetX: Float,
    val targetY: Float,
)

object BallPhysicsConstants {
    const val GRAVITY_FT_S2 = -34.0f
    const val BOUNCE_RESTITUTION = 0.76f
    const val HORIZONTAL_AIR_DRAG = 0.14f
    const val BOUNCE_HORIZONTAL_FRICTION = 0.92f
    const val BALL_RADIUS_FT = 0.16f
}

fun stepBallPhysics(ball: BallState, dt: Float = CourtSpec.FIXED_TIMESTEP_SEC): StepEvents {
    if (!ball.active) {
        return StepEvents(
            bounced = false,
            bounceZSpeed = 0f,
            bounceX = ball.x,
            bounceY = ball.y,
            netOutcome = NetCrossOutcome.NONE,
        )
    }

    val prevX = ball.x
    val prevY = ball.y
    val prevZ = ball.z

    val dragFactor = max(0f, 1f - BallPhysicsConstants.HORIZONTAL_AIR_DRAG * dt)
    ball.vx *= dragFactor
    ball.vy *= dragFactor

    ball.vz += BallPhysicsConstants.GRAVITY_FT_S2 * dt
    ball.x += ball.vx * dt
    ball.y += ball.vy * dt
    ball.z += ball.vz * dt

    var netOutcome = NetCrossOutcome.NONE
    val crossedNet =
        (prevY < CourtSpec.NET_Y && ball.y >= CourtSpec.NET_Y) ||
            (prevY > CourtSpec.NET_Y && ball.y <= CourtSpec.NET_Y)

    if (crossedNet) {
        val dy = ball.y - prevY
        val t = if (abs(dy) > 1e-5f) min(1f, max(0f, (CourtSpec.NET_Y - prevY) / dy)) else 0.5f
        val crossX = prevX + (ball.x - prevX) * t
        val crossZ = prevZ + (ball.z - prevZ) * t
        val requiredZ = CourtSpec.netHeightAt(crossX) + BallPhysicsConstants.BALL_RADIUS_FT * 0.6f

        val withinPosts =
            crossX >= CourtSpec.COURT_LEFT_X - CourtSpec.NET_POST_OFFSET_FT &&
                crossX <= CourtSpec.COURT_RIGHT_X + CourtSpec.NET_POST_OFFSET_FT

        if (withinPosts && crossZ < requiredZ) {
            netOutcome = NetCrossOutcome.HIT_NET
            ball.x = crossX
            val dir = if (ball.vy == 0f) 1f else sign(ball.vy)
            ball.y = CourtSpec.NET_Y - dir * 0.22f
            ball.z = max(0.2f, crossZ)
            ball.vx *= 0.25f
            ball.vy = -ball.vy * 0.22f
            ball.vz = -1.5f
        } else {
            netOutcome = NetCrossOutcome.CLEARED
            ball.crossedNetSinceHit = true
            ball.bouncesOnCurrentSide = 0
        }
    }

    var bounced = false
    var bounceZSpeed = 0f
    var bounceX = ball.x
    var bounceY = ball.y
    val rawZ = ball.z
    if (rawZ <= 0f) {
        val denom = prevZ - rawZ
        val tBounce = if (prevZ > 0f && denom > 1e-5f) min(1f, max(0f, prevZ / denom)) else 1f
        bounceX = prevX + (ball.x - prevX) * tBounce
        bounceY = prevY + (ball.y - prevY) * tBounce

        bounceZSpeed = abs(ball.vz)
        ball.z = 0f
        if (bounceZSpeed > 1.2f) {
            ball.vz = bounceZSpeed * BallPhysicsConstants.BOUNCE_RESTITUTION
            ball.vx *= BallPhysicsConstants.BOUNCE_HORIZONTAL_FRICTION
            ball.vy *= BallPhysicsConstants.BOUNCE_HORIZONTAL_FRICTION
        } else {
            ball.vz = 0f
            ball.vx *= 0.85f
            ball.vy *= 0.85f
        }
        ball.bouncesOnCurrentSide += 1
        bounced = true
    }

    return StepEvents(
        bounced = bounced,
        bounceZSpeed = bounceZSpeed,
        bounceX = bounceX,
        bounceY = bounceY,
        netOutcome = netOutcome,
    )
}

fun classifySwipe(swipe: SwipeVector): ShotType {
    val dist = hypot(swipe.dxFt, swipe.dyFt)
    val speed = dist / max(swipe.durationSec, 0.04f)
    val forwardRatio = if (dist > 0.01f) abs(swipe.dyFt) / dist else 0f

    if (swipe.dyFt > 2.2f && forwardRatio > 0.72f && speed in 10f..27f && dist < 5.2f) {
        return ShotType.LOB
    }
    if (dist < 2.6f || speed < 11.5f) {
        return ShotType.DINK
    }
    return ShotType.DRIVE
}

fun horizontalDragIntegral(flightTimeSec: Float): Float {
    val d = BallPhysicsConstants.HORIZONTAL_AIR_DRAG
    if (d <= 1e-5f) return flightTimeSec
    return (1f - exp((-d * flightTimeSec).toDouble()).toFloat()) / d
}

private fun timeToCrossNet(fromY: Float, targetY: Float, flightTimeSec: Float): Pair<Float, Float> {
    val dyTotal = targetY - fromY
    val dyNet = CourtSpec.NET_Y - fromY
    if (abs(dyTotal) < 1e-4f || dyTotal * dyNet <= 0f || abs(dyNet) >= abs(dyTotal)) {
        return flightTimeSec * 0.5f to 0.5f
    }
    val fracNet = max(0.05f, min(0.95f, dyNet / dyTotal))
    val d = BallPhysicsConstants.HORIZONTAL_AIR_DRAG
    if (d <= 1e-5f) {
        return flightTimeSec * fracNet to fracNet
    }
    val expNet = max(0.02f, 1f - fracNet * (1f - exp((-d * flightTimeSec).toDouble()).toFloat()))
    return (-ln(expNet.toDouble()).toFloat() / d) to fracNet
}

fun computeTargetShotVelocity(
    fromX: Float,
    fromY: Float,
    fromZ: Float,
    targetX: Float,
    targetY: Float,
    shotType: ShotType,
    minNetClearanceFt: Float = when (shotType) {
        ShotType.LOB -> 1.8f
        ShotType.DINK -> 0.65f
        else -> 0.5f
    },
): ShotVelocity {
    val z0 = max(0.25f, fromZ)
    val dist = hypot(targetX - fromX, targetY - fromY)

    var flightTime = when (shotType) {
        ShotType.DINK -> min(0.84f, max(0.62f, dist / 24f))
        ShotType.LOB -> min(1.58f, max(1.24f, dist / 27f))
        ShotType.SERVE -> 0.94f
        ShotType.DRIVE -> min(0.84f, max(0.58f, dist / 46f))
    }

    var vz = (-0.5f * BallPhysicsConstants.GRAVITY_FT_S2 * flightTime * flightTime - z0) / flightTime

    for (i in 0 until 36) {
        val (tNet, fracNet) = timeToCrossNet(fromY, targetY, flightTime)
        val netX = fromX + (targetX - fromX) * fracNet
        val baseRequiredNetZ = CourtSpec.netHeightAt(netX) + BallPhysicsConstants.BALL_RADIUS_FT * 0.6f
        val zAtNet = z0 + vz * tNet + 0.5f * BallPhysicsConstants.GRAVITY_FT_S2 * tNet * tNet

        if (minNetClearanceFt < 0f) {
            vz = (baseRequiredNetZ - 0.35f - z0 - 0.5f * BallPhysicsConstants.GRAVITY_FT_S2 * tNet * tNet) / max(0.15f, tNet)
            break
        }

        if (zAtNet >= baseRequiredNetZ + minNetClearanceFt || flightTime >= 2.2f) {
            break
        }
        flightTime += 0.04f
        vz = (-0.5f * BallPhysicsConstants.GRAVITY_FT_S2 * flightTime * flightTime - z0) / flightTime
    }

    val invIntegral = 1f / horizontalDragIntegral(flightTime)
    val vx = (targetX - fromX) * invIntegral
    val vy = (targetY - fromY) * invIntegral

    return ShotVelocity(vx, vy, vz)
}

fun computeBotReturnShot(
    fromX: Float,
    fromY: Float,
    fromZ: Float,
    hitterY: Float,
    targetSide: CourtSide,
    opponentXs: List<Float>,
    difficulty: BotDifficulty = BotDifficulty.NORMAL,
    aimAwayChance: Float = 1f,
    sidelineMarginFt: Float = 2.0f,
    randFn: () -> Float = { Random.nextFloat() },
): BotReturnShot {
    val minX = CourtSpec.COURT_LEFT_X + sidelineMarginFt
    val maxX = CourtSpec.COURT_RIGHT_X - sidelineMarginFt
    val minY = if (targetSide == CourtSide.LOWER) CourtSpec.COURT_BOTTOM_Y + 2.2f else CourtSpec.NET_Y + 2.6f
    val maxY = if (targetSide == CourtSide.LOWER) CourtSpec.NET_Y - 2.6f else CourtSpec.COURT_TOP_Y - 2.2f

    val roll = randFn()
    val nearKitchen = abs(hitterY - CourtSpec.NET_Y) < 9.5f
    val shotType = when {
        nearKitchen && roll < 0.38f -> ShotType.DINK
        roll < 0.85f -> ShotType.DRIVE
        else -> ShotType.LOB
    }

    val oppX = if (opponentXs.isEmpty()) {
        CourtSpec.COURT_CENTER_X
    } else {
        opponentXs[min(opponentXs.lastIndex, (randFn() * opponentXs.size).toInt())]
    }
    val aimAway = aimAwayChance >= 1f || randFn() < aimAwayChance
    val preferredX = when {
        !aimAway -> oppX + (randFn() - 0.5f) * 3.0f
        oppX >= CourtSpec.COURT_CENTER_X -> CourtSpec.COURT_CENTER_X - 3.6f - randFn() * 3.0f
        else -> CourtSpec.COURT_CENTER_X + 3.6f + randFn() * 3.0f
    }
    var targetX = max(minX, min(maxX, preferredX))

    var targetY = when (shotType) {
        ShotType.DINK -> {
            val depth = 3.2f + randFn() * 2.6f
            if (targetSide == CourtSide.LOWER) CourtSpec.NET_Y - depth else CourtSpec.NET_Y + depth
        }
        ShotType.LOB -> {
            val marginFromBase = 2.6f + randFn() * 2.8f
            if (targetSide == CourtSide.LOWER) CourtSpec.COURT_BOTTOM_Y + marginFromBase else CourtSpec.COURT_TOP_Y - marginFromBase
        }
        else -> {
            val marginFromBase = 3.0f + randFn() * 6.5f
            if (targetSide == CourtSide.LOWER) CourtSpec.COURT_BOTTOM_Y + marginFromBase else CourtSpec.COURT_TOP_Y - marginFromBase
        }
    }
    targetY = max(minY, min(maxY, targetY))

    var minClearance = when (shotType) {
        ShotType.LOB -> 1.8f
        ShotType.DINK -> 0.65f
        else -> 0.5f
    }

    if (randFn() < botErrorChance(difficulty)) {
        val errRoll = randFn()
        if (errRoll < 0.45f) {
            minClearance = -0.35f
        } else if (errRoll < 0.75f) {
            targetY = if (targetSide == CourtSide.LOWER) {
                CourtSpec.COURT_BOTTOM_Y - (0.8f + randFn() * 1.4f)
            } else {
                CourtSpec.COURT_TOP_Y + (0.8f + randFn() * 1.4f)
            }
        } else {
            targetX = if (targetX < CourtSpec.COURT_CENTER_X) {
                CourtSpec.COURT_LEFT_X - (0.8f + randFn() * 1.2f)
            } else {
                CourtSpec.COURT_RIGHT_X + (0.8f + randFn() * 1.2f)
            }
        }
    }

    val vel = computeTargetShotVelocity(
        fromX = fromX,
        fromY = fromY,
        fromZ = fromZ,
        targetX = targetX,
        targetY = targetY,
        shotType = shotType,
        minNetClearanceFt = minClearance,
    )
    return BotReturnShot(shotType, vel.vx, vel.vy, vel.vz, targetX, targetY)
}

fun computeShotVelocity(
    fromX: Float,
    fromY: Float,
    swipe: SwipeVector,
    shotType: ShotType,
    targetSide: CourtSide,
    timingAccuracy: Float,
    scatterSeed: Float = 0f,
    fromZ: Float = 2.0f,
): ShotVelocity {
    val rawDx = swipe.dxFt
    val rawDy = max(0.75f, abs(swipe.dyFt))
    val aimRatio = max(-0.9f, min(0.9f, rawDx / rawDy))

    val errorScale = 1f - max(0f, min(1f, timingAccuracy))
    val scatterX = scatterSeed * 2.2f * errorScale
    val scatterDepth = scatterSeed * 1.8f * errorScale

    val targetY = when (shotType) {
        ShotType.DINK ->
            if (targetSide == CourtSide.UPPER) CourtSpec.NET_Y + 4.4f + scatterDepth * 0.4f
            else CourtSpec.NET_Y - 4.4f - scatterDepth * 0.4f
        ShotType.DRIVE ->
            if (targetSide == CourtSide.UPPER) CourtSpec.COURT_TOP_Y - 4.2f + scatterDepth
            else CourtSpec.COURT_BOTTOM_Y + 4.2f - scatterDepth
        ShotType.LOB ->
            if (targetSide == CourtSide.UPPER) CourtSpec.COURT_TOP_Y - 3.4f + scatterDepth * 0.6f
            else CourtSpec.COURT_BOTTOM_Y + 3.4f - scatterDepth * 0.6f
        ShotType.SERVE ->
            if (targetSide == CourtSide.UPPER) CourtSpec.UPPER_NVZ_Y + 8.5f
            else CourtSpec.LOWER_NVZ_Y - 8.5f
    }

    val travelY = max(4f, abs(targetY - fromY))
    val rawTargetX = max(
        CourtSpec.COURT_LEFT_X + 1.1f,
        min(CourtSpec.COURT_RIGHT_X - 1.1f, fromX + aimRatio * travelY * 0.72f + scatterX),
    )

    return computeTargetShotVelocity(fromX, fromY, fromZ, rawTargetX, targetY, shotType)
}

fun spriteScaleForHeight(z: Float): Float = 1f + min(z, 18f) * 0.085f

class SinglePlayerMatch(
    playerChar: CharacterId = CharacterId.RIO,
    botChar: CharacterId = CharacterId.LOLA,
    scoringMode: ScoringMode = ScoringMode.RALLY,
    format: MatchFormatConfig = MatchFormatConfig(),
) {
    companion object {
        const val PLAYER_SPEED_FT_S = 18.5f
        const val BOT_SPEED_FT_S = 14.2f
        const val HIT_REACH_FT = 3.8f
        const val PLAYER_REACH_FT = 4.3f
        const val MAX_HIT_HEIGHT_FT = 7.5f
        const val POINT_PAUSE_SEC = 1.5f
        const val GAME_BREAK_SEC = 3.5f
        const val BOT_SERVE_DELAY_SEC = 2.6f
    }

    var pointsToWin: Int = format.pointsToWin
        private set
    var bestOf: Int = format.bestOf
        private set
    var difficulty: BotDifficulty = format.difficulty
        private set

    var rules: RulesEngine = RulesEngine(scoringMode, MatchMode.SINGLES, pointsToWin, 2, CourtSide.LOWER)
        private set

    val player = PlayerEntity(
        side = CourtSide.LOWER,
        characterId = playerChar,
        x = CourtSpec.COURT_CENTER_X + 4.5f,
        y = CourtSpec.COURT_BOTTOM_Y - 1.1f,
        targetX = CourtSpec.COURT_CENTER_X + 4.5f,
        targetY = CourtSpec.COURT_BOTTOM_Y - 1.1f,
    )

    val bot = PlayerEntity(
        side = CourtSide.UPPER,
        characterId = botChar,
        x = CourtSpec.COURT_CENTER_X - 4.5f,
        y = CourtSpec.COURT_TOP_Y + 1.1f,
        targetX = CourtSpec.COURT_CENTER_X - 4.5f,
        targetY = CourtSpec.COURT_TOP_Y + 1.1f,
    )

    val ball = BallState(
        x = player.x - 0.6f,
        y = player.y + 0.9f,
        z = 2.4f,
    )

    var rallyPhase: RallyPhase = RallyPhase.WAITING_SERVE
        private set
    var rallyHits: Int = 0
        private set
    var bestRally: Int = 0
        private set
    var lastCallout: String = "Your serve (0 - 0)"
        private set
    var lastShotLabel: String = ""
        private set
    var lastFaultReason: FaultReason? = null
        private set
    var botServeDelaySec: Float = 0f
        private set
    var autoSwingEnabled: Boolean = true

    var gamesLower: Int = 0
        private set
    var gamesUpper: Int = 0
        private set
    var gameNumber: Int = 1
        private set
    val gameScores: MutableList<String> = mutableListOf()

    private var pauseTimerSec = 0f
    private var botCooldownSec = 0f
    private var accumulator = 0f
    private var pendingResolution: PointResolution? = null
    private var gameFirstServer: CourtSide = CourtSide.LOWER
    private var autoSwingPrevDist = Float.POSITIVE_INFINITY
    private var joyX = 0f
    private var joyY = 0f
    private var joyActive = false

    init {
        setupPointForServe()
    }

    fun resetMatch(
        scoringMode: ScoringMode = rules.scoringMode,
        format: MatchFormatConfig = MatchFormatConfig(pointsToWin, bestOf, difficulty),
    ) {
        gamesLower = 0
        gamesUpper = 0
        gameNumber = 1
        gameScores.clear()
        gameFirstServer = CourtSide.LOWER
        pointsToWin = format.pointsToWin
        bestOf = format.bestOf
        difficulty = format.difficulty
        rules = RulesEngine(scoringMode, MatchMode.SINGLES, pointsToWin, 2, CourtSide.LOWER)
        bestRally = 0
        lastShotLabel = ""
        lastFaultReason = null
        setupPointForServe()
    }

    fun setupPointForServe() {
        if (rules.isMatchOver) {
            rallyPhase = RallyPhase.MATCH_OVER
            val winnerName = if (rules.matchWinner == CourtSide.LOWER) {
                player.characterId.displayName
            } else {
                bot.characterId.displayName
            }
            lastCallout = "$winnerName Wins!"
            return
        }

        val assign = rules.currentServeAssignment()
        rallyPhase = RallyPhase.WAITING_SERVE
        rallyHits = 0
        lastFaultReason = null

        val lowerServerX = when {
            assign.servingSide == CourtSide.LOWER && assign.serverBox == ServiceBoxSide.LEFT ->
                CourtSpec.COURT_CENTER_X - 4.5f
            assign.servingSide == CourtSide.LOWER ->
                CourtSpec.COURT_CENTER_X + 4.5f
            assign.targetBox == ServiceBoxSide.LEFT ->
                CourtSpec.COURT_CENTER_X - 4.5f
            else ->
                CourtSpec.COURT_CENTER_X + 4.5f
        }

        val upperX = when {
            assign.servingSide == CourtSide.UPPER && assign.serverBox == ServiceBoxSide.LEFT ->
                CourtSpec.COURT_CENTER_X - 4.5f
            assign.servingSide == CourtSide.UPPER ->
                CourtSpec.COURT_CENTER_X + 4.5f
            assign.targetBox == ServiceBoxSide.LEFT ->
                CourtSpec.COURT_CENTER_X - 4.5f
            else ->
                CourtSpec.COURT_CENTER_X + 4.5f
        }

        player.x = lowerServerX
        player.y = CourtSpec.COURT_BOTTOM_Y - 1.1f
        player.targetX = player.x
        player.targetY = player.y
        player.swingTimer = 0f

        bot.x = upperX
        bot.y = CourtSpec.COURT_TOP_Y + 1.1f
        bot.targetX = bot.x
        bot.targetY = bot.y
        bot.swingTimer = 0f

        if (assign.servingSide == CourtSide.LOWER) {
            ball.x = player.x - 1.0f
            ball.y = player.y + 1.3f
            botServeDelaySec = 0f
            lastCallout = "Your serve (${rules.formattedScoreCall()})"
        } else {
            ball.x = bot.x + 1.0f
            ball.y = bot.y - 1.3f
            botServeDelaySec = BOT_SERVE_DELAY_SEC
            lastCallout = "${bot.characterId.displayName} serving (${rules.formattedScoreCall()})"
        }

        ball.z = 2.4f
        ball.vx = 0f
        ball.vy = 0f
        ball.vz = 0f
        ball.active = false
        ball.bouncesOnCurrentSide = 0
        ball.lastHitterSide = null
        ball.crossedNetSinceHit = false
    }

    fun setPlayerMoveTarget(worldX: Float, worldY: Float) {
        if (rallyPhase == RallyPhase.MATCH_OVER) return
        joyActive = false
        joyX = 0f
        joyY = 0f
        val maxY = if (rallyPhase == RallyPhase.WAITING_SERVE && rules.servingSide == CourtSide.LOWER) {
            CourtSpec.COURT_BOTTOM_Y - 0.3f
        } else {
            CourtSpec.NET_Y - 0.6f
        }
        player.targetX = max(1.5f, min(CourtSpec.WORLD_WIDTH_FT - 1.5f, worldX))
        player.targetY = max(1.5f, min(maxY, worldY))
    }

    fun setJoystickInput(normX: Float, normY: Float) {
        if (rallyPhase == RallyPhase.MATCH_OVER) return
        val mag = hypot(normX, normY)
        if (mag <= 0.08f) {
            if (joyActive) {
                joyActive = false
                joyX = 0f
                joyY = 0f
                player.targetX = player.x
                player.targetY = player.y
            }
            return
        }
        val clampedMag = min(1f, mag)
        val scale = clampedMag / mag
        joyX = normX * scale
        joyY = normY * scale
        joyActive = true
    }

    fun playerHitState(): PlayerHitState {
        if (rallyPhase != RallyPhase.RALLY_LIVE || !ball.active || ball.lastHitterSide == CourtSide.LOWER) {
            return PlayerHitState.NONE
        }
        val dist = hypot(ball.x - player.x, ball.y - player.y)
        if (dist > PLAYER_REACH_FT || ball.y > CourtSpec.NET_Y + 0.8f) return PlayerHitState.NONE
        if (ball.z > MAX_HIT_HEIGHT_FT) return PlayerHitState.TOO_HIGH
        if (ball.bouncesOnCurrentSide == 0) {
            if (rules.rallyShotCount == 1 || rules.rallyShotCount == 2) return PlayerHitState.WAIT_BOUNCE
            if (CourtSpec.isInKitchen(player.x, player.y, CourtSide.LOWER)) return PlayerHitState.KITCHEN
        }
        return PlayerHitState.READY
    }

    fun canPlayerReachBall(): Boolean {
        if (rallyPhase != RallyPhase.RALLY_LIVE || !ball.active) return false
        if (ball.lastHitterSide == CourtSide.LOWER) return false
        val dist = hypot(ball.x - player.x, ball.y - player.y)
        return dist <= PLAYER_REACH_FT &&
            ball.z <= MAX_HIT_HEIGHT_FT &&
            ball.y <= CourtSpec.NET_Y + 0.8f
    }

    fun launchServe(swipe: SwipeVector? = null): ShotOutcome {
        if (rallyPhase != RallyPhase.WAITING_SERVE) return ShotOutcome()
        val assign = rules.currentServeAssignment()
        val server = if (assign.servingSide == CourtSide.LOWER) player else bot

        val footFault = rules.beginServe(server.x, server.y)
        if (footFault != null) {
            return ShotOutcome(fault = applyFault(assign.servingSide, footFault))
        }

        val targetX =
            if (assign.targetBox == ServiceBoxSide.LEFT) CourtSpec.COURT_LEFT_X + 5.0f
            else CourtSpec.COURT_RIGHT_X - 5.0f
        val targetY =
            if (assign.targetCourtSide == CourtSide.UPPER) CourtSpec.UPPER_NVZ_Y + 7.8f
            else CourtSpec.LOWER_NVZ_Y - 7.8f

        val tof = 0.96f
        val invIntegral = 1f / horizontalDragIntegral(tof)
        val aimOffset = if (swipe != null) max(-2.6f, min(2.6f, swipe.dxFt * 0.42f)) else 0f
        ball.vx = (targetX + aimOffset - ball.x) * invIntegral
        ball.vy = (targetY - ball.y) * invIntegral
        ball.vz = -0.5f * BallPhysicsConstants.GRAVITY_FT_S2 * tof - ball.z / tof
        ball.active = true
        ball.lastHitterSide = assign.servingSide
        ball.crossedNetSinceHit = false
        ball.bouncesOnCurrentSide = 0

        server.swingTimer = 0.24f
        server.lastSwingType = ShotType.SERVE
        rallyPhase = RallyPhase.RALLY_LIVE
        botServeDelaySec = 0f
        rallyHits = 0
        bestRally = max(bestRally, 1)
        lastCallout = "Serve!"
        lastShotLabel = "Serve"

        return ShotOutcome(
            hit = HitFeedback(
                hitterSide = assign.servingSide,
                shotType = ShotType.SERVE,
                timingAccuracy = 0.95f,
                hitX = ball.x,
                hitY = ball.y,
            ),
        )
    }

    fun triggerReadyBotServe(): ShotOutcome {
        if (rallyPhase != RallyPhase.WAITING_SERVE || rules.servingSide != CourtSide.UPPER) {
            return ShotOutcome()
        }
        botServeDelaySec = 0f
        return launchServe()
    }

    fun attemptPlayerSwipeHit(
        swipe: SwipeVector,
        forcedShotType: ShotType? = null,
    ): ShotOutcome {
        if (
            rallyPhase == RallyPhase.WAITING_SERVE &&
            rules.servingSide == CourtSide.LOWER &&
            (swipe.dyFt > 1.2f || forcedShotType != null)
        ) {
            return launchServe(swipe)
        }
        if (!canPlayerReachBall()) return ShotOutcome()

        val ruleFault = rules.validatePlayerHit(
            CourtSide.LOWER,
            player.x,
            player.y,
            ball.bouncesOnCurrentSide,
        )
        if (ruleFault != null) {
            return ShotOutcome(fault = applyFault(CourtSide.LOWER, ruleFault))
        }

        val shotType = forcedShotType ?: classifySwipe(swipe)
        val dist = hypot(ball.x - player.x, ball.y - player.y)
        val distScore = 1f - min(1f, max(0f, dist / PLAYER_REACH_FT)) * 0.55f
        val heightSweet = 1f - min(1f, max(0f, abs(ball.z - 2.3f) / 3.8f)) * 0.45f
        val timingAccuracy = max(0.25f, min(1f, distScore * 0.6f + heightSweet * 0.4f))

        val scatterSeed = Random.nextFloat() * 2f - 1f
        val vel = computeShotVelocity(
            fromX = ball.x,
            fromY = ball.y,
            swipe = swipe,
            shotType = shotType,
            targetSide = CourtSide.UPPER,
            timingAccuracy = timingAccuracy,
            scatterSeed = scatterSeed,
            fromZ = ball.z,
        )

        ball.vx = vel.vx
        ball.vy = vel.vy
        ball.vz = vel.vz
        ball.lastHitterSide = CourtSide.LOWER
        ball.crossedNetSinceHit = false
        ball.bouncesOnCurrentSide = 0

        player.swingTimer = 0.22f
        player.lastSwingType = shotType
        bestRally = max(bestRally, rules.rallyShotCount)
        rallyHits = if (rules.rallyShotCount >= 3) rules.rallyShotCount else 0
        val label = when (shotType) {
            ShotType.DINK -> "Dink"
            ShotType.DRIVE -> "Drive"
            ShotType.LOB -> "Lob"
            ShotType.SERVE -> "Serve"
        }
        lastShotLabel = "$label · ${(timingAccuracy * 100f).roundToInt()}%"
        lastCallout = when (shotType) {
            ShotType.DINK -> "Soft Dink!"
            ShotType.DRIVE -> "Power Drive!"
            ShotType.LOB -> "High Lob!"
            ShotType.SERVE -> "Serve!"
        }

        return ShotOutcome(
            hit = HitFeedback(
                hitterSide = CourtSide.LOWER,
                shotType = shotType,
                timingAccuracy = timingAccuracy,
                hitX = ball.x,
                hitY = ball.y,
            ),
        )
    }

    fun update(rawDeltaSec: Float): List<SimTickOutput> {
        val clampedDt = min(rawDeltaSec, 0.05f)
        accumulator += clampedDt
        val outputs = mutableListOf<SimTickOutput>()

        while (accumulator >= CourtSpec.FIXED_TIMESTEP_SEC) {
            outputs.add(fixedStep(CourtSpec.FIXED_TIMESTEP_SEC))
            accumulator -= CourtSpec.FIXED_TIMESTEP_SEC
        }
        return outputs
    }

    private fun fixedStep(dt: Float): SimTickOutput {
        if (joyActive) {
            val maxY = if (rallyPhase == RallyPhase.WAITING_SERVE && rules.servingSide == CourtSide.LOWER) {
                CourtSpec.COURT_BOTTOM_Y - 0.3f
            } else {
                CourtSpec.NET_Y - 0.6f
            }
            player.x = (player.x + joyX * PLAYER_SPEED_FT_S * dt).coerceIn(1.5f, CourtSpec.WORLD_WIDTH_FT - 1.5f)
            player.y = (player.y + joyY * PLAYER_SPEED_FT_S * dt).coerceIn(1.5f, maxY)
            player.targetX = player.x
            player.targetY = player.y
        } else {
            moveTowardTarget(player, PLAYER_SPEED_FT_S, dt)
        }
        if (player.swingTimer > 0f) player.swingTimer = max(0f, player.swingTimer - dt)
        if (bot.swingTimer > 0f) bot.swingTimer = max(0f, bot.swingTimer - dt)
        if (botCooldownSec > 0f) botCooldownSec = max(0f, botCooldownSec - dt)

        val noEvents = StepEvents(bounceX = ball.x, bounceY = ball.y)

        if (rallyPhase == RallyPhase.MATCH_OVER) {
            return SimTickOutput(noEvents)
        }

        if (rallyPhase == RallyPhase.WAITING_SERVE) {
            if (rules.servingSide == CourtSide.LOWER) {
                ball.x = player.x - 1.0f
                ball.y = player.y + 1.3f
                ball.z = 2.4f
            } else {
                ball.x = bot.x + 1.0f
                ball.y = bot.y - 1.3f
                ball.z = 2.4f
                botServeDelaySec -= dt
                if (botServeDelaySec <= 0f) {
                    val served = launchServe()
                    return SimTickOutput(noEvents, served.hit, served.fault)
                }
            }
            return SimTickOutput(noEvents)
        }

        if (rallyPhase == RallyPhase.POINT_PAUSE) {
            pauseTimerSec -= dt
            if (pauseTimerSec <= 0f) {
                setupPointForServe()
            }
            return SimTickOutput(noEvents)
        }

        updateBotAI(dt)
        val botHit = tryBotHit()
        if (pendingResolution != null) {
            val pending = pendingResolution
            pendingResolution = null
            return SimTickOutput(noEvents, pointResolution = pending)
        }

        var autoHit: HitFeedback? = null
        if (autoSwingEnabled && canPlayerReachBall()) {
            val dist = hypot(ball.x - player.x, ball.y - player.y)
            val isVolley = ball.bouncesOnCurrentSide == 0
            val wouldFault = isVolley && (
                rules.rallyShotCount == 1 ||
                    rules.rallyShotCount == 2 ||
                    CourtSpec.isInKitchen(player.x, player.y, CourtSide.LOWER)
                )
            val leaving = dist > autoSwingPrevDist
            autoSwingPrevDist = dist
            if (!wouldFault && (dist <= PLAYER_REACH_FT * 0.7f || leaving)) {
                val res = attemptPlayerSwipeHit(SwipeVector(0f, 6f, 0.15f), ShotType.DRIVE)
                autoSwingPrevDist = Float.POSITIVE_INFINITY
                if (res.fault != null) return SimTickOutput(noEvents, pointResolution = res.fault)
                autoHit = res.hit
            }
        } else {
            autoSwingPrevDist = Float.POSITIVE_INFINITY
        }

        val stepEvents = stepBallPhysics(ball, dt)

        if (stepEvents.netOutcome == NetCrossOutcome.HIT_NET) {
            val faulting = ball.lastHitterSide ?: CourtSide.LOWER
            return SimTickOutput(stepEvents, pointResolution = applyFault(faulting, FaultReason.INTO_NET))
        }

        if (stepEvents.bounced) {
            if (ball.bouncesOnCurrentSide == 1) {
                val bounceFault = rules.validateFirstBounce(
                    stepEvents.bounceX,
                    stepEvents.bounceY,
                    ball.lastHitterSide,
                )
                if (bounceFault != null) {
                    val faulting = ball.lastHitterSide ?: CourtSide.LOWER
                    return SimTickOutput(stepEvents, pointResolution = applyFault(faulting, bounceFault))
                }
            }
            if (ball.bouncesOnCurrentSide >= 2) {
                val receiver = if (ball.lastHitterSide == CourtSide.LOWER) CourtSide.UPPER else CourtSide.LOWER
                return SimTickOutput(stepEvents, pointResolution = applyFault(receiver, FaultReason.DOUBLE_BOUNCE))
            }
        }

        if (
            ball.x < -2f ||
            ball.x > CourtSpec.WORLD_WIDTH_FT + 2f ||
            ball.y < -3f ||
            ball.y > CourtSpec.WORLD_HEIGHT_FT + 3f
        ) {
            if (ball.crossedNetSinceHit && ball.bouncesOnCurrentSide >= 1) {
                val receiver = if (ball.lastHitterSide == CourtSide.LOWER) CourtSide.UPPER else CourtSide.LOWER
                return SimTickOutput(stepEvents, pointResolution = applyFault(receiver, FaultReason.DOUBLE_BOUNCE))
            }
            val faulting = ball.lastHitterSide ?: CourtSide.LOWER
            val fault = if (rules.rallyShotCount == 1) FaultReason.SERVE_OUT_OF_BOX else FaultReason.OUT_OF_BOUNDS
            return SimTickOutput(stepEvents, pointResolution = applyFault(faulting, fault))
        }

        return SimTickOutput(stepEvents, hitFeedback = botHit ?: autoHit)
    }

    private fun updateBotAI(dt: Float) {
        if (ball.lastHitterSide == CourtSide.LOWER && ball.vy > 0f) {
            val timeToBotY = if (ball.vy > 0.5f) {
                min(1.4f, max(0f, (bot.y - ball.y) / ball.vy))
            } else {
                0.4f
            }
            val predX = max(
                CourtSpec.COURT_LEFT_X + 1f,
                min(CourtSpec.COURT_RIGHT_X - 1f, ball.x + ball.vx * timeToBotY),
            )
            val minBotY = if (rules.rallyShotCount == 1) CourtSpec.COURT_TOP_Y - 1.5f else CourtSpec.UPPER_NVZ_Y + 0.8f
            val predY = max(
                minBotY,
                min(CourtSpec.COURT_TOP_Y + 2.0f, ball.y + ball.vy * min(timeToBotY, 0.45f)),
            )
            bot.targetX = predX
            bot.targetY = predY
        } else {
            bot.targetX = CourtSpec.COURT_CENTER_X
            bot.targetY = CourtSpec.UPPER_NVZ_Y + 2.5f
        }
        val profile = BOT_PROFILES.getValue(difficulty)
        moveTowardTarget(bot, profile.speedFtS, dt)
    }

    private fun tryBotHit(): HitFeedback? {
        if (botCooldownSec > 0f || !ball.active) return null
        if (ball.lastHitterSide != CourtSide.LOWER || !ball.crossedNetSinceHit) return null
        if (ball.y < CourtSpec.NET_Y + 0.8f) return null

        if ((rules.rallyShotCount == 1 || rules.rallyShotCount == 2) && ball.bouncesOnCurrentSide == 0) {
            return null
        }
        if (ball.bouncesOnCurrentSide == 0 && CourtSpec.isInKitchen(bot.x, bot.y, CourtSide.UPPER)) {
            return null
        }

        val profile = BOT_PROFILES.getValue(difficulty)
        val dist = hypot(ball.x - bot.x, ball.y - bot.y)
        if (dist > HIT_REACH_FT * profile.reachScale || ball.z > MAX_HIT_HEIGHT_FT) {
            return null
        }

        val fault = rules.validatePlayerHit(CourtSide.UPPER, bot.x, bot.y, ball.bouncesOnCurrentSide)
        if (fault != null) {
            pendingResolution = applyFault(CourtSide.UPPER, fault)
            return null
        }

        val shot = computeBotReturnShot(
            fromX = ball.x,
            fromY = ball.y,
            fromZ = ball.z,
            hitterY = bot.y,
            targetSide = CourtSide.LOWER,
            opponentXs = listOf(player.x),
            difficulty = difficulty,
            aimAwayChance = profile.aimAwayChance,
            sidelineMarginFt = profile.sidelineMarginFt,
        )

        ball.vx = shot.vx
        ball.vy = shot.vy
        ball.vz = shot.vz
        ball.lastHitterSide = CourtSide.UPPER
        ball.crossedNetSinceHit = false
        ball.bouncesOnCurrentSide = 0

        bot.swingTimer = 0.24f
        bot.lastSwingType = shot.shotType
        botCooldownSec = 0.35f
        bestRally = max(bestRally, rules.rallyShotCount)
        rallyHits = if (rules.rallyShotCount >= 3) rules.rallyShotCount else 0
        lastCallout = when (shot.shotType) {
            ShotType.DINK -> "Bot Dink"
            ShotType.LOB -> "Bot Lob"
            else -> "Bot Drive"
        }

        return HitFeedback(
            hitterSide = CourtSide.UPPER,
            shotType = shot.shotType,
            timingAccuracy = 0.88f,
            hitX = ball.x,
            hitY = ball.y,
        )
    }

    private fun applyFault(faultingSide: CourtSide, reason: FaultReason): PointResolution? {
        if (rallyPhase == RallyPhase.POINT_PAUSE || rallyPhase == RallyPhase.MATCH_OVER) {
            return null
        }
        val resolution = rules.resolveFault(faultingSide, reason)
        lastFaultReason = reason
        ball.active = false
        pauseTimerSec = POINT_PAUSE_SEC

        if (resolution.matchWinner != null) {
            val gameWinner = resolution.matchWinner
            if (gameWinner == CourtSide.LOWER) gamesLower += 1 else gamesUpper += 1
            val gameScore = "${rules.scoreLower}-${rules.scoreUpper}"
            gameScores.add(gameScore)
            val gamesNeeded = ceil(bestOf / 2.0).toInt()
            if (gamesLower < gamesNeeded && gamesUpper < gamesNeeded) {
                val winnerName = if (gameWinner == CourtSide.LOWER) {
                    player.characterId.displayName
                } else {
                    bot.characterId.displayName
                }
                val finishedGame = gameNumber
                gameNumber += 1
                gameFirstServer = if (gameFirstServer == CourtSide.LOWER) CourtSide.UPPER else CourtSide.LOWER
                rules = RulesEngine(rules.scoringMode, MatchMode.SINGLES, pointsToWin, 2, gameFirstServer)
                pauseTimerSec = GAME_BREAK_SEC
                rallyPhase = RallyPhase.POINT_PAUSE
                lastCallout = "Game $finishedGame to $winnerName $gameScore · Games $gamesLower-$gamesUpper"
                return resolution.copy(matchWinner = null, calloutText = lastCallout)
            }
        }

        lastCallout = resolution.calloutText
        rallyPhase = if (resolution.matchWinner != null) RallyPhase.MATCH_OVER else RallyPhase.POINT_PAUSE
        return resolution
    }

    private fun moveTowardTarget(entity: PlayerEntity, maxSpeed: Float, dt: Float) {
        val dx = entity.targetX - entity.x
        val dy = entity.targetY - entity.y
        val dist = hypot(dx, dy)
        if (dist < 0.04f) {
            entity.x = entity.targetX
            entity.y = entity.targetY
            return
        }
        val step = min(dist, maxSpeed * dt)
        entity.x += (dx / dist) * step
        entity.y += (dy / dist) * step
    }
}
