package com.picklerally.core.sim

import com.picklerally.core.characters.CharacterId
import com.picklerally.core.court.CourtSide
import com.picklerally.core.court.CourtSpec
import com.picklerally.core.court.ServiceBoxSide
import com.picklerally.core.net.MatchStateSnapshot
import com.picklerally.core.net.PlayerInputSnapshot
import com.picklerally.core.net.RoomStatus
import com.picklerally.core.physics.BallPhysics
import com.picklerally.core.physics.BallState
import com.picklerally.core.physics.NetCrossOutcome
import com.picklerally.core.physics.ShotType
import com.picklerally.core.physics.SwipeVector
import com.picklerally.core.rules.FaultReason
import com.picklerally.core.rules.MatchMode
import com.picklerally.core.rules.RulesEngine
import com.picklerally.core.rules.ScoringMode
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Pure Kotlin 1v1 host-authoritative online match engine in core/.
 * - Host runs the official 60 Hz RulesEngine + BallPhysics simulation and emits ~20 Hz snapshots.
 * - Guest always plays at the bottom (LOWER in local view); coordinates mirror across court center.
 * - Guest predicts its own movement locally and smoothly interpolates ball & host opponent.
 */
class OnlineMatch1v1(
    val isHost: Boolean,
    val localUid: String,
    localChar: CharacterId = CharacterId.RIO,
    remoteChar: CharacterId = CharacterId.LOLA,
    scoringMode: ScoringMode = ScoringMode.RALLY
) {
    val rules = RulesEngine(scoringMode = scoringMode, matchMode = MatchMode.SINGLES)

    // In local screen view, local player is ALWAYS bottom (LOWER) and opponent is ALWAYS top (UPPER).
    val localPlayer = PlayerEntity(
        side = CourtSide.LOWER,
        characterId = localChar,
        x = CourtSpec.COURT_CENTER_X + 4.5f,
        y = CourtSpec.COURT_BOTTOM_Y - 1.1f,
        targetX = CourtSpec.COURT_CENTER_X + 4.5f,
        targetY = CourtSpec.COURT_BOTTOM_Y - 1.1f
    )

    val remotePlayer = PlayerEntity(
        side = CourtSide.UPPER,
        characterId = remoteChar,
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
    var lastCallout: String = "Waiting for opponent..."
        private set
    var hostDisconnected: Boolean = false
        private set

    // Host score / Guest score in canonical host-perspective coordinates
    var scoreLocal: Int = 0
        private set
    var scoreRemote: Int = 0
        private set
    var servingLocal: Boolean = isHost
        private set
    var matchWinnerLocal: Boolean? = null
        private set

    private var tickCounter: Long = 0L
    private var accumulator: Float = 0f
    private var publishAccumulator: Float = 0f
    private var pauseTimerSec: Float = 0f
    private var lastProcessedGuestSwingSeq: Long = -1L
    private var inputSeqCounter: Long = 0L

    // Guest interpolation targets received from host snapshots
    private var interpBallX = 14f
    private var interpBallY = 49f
    private var interpBallZ = 2.4f
    private var interpRemoteX = CourtSpec.COURT_CENTER_X - 4.5f
    private var interpRemoteY = CourtSpec.COURT_TOP_Y + 1.1f

    private var pendingGuestSwipe: SwipeVector? = null
    private val rng = Random(7)

    companion object {
        const val PUBLISH_INTERVAL_SEC = 0.05f // 20 Hz state sync
        const val HOST_PLAYER_SPEED_FT_S = 18.5f
        const val LOCAL_HIT_REACH_FT = 3.9f
        const val REMOTE_GENEROUS_REACH_FT = 4.75f // Latency compensation for guest
        const val POINT_PAUSE_SEC = 1.6f

        fun mirrorX(x: Float): Float = CourtSpec.WORLD_WIDTH_FT - x
        fun mirrorY(y: Float): Float = CourtSpec.WORLD_HEIGHT_FT - y
    }

    init {
        if (isHost) {
            setupPointForServe()
        }
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
        scoreLocal = rules.scoreLower
        scoreRemote = rules.scoreUpper
        servingLocal = assign.servingSide == CourtSide.LOWER

        val lowerX =
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

    fun setLocalMoveTarget(worldX: Float, worldY: Float): PlayerInputSnapshot {
        val maxY = if (rallyPhase == RallyPhase.WAITING_SERVE && servingLocal) {
            CourtSpec.COURT_BOTTOM_Y - 0.3f
        } else {
            CourtSpec.NET_Y - 0.6f
        }
        localPlayer.targetX = worldX.coerceIn(1.5f, CourtSpec.WORLD_WIDTH_FT - 1.5f)
        localPlayer.targetY = worldY.coerceIn(1.5f, maxY)

        inputSeqCounter += 1L
        return PlayerInputSnapshot(
            uid = localUid,
            sequence = inputSeqCounter,
            targetX = localPlayer.targetX,
            targetY = localPlayer.targetY
        )
    }

    fun triggerLocalSwipe(swipe: SwipeVector): PlayerInputSnapshot? {
        if (isHost) {
            handleHostSwipe(swipe)
            return null
        }

        // Guest sends raw swipe in its own bottom-perspective coordinates; Host mirrors it.
        inputSeqCounter += 1L
        val shotType = if (rallyPhase == RallyPhase.WAITING_SERVE && servingLocal) {
            "SERVE"
        } else {
            BallPhysics.classifySwipe(swipe).name
        }
        return PlayerInputSnapshot(
            uid = localUid,
            sequence = inputSeqCounter,
            targetX = localPlayer.targetX,
            targetY = localPlayer.targetY,
            swingType = shotType,
            aimX = swipe.dxFt,
            aimY = swipe.dyFt,
            swipeDurationSec = swipe.durationSec
        )
    }

    fun applyRemoteGuestInput(input: PlayerInputSnapshot) {
        if (!isHost) return
        // Mirror guest's bottom-perspective target into Host's upper court
        remotePlayer.targetX = mirrorX(input.targetX).coerceIn(1.5f, CourtSpec.WORLD_WIDTH_FT - 1.5f)
        remotePlayer.targetY = mirrorY(input.targetY).coerceIn(CourtSpec.NET_Y + 0.6f, CourtSpec.WORLD_HEIGHT_FT - 1.5f)

        if (input.swingType != null && input.sequence > lastProcessedGuestSwingSeq) {
            lastProcessedGuestSwingSeq = input.sequence
            pendingGuestSwipe = SwipeVector(
                dxFt = -input.aimX,
                dyFt = -input.aimY,
                durationSec = input.swipeDurationSec
            )
        }
    }

    fun applyHostSnapshot(snapshot: MatchStateSnapshot) {
        if (isHost) return

        // Mirror host-space coordinates so Guest always sees itself at bottom (LOWER)
        interpBallX = mirrorX(snapshot.ballX)
        interpBallY = mirrorY(snapshot.ballY)
        interpBallZ = snapshot.ballZ
        ball.active = snapshot.ballActive

        interpRemoteX = mirrorX(snapshot.hostX)
        interpRemoteY = mirrorY(snapshot.hostY)
        remotePlayer.swingTimer = snapshot.hostSwingTimer
        localPlayer.swingTimer = max(localPlayer.swingTimer, snapshot.guestSwingTimer)

        scoreLocal = snapshot.scoreGuest
        scoreRemote = snapshot.scoreHost
        servingLocal = snapshot.servingSide == "GUEST"
        rallyHits = snapshot.rallyHits
        lastCallout = snapshot.lastEvent ?: ""

        matchWinnerLocal = when (snapshot.matchWinner) {
            "GUEST" -> true
            "HOST" -> false
            else -> null
        }

        rallyPhase = when {
            matchWinnerLocal != null -> RallyPhase.MATCH_OVER
            !snapshot.ballActive && snapshot.rallyShotCount == 0 && (snapshot.lastEvent?.contains("fault") == true ||
                snapshot.lastEvent?.contains("Out") == true ||
                snapshot.lastEvent?.contains("Net") == true ||
                snapshot.lastEvent?.contains("bounce") == true) -> RallyPhase.POINT_PAUSE
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

    /**
     * Step 60 Hz simulation. Returns a MatchStateSnapshot when Host reaches a 20 Hz publish boundary.
     */
    fun update(rawDeltaSec: Float): MatchStateSnapshot? {
        if (hostDisconnected) return null

        val clampedDt = min(rawDeltaSec, 0.05f)
        accumulator += clampedDt
        var snapshotToPublish: MatchStateSnapshot? = null

        while (accumulator >= CourtSpec.FIXED_TIMESTEP_SEC) {
            if (isHost) {
                stepHost(CourtSpec.FIXED_TIMESTEP_SEC)
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

        return snapshotToPublish
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
        }
    )

    private fun stepGuest(dt: Float) {
        // Guest predicts own movement locally and smoothly interpolates opponent + ball
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

    private fun stepHost(dt: Float) {
        tickCounter += 1L
        moveTowardTarget(localPlayer, HOST_PLAYER_SPEED_FT_S, dt)
        moveTowardTarget(remotePlayer, HOST_PLAYER_SPEED_FT_S, dt)
        if (localPlayer.swingTimer > 0f) localPlayer.swingTimer = max(0f, localPlayer.swingTimer - dt)
        if (remotePlayer.swingTimer > 0f) remotePlayer.swingTimer = max(0f, remotePlayer.swingTimer - dt)

        if (rallyPhase == RallyPhase.MATCH_OVER) return

        if (rallyPhase == RallyPhase.WAITING_SERVE) {
            if (rules.servingSide == CourtSide.LOWER) {
                ball.x = localPlayer.x - 1.0f
                ball.y = localPlayer.y + 1.3f
                ball.z = 2.4f
            } else {
                ball.x = remotePlayer.x + 1.0f
                ball.y = remotePlayer.y - 1.3f
                ball.z = 2.4f
                pendingGuestSwipe?.let { swipe ->
                    pendingGuestSwipe = null
                    launchHostAuthoritativeServe(CourtSide.UPPER, swipe)
                }
            }
            return
        }

        if (rallyPhase == RallyPhase.POINT_PAUSE) {
            pauseTimerSec -= dt
            if (pauseTimerSec <= 0f) {
                setupPointForServe()
            }
            return
        }

        // Process pending guest swing with generous reach tolerance
        pendingGuestSwipe?.let { swipe ->
            pendingGuestSwipe = null
            handleGuestSwingOnHost(swipe)
        }

        val stepEvents = BallPhysics.step(ball, dt)

        if (stepEvents.netOutcome == NetCrossOutcome.HIT_NET) {
            applyFault(ball.lastHitterSide ?: CourtSide.LOWER, FaultReason.INTO_NET)
            return
        }

        if (stepEvents.bounced) {
            if (ball.bouncesOnCurrentSide == 1) {
                val bounceFault = rules.validateFirstBounce(stepEvents.bounceX, stepEvents.bounceY, ball.lastHitterSide)
                if (bounceFault != null) {
                    applyFault(ball.lastHitterSide ?: CourtSide.LOWER, bounceFault)
                    return
                }
            }
            if (ball.bouncesOnCurrentSide >= 2) {
                val receiver = if (ball.lastHitterSide == CourtSide.LOWER) CourtSide.UPPER else CourtSide.LOWER
                applyFault(receiver, FaultReason.DOUBLE_BOUNCE)
                return
            }
        }

        if (ball.x < -2f || ball.x > CourtSpec.WORLD_WIDTH_FT + 2f ||
            ball.y < -3f || ball.y > CourtSpec.WORLD_HEIGHT_FT + 3f
        ) {
            if (ball.crossedNetSinceHit && ball.bouncesOnCurrentSide >= 1) {
                val receiver = if (ball.lastHitterSide == CourtSide.LOWER) CourtSide.UPPER else CourtSide.LOWER
                applyFault(receiver, FaultReason.DOUBLE_BOUNCE)
                return
            }
            val faulting = ball.lastHitterSide ?: CourtSide.LOWER
            val fault = if (rules.rallyShotCount == 1) FaultReason.SERVE_OUT_OF_BOX else FaultReason.OUT_OF_BOUNDS
            applyFault(faulting, fault)
        }
    }

    private fun handleHostSwipe(swipe: SwipeVector) {
        if (rallyPhase == RallyPhase.WAITING_SERVE && rules.servingSide == CourtSide.LOWER && swipe.dyFt > 1.0f) {
            launchHostAuthoritativeServe(CourtSide.LOWER, swipe)
            return
        }
        if (rallyPhase != RallyPhase.RALLY_LIVE || !ball.active || ball.lastHitterSide == CourtSide.LOWER) return
        val dist = hypot(ball.x - localPlayer.x, ball.y - localPlayer.y)
        if (dist > LOCAL_HIT_REACH_FT || ball.z > SinglePlayerMatch.MAX_HIT_HEIGHT_FT) return

        val fault = rules.validatePlayerHit(CourtSide.LOWER, localPlayer.x, localPlayer.y, ball.bouncesOnCurrentSide)
        if (fault != null) {
            applyFault(CourtSide.LOWER, fault)
            return
        }

        val shotType = BallPhysics.classifySwipe(swipe)
        val (vx, vy, vz) = BallPhysics.computeShotVelocity(
            fromX = ball.x,
            fromY = ball.y,
            swipe = swipe,
            shotType = shotType,
            targetSide = CourtSide.UPPER,
            timingAccuracy = 0.9f,
            scatterSeed = rng.nextFloat() * 2f - 1f,
            fromZ = ball.z
        )
        ball.vx = vx
        ball.vy = vy
        ball.vz = vz
        ball.lastHitterSide = CourtSide.LOWER
        ball.crossedNetSinceHit = false
        ball.bouncesOnCurrentSide = 0
        localPlayer.swingTimer = 0.22f
        rallyHits = if (rules.rallyShotCount >= 3) rules.rallyShotCount else 0
        lastCallout = "Host ${shotType.label}"
    }

    private fun handleGuestSwingOnHost(swipe: SwipeVector) {
        if (rallyPhase != RallyPhase.RALLY_LIVE || !ball.active || ball.lastHitterSide == CourtSide.UPPER) return
        val dist = hypot(ball.x - remotePlayer.x, ball.y - remotePlayer.y)
        // Generous hit reach for remote guest to absorb network latency
        if (dist > REMOTE_GENEROUS_REACH_FT || ball.z > SinglePlayerMatch.MAX_HIT_HEIGHT_FT + 1.0f) return

        val fault = rules.validatePlayerHit(CourtSide.UPPER, remotePlayer.x, remotePlayer.y, ball.bouncesOnCurrentSide)
        if (fault != null) {
            applyFault(CourtSide.UPPER, fault)
            return
        }

        val shotType = BallPhysics.classifySwipe(swipe)
        val (vx, vy, vz) = BallPhysics.computeShotVelocity(
            fromX = ball.x,
            fromY = ball.y,
            swipe = swipe,
            shotType = shotType,
            targetSide = CourtSide.LOWER,
            timingAccuracy = 0.9f,
            scatterSeed = rng.nextFloat() * 2f - 1f,
            fromZ = ball.z
        )
        ball.vx = vx
        ball.vy = vy
        ball.vz = vz
        ball.lastHitterSide = CourtSide.UPPER
        ball.crossedNetSinceHit = false
        ball.bouncesOnCurrentSide = 0
        remotePlayer.swingTimer = 0.22f
        rallyHits = if (rules.rallyShotCount >= 3) rules.rallyShotCount else 0
        lastCallout = "Guest ${shotType.label}"
    }

    private fun launchHostAuthoritativeServe(serverSide: CourtSide, swipe: SwipeVector) {
        val assign = rules.currentServeAssignment()
        val server = if (serverSide == CourtSide.LOWER) localPlayer else remotePlayer
        val footFault = rules.beginServe(server.x, server.y)
        if (footFault != null) {
            applyFault(serverSide, footFault)
            return
        }

        val targetX = if (assign.targetBox == ServiceBoxSide.LEFT) CourtSpec.COURT_LEFT_X + 5.0f else CourtSpec.COURT_RIGHT_X - 5.0f
        val targetY = if (assign.targetCourtSide == CourtSide.UPPER) CourtSpec.UPPER_NVZ_Y + 7.8f else CourtSpec.LOWER_NVZ_Y - 7.8f
        val tof = 0.96f
        val aimOffset = (swipe.dxFt * 0.45f).coerceIn(-2.8f, 2.8f)

        ball.vx = ((targetX + aimOffset) - ball.x) / tof
        ball.vy = (targetY - ball.y) / tof
        ball.vz = (-0.5f * BallPhysics.GRAVITY_FT_S2 * tof) - (ball.z / tof)
        ball.active = true
        ball.lastHitterSide = serverSide
        ball.crossedNetSinceHit = false
        ball.bouncesOnCurrentSide = 0

        server.swingTimer = 0.24f
        rallyPhase = RallyPhase.RALLY_LIVE
        rallyHits = 0
        lastCallout = "Serve!"
    }

    private fun applyFault(faultingSide: CourtSide, reason: FaultReason) {
        if (rallyPhase == RallyPhase.POINT_PAUSE || rallyPhase == RallyPhase.MATCH_OVER) return
        val res = rules.resolveFault(faultingSide, reason)
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
