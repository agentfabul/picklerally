package com.picklerally.app.game

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

class DoublesMatch(
    playerChar: CharacterId = CharacterId.RIO,
    partnerChar: CharacterId = CharacterId.DILL,
    upperRightChar: CharacterId = CharacterId.LOLA,
    upperLeftChar: CharacterId = CharacterId.BERT,
    scoringMode: ScoringMode = ScoringMode.SIDE_OUT,
    private val difficulty: BotDifficulty = BotDifficulty.NORMAL,
) {
    companion object {
        const val PLAYER_SPEED_FT_S = 18.0f
        const val PARTNER_SPEED_FT_S = 15.2f
        const val BOT_SPEED_FT_S = 15.0f
        const val HIT_REACH_FT = 3.9f
        const val PLAYER_REACH_FT = 4.3f
        const val MAX_HIT_HEIGHT_FT = 7.4f
        const val POINT_PAUSE_SEC = 1.5f
        const val AI_SERVE_DELAY_SEC = 2.6f
    }

    var rules: RulesEngine = RulesEngine(scoringMode, MatchMode.DOUBLES, 11, 2, CourtSide.LOWER)
        private set

    val lowerTeam: List<PlayerEntity> = listOf(
        PlayerEntity(
            side = CourtSide.LOWER,
            characterId = playerChar,
            x = CourtSpec.COURT_CENTER_X + 4.8f,
            y = CourtSpec.COURT_BOTTOM_Y - 1.1f,
            targetX = CourtSpec.COURT_CENTER_X + 4.8f,
            targetY = CourtSpec.COURT_BOTTOM_Y - 1.1f,
        ),
        PlayerEntity(
            side = CourtSide.LOWER,
            characterId = partnerChar,
            x = CourtSpec.COURT_CENTER_X - 4.8f,
            y = CourtSpec.COURT_BOTTOM_Y - 1.4f,
            targetX = CourtSpec.COURT_CENTER_X - 4.8f,
            targetY = CourtSpec.COURT_BOTTOM_Y - 1.4f,
        ),
    )

    val upperTeam: List<PlayerEntity> = listOf(
        PlayerEntity(
            side = CourtSide.UPPER,
            characterId = upperRightChar,
            x = CourtSpec.COURT_CENTER_X - 4.8f,
            y = CourtSpec.COURT_TOP_Y + 1.1f,
            targetX = CourtSpec.COURT_CENTER_X - 4.8f,
            targetY = CourtSpec.COURT_TOP_Y + 1.1f,
        ),
        PlayerEntity(
            side = CourtSide.UPPER,
            characterId = upperLeftChar,
            x = CourtSpec.COURT_CENTER_X + 4.8f,
            y = CourtSpec.COURT_TOP_Y + 1.4f,
            targetX = CourtSpec.COURT_CENTER_X + 4.8f,
            targetY = CourtSpec.COURT_TOP_Y + 1.4f,
        ),
    )

    val player: PlayerEntity get() = lowerTeam[0]
    val partner: PlayerEntity get() = lowerTeam[1]

    val ball = BallState(
        x = lowerTeam[0].x - 0.9f,
        y = lowerTeam[0].y + 1.3f,
        z = 2.4f,
    )

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
    var lastFaultReason: FaultReason? = null
        private set
    var aiServeTimerSec: Float = 0f
        private set
    var autoSwingEnabled: Boolean = true

    private var accumulator = 0f
    private var pauseTimerSec = 0f
    private var partnerHitCooldownSec = 0f
    private var upperHitCooldownSec = 0f
    private var autoSwingPrevDist = Float.POSITIVE_INFINITY

    init {
        setupPointForServe()
    }

    fun activeServerIndex(): Int = rules.currentServeAssignment().serverIndexInTeam

    fun activeServerSide(): CourtSide = rules.servingSide

    fun resetMatch(scoringMode: ScoringMode = rules.scoringMode) {
        rules = RulesEngine(scoringMode, MatchMode.DOUBLES, 11, 2, CourtSide.LOWER)
        rallyHits = 0
        bestRally = 0
        lastShotLabel = ""
        lastFaultReason = null
        setupPointForServe()
    }

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
        aiServeTimerSec =
            if (assign.servingSide == CourtSide.LOWER && assign.serverIndexInTeam == 0) 0f
            else AI_SERVE_DELAY_SEC
        partnerHitCooldownSec = 0f
        upperHitCooldownSec = 0f
        lastFaultReason = null

        val lowerRightIdx = rules.lowerRightPlayerIndex
        val lowerLeftIdx = 1 - lowerRightIdx
        val upperRightIdx = rules.upperRightPlayerIndex
        val upperLeftIdx = 1 - upperRightIdx

        val lowerRightX = CourtSpec.COURT_CENTER_X + 4.8f
        val lowerLeftX = CourtSpec.COURT_CENTER_X - 4.8f
        val upperRightX = CourtSpec.COURT_CENTER_X - 4.8f
        val upperLeftX = CourtSpec.COURT_CENTER_X + 4.8f

        val receiverSide = if (assign.servingSide == CourtSide.LOWER) CourtSide.UPPER else CourtSide.LOWER

        positionEntity(
            lowerTeam[lowerRightIdx],
            lowerRightX,
            yForRole(
                CourtSide.LOWER,
                assign.servingSide == CourtSide.LOWER,
                assign.servingSide == CourtSide.LOWER && assign.serverIndexInTeam == lowerRightIdx,
                receiverSide == CourtSide.LOWER && assign.targetBox == ServiceBoxSide.RIGHT,
            ),
        )
        positionEntity(
            lowerTeam[lowerLeftIdx],
            lowerLeftX,
            yForRole(
                CourtSide.LOWER,
                assign.servingSide == CourtSide.LOWER,
                assign.servingSide == CourtSide.LOWER && assign.serverIndexInTeam == lowerLeftIdx,
                receiverSide == CourtSide.LOWER && assign.targetBox == ServiceBoxSide.LEFT,
            ),
        )
        positionEntity(
            upperTeam[upperRightIdx],
            upperRightX,
            yForRole(
                CourtSide.UPPER,
                assign.servingSide == CourtSide.UPPER,
                assign.servingSide == CourtSide.UPPER && assign.serverIndexInTeam == upperRightIdx,
                receiverSide == CourtSide.UPPER && assign.targetBox == ServiceBoxSide.LEFT,
            ),
        )
        positionEntity(
            upperTeam[upperLeftIdx],
            upperLeftX,
            yForRole(
                CourtSide.UPPER,
                assign.servingSide == CourtSide.UPPER,
                assign.servingSide == CourtSide.UPPER && assign.serverIndexInTeam == upperLeftIdx,
                receiverSide == CourtSide.UPPER && assign.targetBox == ServiceBoxSide.RIGHT,
            ),
        )

        val server =
            if (assign.servingSide == CourtSide.LOWER) lowerTeam[assign.serverIndexInTeam]
            else upperTeam[assign.serverIndexInTeam]

        ball.x = server.x + (if (assign.servingSide == CourtSide.LOWER) -0.9f else 0.9f)
        ball.y = server.y + (if (assign.servingSide == CourtSide.LOWER) 1.3f else -1.3f)
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

    private fun yForRole(
        side: CourtSide,
        isServingTeam: Boolean,
        isServer: Boolean,
        isTargetBox: Boolean,
    ): Float {
        if (side == CourtSide.LOWER) {
            if (isServingTeam && isServer) return CourtSpec.COURT_BOTTOM_Y - 1.1f
            if (isServingTeam) return CourtSpec.COURT_BOTTOM_Y - 1.4f
            if (isTargetBox) return CourtSpec.COURT_BOTTOM_Y - 1.1f
            return CourtSpec.LOWER_NVZ_Y - 1.2f
        }
        if (isServingTeam && isServer) return CourtSpec.COURT_TOP_Y + 1.1f
        if (isServingTeam) return CourtSpec.COURT_TOP_Y + 1.4f
        if (isTargetBox) return CourtSpec.COURT_TOP_Y + 1.1f
        return CourtSpec.UPPER_NVZ_Y + 1.2f
    }

    private fun positionEntity(entity: PlayerEntity, x: Float, y: Float) {
        entity.x = x
        entity.y = y
        entity.targetX = x
        entity.targetY = y
        entity.swingTimer = 0f
    }

    fun setPlayerMoveTarget(worldX: Float, worldY: Float) {
        if (rallyPhase == RallyPhase.MATCH_OVER) return
        val maxY = if (
            rallyPhase == RallyPhase.WAITING_SERVE &&
            rules.servingSide == CourtSide.LOWER &&
            activeServerIndex() == 0
        ) {
            CourtSpec.COURT_BOTTOM_Y - 0.3f
        } else {
            CourtSpec.NET_Y - 0.6f
        }
        player.targetX = max(1.5f, min(CourtSpec.WORLD_WIDTH_FT - 1.5f, worldX))
        player.targetY = max(1.5f, min(maxY, worldY))
    }

    fun playerHitState(): PlayerHitState {
        if (rallyPhase != RallyPhase.RALLY_LIVE || !ball.active || ball.lastHitterSide == CourtSide.LOWER) {
            return PlayerHitState.NONE
        }
        val dist = hypot(ball.x - player.x, ball.y - player.y)
        if (dist > PLAYER_REACH_FT || ball.y > CourtSpec.NET_Y + 1.2f) return PlayerHitState.NONE
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
        if (ball.y > CourtSpec.NET_Y + 1.2f) return false
        val dist = hypot(ball.x - player.x, ball.y - player.y)
        return dist <= PLAYER_REACH_FT && ball.z <= MAX_HIT_HEIGHT_FT
    }

    fun launchServe(aimOffsetX: Float = 0f): ShotOutcome {
        if (
            rallyPhase != RallyPhase.WAITING_SERVE ||
            rules.servingSide != CourtSide.LOWER ||
            activeServerIndex() != 0
        ) {
            return ShotOutcome()
        }
        return executeServe(CourtSide.LOWER, player, aimOffsetX)
    }

    fun triggerReadyAiServe(): ShotOutcome {
        if (rallyPhase != RallyPhase.WAITING_SERVE) return ShotOutcome()
        val assign = rules.currentServeAssignment()
        if (assign.servingSide == CourtSide.LOWER && assign.serverIndexInTeam == 0) return ShotOutcome()
        val server =
            if (assign.servingSide == CourtSide.LOWER) lowerTeam[assign.serverIndexInTeam]
            else upperTeam[assign.serverIndexInTeam]
        aiServeTimerSec = 0f
        return executeServe(assign.servingSide, server, 0f)
    }

    fun attemptPlayerSwipeHit(
        swipe: SwipeVector,
        forcedShotType: ShotType? = null,
    ): ShotOutcome {
        if (
            rallyPhase == RallyPhase.WAITING_SERVE &&
            rules.servingSide == CourtSide.LOWER &&
            activeServerIndex() == 0 &&
            (swipe.dyFt > 1.0f || forcedShotType != null)
        ) {
            return launchServe(swipe.dxFt * 0.45f)
        }

        if (!canPlayerReachBall()) return ShotOutcome()

        val fault = rules.validatePlayerHit(CourtSide.LOWER, player.x, player.y, ball.bouncesOnCurrentSide)
        if (fault != null) {
            return ShotOutcome(fault = applyFault(CourtSide.LOWER, fault))
        }

        val shotType = forcedShotType ?: classifySwipe(swipe)
        val vel = computeShotVelocity(
            fromX = ball.x,
            fromY = ball.y,
            swipe = swipe,
            shotType = shotType,
            targetSide = CourtSide.UPPER,
            timingAccuracy = 0.88f,
            scatterSeed = Random.nextFloat() * 2f - 1f,
            fromZ = ball.z,
        )

        ball.vx = vel.vx
        ball.vy = vel.vy
        ball.vz = vel.vz
        ball.lastHitterSide = CourtSide.LOWER
        ball.crossedNetSinceHit = false
        ball.bouncesOnCurrentSide = 0
        player.swingTimer = 0.24f
        player.lastSwingType = shotType
        bestRally = max(bestRally, rules.rallyShotCount)
        rallyHits = if (rules.rallyShotCount >= 3) rules.rallyShotCount else 0
        lastShotLabel = shotType.name

        return ShotOutcome(
            hit = HitFeedback(
                hitterSide = CourtSide.LOWER,
                shotType = shotType,
                timingAccuracy = 0.88f,
                hitX = ball.x,
                hitY = ball.y,
            ),
        )
    }

    fun update(rawDeltaSec: Float): List<SimTickOutput> {
        val clamped = min(rawDeltaSec, 0.05f)
        accumulator += clamped
        val outputs = mutableListOf<SimTickOutput>()

        while (accumulator >= CourtSpec.FIXED_TIMESTEP_SEC) {
            outputs.add(stepFixed(CourtSpec.FIXED_TIMESTEP_SEC))
            accumulator -= CourtSpec.FIXED_TIMESTEP_SEC
        }
        return outputs
    }

    private fun stepFixed(dt: Float): SimTickOutput {
        moveTowardTarget(player, PLAYER_SPEED_FT_S, dt)
        moveTowardTarget(partner, PARTNER_SPEED_FT_S, dt)
        val oppSpeed = max(10f, BOT_PROFILES.getValue(difficulty).speedFtS + 0.8f)
        moveTowardTarget(upperTeam[0], oppSpeed, dt)
        moveTowardTarget(upperTeam[1], oppSpeed, dt)

        for (p in lowerTeam + upperTeam) {
            if (p.swingTimer > 0f) p.swingTimer = max(0f, p.swingTimer - dt)
        }
        if (partnerHitCooldownSec > 0f) partnerHitCooldownSec = max(0f, partnerHitCooldownSec - dt)
        if (upperHitCooldownSec > 0f) upperHitCooldownSec = max(0f, upperHitCooldownSec - dt)

        val idleEvents = StepEvents(bounceX = ball.x, bounceY = ball.y)

        if (rallyPhase == RallyPhase.MATCH_OVER) {
            return SimTickOutput(idleEvents)
        }

        if (rallyPhase == RallyPhase.POINT_PAUSE) {
            pauseTimerSec -= dt
            if (pauseTimerSec <= 0f) {
                setupPointForServe()
            }
            return SimTickOutput(idleEvents)
        }

        if (rallyPhase == RallyPhase.WAITING_SERVE) {
            val assign = rules.currentServeAssignment()
            val server =
                if (assign.servingSide == CourtSide.LOWER) lowerTeam[assign.serverIndexInTeam]
                else upperTeam[assign.serverIndexInTeam]
            ball.x = server.x + (if (assign.servingSide == CourtSide.LOWER) -0.9f else 0.9f)
            ball.y = server.y + (if (assign.servingSide == CourtSide.LOWER) 1.3f else -1.3f)
            ball.z = 2.4f

            if (!(assign.servingSide == CourtSide.LOWER && assign.serverIndexInTeam == 0)) {
                aiServeTimerSec -= dt
                if (aiServeTimerSec <= 0f) {
                    val res = executeServe(assign.servingSide, server, 0f)
                    return SimTickOutput(idleEvents, res.hit, res.fault)
                }
            }
            return SimTickOutput(idleEvents)
        }

        updateDoublesAiPositioning()

        var hitFeedback: HitFeedback? = null
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
                if (res.fault != null) return SimTickOutput(idleEvents, pointResolution = res.fault)
                hitFeedback = res.hit
            }
        } else {
            autoSwingPrevDist = Float.POSITIVE_INFINITY
        }

        val stepEvents = stepBallPhysics(ball, dt)
        var pointResolution: PointResolution? = null

        if (stepEvents.netOutcome == NetCrossOutcome.HIT_NET) {
            pointResolution = applyFault(ball.lastHitterSide ?: CourtSide.LOWER, FaultReason.INTO_NET)
            return SimTickOutput(stepEvents, hitFeedback, pointResolution)
        }

        if (stepEvents.bounced) {
            if (ball.bouncesOnCurrentSide == 1) {
                val bounceFault = rules.validateFirstBounce(
                    stepEvents.bounceX,
                    stepEvents.bounceY,
                    ball.lastHitterSide,
                )
                if (bounceFault != null) {
                    pointResolution = applyFault(ball.lastHitterSide ?: CourtSide.LOWER, bounceFault)
                    return SimTickOutput(stepEvents, hitFeedback, pointResolution)
                }
            }
            if (ball.bouncesOnCurrentSide >= 2) {
                val receivingSide =
                    if (ball.lastHitterSide == CourtSide.LOWER) CourtSide.UPPER else CourtSide.LOWER
                pointResolution = applyFault(receivingSide, FaultReason.DOUBLE_BOUNCE)
                return SimTickOutput(stepEvents, hitFeedback, pointResolution)
            }
        }

        if (
            ball.x < -2f ||
            ball.x > CourtSpec.WORLD_WIDTH_FT + 2f ||
            ball.y < -3f ||
            ball.y > CourtSpec.WORLD_HEIGHT_FT + 3f
        ) {
            if (ball.crossedNetSinceHit && ball.bouncesOnCurrentSide >= 1) {
                val receivingSide =
                    if (ball.lastHitterSide == CourtSide.LOWER) CourtSide.UPPER else CourtSide.LOWER
                pointResolution = applyFault(receivingSide, FaultReason.DOUBLE_BOUNCE)
                return SimTickOutput(stepEvents, hitFeedback, pointResolution)
            }
            val faultSide = ball.lastHitterSide ?: CourtSide.LOWER
            val fault = if (rules.rallyShotCount == 1) FaultReason.SERVE_OUT_OF_BOX else FaultReason.OUT_OF_BOUNDS
            pointResolution = applyFault(faultSide, fault)
            return SimTickOutput(stepEvents, hitFeedback, pointResolution)
        }

        if (
            ball.lastHitterSide == CourtSide.UPPER &&
            ball.crossedNetSinceHit &&
            partnerHitCooldownSec <= 0f &&
            (rules.rallyShotCount > 2 || ball.bouncesOnCurrentSide >= 1)
        ) {
            val dPartner = hypot(ball.x - partner.x, ball.y - partner.y)
            val dHuman = hypot(ball.x - player.x, ball.y - player.y)
            if (
                dPartner <= HIT_REACH_FT &&
                (isOnPartnerSide(ball.x) || dPartner + 1.5f < dHuman) &&
                ball.z <= MAX_HIT_HEIGHT_FT
            ) {
                val fault = rules.validatePlayerHit(CourtSide.LOWER, partner.x, partner.y, ball.bouncesOnCurrentSide)
                if (fault != null) {
                    pointResolution = applyFault(CourtSide.LOWER, fault)
                    return SimTickOutput(stepEvents, hitFeedback, pointResolution)
                }
                hitFeedback = launchAiShot(partner, CourtSide.UPPER)
                partnerHitCooldownSec = 0.42f
            }
        }

        if (
            ball.lastHitterSide == CourtSide.LOWER &&
            ball.crossedNetSinceHit &&
            upperHitCooldownSec <= 0f &&
            (rules.rallyShotCount > 2 || ball.bouncesOnCurrentSide >= 1)
        ) {
            val d0 = hypot(ball.x - upperTeam[0].x, ball.y - upperTeam[0].y)
            val d1 = hypot(ball.x - upperTeam[1].x, ball.y - upperTeam[1].y)
            val responder = if (d0 <= d1) upperTeam[0] else upperTeam[1]
            val dist = min(d0, d1)
            if (dist <= HIT_REACH_FT && ball.z <= MAX_HIT_HEIGHT_FT) {
                val fault = rules.validatePlayerHit(CourtSide.UPPER, responder.x, responder.y, ball.bouncesOnCurrentSide)
                if (fault != null) {
                    pointResolution = applyFault(CourtSide.UPPER, fault)
                    return SimTickOutput(stepEvents, hitFeedback, pointResolution)
                }
                hitFeedback = launchAiShot(responder, CourtSide.LOWER)
                upperHitCooldownSec = 0.42f
            }
        }

        return SimTickOutput(stepEvents, hitFeedback, pointResolution)
    }

    private fun executeServe(
        serverSide: CourtSide,
        server: PlayerEntity,
        aimOffsetX: Float,
    ): ShotOutcome {
        val assign = rules.currentServeAssignment()
        val footFault = rules.beginServe(server.x, server.y)
        if (footFault != null) {
            return ShotOutcome(fault = applyFault(serverSide, footFault))
        }

        val targetX =
            if (assign.targetBox == ServiceBoxSide.LEFT) CourtSpec.COURT_LEFT_X + 5.0f
            else CourtSpec.COURT_RIGHT_X - 5.0f
        val targetY =
            if (assign.targetCourtSide == CourtSide.UPPER) CourtSpec.UPPER_NVZ_Y + 7.8f
            else CourtSpec.LOWER_NVZ_Y - 7.8f

        val tof = 0.98f
        val invIntegral = 1f / horizontalDragIntegral(tof)
        val offset = max(-2.5f, min(2.5f, aimOffsetX))
        ball.vx = (targetX + offset - ball.x) * invIntegral
        ball.vy = (targetY - ball.y) * invIntegral
        ball.vz = -0.5f * BallPhysicsConstants.GRAVITY_FT_S2 * tof - ball.z / tof
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

        return ShotOutcome(
            hit = HitFeedback(
                hitterSide = serverSide,
                shotType = ShotType.SERVE,
                timingAccuracy = 0.95f,
                hitX = ball.x,
                hitY = ball.y,
            ),
        )
    }

    private fun launchAiShot(hitter: PlayerEntity, targetSide: CourtSide): HitFeedback {
        val defenders = if (targetSide == CourtSide.UPPER) upperTeam else lowerTeam
        val profile = BOT_PROFILES.getValue(difficulty)
        val shot = computeBotReturnShot(
            fromX = ball.x,
            fromY = ball.y,
            fromZ = ball.z,
            hitterY = hitter.y,
            targetSide = targetSide,
            opponentXs = listOf(defenders[0].x, defenders[1].x),
            difficulty = if (targetSide == CourtSide.LOWER) difficulty else BotDifficulty.NORMAL,
            aimAwayChance = if (targetSide == CourtSide.LOWER) profile.aimAwayChance else 1f,
            sidelineMarginFt = if (targetSide == CourtSide.LOWER) profile.sidelineMarginFt else 2.0f,
        )

        ball.vx = shot.vx
        ball.vy = shot.vy
        ball.vz = shot.vz
        val hitterSide = if (targetSide == CourtSide.UPPER) CourtSide.LOWER else CourtSide.UPPER
        ball.lastHitterSide = hitterSide
        ball.crossedNetSinceHit = false
        ball.bouncesOnCurrentSide = 0
        hitter.swingTimer = 0.24f
        hitter.lastSwingType = shot.shotType
        bestRally = max(bestRally, rules.rallyShotCount)
        rallyHits = if (rules.rallyShotCount >= 3) rules.rallyShotCount else 0
        lastShotLabel = shot.shotType.name

        return HitFeedback(
            hitterSide = hitterSide,
            shotType = shot.shotType,
            timingAccuracy = 0.88f,
            hitX = ball.x,
            hitY = ball.y,
        )
    }

    private fun isOnPartnerSide(x: Float): Boolean {
        val partnerCoversLeft = player.x >= CourtSpec.COURT_CENTER_X
        return if (partnerCoversLeft) x < CourtSpec.COURT_CENTER_X else x >= CourtSpec.COURT_CENTER_X
    }

    private fun chasePoint(side: CourtSide): Pair<Float, Float> {
        val b = ball
        if (b.bouncesOnCurrentSide > 0 || abs(b.vy) < 0.5f) return b.x to b.y
        val g = BallPhysicsConstants.GRAVITY_FT_S2
        val t = (-b.vz - sqrt(max(0f, b.vz * b.vz - 2f * g * b.z))) / g
        val landX = b.x + b.vx * t
        val landY = b.y + b.vy * t
        val backOff = 2.2f
        val dirY = if (side == CourtSide.UPPER) 1f else -1f
        return (landX + (b.vx / max(1f, abs(b.vy))) * backOff) to (landY + dirY * backOff)
    }

    private fun updateDoublesAiPositioning() {
        val partnerIdealX =
            if (player.x >= CourtSpec.COURT_CENTER_X) CourtSpec.COURT_CENTER_X - 4.6f
            else CourtSpec.COURT_CENTER_X + 4.6f
        val partnerIdealY =
            if (rules.rallyShotCount <= 2) CourtSpec.COURT_BOTTOM_Y - 1.2f else CourtSpec.LOWER_NVZ_Y - 1.0f

        if (ball.lastHitterSide == CourtSide.UPPER && ball.y < CourtSpec.NET_Y + 4f) {
            val (cpX, cpY) = chasePoint(CourtSide.LOWER)
            val distPartnerToCp = hypot(cpX - partner.x, cpY - partner.y)
            val distHumanToCp = hypot(cpX - player.x, cpY - player.y)
            if (isOnPartnerSide(cpX) || distPartnerToCp + 2.0f < distHumanToCp) {
                partner.targetX = max(CourtSpec.COURT_LEFT_X + 1.2f, min(CourtSpec.COURT_RIGHT_X - 1.2f, cpX))
                partner.targetY = max(2.0f, min(CourtSpec.LOWER_NVZ_Y - 0.6f, cpY))
            } else {
                partner.targetX = partnerIdealX
                partner.targetY = partnerIdealY
            }
        } else {
            partner.targetX = partnerIdealX
            partner.targetY = partnerIdealY
        }

        val upperBaseY =
            if (rules.rallyShotCount <= 2) CourtSpec.COURT_TOP_Y + 1.4f else CourtSpec.UPPER_NVZ_Y + 1.1f
        val u0TargetX = CourtSpec.COURT_CENTER_X - 4.6f
        val u1TargetX = CourtSpec.COURT_CENTER_X + 4.6f

        if (ball.lastHitterSide == CourtSide.LOWER && ball.y > CourtSpec.NET_Y - 4f) {
            val (cpX, cpY) = chasePoint(CourtSide.UPPER)
            val chase0 = abs(ball.x - upperTeam[0].x) <= abs(ball.x - upperTeam[1].x)
            if (chase0) {
                upperTeam[0].targetX = max(CourtSpec.COURT_LEFT_X + 1.0f, min(CourtSpec.COURT_RIGHT_X - 1.0f, cpX))
                upperTeam[0].targetY = max(CourtSpec.UPPER_NVZ_Y + 0.6f, min(CourtSpec.WORLD_HEIGHT_FT - 2.0f, cpY))
                upperTeam[1].targetX = u1TargetX
                upperTeam[1].targetY = upperBaseY
            } else {
                upperTeam[0].targetX = u0TargetX
                upperTeam[0].targetY = upperBaseY
                upperTeam[1].targetX = max(CourtSpec.COURT_LEFT_X + 1.0f, min(CourtSpec.COURT_RIGHT_X - 1.0f, cpX))
                upperTeam[1].targetY = max(CourtSpec.UPPER_NVZ_Y + 0.6f, min(CourtSpec.WORLD_HEIGHT_FT - 2.0f, cpY))
            }
        } else {
            upperTeam[0].targetX = u0TargetX
            upperTeam[0].targetY = upperBaseY
            upperTeam[1].targetX = u1TargetX
            upperTeam[1].targetY = upperBaseY
        }
    }

    private fun applyFault(faultingSide: CourtSide, reason: FaultReason): PointResolution? {
        if (rallyPhase == RallyPhase.POINT_PAUSE || rallyPhase == RallyPhase.MATCH_OVER) {
            return null
        }
        val resolution = rules.resolveFault(faultingSide, reason)
        lastCallout = resolution.calloutText
        lastFaultReason = reason
        rallyPhase = if (resolution.matchWinner != null) RallyPhase.MATCH_OVER else RallyPhase.POINT_PAUSE
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
