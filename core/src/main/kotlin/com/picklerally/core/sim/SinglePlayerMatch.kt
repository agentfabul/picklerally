package com.picklerally.core.sim

import com.picklerally.core.characters.CharacterId
import com.picklerally.core.court.CourtSide
import com.picklerally.core.court.CourtSpec
import com.picklerally.core.court.ServiceBoxSide
import com.picklerally.core.physics.BallPhysics
import com.picklerally.core.physics.BallState
import com.picklerally.core.physics.NetCrossOutcome
import com.picklerally.core.physics.ShotType
import com.picklerally.core.physics.StepEvents
import com.picklerally.core.physics.SwipeVector
import com.picklerally.core.rules.FaultReason
import com.picklerally.core.rules.MatchMode
import com.picklerally.core.rules.PointResolution
import com.picklerally.core.rules.RulesEngine
import com.picklerally.core.rules.ScoringMode
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class RallyPhase {
    WAITING_SERVE,
    RALLY_LIVE,
    POINT_PAUSE,
    MATCH_OVER
}

data class PlayerEntity(
    val side: CourtSide,
    var characterId: CharacterId,
    var x: Float,
    var y: Float,
    var targetX: Float,
    var targetY: Float,
    var swingTimer: Float = 0f,
    var lastSwingType: ShotType = ShotType.DRIVE
)

data class HitFeedback(
    val hitterSide: CourtSide,
    val shotType: ShotType,
    val timingAccuracy: Float,
    val hitX: Float,
    val hitY: Float
)

data class SimTickOutput(
    val stepEvents: StepEvents = StepEvents(),
    val hitFeedback: HitFeedback? = null,
    val pointResolution: PointResolution? = null
)

/**
 * Pure Kotlin Phase 3 single-player match simulation integrating RulesEngine
 * (underhand diagonal serve, two-bounce rule, kitchen NVZ rule, and Rally/Side-Out scoring to 11).
 */
class SinglePlayerMatch(
    playerChar: CharacterId = CharacterId.RIO,
    botChar: CharacterId = CharacterId.LOLA,
    scoringMode: ScoringMode = ScoringMode.RALLY
) {
    val rules = RulesEngine(scoringMode = scoringMode, matchMode = MatchMode.SINGLES)

    val player = PlayerEntity(
        side = CourtSide.LOWER,
        characterId = playerChar,
        x = CourtSpec.COURT_CENTER_X + 4.5f,
        y = CourtSpec.COURT_BOTTOM_Y - 1.1f,
        targetX = CourtSpec.COURT_CENTER_X + 4.5f,
        targetY = CourtSpec.COURT_BOTTOM_Y - 1.1f
    )

    val bot = PlayerEntity(
        side = CourtSide.UPPER,
        characterId = botChar,
        x = CourtSpec.COURT_CENTER_X - 4.5f,
        y = CourtSpec.COURT_TOP_Y + 1.1f,
        targetX = CourtSpec.COURT_CENTER_X - 4.5f,
        targetY = CourtSpec.COURT_TOP_Y + 1.1f
    )

    val ball = BallState()

    var rallyPhase: RallyPhase = RallyPhase.WAITING_SERVE
        private set
    var rallyHits: Int = 0
        private set
    var bestRally: Int = 0
        private set
    var lastCallout: String = "Serve from right box"
        private set
    var lastShotLabel: String = ""
        private set
    var lastFaultReason: FaultReason? = null
        private set

    var botServeDelaySec: Float = 0f
        private set

    private var pauseTimerSec = 0f
    private var botCooldownSec = 0f
    private var accumulator = 0f
    private val rng = Random(42)

    companion object {
        const val PLAYER_SPEED_FT_S = 18.5f
        const val BOT_SPEED_FT_S = 14.2f
        const val HIT_REACH_FT = 3.8f
        const val MAX_HIT_HEIGHT_FT = 6.0f
        const val POINT_PAUSE_SEC = 1.5f
        const val BOT_SERVE_DELAY_SEC = 2.6f
    }

    init {
        resetMatch(scoringMode)
    }

    fun resetMatch(newScoringMode: ScoringMode = rules.scoringMode) {
        if (newScoringMode != rules.scoringMode) {
            // Recreating match cleanly is recommended when changing scoring mode,
            // but reset keeps current RulesEngine settings.
        }
        rules.reset(CourtSide.LOWER)
        bestRally = 0
        lastShotLabel = ""
        lastFaultReason = null
        setupPointForServe()
    }

    fun setupPointForServe() {
        if (rules.isMatchOver) {
            rallyPhase = RallyPhase.MATCH_OVER
            lastCallout = if (rules.matchWinner == CourtSide.LOWER) "Rio Wins!" else "Lola Wins!"
            return
        }

        val assign = rules.currentServeAssignment()
        rallyPhase = RallyPhase.WAITING_SERVE
        rallyHits = 0
        lastFaultReason = null

        val lowerServerX =
            if (assign.servingSide == CourtSide.LOWER && assign.serverBox == ServiceBoxSide.LEFT) {
                CourtSpec.COURT_CENTER_X - 4.5f
            } else if (assign.servingSide == CourtSide.LOWER) {
                CourtSpec.COURT_CENTER_X + 4.5f
            } else if (assign.targetBox == ServiceBoxSide.LEFT) {
                CourtSpec.COURT_CENTER_X - 4.5f
            } else {
                CourtSpec.COURT_CENTER_X + 4.5f
            }

        val upperX =
            if (assign.servingSide == CourtSide.UPPER && assign.serverBox == ServiceBoxSide.LEFT) {
                CourtSpec.COURT_CENTER_X - 4.5f
            } else if (assign.servingSide == CourtSide.UPPER) {
                CourtSpec.COURT_CENTER_X + 4.5f
            } else if (assign.targetBox == ServiceBoxSide.LEFT) {
                CourtSpec.COURT_CENTER_X - 4.5f
            } else {
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
            lastCallout = "Your serve (${rules.formattedScoreCall()})"
        } else {
            ball.x = bot.x + 1.0f
            ball.y = bot.y - 1.3f
            botServeDelaySec = BOT_SERVE_DELAY_SEC
            lastCallout = "Lola serving (${rules.formattedScoreCall()})"
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
        val maxY = if (rallyPhase == RallyPhase.WAITING_SERVE && rules.servingSide == CourtSide.LOWER) {
            CourtSpec.COURT_BOTTOM_Y - 0.3f
        } else {
            CourtSpec.NET_Y - 0.6f
        }
        player.targetX = worldX.coerceIn(1.5f, CourtSpec.WORLD_WIDTH_FT - 1.5f)
        player.targetY = worldY.coerceIn(1.5f, maxY)
    }

    fun canPlayerReachBall(): Boolean {
        if (rallyPhase != RallyPhase.RALLY_LIVE || !ball.active) return false
        if (ball.lastHitterSide == CourtSide.LOWER) return false
        val dist = hypot(ball.x - player.x, ball.y - player.y)
        return dist <= HIT_REACH_FT && ball.z <= MAX_HIT_HEIGHT_FT && ball.y <= CourtSpec.NET_Y + 0.8f
    }

    fun launchServe(swipe: SwipeVector? = null): HitFeedback? {
        if (rallyPhase != RallyPhase.WAITING_SERVE) return null
        val assign = rules.currentServeAssignment()
        val server = if (assign.servingSide == CourtSide.LOWER) player else bot

        val footFault = rules.beginServe(server.x, server.y)
        if (footFault != null) {
            applyFault(assign.servingSide, footFault)
            return null
        }

        val targetX = if (assign.targetBox == ServiceBoxSide.LEFT) {
            CourtSpec.COURT_LEFT_X + 5.0f
        } else {
            CourtSpec.COURT_RIGHT_X - 5.0f
        }
        val targetY = if (assign.targetCourtSide == CourtSide.UPPER) {
            CourtSpec.UPPER_NVZ_Y + 7.8f
        } else {
            CourtSpec.LOWER_NVZ_Y - 7.8f
        }

        val tof = 0.96f
        val invIntegral = 1f / BallPhysics.horizontalDragIntegral(tof)
        val aimOffset = if (swipe != null) (swipe.dxFt * 0.42f).coerceIn(-2.6f, 2.6f) else 0f
        val vx = ((targetX + aimOffset) - ball.x) * invIntegral
        val vy = (targetY - ball.y) * invIntegral
        val vz = (-0.5f * BallPhysics.GRAVITY_FT_S2 * tof) - (ball.z / tof)

        ball.vx = vx
        ball.vy = vy
        ball.vz = vz
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

        return HitFeedback(assign.servingSide, ShotType.SERVE, 0.95f, ball.x, ball.y)
    }

    fun triggerReadyBotServe(): HitFeedback? {
        if (rallyPhase != RallyPhase.WAITING_SERVE || rules.servingSide != CourtSide.UPPER) return null
        botServeDelaySec = 0f
        return launchServe()
    }

    fun attemptPlayerSwipeHit(swipe: SwipeVector): HitFeedback? {
        if (rallyPhase == RallyPhase.WAITING_SERVE &&
            rules.servingSide == CourtSide.LOWER &&
            swipe.dyFt > 1.2f
        ) {
            return launchServe(swipe)
        }
        if (!canPlayerReachBall()) return null

        // Enforce two-bounce & kitchen volley rules BEFORE launching ball
        val ruleFault = rules.validatePlayerHit(
            hitterSide = CourtSide.LOWER,
            hitterX = player.x,
            hitterY = player.y,
            bouncesOnCurrentSide = ball.bouncesOnCurrentSide
        )
        if (ruleFault != null) {
            applyFault(CourtSide.LOWER, ruleFault)
            return null
        }

        val shotType = BallPhysics.classifySwipe(swipe)
        val dist = hypot(ball.x - player.x, ball.y - player.y)
        val distScore = 1f - (dist / HIT_REACH_FT).coerceIn(0f, 1f) * 0.55f
        val heightSweet = 1f - (abs(ball.z - 2.3f) / 3.8f).coerceIn(0f, 1f) * 0.45f
        val timingAccuracy = (distScore * 0.6f + heightSweet * 0.4f).coerceIn(0.25f, 1f)

        val scatterSeed = (rng.nextFloat() * 2f - 1f)
        val (vx, vy, vz) = BallPhysics.computeShotVelocity(
            fromX = ball.x,
            fromY = ball.y,
            swipe = swipe,
            shotType = shotType,
            targetSide = CourtSide.UPPER,
            timingAccuracy = timingAccuracy,
            scatterSeed = scatterSeed,
            fromZ = ball.z
        )

        ball.vx = vx
        ball.vy = vy
        ball.vz = vz
        ball.lastHitterSide = CourtSide.LOWER
        ball.crossedNetSinceHit = false
        ball.bouncesOnCurrentSide = 0

        player.swingTimer = 0.22f
        player.lastSwingType = shotType
        bestRally = max(bestRally, rules.rallyShotCount)
        rallyHits = if (rules.rallyShotCount >= 3) rules.rallyShotCount else 0
        lastShotLabel = "${shotType.label} · ${(timingAccuracy * 100).toInt()}%"
        lastCallout = when (shotType) {
            ShotType.DINK -> "Soft Dink!"
            ShotType.DRIVE -> "Power Drive!"
            ShotType.LOB -> "High Lob!"
            ShotType.SERVE -> "Serve!"
        }

        return HitFeedback(CourtSide.LOWER, shotType, timingAccuracy, ball.x, ball.y)
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
        moveTowardTarget(player, PLAYER_SPEED_FT_S, dt)
        if (player.swingTimer > 0f) player.swingTimer = max(0f, player.swingTimer - dt)
        if (bot.swingTimer > 0f) bot.swingTimer = max(0f, bot.swingTimer - dt)
        if (botCooldownSec > 0f) botCooldownSec = max(0f, botCooldownSec - dt)

        if (rallyPhase == RallyPhase.MATCH_OVER) {
            return SimTickOutput()
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
                    val serveHit = launchServe()
                    return SimTickOutput(hitFeedback = serveHit)
                }
            }
            return SimTickOutput()
        }

        if (rallyPhase == RallyPhase.POINT_PAUSE) {
            pauseTimerSec -= dt
            if (pauseTimerSec <= 0f) {
                setupPointForServe()
            }
            return SimTickOutput()
        }

        // Bot positioning & hit check
        updateBotAI(dt)
        val botHit = tryBotHit()

        // Step 3D ball physics
        val stepEvents = BallPhysics.step(ball, dt)

        // Net fault
        if (stepEvents.netOutcome == NetCrossOutcome.HIT_NET) {
            val faulting = ball.lastHitterSide ?: CourtSide.LOWER
            val res = applyFault(faulting, FaultReason.INTO_NET)
            return SimTickOutput(stepEvents = stepEvents, pointResolution = res)
        }

        if (stepEvents.bounced) {
            if (ball.bouncesOnCurrentSide == 1) {
                val bounceFault = rules.validateFirstBounce(stepEvents.bounceX, stepEvents.bounceY, ball.lastHitterSide)
                if (bounceFault != null) {
                    val faulting = ball.lastHitterSide ?: CourtSide.LOWER
                    val res = applyFault(faulting, bounceFault)
                    return SimTickOutput(stepEvents = stepEvents, pointResolution = res)
                }
            }
            if (ball.bouncesOnCurrentSide >= 2) {
                val receivingSide = if (ball.lastHitterSide == CourtSide.LOWER) CourtSide.UPPER else CourtSide.LOWER
                val res = applyFault(receivingSide, FaultReason.DOUBLE_BOUNCE)
                return SimTickOutput(stepEvents = stepEvents, pointResolution = res)
            }
        }

        if (ball.x < -2f || ball.x > CourtSpec.WORLD_WIDTH_FT + 2f ||
            ball.y < -3f || ball.y > CourtSpec.WORLD_HEIGHT_FT + 3f
        ) {
            if (ball.crossedNetSinceHit && ball.bouncesOnCurrentSide >= 1) {
                val receivingSide = if (ball.lastHitterSide == CourtSide.LOWER) CourtSide.UPPER else CourtSide.LOWER
                val res = applyFault(receivingSide, FaultReason.DOUBLE_BOUNCE)
                return SimTickOutput(stepEvents = stepEvents, pointResolution = res)
            }
            val faulting = ball.lastHitterSide ?: CourtSide.LOWER
            val fault = if (rules.rallyShotCount == 1) FaultReason.SERVE_OUT_OF_BOX else FaultReason.OUT_OF_BOUNDS
            val res = applyFault(faulting, fault)
            return SimTickOutput(stepEvents = stepEvents, pointResolution = res)
        }

        return SimTickOutput(stepEvents = stepEvents, hitFeedback = botHit)
    }

    private fun updateBotAI(dt: numberFromFloat) {
        if (ball.lastHitterSide == CourtSide.LOWER && ball.vy > 0f) {
            val timeToBotY = if (ball.vy > 0.5f) ((bot.y - ball.y) / ball.vy).coerceIn(0f, 1.4f) else 0.4f
            val predX = (ball.x + ball.vx * timeToBotY).coerceIn(CourtSpec.COURT_LEFT_X + 1f, CourtSpec.COURT_RIGHT_X - 1f)
            // On shot 1 (serve return), bot stays behind baseline until bounce; otherwise covers court cleanly outside kitchen
            val minBotY = if (rules.rallyShotCount == 1) CourtSpec.COURT_TOP_Y - 1.5f else CourtSpec.UPPER_NVZ_Y + 0.8f
            val predY = (ball.y + ball.vy * min(timeToBotY, 0.45f))
                .coerceIn(minBotY, CourtSpec.COURT_TOP_Y + 2.0f)
            bot.targetX = predX
            bot.targetY = predY
        } else {
            bot.targetX = CourtSpec.COURT_CENTER_X
            bot.targetY = CourtSpec.UPPER_NVZ_Y + 2.5f
        }
        moveTowardTarget(bot, BOT_SPEED_FT_S, dt)
    }

    private fun tryBotHit(): HitFeedback? {
        if (botCooldownSec > 0f || !ball.active) return null
        if (ball.lastHitterSide != CourtSide.LOWER || !ball.crossedNetSinceHit) return null
        if (ball.y < CourtSpec.NET_Y + 0.8f) return null

        // Bot obeys two-bounce rule on return of serve
        if (rules.rallyShotCount == 1 && ball.bouncesOnCurrentSide == 0) return null
        // Bot steps back rather than volleying from inside the kitchen
        if (ball.bouncesOnCurrentSide == 0 && CourtSpec.isInKitchen(bot.x, bot.y, CourtSide.UPPER)) return null

        val dist = hypot(ball.x - bot.x, ball.y - bot.y)
        if (dist > HIT_REACH_FT * 0.95f || ball.z > MAX_HIT_HEIGHT_FT) return null

        val fault = rules.validatePlayerHit(
            hitterSide = CourtSide.UPPER,
            hitterX = bot.x,
            hitterY = bot.y,
            bouncesOnCurrentSide = ball.bouncesOnCurrentSide
        )
        if (fault != null) {
            applyFault(CourtSide.UPPER, fault)
            return null
        }

        val botShot = BallPhysics.computeBotReturnShot(
            fromX = ball.x,
            fromY = ball.y,
            fromZ = ball.z,
            hitterY = bot.y,
            targetSide = CourtSide.LOWER,
            opponentXs = listOf(player.x),
            difficulty = com.picklerally.core.physics.BotDifficulty.NORMAL,
            rng = rng
        )
        val shotType = botShot.shotType

        ball.vx = botShot.vx
        ball.vy = botShot.vy
        ball.vz = botShot.vz
        ball.lastHitterSide = CourtSide.UPPER
        ball.crossedNetSinceHit = false
        ball.bouncesOnCurrentSide = 0

        bot.swingTimer = 0.24f
        bot.lastSwingType = shotType
        botCooldownSec = 0.35f
        bestRally = max(bestRally, rules.rallyShotCount)
        rallyHits = if (rules.rallyShotCount >= 3) rules.rallyShotCount else 0
        lastCallout = "Bot ${shotType.label}"

        return HitFeedback(CourtSide.UPPER, shotType, 0.88f, ball.x, ball.y)
    }

    private fun applyFault(faultingSide: CourtSide, reason: FaultReason): PointResolution {
        val resolution = rules.resolveFault(faultingSide, reason)
        lastFaultReason = reason
        lastCallout = resolution.calloutText
        rallyPhase = if (resolution.matchWinner != null) RallyPhase.MATCH_OVER else RallyPhase.POINT_PAUSE
        pauseTimerSec = POINT_PAUSE_SEC
        ball.active = false
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

private typealias numberFromFloat = Float
