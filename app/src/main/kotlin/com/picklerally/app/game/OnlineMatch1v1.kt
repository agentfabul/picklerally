package com.picklerally.app.game

import kotlinx.serialization.Serializable
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class RoomStatus {
    WAITING,
    PLAYING,
    HOST_LEFT,
    FINISHED;

    companion object {
        fun fromStringOrDefault(raw: String?, fallback: RoomStatus = WAITING): RoomStatus =
            entries.firstOrNull { it.name == raw } ?: fallback
    }
}

enum class RoomMode {
    SINGLES_1V1,
    DOUBLES_2V2,
}

@Serializable
data class PlayerInputSnapshot(
    val uid: String,
    val sequence: Long,
    val targetX: Float,
    val targetY: Float,
    val swingType: String?,
    val aimX: Float,
    val aimY: Float,
    val swipeDurationSec: Float,
    val timestampMs: Long,
)

@Serializable
data class MatchStateSnapshot(
    val tick: Long,
    val ballX: Float,
    val ballY: Float,
    val ballZ: Float,
    val ballVx: Float,
    val ballVy: Float,
    val ballVz: Float,
    val ballActive: Boolean,
    val hostX: Float,
    val hostY: Float,
    val guestX: Float,
    val guestY: Float,
    val hostSwingTimer: Float,
    val guestSwingTimer: Float,
    val scoreHost: Int,
    val scoreGuest: Int,
    val servingSide: String,
    val rallyShotCount: Int,
    val rallyHits: Int,
    val lastEvent: String?,
    val matchWinner: String?,
    val publishedAtMs: Long,
    val lastHitterSide: String? = null,
    val ballBouncesOnSide: Int = 0,
)

object RoomCodeGenerator {
    private val roomRegex = Regex("^PKL-[0-9]{3}$")

    fun generate(): String {
        val code = Random.nextInt(100, 1000)
        return "PKL-$code"
    }

    fun isValid(code: String): Boolean = roomRegex.matches(code.trim().uppercase())
}

data class OnlineStepResult(
    val snapshotToPublish: MatchStateSnapshot? = null,
    val stepEvents: StepEvents = StepEvents(),
    val hitFeedback: HitFeedback? = null,
    val pointResolution: PointResolution? = null,
)

data class LocalSwipeResult(
    val guestInput: PlayerInputSnapshot? = null,
    val hit: HitFeedback? = null,
    val fault: PointResolution? = null,
)

class OnlineMatch1v1(
    val isHost: Boolean,
    val localUid: String,
    localChar: CharacterId = CharacterId.RIO,
    remoteChar: CharacterId = CharacterId.LOLA,
    scoringMode: ScoringMode = ScoringMode.RALLY,
) {
    companion object {
        const val PUBLISH_INTERVAL_SEC = 0.05f
        const val HOST_PLAYER_SPEED_FT_S = 18.5f
        const val LOCAL_HIT_REACH_FT = 3.9f
        const val REMOTE_GENEROUS_REACH_FT = 4.75f
        const val MAX_HIT_HEIGHT_FT = 6.0f
        const val POINT_PAUSE_SEC = 1.6f

        fun mirrorX(x: Float): Float = CourtSpec.WORLD_WIDTH_FT - x
        fun mirrorY(y: Float): Float = CourtSpec.WORLD_HEIGHT_FT - y
    }

    val rules: RulesEngine = RulesEngine(scoringMode, MatchMode.SINGLES)

    val localPlayer = PlayerEntity(
        side = CourtSide.LOWER,
        characterId = localChar,
        x = CourtSpec.COURT_CENTER_X + 4.5f,
        y = CourtSpec.COURT_BOTTOM_Y - 1.1f,
        targetX = CourtSpec.COURT_CENTER_X + 4.5f,
        targetY = CourtSpec.COURT_BOTTOM_Y - 1.1f,
    )

    val remotePlayer = PlayerEntity(
        side = CourtSide.UPPER,
        characterId = remoteChar,
        x = CourtSpec.COURT_CENTER_X - 4.5f,
        y = CourtSpec.COURT_TOP_Y + 1.1f,
        targetX = CourtSpec.COURT_CENTER_X - 4.5f,
        targetY = CourtSpec.COURT_TOP_Y + 1.1f,
    )

    val ball = BallState(x = 14f, y = 7f, z = 2.4f)

    var rallyPhase: RallyPhase = RallyPhase.WAITING_SERVE
        private set
    var rallyHits: Int = 0
        private set
    var bestRally: Int = 0
        private set
    var lastCallout: String = "Waiting for opponent..."
        private set
    var lastShotLabel: String = ""
        private set
    var lastFaultReason: FaultReason? = null
        private set
    var hostDisconnected: Boolean = false
        private set

    var scoreLocal: Int = 0
        private set
    var scoreRemote: Int = 0
        private set
    var servingLocal: Boolean = isHost
        private set
    var matchWinnerLocal: Boolean? = null
        private set
    var syncedRallyShotCount: Int = 0
        private set
    var syncedBallBouncesOnSide: Int = 0
        private set
    private var syncedBallLastHitterLocal: Boolean = false

    var autoSwingEnabled: Boolean = true
    private var autoSwingPrevDist = Float.POSITIVE_INFINITY

    private var tickCounter = 0L
    private var accumulator = 0f
    private var publishAccumulator = 0f
    private var pauseTimerSec = 0f
    private var lastProcessedGuestSwingSeq = -1L
    private var inputSeqCounter = 0L

    private var interpBallX = 14f
    private var interpBallY = 49f
    private var interpBallZ = 2.4f
    private var interpRemoteX = CourtSpec.COURT_CENTER_X - 4.5f
    private var interpRemoteY = CourtSpec.COURT_TOP_Y + 1.1f
    private var pendingGuestSwipe: SwipeVector? = null

    init {
        if (isHost) {
            setupPointForServe()
        }
    }

    fun currentServeAssignment(): ServeAssignment {
        if (isHost) return rules.currentServeAssignment()
        val servingSide = if (servingLocal) CourtSide.LOWER else CourtSide.UPPER
        val servingScore = if (servingLocal) scoreLocal else scoreRemote
        val isEvenScore = servingScore % 2 == 0
        val serverBox = when (servingSide) {
            CourtSide.LOWER -> if (isEvenScore) ServiceBoxSide.RIGHT else ServiceBoxSide.LEFT
            CourtSide.UPPER -> if (isEvenScore) ServiceBoxSide.LEFT else ServiceBoxSide.RIGHT
        }
        val targetBox =
            if (serverBox == ServiceBoxSide.RIGHT) ServiceBoxSide.LEFT else ServiceBoxSide.RIGHT
        val targetCourtSide =
            if (servingSide == CourtSide.LOWER) CourtSide.UPPER else CourtSide.LOWER
        return ServeAssignment(
            servingSide = servingSide,
            serverIndexInTeam = 0,
            serverNumberCall = 1,
            serverBox = serverBox,
            targetCourtSide = targetCourtSide,
            targetBox = targetBox,
        )
    }

    fun setupPointForServe() {
        if (!isHost) return
        if (rules.isMatchOver) {
            rallyPhase = RallyPhase.MATCH_OVER
            matchWinnerLocal = rules.matchWinner == CourtSide.LOWER
            lastCallout = if (matchWinnerLocal == true) "You Win!" else "Opponent Wins!"
            return
        }

        val assign = rules.currentServeAssignment()
        rallyPhase = RallyPhase.WAITING_SERVE
        rallyHits = 0
        lastFaultReason = null
        scoreLocal = rules.scoreLower
        scoreRemote = rules.scoreUpper
        servingLocal = assign.servingSide == CourtSide.LOWER

        val lowerX = when {
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

        localPlayer.x = lowerX
        localPlayer.y = CourtSpec.COURT_BOTTOM_Y - 1.1f
        localPlayer.targetX = localPlayer.x
        localPlayer.targetY = localPlayer.y
        localPlayer.swingTimer = 0f

        remotePlayer.x = upperX
        remotePlayer.y = CourtSpec.COURT_TOP_Y + 1.1f
        remotePlayer.targetX = remotePlayer.x
        remotePlayer.targetY = remotePlayer.y
        remotePlayer.swingTimer = 0f

        if (servingLocal) {
            ball.x = localPlayer.x - 1.0f
            ball.y = localPlayer.y + 1.3f
            lastCallout = "Your serve (${rules.formattedScoreCall()})"
        } else {
            ball.x = remotePlayer.x + 1.0f
            ball.y = remotePlayer.y - 1.3f
            lastCallout = "Opponent serving (${rules.formattedScoreCall()})"
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

    fun activeRallyShotCount(): Int = if (isHost) rules.rallyShotCount else syncedRallyShotCount

    fun activeBallBouncesOnSide(): Int = if (isHost) ball.bouncesOnCurrentSide else syncedBallBouncesOnSide

    fun localPlayerHitState(): PlayerHitState {
        if (rallyPhase != RallyPhase.RALLY_LIVE || !ball.active) return PlayerHitState.NONE
        val hitByLocal = if (isHost) ball.lastHitterSide == CourtSide.LOWER else syncedBallLastHitterLocal
        if (hitByLocal) return PlayerHitState.NONE
        val dist = hypot(ball.x - localPlayer.x, ball.y - localPlayer.y)
        if (dist > LOCAL_HIT_REACH_FT || ball.y > CourtSpec.NET_Y + 0.8f) return PlayerHitState.NONE
        if (ball.z > MAX_HIT_HEIGHT_FT) return PlayerHitState.TOO_HIGH
        val shotCount = activeRallyShotCount()
        val bounces = activeBallBouncesOnSide()
        if (bounces == 0) {
            if (shotCount == 1 || shotCount == 2) return PlayerHitState.WAIT_BOUNCE
            if (CourtSpec.isInKitchen(localPlayer.x, localPlayer.y, CourtSide.LOWER)) return PlayerHitState.KITCHEN
        }
        return PlayerHitState.READY
    }

    fun canLocalReachBall(): Boolean {
        if (rallyPhase != RallyPhase.RALLY_LIVE || !ball.active) return false
        val hitByLocal = if (isHost) ball.lastHitterSide == CourtSide.LOWER else syncedBallLastHitterLocal
        if (hitByLocal) return false
        val dist = hypot(ball.x - localPlayer.x, ball.y - localPlayer.y)
        return dist <= LOCAL_HIT_REACH_FT &&
            ball.z <= MAX_HIT_HEIGHT_FT &&
            ball.y <= CourtSpec.NET_Y + 0.8f
    }

    fun setLocalMoveTarget(worldX: Float, worldY: Float): PlayerInputSnapshot {
        val maxY = if (rallyPhase == RallyPhase.WAITING_SERVE && servingLocal) {
            CourtSpec.COURT_BOTTOM_Y - 0.3f
        } else {
            CourtSpec.NET_Y - 0.6f
        }
        localPlayer.targetX = max(1.5f, min(CourtSpec.WORLD_WIDTH_FT - 1.5f, worldX))
        localPlayer.targetY = max(1.5f, min(maxY, worldY))

        inputSeqCounter += 1L
        return PlayerInputSnapshot(
            uid = localUid,
            sequence = inputSeqCounter,
            targetX = localPlayer.targetX,
            targetY = localPlayer.targetY,
            swingType = null,
            aimX = 0f,
            aimY = 0f,
            swipeDurationSec = 0.2f,
            timestampMs = System.currentTimeMillis(),
        )
    }

    fun triggerLocalSwipe(swipe: SwipeVector): LocalSwipeResult {
        if (isHost) {
            val res = handleHostSwipe(swipe)
            return LocalSwipeResult(guestInput = null, hit = res.hit, fault = res.fault)
        }

        inputSeqCounter += 1L
        val shotType =
            if (rallyPhase == RallyPhase.WAITING_SERVE && servingLocal) ShotType.SERVE
            else classifySwipe(swipe)
        localPlayer.swingTimer = 0.22f
        localPlayer.lastSwingType = shotType

        return LocalSwipeResult(
            guestInput = PlayerInputSnapshot(
                uid = localUid,
                sequence = inputSeqCounter,
                targetX = localPlayer.targetX,
                targetY = localPlayer.targetY,
                swingType = shotType.name,
                aimX = swipe.dxFt,
                aimY = swipe.dyFt,
                swipeDurationSec = swipe.durationSec,
                timestampMs = System.currentTimeMillis(),
            ),
            hit = HitFeedback(
                hitterSide = CourtSide.LOWER,
                shotType = shotType,
                timingAccuracy = 0.9f,
                hitX = ball.x,
                hitY = ball.y,
            ),
        )
    }

    fun applyRemoteGuestInput(input: PlayerInputSnapshot) {
        if (!isHost) return
        remotePlayer.targetX = max(1.5f, min(CourtSpec.WORLD_WIDTH_FT - 1.5f, mirrorX(input.targetX)))
        remotePlayer.targetY = max(
            CourtSpec.NET_Y + 0.6f,
            min(CourtSpec.WORLD_HEIGHT_FT - 1.5f, mirrorY(input.targetY)),
        )

        if (input.swingType != null && input.sequence > lastProcessedGuestSwingSeq) {
            lastProcessedGuestSwingSeq = input.sequence
            pendingGuestSwipe = SwipeVector(
                dxFt = -input.aimX,
                dyFt = -input.aimY,
                durationSec = input.swipeDurationSec,
            )
        }
    }

    fun applyHostSnapshot(snapshot: MatchStateSnapshot) {
        if (isHost) return

        val wasInactive = !ball.active && rallyPhase != RallyPhase.RALLY_LIVE
        interpBallX = mirrorX(snapshot.ballX)
        interpBallY = mirrorY(snapshot.ballY)
        interpBallZ = snapshot.ballZ
        if (wasInactive || !snapshot.ballActive) {
            ball.x = interpBallX
            ball.y = interpBallY
            ball.z = interpBallZ
        }
        ball.active = snapshot.ballActive

        interpRemoteX = mirrorX(snapshot.hostX)
        interpRemoteY = mirrorY(snapshot.hostY)
        remotePlayer.swingTimer = snapshot.hostSwingTimer
        localPlayer.swingTimer = max(localPlayer.swingTimer, snapshot.guestSwingTimer)

        scoreLocal = snapshot.scoreGuest
        scoreRemote = snapshot.scoreHost
        servingLocal = snapshot.servingSide == "GUEST"
        syncedRallyShotCount = snapshot.rallyShotCount
        syncedBallBouncesOnSide = snapshot.ballBouncesOnSide
        syncedBallLastHitterLocal = snapshot.lastHitterSide == "GUEST"
        ball.bouncesOnCurrentSide = snapshot.ballBouncesOnSide
        rallyHits = snapshot.rallyHits
        bestRally = max(bestRally, max(snapshot.rallyShotCount, rallyHits))
        lastCallout = snapshot.lastEvent ?: ""

        matchWinnerLocal = when (snapshot.matchWinner) {
            "GUEST" -> true
            "HOST" -> false
            else -> null
        }

        val eventText = snapshot.lastEvent ?: ""
        rallyPhase = when {
            matchWinnerLocal != null -> RallyPhase.MATCH_OVER
            !snapshot.ballActive &&
                snapshot.rallyShotCount == 0 &&
                (eventText.contains("fault", ignoreCase = true) ||
                    eventText.contains("Out!", ignoreCase = true) ||
                    eventText.contains("Net!", ignoreCase = true) ||
                    eventText.contains("bounce", ignoreCase = true)) -> RallyPhase.POINT_PAUSE
            !snapshot.ballActive -> RallyPhase.WAITING_SERVE
            else -> RallyPhase.RALLY_LIVE
        }
    }

    fun onRoomMetaUpdated(status: RoomStatus) {
        if (!isHost && status == RoomStatus.HOST_LEFT) {
            hostDisconnected = true
            rallyPhase = RallyPhase.MATCH_OVER
            lastCallout = "Host disconnected"
        }
    }

    fun update(rawDeltaSec: Float): OnlineStepResult {
        val noEvents = StepEvents(bounceX = ball.x, bounceY = ball.y)
        if (hostDisconnected) return OnlineStepResult(stepEvents = noEvents)

        val clampedDt = min(rawDeltaSec, 0.05f)
        accumulator += clampedDt

        var snapshotToPublish: MatchStateSnapshot? = null
        var lastEvents = noEvents
        var lastHit: HitFeedback? = null
        var lastRes: PointResolution? = null

        while (accumulator >= CourtSpec.FIXED_TIMESTEP_SEC) {
            if (isHost) {
                val res = stepHost(CourtSpec.FIXED_TIMESTEP_SEC)
                lastEvents = res.stepEvents
                if (res.hitFeedback != null) lastHit = res.hitFeedback
                if (res.pointResolution != null) lastRes = res.pointResolution

                publishAccumulator += CourtSpec.FIXED_TIMESTEP_SEC
                if (publishAccumulator >= PUBLISH_INTERVAL_SEC) {
                    publishAccumulator -= PUBLISH_INTERVAL_SEC
                    snapshotToPublish = buildSnapshot()
                }
            } else {
                stepGuest(CourtSpec.FIXED_TIMESTEP_SEC)
            }
            accumulator -= CourtSpec.FIXED_TIMESTEP_SEC
        }

        return OnlineStepResult(
            snapshotToPublish = snapshotToPublish,
            stepEvents = lastEvents,
            hitFeedback = lastHit,
            pointResolution = lastRes,
        )
    }

    fun buildSnapshot(): MatchStateSnapshot = MatchStateSnapshot(
        tick = tickCounter,
        ballX = ball.x,
        ballY = ball.y,
        ballZ = ball.z,
        ballVx = ball.vx,
        ballVy = ball.vy,
        ballVz = ball.vz,
        ballActive = ball.active,
        hostX = localPlayer.x,
        hostY = localPlayer.y,
        guestX = remotePlayer.x,
        guestY = remotePlayer.y,
        hostSwingTimer = localPlayer.swingTimer,
        guestSwingTimer = remotePlayer.swingTimer,
        scoreHost = rules.scoreLower,
        scoreGuest = rules.scoreUpper,
        servingSide = if (rules.servingSide == CourtSide.LOWER) "HOST" else "GUEST",
        rallyShotCount = rules.rallyShotCount,
        rallyHits = rallyHits,
        lastEvent = lastCallout,
        matchWinner = when (rules.matchWinner) {
            CourtSide.LOWER -> "HOST"
            CourtSide.UPPER -> "GUEST"
            null -> null
        },
        publishedAtMs = System.currentTimeMillis(),
        lastHitterSide = when (ball.lastHitterSide) {
            CourtSide.LOWER -> "HOST"
            CourtSide.UPPER -> "GUEST"
            null -> null
        },
        ballBouncesOnSide = ball.bouncesOnCurrentSide,
    )

    private fun stepGuest(dt: Float) {
        moveTowardTarget(localPlayer, HOST_PLAYER_SPEED_FT_S, dt)
        val lerpAlpha = min(1f, dt * 18f)
        remotePlayer.x += (interpRemoteX - remotePlayer.x) * lerpAlpha
        remotePlayer.y += (interpRemoteY - remotePlayer.y) * lerpAlpha
        ball.x += (interpBallX - ball.x) * lerpAlpha
        ball.y += (interpBallY - ball.y) * lerpAlpha
        ball.z += (interpBallZ - ball.z) * lerpAlpha

        if (localPlayer.swingTimer > 0f) localPlayer.swingTimer = max(0f, localPlayer.swingTimer - dt)
        if (remotePlayer.swingTimer > 0f) remotePlayer.swingTimer = max(0f, remotePlayer.swingTimer - dt)
    }

    private fun stepHost(dt: Float): SimTickOutput {
        tickCounter += 1L
        moveTowardTarget(localPlayer, HOST_PLAYER_SPEED_FT_S, dt)
        moveTowardTarget(remotePlayer, HOST_PLAYER_SPEED_FT_S, dt)
        if (localPlayer.swingTimer > 0f) localPlayer.swingTimer = max(0f, localPlayer.swingTimer - dt)
        if (remotePlayer.swingTimer > 0f) remotePlayer.swingTimer = max(0f, remotePlayer.swingTimer - dt)

        val noEvents = StepEvents(bounceX = ball.x, bounceY = ball.y)
        if (rallyPhase == RallyPhase.MATCH_OVER) return SimTickOutput(noEvents)

        if (rallyPhase == RallyPhase.WAITING_SERVE) {
            if (rules.servingSide == CourtSide.LOWER) {
                ball.x = localPlayer.x - 1.0f
                ball.y = localPlayer.y + 1.3f
                ball.z = 2.4f
            } else {
                ball.x = remotePlayer.x + 1.0f
                ball.y = remotePlayer.y - 1.3f
                ball.z = 2.4f
                val pending = pendingGuestSwipe
                if (pending != null) {
                    pendingGuestSwipe = null
                    val served = launchHostAuthoritativeServe(CourtSide.UPPER, pending)
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

        var guestHit: HitFeedback? = null
        val pending = pendingGuestSwipe
        if (pending != null) {
            pendingGuestSwipe = null
            val res = handleGuestSwingOnHost(pending)
            guestHit = res.hit
            if (res.fault != null) {
                return SimTickOutput(noEvents, pointResolution = res.fault)
            }
        }

        var hostAutoHit: HitFeedback? = null
        if (autoSwingEnabled && canLocalReachBall()) {
            val dist = hypot(ball.x - localPlayer.x, ball.y - localPlayer.y)
            val isVolley = ball.bouncesOnCurrentSide == 0
            val wouldFault = isVolley && (
                rules.rallyShotCount == 1 ||
                    rules.rallyShotCount == 2 ||
                    CourtSpec.isInKitchen(localPlayer.x, localPlayer.y, CourtSide.LOWER)
                )
            val leaving = dist > autoSwingPrevDist
            autoSwingPrevDist = dist
            if (!wouldFault && (dist <= LOCAL_HIT_REACH_FT * 0.7f || leaving)) {
                val res = handleHostSwipe(SwipeVector(0f, 6f, 0.15f))
                autoSwingPrevDist = Float.POSITIVE_INFINITY
                if (res.fault != null) return SimTickOutput(noEvents, pointResolution = res.fault)
                hostAutoHit = res.hit
            }
        } else {
            autoSwingPrevDist = Float.POSITIVE_INFINITY
        }

        val stepEvents = stepBallPhysics(ball, dt)

        if (stepEvents.netOutcome == NetCrossOutcome.HIT_NET) {
            val fault = applyFault(ball.lastHitterSide ?: CourtSide.LOWER, FaultReason.INTO_NET)
            return SimTickOutput(stepEvents, guestHit ?: hostAutoHit, fault)
        }

        if (stepEvents.bounced) {
            if (ball.bouncesOnCurrentSide == 1) {
                val bounceFault = rules.validateFirstBounce(
                    stepEvents.bounceX,
                    stepEvents.bounceY,
                    ball.lastHitterSide,
                )
                if (bounceFault != null) {
                    val fault = applyFault(ball.lastHitterSide ?: CourtSide.LOWER, bounceFault)
                    return SimTickOutput(stepEvents, guestHit ?: hostAutoHit, fault)
                }
            }
            if (ball.bouncesOnCurrentSide >= 2) {
                val receiver = if (ball.lastHitterSide == CourtSide.LOWER) CourtSide.UPPER else CourtSide.LOWER
                val fault = applyFault(receiver, FaultReason.DOUBLE_BOUNCE)
                return SimTickOutput(stepEvents, guestHit ?: hostAutoHit, fault)
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
                val fault = applyFault(receiver, FaultReason.DOUBLE_BOUNCE)
                return SimTickOutput(stepEvents, guestHit ?: hostAutoHit, fault)
            }
            val faulting = ball.lastHitterSide ?: CourtSide.LOWER
            val reason = if (rules.rallyShotCount == 1) FaultReason.SERVE_OUT_OF_BOX else FaultReason.OUT_OF_BOUNDS
            val fault = applyFault(faulting, reason)
            return SimTickOutput(stepEvents, guestHit ?: hostAutoHit, fault)
        }

        return SimTickOutput(stepEvents, guestHit ?: hostAutoHit)
    }

    private fun handleHostSwipe(swipe: SwipeVector): ShotOutcome {
        if (rallyPhase == RallyPhase.WAITING_SERVE && rules.servingSide == CourtSide.LOWER && swipe.dyFt > 1.0f) {
            return launchHostAuthoritativeServe(CourtSide.LOWER, swipe)
        }
        if (rallyPhase != RallyPhase.RALLY_LIVE || !ball.active || ball.lastHitterSide == CourtSide.LOWER) {
            return ShotOutcome()
        }
        val dist = hypot(ball.x - localPlayer.x, ball.y - localPlayer.y)
        if (dist > LOCAL_HIT_REACH_FT || ball.z > MAX_HIT_HEIGHT_FT) {
            return ShotOutcome()
        }

        val faultReason = rules.validatePlayerHit(
            CourtSide.LOWER,
            localPlayer.x,
            localPlayer.y,
            ball.bouncesOnCurrentSide,
        )
        if (faultReason != null) {
            return ShotOutcome(fault = applyFault(CourtSide.LOWER, faultReason))
        }

        val shotType = classifySwipe(swipe)
        val vel = computeShotVelocity(
            fromX = ball.x,
            fromY = ball.y,
            swipe = swipe,
            shotType = shotType,
            targetSide = CourtSide.UPPER,
            timingAccuracy = 0.9f,
            scatterSeed = (Random.nextFloat() * 2f - 1f) * 0.4f,
            fromZ = ball.z,
        )

        ball.vx = vel.vx
        ball.vy = vel.vy
        ball.vz = vel.vz
        ball.lastHitterSide = CourtSide.LOWER
        ball.crossedNetSinceHit = false
        ball.bouncesOnCurrentSide = 0
        localPlayer.swingTimer = 0.22f
        localPlayer.lastSwingType = shotType
        bestRally = max(bestRally, rules.rallyShotCount)
        rallyHits = if (rules.rallyShotCount >= 3) rules.rallyShotCount else 0
        lastShotLabel = "Host ${shotType.name}"
        lastCallout = "Host ${shotType.name}"

        return ShotOutcome(
            hit = HitFeedback(
                hitterSide = CourtSide.LOWER,
                shotType = shotType,
                timingAccuracy = 0.9f,
                hitX = ball.x,
                hitY = ball.y,
            ),
        )
    }

    private fun handleGuestSwingOnHost(swipe: SwipeVector): ShotOutcome {
        if (rallyPhase != RallyPhase.RALLY_LIVE || !ball.active || ball.lastHitterSide == CourtSide.UPPER) {
            return ShotOutcome()
        }
        val dist = hypot(ball.x - remotePlayer.x, ball.y - remotePlayer.y)
        if (dist > REMOTE_GENEROUS_REACH_FT || ball.z > MAX_HIT_HEIGHT_FT + 1.0f) {
            return ShotOutcome()
        }

        val faultReason = rules.validatePlayerHit(
            CourtSide.UPPER,
            remotePlayer.x,
            remotePlayer.y,
            ball.bouncesOnCurrentSide,
        )
        if (faultReason != null) {
            return ShotOutcome(fault = applyFault(CourtSide.UPPER, faultReason))
        }

        val shotType = classifySwipe(swipe)
        val vel = computeShotVelocity(
            fromX = ball.x,
            fromY = ball.y,
            swipe = swipe,
            shotType = shotType,
            targetSide = CourtSide.LOWER,
            timingAccuracy = 0.9f,
            scatterSeed = (Random.nextFloat() * 2f - 1f) * 0.4f,
            fromZ = ball.z,
        )

        ball.vx = vel.vx
        ball.vy = vel.vy
        ball.vz = vel.vz
        ball.lastHitterSide = CourtSide.UPPER
        ball.crossedNetSinceHit = false
        ball.bouncesOnCurrentSide = 0
        remotePlayer.swingTimer = 0.22f
        remotePlayer.lastSwingType = shotType
        bestRally = max(bestRally, rules.rallyShotCount)
        rallyHits = if (rules.rallyShotCount >= 3) rules.rallyShotCount else 0
        lastCallout = "Guest ${shotType.name}"

        return ShotOutcome(
            hit = HitFeedback(
                hitterSide = CourtSide.UPPER,
                shotType = shotType,
                timingAccuracy = 0.9f,
                hitX = ball.x,
                hitY = ball.y,
            ),
        )
    }

    private fun launchHostAuthoritativeServe(serverSide: CourtSide, swipe: SwipeVector): ShotOutcome {
        val assign = rules.currentServeAssignment()
        val server = if (serverSide == CourtSide.LOWER) localPlayer else remotePlayer
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

        val tof = 0.96f
        val invIntegral = 1f / horizontalDragIntegral(tof)
        val aimOffset = max(-2.8f, min(2.8f, swipe.dxFt * 0.45f))
        ball.vx = (targetX + aimOffset - ball.x) * invIntegral
        ball.vy = (targetY - ball.y) * invIntegral
        ball.vz = -0.5f * BallPhysicsConstants.GRAVITY_FT_S2 * tof - ball.z / tof
        ball.active = true
        ball.lastHitterSide = serverSide
        ball.crossedNetSinceHit = false
        ball.bouncesOnCurrentSide = 0

        server.swingTimer = 0.24f
        server.lastSwingType = ShotType.SERVE
        rallyPhase = RallyPhase.RALLY_LIVE
        rallyHits = 0
        bestRally = max(bestRally, 1)
        lastCallout = "Serve!"

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

    private fun applyFault(faultingSide: CourtSide, reason: FaultReason): PointResolution? {
        if (rallyPhase == RallyPhase.POINT_PAUSE || rallyPhase == RallyPhase.MATCH_OVER) return null
        val res = rules.resolveFault(faultingSide, reason)
        lastFaultReason = reason
        scoreLocal = rules.scoreLower
        scoreRemote = rules.scoreUpper
        lastCallout = res.calloutText
        rallyPhase = if (res.matchWinner != null) RallyPhase.MATCH_OVER else RallyPhase.POINT_PAUSE
        matchWinnerLocal = when (res.matchWinner) {
            CourtSide.LOWER -> true
            CourtSide.UPPER -> false
            null -> null
        }
        pauseTimerSec = POINT_PAUSE_SEC
        ball.active = false
        return res
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
