package com.picklerally.core.sim

import com.picklerally.core.characters.CharacterId
import com.picklerally.core.court.CourtSide
import com.picklerally.core.court.CourtSpec
import com.picklerally.core.court.ServiceBoxSide
import com.picklerally.core.physics.BallPhysics
import com.picklerally.core.physics.BallState
import com.picklerally.core.physics.NetCrossOutcome
import com.picklerally.core.physics.ShotType
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

/**
 * Pure Kotlin 2v2 Doubles simulation in core/.
 * - Enforces official doubles serving rotation ("0 - 0 - 2" opening turn, #1/#2 calls,
 *   side switching via RulesEngine.lowerRightPlayerIndex / upperRightPlayerIndex).
 * - Lower player index 0 is human-controlled; index 1 is an AI partner.
 * - Upper team consists of two AI opponents covering left and right halves.
 */
class DoublesMatch(
    playerCharacter: CharacterId = CharacterId.RIO,
    partnerCharacter: CharacterId = CharacterId.DILL,
    upperRightChar: CharacterId = CharacterId.LOLA,
    upperLeftChar: CharacterId = CharacterId.BERT,
    scoringMode: ScoringMode = ScoringMode.SIDE_OUT
) {
    val rules = RulesEngine(
        scoringMode = scoringMode,
        matchMode = MatchMode.DOUBLES,
        pointsToWin = 11,
        winByMargin = 2,
        initialServer = CourtSide.LOWER
    )

    val lowerTeam: List<PlayerEntity> = listOf(
        PlayerEntity(
            side = CourtSide.LOWER,
            characterId = playerCharacter,
            x = CourtSpec.COURT_CENTER_X + 4.5f,
            y = CourtSpec.COURT_BOTTOM_Y - 1.1f,
            targetX = CourtSpec.COURT_CENTER_X + 4.5f,
            targetY = CourtSpec.COURT_BOTTOM_Y - 1.1f
        ),
        PlayerEntity(
            side = CourtSide.LOWER,
            characterId = partnerCharacter,
            x = CourtSpec.COURT_CENTER_X - 4.5f,
            y = CourtSpec.LOWER_NVZ_Y - 0.8f,
            targetX = CourtSpec.COURT_CENTER_X - 4.5f,
            targetY = CourtSpec.LOWER_NVZ_Y - 0.8f
        )
    )

    val upperTeam: List<PlayerEntity> = listOf(
        PlayerEntity(
            side = CourtSide.UPPER,
            characterId = upperRightChar,
            x = CourtSpec.COURT_CENTER_X - 4.5f,
            y = CourtSpec.COURT_TOP_Y + 1.1f,
            targetX = CourtSpec.COURT_CENTER_X - 4.5f,
            targetY = CourtSpec.COURT_TOP_Y + 1.1f
        ),
        PlayerEntity(
            side = CourtSide.UPPER,
            characterId = upperLeftChar,
            x = CourtSpec.COURT_CENTER_X + 4.5f,
            y = CourtSpec.UPPER_NVZ_Y + 0.8f,
            targetX = CourtSpec.COURT_CENTER_X + 4.5f,
            targetY = CourtSpec.UPPER_NVZ_Y + 0.8f
        )
    )

    val player: PlayerEntity get() = lowerTeam[0]
    val partner: PlayerEntity get() = lowerTeam[1]
    val ball = BallState()

    var rallyPhase: RallyPhase = RallyPhase.WAITING_SERVE
        private set
    var rallyHits: Int = 0
        private set
    var bestRally: Int = 0
        private set
    var lastCallout: String = ""
        private set
    var lastShotLabel: String = ""
        private set

    var aiServeTimerSec: Float = 0f
        private set

    private var accumulator: Float = 0f
    private var pauseTimerSec: Float = 0f
    private var partnerHitCooldownSec: Float = 0f
    private var upperHitCooldownSec: Float = 0f
    private val rng = Random(22)

    companion object {
        const val PLAYER_SPEED_FT_S = 18.0f
        const val PARTNER_SPEED_FT_S = 15.2f
        const val BOT_SPEED_FT_S = 15.0f
        const val HIT_REACH_FT = 3.9f
        const val MAX_HIT_HEIGHT_FT = 7.4f
        const val POINT_PAUSE_SEC = 1.5f
        const val AI_SERVE_DELAY_SEC = 2.6f
    }

    init {
        setupPointForServe()
    }

    fun reset() {
        rules.reset(CourtSide.LOWER)
        rallyHits = 0
        bestRally = 0
        lastShotLabel = ""
        setupPointForServe()
    }

    /**
     * Returns index (0 or 1) of the active server in [lowerTeam] or [upperTeam].
     */
    fun activeServerIndex(): Int = rules.currentServeAssignment().serverIndexInTeam

    fun activeServerSide(): CourtSide = rules.servingSide

    fun setupPointForServe() {
        if (rules.isMatchOver) {
            rallyPhase = RallyPhase.MATCH_OVER
            lastCallout = if (rules.matchWinner == CourtSide.LOWER) "Your Team Wins!" else "Opponents Win!"
            return
        }

        val assign = rules.currentServeAssignment()
        rallyPhase = RallyPhase.WAITING_SERVE
        rallyHits = 0
        pauseTimerSec = 0f
        aiServeTimerSec = if (assign.servingSide == CourtSide.LOWER && assign.serverIndexInTeam == 0) 0f else AI_SERVE_DELAY_SEC
        partnerHitCooldownSec = 0f
        upperHitCooldownSec = 0f

        val lowerRightIdx = rules.lowerRightPlayerIndex
        val lowerLeftIdx = 1 - lowerRightIdx
        val upperRightIdx = rules.upperRightPlayerIndex
        val upperLeftIdx = 1 - upperRightIdx

        val lowerRightX = CourtSpec.COURT_CENTER_X + 4.8f
        val lowerLeftX = CourtSpec.COURT_CENTER_X - 4.8f
        val upperRightX = CourtSpec.COURT_CENTER_X - 4.8f // Upper player's right is world LEFT
        val upperLeftX = CourtSpec.COURT_CENTER_X + 4.8f

        val receiverSide = if (assign.servingSide == CourtSide.LOWER) CourtSide.UPPER else CourtSide.LOWER

        // Place LOWER team according to right/left indices and server/receiver roles
        positionPlayer(
            lowerTeam[lowerRightIdx],
            lowerRightX,
            yForDoublesRole(
                side = CourtSide.LOWER,
                isServingTeam = assign.servingSide == CourtSide.LOWER,
                isServer = assign.servingSide == CourtSide.LOWER && assign.serverIndexInTeam == lowerRightIdx,
                isTargetBox = receiverSide == CourtSide.LOWER && assign.targetBox == ServiceBoxSide.RIGHT
            )
        )
        positionPlayer(
            lowerTeam[lowerLeftIdx],
            lowerLeftX,
            yForDoublesRole(
                side = CourtSide.LOWER,
                isServingTeam = assign.servingSide == CourtSide.LOWER,
                isServer = assign.servingSide == CourtSide.LOWER && assign.serverIndexInTeam == lowerLeftIdx,
                isTargetBox = receiverSide == CourtSide.LOWER && assign.targetBox == ServiceBoxSide.LEFT
            )
        )

        // Place UPPER team according to right/left indices and server/receiver roles
        positionPlayer(
            upperTeam[upperRightIdx],
            upperRightX,
            yForDoublesRole(
                side = CourtSide.UPPER,
                isServingTeam = assign.servingSide == CourtSide.UPPER,
                isServer = assign.servingSide == CourtSide.UPPER && assign.serverIndexInTeam == upperRightIdx,
                isTargetBox = receiverSide == CourtSide.UPPER && assign.targetBox == ServiceBoxSide.LEFT
            )
        )
        positionPlayer(
            upperTeam[upperLeftIdx],
            upperLeftX,
            yForDoublesRole(
                side = CourtSide.UPPER,
                isServingTeam = assign.servingSide == CourtSide.UPPER,
                isServer = assign.servingSide == CourtSide.UPPER && assign.serverIndexInTeam == upperLeftIdx,
                isTargetBox = receiverSide == CourtSide.UPPER && assign.targetBox == ServiceBoxSide.RIGHT
            )
        )

        val server = if (assign.servingSide == CourtSide.LOWER) {
            lowerTeam[assign.serverIndexInTeam]
        } else {
            upperTeam[assign.serverIndexInTeam]
        }

        ball.x = server.x + if (assign.servingSide == CourtSide.LOWER) -0.9f else 0.9f
        ball.y = server.y + if (assign.servingSide == CourtSide.LOWER) 1.3f else -1.3f
        ball.z = 2.4f
        ball.vx = 0f
        ball.vy = 0f
        ball.vz = 0f
        ball.active = false
        ball.bouncesOnCurrentSide = 0
        ball.lastHitterSide = null
        ball.crossedNetSinceHit = false

        lastCallout = when {
            assign.servingSide == CourtSide.LOWER && assign.serverIndexInTeam == 0 ->
                "Your serve (${rules.formattedScoreCall()})"
            assign.servingSide == CourtSide.LOWER ->
                "Partner serve (${rules.formattedScoreCall()})"
            else ->
                "Opponent serve (${rules.formattedScoreCall()})"
        }
    }

    private fun yForDoublesRole(
        side: CourtSide,
        isServingTeam: Boolean,
        isServer: Boolean,
        isTargetBox: Boolean
    ): Float = if (side == CourtSide.LOWER) {
        when {
            isServingTeam && isServer -> CourtSpec.COURT_BOTTOM_Y - 1.1f
            isServingTeam -> CourtSpec.COURT_BOTTOM_Y - 1.4f
            isTargetBox -> CourtSpec.COURT_BOTTOM_Y - 1.1f
            else -> CourtSpec.LOWER_NVZ_Y - 1.2f
        }
    } else {
        when {
            isServingTeam && isServer -> CourtSpec.COURT_TOP_Y + 1.1f
            isServingTeam -> CourtSpec.COURT_TOP_Y + 1.4f
            isTargetBox -> CourtSpec.COURT_TOP_Y + 1.1f
            else -> CourtSpec.UPPER_NVZ_Y + 1.2f
        }
    }

    private fun positionPlayer(entity: PlayerEntity, x: Float, y: Float) {
        entity.x = x
        entity.y = y
        entity.targetX = x
        entity.targetY = y
        entity.swingTimer = 0f
    }

    fun setPlayerMoveTarget(worldX: Float, worldY: Float) {
        val maxY = if (rallyPhase == RallyPhase.WAITING_SERVE &&
            rules.servingSide == CourtSide.LOWER &&
            activeServerIndex() == 0
        ) {
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
        if (ball.y > CourtSpec.NET_Y + 1.2f) return false
        return hypot(ball.x - player.x, ball.y - player.y) <= HIT_REACH_FT &&
            ball.z <= MAX_HIT_HEIGHT_FT
    }

    fun launchHumanServe(aimOffsetX: Float = 0f): FaultReason? {
        if (rallyPhase != RallyPhase.WAITING_SERVE) return null
        if (rules.servingSide != CourtSide.LOWER || activeServerIndex() != 0) return null
        return executeServe(CourtSide.LOWER, player, aimOffsetX)
    }

    fun attemptPlayerSwipeHit(
        swipe: SwipeVector,
        forcedShotType: ShotType? = null
    ): Boolean {
        if (rallyPhase == RallyPhase.WAITING_SERVE &&
            rules.servingSide == CourtSide.LOWER &&
            activeServerIndex() == 0 &&
            swipe.dyFt > 1.0f
        ) {
            return launchHumanServe(swipe.dxFt * 0.45f) == null
        }

        if (!canPlayerReachBall()) return false

        val fault = rules.validatePlayerHit(
            hitterSide = CourtSide.LOWER,
            hitterX = player.x,
            hitterY = player.y,
            bouncesOnCurrentSide = ball.bouncesOnCurrentSide
        )
        if (fault != null) {
            applyFault(CourtSide.LOWER, fault)
            return false
        }

        val shotType = forcedShotType ?: BallPhysics.classifySwipe(swipe)
        val (vx, vy, vz) = BallPhysics.computeShotVelocity(
            fromX = ball.x,
            fromY = ball.y,
            swipe = swipe,
            shotType = shotType,
            targetSide = CourtSide.UPPER,
            timingAccuracy = 0.88f,
            scatterSeed = rng.nextFloat() * 2f - 1f
        )

        ball.vx = vx
        ball.vy = vy
        ball.vz = vz
        ball.lastHitterSide = CourtSide.LOWER
        ball.crossedNetSinceHit = false
        ball.bouncesOnCurrentSide = 0
        player.swingTimer = 0.24f
        bestRally = max(bestRally, rules.rallyShotCount)
        rallyHits = if (rules.rallyShotCount >= 3) rules.rallyShotCount else 0
        lastShotLabel = shotType.label
        return true
    }

    fun update(rawDeltaSec: Float): PointResolution? {
        val clampedDt = min(rawDeltaSec, 0.05f)
        accumulator += clampedDt
        var resolved: PointResolution? = null

        while (accumulator >= CourtSpec.FIXED_TIMESTEP_SEC) {
            val pt = stepFixed(CourtSpec.FIXED_TIMESTEP_SEC)
            if (pt != null) resolved = pt
            accumulator -= CourtSpec.FIXED_TIMESTEP_SEC
        }
        return resolved
    }

    private fun stepFixed(dt: Float): PointResolution? {
        moveTowardTarget(player, PLAYER_SPEED_FT_S, dt)
        moveTowardTarget(partner, PARTNER_SPEED_FT_S, dt)
        moveTowardTarget(upperTeam[0], BOT_SPEED_FT_S, dt)
        moveTowardTarget(upperTeam[1], BOT_SPEED_FT_S, dt)

        for (p in lowerTeam) if (p.swingTimer > 0f) p.swingTimer = max(0f, p.swingTimer - dt)
        for (p in upperTeam) if (p.swingTimer > 0f) p.swingTimer = max(0f, p.swingTimer - dt)
        if (partnerHitCooldownSec > 0f) partnerHitCooldownSec = max(0f, partnerHitCooldownSec - dt)
        if (upperHitCooldownSec > 0f) upperHitCooldownSec = max(0f, upperHitCooldownSec - dt)

        if (rallyPhase == RallyPhase.MATCH_OVER) return null

        if (rallyPhase == RallyPhase.POINT_PAUSE) {
            pauseTimerSec -= dt
            if (pauseTimerSec <= 0f) {
                setupPointForServe()
            }
            return null
        }

        if (rallyPhase == RallyPhase.WAITING_SERVE) {
            val assign = rules.currentServeAssignment()
            val server = if (assign.servingSide == CourtSide.LOWER) {
                lowerTeam[assign.serverIndexInTeam]
            } else {
                upperTeam[assign.serverIndexInTeam]
            }
            ball.x = server.x + if (assign.servingSide == CourtSide.LOWER) -0.9f else 0.9f
            ball.y = server.y + if (assign.servingSide == CourtSide.LOWER) 1.3f else -1.3f
            ball.z = 2.4f

            // AI server (partner or upper bot) serves automatically after short pause
            if (!(assign.servingSide == CourtSide.LOWER && assign.serverIndexInTeam == 0)) {
                aiServeTimerSec -= dt
                if (aiServeTimerSec <= 0f) {
                    executeServe(assign.servingSide, server, 0f)
                }
            }
            return null
        }

        // RALLY_LIVE
        updateDoublesAiPositioning()

        val stepEvents = BallPhysics.step(ball, dt)

        if (stepEvents.netOutcome == NetCrossOutcome.HIT_NET) {
            val faultSide = ball.lastHitterSide ?: CourtSide.LOWER
            return applyFault(faultSide, FaultReason.INTO_NET)
        }

        if (stepEvents.bounced) {
            if (ball.bouncesOnCurrentSide == 1) {
                val bounceFault = rules.validateFirstBounce(stepEvents.bounceX, stepEvents.bounceY, ball.lastHitterSide)
                if (bounceFault != null) {
                    return applyFault(ball.lastHitterSide ?: CourtSide.LOWER, bounceFault)
                }
            }
            if (ball.bouncesOnCurrentSide >= 2) {
                val receivingSide = if (ball.lastHitterSide == CourtSide.LOWER) CourtSide.UPPER else CourtSide.LOWER
                return applyFault(receivingSide, FaultReason.DOUBLE_BOUNCE)
            }
        }

        if (ball.x < -2f || ball.x > CourtSpec.WORLD_WIDTH_FT + 2f ||
            ball.y < -3f || ball.y > CourtSpec.WORLD_HEIGHT_FT + 3f
        ) {
            if (ball.crossedNetSinceHit && ball.bouncesOnCurrentSide >= 1) {
                val receivingSide = if (ball.lastHitterSide == CourtSide.LOWER) CourtSide.UPPER else CourtSide.LOWER
                return applyFault(receivingSide, FaultReason.DOUBLE_BOUNCE)
            }
            val faultSide = ball.lastHitterSide ?: CourtSide.LOWER
            val fault = if (rules.rallyShotCount == 1) FaultReason.SERVE_OUT_OF_BOX else FaultReason.OUT_OF_BOUNDS
            return applyFault(faultSide, fault)
        }

        // AI partner return if human teammate isn't closer
        if (ball.lastHitterSide == CourtSide.UPPER &&
            ball.crossedNetSinceHit &&
            partnerHitCooldownSec <= 0f &&
            (rules.rallyShotCount > 2 || ball.bouncesOnCurrentSide >= 1)
        ) {
            val dPartner = hypot(ball.x - partner.x, ball.y - partner.y)
            val dHuman = hypot(ball.x - player.x, ball.y - player.y)
            if (dPartner <= HIT_REACH_FT && dPartner + 0.5f < dHuman && ball.z <= MAX_HIT_HEIGHT_FT) {
                val fault = rules.validatePlayerHit(CourtSide.LOWER, partner.x, partner.y, ball.bouncesOnCurrentSide)
                if (fault != null) {
                    return applyFault(CourtSide.LOWER, fault)
                }
                launchAiShot(partner, CourtSide.UPPER)
                partnerHitCooldownSec = 0.42f
                return null
            }
        }

        // Upper AI opponents return
        if (ball.lastHitterSide == CourtSide.LOWER &&
            ball.crossedNetSinceHit &&
            upperHitCooldownSec <= 0f &&
            (rules.rallyShotCount > 2 || ball.bouncesOnCurrentSide >= 1)
        ) {
            val responder = upperTeam.minByOrNull { hypot(ball.x - it.x, ball.y - it.y) }
            if (responder != null &&
                hypot(ball.x - responder.x, ball.y - responder.y) <= HIT_REACH_FT &&
                ball.z <= MAX_HIT_HEIGHT_FT
            ) {
                val fault = rules.validatePlayerHit(CourtSide.UPPER, responder.x, responder.y, ball.bouncesOnCurrentSide)
                if (fault != null) {
                    return applyFault(CourtSide.UPPER, fault)
                }
                launchAiShot(responder, CourtSide.LOWER)
                upperHitCooldownSec = 0.42f
                return null
            }
        }

        return null
    }

    private fun executeServe(serverSide: CourtSide, server: PlayerEntity, aimOffsetX: Float): FaultReason? {
        val assign = rules.currentServeAssignment()
        val footFault = rules.beginServe(server.x, server.y)
        if (footFault != null) {
            applyFault(serverSide, footFault)
            return footFault
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

        val tof = 0.98f
        val invIntegral = 1f / BallPhysics.horizontalDragIntegral(tof)
        val offset = aimOffsetX.coerceIn(-2.5f, 2.5f)
        ball.vx = ((targetX + offset) - ball.x) * invIntegral
        ball.vy = (targetY - ball.y) * invIntegral
        ball.vz = (-0.5f * BallPhysics.GRAVITY_FT_S2 * tof) - (ball.z / tof)
        ball.active = true
        ball.lastHitterSide = serverSide
        ball.crossedNetSinceHit = false
        ball.bouncesOnCurrentSide = 0

        server.swingTimer = 0.24f
        rallyPhase = RallyPhase.RALLY_LIVE
        aiServeTimerSec = 0f
        rallyHits = 0
        bestRally = max(bestRally, 1)
        lastShotLabel = "Serve"
        lastCallout = ""
        return null
    }

    private fun launchAiShot(hitter: PlayerEntity, targetSide: CourtSide) {
        val defenders = if (targetSide == CourtSide.UPPER) upperTeam else lowerTeam
        val shot = BallPhysics.computeBotReturnShot(
            fromX = ball.x,
            fromY = ball.y,
            fromZ = ball.z,
            hitterY = hitter.y,
            targetSide = targetSide,
            opponentXs = defenders.map { it.x },
            difficulty = com.picklerally.core.physics.BotDifficulty.NORMAL,
            rng = rng
        )
        val shotType = shot.shotType

        ball.vx = shot.vx
        ball.vy = shot.vy
        ball.vz = shot.vz
        ball.lastHitterSide = if (targetSide == CourtSide.UPPER) CourtSide.LOWER else CourtSide.UPPER
        ball.crossedNetSinceHit = false
        ball.bouncesOnCurrentSide = 0
        hitter.swingTimer = 0.24f
        bestRally = max(bestRally, rules.rallyShotCount)
        rallyHits = if (rules.rallyShotCount >= 3) rules.rallyShotCount else 0
        lastShotLabel = shotType.label
    }

    private fun updateDoublesAiPositioning() {
        // Partner covers whichever horizontal side human player leaves open, waiting behind baseline if 2-bounce active
        val partnerIdealX = if (player.x >= CourtSpec.COURT_CENTER_X) {
            CourtSpec.COURT_CENTER_X - 4.6f
        } else {
            CourtSpec.COURT_CENTER_X + 4.6f
        }
        val partnerIdealY = if (rules.rallyShotCount <= 2) {
            CourtSpec.COURT_BOTTOM_Y - 1.2f
        } else {
            CourtSpec.LOWER_NVZ_Y - 1.0f
        }
        if (ball.lastHitterSide == CourtSide.UPPER && ball.y < CourtSpec.NET_Y + 4f) {
            val dPartner = hypot(ball.x - partner.x, ball.y - partner.y)
            val dHuman = hypot(ball.x - player.x, ball.y - player.y)
            if (dPartner <= dHuman + 1.0f) {
                partner.targetX = ball.x.coerceIn(CourtSpec.COURT_LEFT_X + 1.2f, CourtSpec.COURT_RIGHT_X - 1.2f)
                partner.targetY = ball.y.coerceIn(2.0f, CourtSpec.LOWER_NVZ_Y - 0.6f)
            } else {
                partner.targetX = partnerIdealX
                partner.targetY = partnerIdealY
            }
        } else {
            partner.targetX = partnerIdealX
            partner.targetY = partnerIdealY
        }

        // Upper pair splits court left/right and moves to kitchen line once two-bounce rule clears
        val upperBaseY = if (rules.rallyShotCount <= 2) {
            CourtSpec.COURT_TOP_Y + 1.4f
        } else {
            CourtSpec.UPPER_NVZ_Y + 1.1f
        }
        val u0TargetX = CourtSpec.COURT_CENTER_X - 4.6f
        val u1TargetX = CourtSpec.COURT_CENTER_X + 4.6f

        if (ball.lastHitterSide == CourtSide.LOWER && ball.y > CourtSpec.NET_Y - 4f) {
            val chase0 = abs(ball.x - upperTeam[0].x) <= abs(ball.x - upperTeam[1].x)
            if (chase0) {
                upperTeam[0].targetX = ball.x.coerceIn(CourtSpec.COURT_LEFT_X + 1.0f, CourtSpec.COURT_RIGHT_X - 1.0f)
                upperTeam[0].targetY = ball.y.coerceIn(CourtSpec.UPPER_NVZ_Y + 0.6f, CourtSpec.WORLD_HEIGHT_FT - 2.0f)
                upperTeam[1].targetX = u1TargetX
                upperTeam[1].targetY = upperBaseY
            } else {
                upperTeam[0].targetX = u0TargetX
                upperTeam[0].targetY = upperBaseY
                upperTeam[1].targetX = ball.x.coerceIn(CourtSpec.COURT_LEFT_X + 1.0f, CourtSpec.COURT_RIGHT_X - 1.0f)
                upperTeam[1].targetY = ball.y.coerceIn(CourtSpec.UPPER_NVZ_Y + 0.6f, CourtSpec.WORLD_HEIGHT_FT - 2.0f)
            }
        } else {
            upperTeam[0].targetX = u0TargetX
            upperTeam[0].targetY = upperBaseY
            upperTeam[1].targetX = u1TargetX
            upperTeam[1].targetY = upperBaseY
        }
    }

    private fun applyFault(faultingSide: CourtSide, reason: FaultReason): PointResolution {
        val resolution = rules.resolveFault(faultingSide, reason)
        lastCallout = resolution.calloutText
        rallyPhase = if (resolution.matchWinner != null) {
            RallyPhase.MATCH_OVER
        } else {
            RallyPhase.POINT_PAUSE
        }
        pauseTimerSec = POINT_PAUSE_SEC
        ball.active = false
        return resolution
    }

    private fun moveTowardTarget(entity: PlayerEntity, speed: Float, dt: Float) {
        val dx = entity.targetX - entity.x
        val dy = entity.targetY - entity.y
        val dist = hypot(dx, dy)
        if (dist < 0.04f) {
            entity.x = entity.targetX
            entity.y = entity.targetY
            return
        }
        val step = min(dist, speed * dt)
        entity.x += (dx / dist) * step
        entity.y += (dy / dist) * step
    }
}
