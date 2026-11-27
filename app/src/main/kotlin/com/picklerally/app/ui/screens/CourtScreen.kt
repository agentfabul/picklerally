package com.picklerally.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseUser
import com.picklerally.app.R
import com.picklerally.app.data.FinishedMatchInput
import com.picklerally.app.data.NearbyConnectionsManager
import com.picklerally.app.data.StatsRepository
import com.picklerally.app.game.BotDifficulty
import com.picklerally.app.game.CharacterId
import com.picklerally.app.game.CourtSide
import com.picklerally.app.game.CourtSpec
import com.picklerally.app.game.DoublesMatch
import com.picklerally.app.game.HitFeedback
import com.picklerally.app.game.MatchFormatConfig
import com.picklerally.app.game.MatchRecordMode
import com.picklerally.app.game.OnlineMatch1v1
import com.picklerally.app.game.PlayerHitState
import com.picklerally.app.game.PointResolution
import com.picklerally.app.game.RallyPhase
import com.picklerally.app.game.ScoringMode
import com.picklerally.app.game.ShotType
import com.picklerally.app.game.SinglePlayerMatch
import com.picklerally.app.game.SoundFxEngine
import com.picklerally.app.game.SwipeVector
import com.picklerally.app.game.characterPalette
import com.picklerally.app.ui.components.CharacterPortrait
import com.picklerally.app.ui.components.CourtRenderScene
import com.picklerally.app.ui.components.CourtViewportMetrics
import com.picklerally.app.ui.components.Particle
import com.picklerally.app.ui.components.drawCourtScene
import com.picklerally.app.ui.theme.AmberGold
import com.picklerally.app.ui.theme.BorderSlate
import com.picklerally.app.ui.theme.CourtApron
import com.picklerally.app.ui.theme.GoldBall
import com.picklerally.app.ui.theme.LimeAccent
import com.picklerally.app.ui.theme.NavyCard
import com.picklerally.app.ui.theme.NavyDeep
import com.picklerally.app.ui.theme.OrangeAccent
import com.picklerally.app.ui.theme.RoseDarkBg
import com.picklerally.app.ui.theme.RoseFault
import com.picklerally.app.ui.theme.SkyAccent
import com.picklerally.app.ui.theme.TextPrimary
import com.picklerally.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class CourtMatchKind {
    SOLO,
    DOUBLES,
    NEARBY,
}

private enum class MatchOverlayState {
    PLAYING,
    PAUSED,
    GAME_OVER,
}

private enum class MatchSaveState {
    IDLE,
    SAVING,
    CLOUD,
    LOCAL,
}

data class CourtHudState(
    val rallyPhase: RallyPhase = RallyPhase.WAITING_SERVE,
    val scoreLower: Int = 0,
    val scoreUpper: Int = 0,
    val servingSide: CourtSide = CourtSide.LOWER,
    val serverNumberCall: Int = 1,
    val serverIndexInTeam: Int = 0,
    val formattedScoreCall: String = "0 - 0",
    val rallyShotCount: Int = 0,
    val ballBouncesOnSide: Int = 0,
    val botServeCountdownSec: Int = 0,
    val rallyHits: Int = 0,
    val bestRally: Int = 0,
    val lastCallout: String = "Your serve (0 - 0)",
    val lastShotLabel: String = "",
    val inReach: Boolean = false,
    val matchWinner: CourtSide? = null,
    val hostDisconnected: Boolean = false,
    val matchStatus: String = "",
    val gamesLower: Int = 0,
    val gamesUpper: Int = 0,
    val gameScoresSummary: String = "",
)

@Composable
fun CourtScreen(
    matchKind: CourtMatchKind,
    scoringMode: ScoringMode,
    pointsToWin: Int,
    bestOf: Int,
    difficulty: BotDifficulty,
    playerChar: CharacterId,
    partnerChar: CharacterId,
    opponentChar: CharacterId,
    muted: Boolean,
    hasSeenControlsHint: Boolean,
    authUser: FirebaseUser?,
    soundFx: SoundFxEngine,
    statsRepository: StatsRepository,
    nearbyManager: NearbyConnectionsManager,
    nearbyIsHost: Boolean,
    nearbyRemoteChar: CharacterId,
    nearbyOpponentConnected: Boolean,
    onToggleMute: () -> Unit,
    onControlsHintSeen: () -> Unit,
    onOpenCharacterSelect: () -> Unit,
    onExitToMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var overlayState by remember { mutableStateOf(MatchOverlayState.PLAYING) }
    var showDimensions by remember { mutableStateOf(false) }
    var saveState by remember { mutableStateOf(MatchSaveState.IDLE) }
    var matchSaveTriggered by remember { mutableStateOf(false) }
    var resetNonce by remember { mutableIntStateOf(0) }
    var frameTicker by remember { mutableIntStateOf(0) }

    val soloMatch = remember(resetNonce, matchKind, scoringMode, pointsToWin, bestOf, difficulty, playerChar, opponentChar) {
        if (matchKind == CourtMatchKind.SOLO) {
            SinglePlayerMatch(
                playerChar = playerChar,
                botChar = opponentChar,
                scoringMode = scoringMode,
                format = MatchFormatConfig(pointsToWin, bestOf, difficulty),
            )
        } else {
            null
        }
    }

    val doublesMatch = remember(resetNonce, matchKind, scoringMode, difficulty, playerChar, partnerChar, opponentChar) {
        if (matchKind == CourtMatchKind.DOUBLES) {
            DoublesMatch(
                playerChar = playerChar,
                partnerChar = partnerChar,
                upperRightChar = opponentChar,
                upperLeftChar = if (opponentChar == CharacterId.BERT) CharacterId.LOLA else CharacterId.BERT,
                scoringMode = scoringMode,
                difficulty = difficulty,
            )
        } else {
            null
        }
    }

    val onlineMatch = remember(resetNonce, matchKind, scoringMode, nearbyIsHost, playerChar, nearbyRemoteChar) {
        if (matchKind == CourtMatchKind.NEARBY) {
            OnlineMatch1v1(
                isHost = nearbyIsHost,
                localUid = if (nearbyIsHost) "nearby-host" else "nearby-guest",
                localChar = playerChar,
                remoteChar = nearbyRemoteChar,
                scoringMode = scoringMode,
            )
        } else {
            null
        }
    }

    // Wire Nearby callbacks to OnlineMatch1v1
    DisposableEffect(onlineMatch) {
        if (onlineMatch != null) {
            nearbyManager.onRemoteInput = { input -> onlineMatch.applyRemoteGuestInput(input) }
            nearbyManager.onHostSnapshot = { snap -> onlineMatch.applyHostSnapshot(snap) }
            nearbyManager.onRoomStatusChange = { status -> onlineMatch.onRoomMetaUpdated(status) }
            nearbyManager.onRemoteCharacterReceived = { ch -> onlineMatch.remotePlayer.characterId = ch }
        }
        onDispose {
            nearbyManager.onRemoteInput = null
            nearbyManager.onHostSnapshot = null
            nearbyManager.onRoomStatusChange = null
            nearbyManager.onRemoteCharacterReceived = null
        }
    }

    var hud by remember(resetNonce, matchKind) { mutableStateOf(CourtHudState()) }

    // Visual animation state kept across frames without allocations
    val particles = remember { List(28) { Particle() } }
    var elapsedSec by remember { mutableStateOf(0f) }
    var hitstopTimer by remember { mutableStateOf(0f) }
    var shakeIntensity by remember { mutableStateOf(0f) }
    var calloutAlpha by remember { mutableStateOf(0f) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }

    var pointerActive by remember { mutableStateOf(false) }
    var swipeConsumed by remember { mutableStateOf(false) }
    var swipeStartX by remember { mutableStateOf(0f) }
    var swipeStartY by remember { mutableStateOf(0f) }
    var swipeCurX by remember { mutableStateOf(0f) }
    var swipeCurY by remember { mutableStateOf(0f) }
    var swipeStartTime by remember { mutableStateOf(0f) }
    var lastInputPushSec by remember { mutableStateOf(0f) }

    fun spawnBurst(wx: Float, wy: Float, color: Color, count: Int, speed: Float) {
        var spawned = 0
        for (p in particles) {
            if (spawned >= count) break
            if (p.life <= 0f) {
                val angle = ((PI * 2.0 * spawned) / count).toFloat() + Random.nextFloat() * 0.5f
                val mag = speed * (0.55f + Random.nextFloat() * 0.65f)
                p.x = wx
                p.y = wy
                p.vx = cos(angle) * mag
                p.vy = sin(angle) * mag
                p.maxLife = 0.24f + Random.nextFloat() * 0.14f
                p.life = p.maxLife
                p.color = color
                spawned += 1
            }
        }
    }

    fun triggerHitEffects(shotType: ShotType, hitX: Float, hitY: Float, isUpper: Boolean) {
        soundFx.playPaddleHit(shotType, isUpper)
        calloutAlpha = 1f
        when (shotType) {
            ShotType.DRIVE -> {
                hitstopTimer = 0.035f
                shakeIntensity = 0.24f
                spawnBurst(hitX, hitY, OrangeAccent, 10, 8.5f)
            }
            ShotType.LOB -> spawnBurst(hitX, hitY, SkyAccent, 7, 5.5f)
            else -> spawnBurst(hitX, hitY, LimeAccent, 6, 4.2f)
        }
    }

    fun triggerFaultEffects(resolution: PointResolution) {
        soundFx.playPointEnd(resolution.calloutText)
        when {
            resolution.matchWinner != null -> soundFx.playMatchVictory(resolution.matchWinner == CourtSide.LOWER)
            resolution.pointAwardedTo != null -> soundFx.playPointScored(resolution.pointAwardedTo == CourtSide.LOWER)
        }
        calloutAlpha = 1.25f
        shakeIntensity = 0.22f
    }

    fun pushMove(wx: Float, wy: Float, force: Boolean) {
        if (onlineMatch != null) {
            val inp = onlineMatch.setLocalMoveTarget(wx, wy)
            if (!onlineMatch.isHost && (force || elapsedSec - lastInputPushSec >= 0.045f)) {
                lastInputPushSec = elapsedSec
                nearbyManager.sendInput(inp)
            }
        } else if (doublesMatch != null) {
            doublesMatch.setPlayerMoveTarget(wx, wy)
        } else {
            soloMatch?.setPlayerMoveTarget(wx, wy)
        }
    }

    fun attemptSwipe(dx: Float, dy: Float, duration: Float): Boolean {
        if (onlineMatch != null) {
            val res = onlineMatch.triggerLocalSwipe(SwipeVector(dx, dy, duration))
            res.guestInput?.let { nearbyManager.sendInput(it) }
            res.hit?.let {
                triggerHitEffects(it.shotType, it.hitX, it.hitY, false)
                return true
            }
            res.fault?.let {
                triggerFaultEffects(it)
                return true
            }
            return false
        }
        val res = doublesMatch?.attemptPlayerSwipeHit(SwipeVector(dx, dy, duration))
            ?: soloMatch?.attemptPlayerSwipeHit(SwipeVector(dx, dy, duration))
            ?: return false
        res.hit?.let {
            triggerHitEffects(it.shotType, it.hitX, it.hitY, false)
            return true
        }
        res.fault?.let {
            triggerFaultEffects(it)
            return true
        }
        return false
    }

    fun triggerServeButton() {
        onControlsHintSeen()
        if (onlineMatch != null) {
            if (onlineMatch.rallyPhase == RallyPhase.WAITING_SERVE && onlineMatch.servingLocal) {
                val res = onlineMatch.triggerLocalSwipe(SwipeVector(0f, 3.4f, 0.2f))
                res.guestInput?.let { nearbyManager.sendInput(it) }
                res.hit?.let { triggerHitEffects(ShotType.SERVE, it.hitX, it.hitY, false) }
                res.fault?.let { triggerFaultEffects(it) }
            }
            return
        }
        val res = doublesMatch?.launchServe() ?: soloMatch?.launchServe() ?: return
        res.hit?.let { triggerHitEffects(ShotType.SERVE, it.hitX, it.hitY, false) }
        res.fault?.let { triggerFaultEffects(it) }
    }

    fun triggerReceiveButton() {
        val res = doublesMatch?.triggerReadyAiServe() ?: soloMatch?.triggerReadyBotServe() ?: return
        res.hit?.let { triggerHitEffects(ShotType.SERVE, it.hitX, it.hitY, true) }
        res.fault?.let { triggerFaultEffects(it) }
    }

    fun leaveCourt() {
        if (matchKind == CourtMatchKind.NEARBY) {
            nearbyManager.leaveSession()
        }
        onExitToMenu()
    }

    BackHandler {
        if (overlayState == MatchOverlayState.PLAYING) {
            overlayState = MatchOverlayState.PAUSED
        } else if (overlayState == MatchOverlayState.PAUSED) {
            overlayState = MatchOverlayState.PLAYING
        } else {
            leaveCourt()
        }
    }

    // 60 fps simulation frame loop
    LaunchedEffect(overlayState, resetNonce, matchKind) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos == 0L) {
                    lastNanos = frameNanos
                }
                val rawDt = (frameNanos - lastNanos) / 1_000_000_000f
                lastNanos = frameNanos
                val dt = min(rawDt.coerceAtLeast(0f), 0.05f)

                if (overlayState == MatchOverlayState.PLAYING) {
                    elapsedSec += dt
                    if (hitstopTimer > 0f) {
                        hitstopTimer = max(0f, hitstopTimer - dt)
                    } else if (onlineMatch != null) {
                        val res = onlineMatch.update(dt)
                        if (res.snapshotToPublish != null && onlineMatch.isHost) {
                            nearbyManager.publishState(res.snapshotToPublish)
                        }
                        if (res.stepEvents.bounced) {
                            soundFx.playBounce(res.stepEvents.bounceZSpeed)
                            spawnBurst(res.stepEvents.bounceX, res.stepEvents.bounceY, Color.White, 4, 2.5f)
                        }
                        res.hitFeedback?.let {
                            triggerHitEffects(it.shotType, it.hitX, it.hitY, it.hitterSide == CourtSide.UPPER)
                        }
                        res.pointResolution?.let { triggerFaultEffects(it) }
                    } else {
                        val ticks = doublesMatch?.update(dt) ?: soloMatch?.update(dt).orEmpty()
                        for (tick in ticks) {
                            if (tick.stepEvents.bounced) {
                                soundFx.playBounce(tick.stepEvents.bounceZSpeed)
                                spawnBurst(tick.stepEvents.bounceX, tick.stepEvents.bounceY, Color.White, 4, 2.5f)
                            }
                            tick.hitFeedback?.let {
                                triggerHitEffects(it.shotType, it.hitX, it.hitY, it.hitterSide == CourtSide.UPPER)
                            }
                            tick.pointResolution?.let { triggerFaultEffects(it) }
                        }
                    }

                    if (shakeIntensity > 0f) shakeIntensity = max(0f, shakeIntensity - dt * 1.8f)
                    if (calloutAlpha > 0f) calloutAlpha = max(0f, calloutAlpha - dt * 0.72f)
                    for (p in particles) {
                        if (p.life > 0f) {
                            p.x += p.vx * dt
                            p.y += p.vy * dt
                            p.life -= dt
                        }
                    }
                }

                // Sync HUD
                val activePhase = onlineMatch?.rallyPhase ?: doublesMatch?.rallyPhase ?: soloMatch?.rallyPhase ?: RallyPhase.WAITING_SERVE
                val scoreLower = onlineMatch?.scoreLocal ?: doublesMatch?.rules?.scoreLower ?: soloMatch?.rules?.scoreLower ?: 0
                val scoreUpper = onlineMatch?.scoreRemote ?: doublesMatch?.rules?.scoreUpper ?: soloMatch?.rules?.scoreUpper ?: 0
                val servingSide = when {
                    onlineMatch != null -> if (onlineMatch.servingLocal) CourtSide.LOWER else CourtSide.UPPER
                    doublesMatch != null -> doublesMatch.rules.servingSide
                    soloMatch != null -> soloMatch.rules.servingSide
                    else -> CourtSide.LOWER
                }
                val botTimer = when {
                    activePhase != RallyPhase.WAITING_SERVE || onlineMatch != null -> 0f
                    doublesMatch != null -> doublesMatch.aiServeTimerSec
                    soloMatch != null -> soloMatch.botServeDelaySec
                    else -> 0f
                }
                val matchWinner = when {
                    onlineMatch != null -> when (onlineMatch.matchWinnerLocal) {
                        true -> CourtSide.LOWER
                        false -> CourtSide.UPPER
                        null -> null
                    }
                    doublesMatch != null -> doublesMatch.rules.matchWinner
                    soloMatch != null -> soloMatch.rules.matchWinner
                    else -> null
                }
                val nextHud = CourtHudState(
                    rallyPhase = activePhase,
                    scoreLower = scoreLower,
                    scoreUpper = scoreUpper,
                    servingSide = servingSide,
                    serverNumberCall = onlineMatch?.let { 1 } ?: doublesMatch?.rules?.serverNumberCall ?: 1,
                    serverIndexInTeam = onlineMatch?.let { 0 } ?: doublesMatch?.activeServerIndex() ?: 0,
                    formattedScoreCall = onlineMatch?.let { "$scoreLower - $scoreUpper" }
                        ?: doublesMatch?.rules?.formattedScoreCall()
                        ?: soloMatch?.rules?.formattedScoreCall()
                        ?: "0 - 0",
                    rallyShotCount = onlineMatch?.rules?.rallyShotCount
                        ?: doublesMatch?.rules?.rallyShotCount
                        ?: soloMatch?.rules?.rallyShotCount ?: 0,
                    ballBouncesOnSide = onlineMatch?.ball?.bouncesOnCurrentSide
                        ?: doublesMatch?.ball?.bouncesOnCurrentSide
                        ?: soloMatch?.ball?.bouncesOnCurrentSide ?: 0,
                    botServeCountdownSec = if (botTimer > 0f) ceil(botTimer).toInt() else 0,
                    rallyHits = onlineMatch?.rallyHits ?: doublesMatch?.rallyHits ?: soloMatch?.rallyHits ?: 0,
                    bestRally = onlineMatch?.bestRally ?: doublesMatch?.bestRally ?: soloMatch?.bestRally ?: 0,
                    lastCallout = onlineMatch?.lastCallout ?: doublesMatch?.lastCallout ?: soloMatch?.lastCallout ?: "",
                    lastShotLabel = onlineMatch?.lastShotLabel ?: doublesMatch?.lastShotLabel ?: soloMatch?.lastShotLabel ?: "",
                    inReach = onlineMatch?.canLocalReachBall() ?: doublesMatch?.canPlayerReachBall() ?: soloMatch?.canPlayerReachBall() ?: false,
                    matchWinner = matchWinner,
                    hostDisconnected = onlineMatch?.hostDisconnected ?: false,
                    matchStatus = if (soloMatch != null && soloMatch.bestOf == 3) {
                        "G${soloMatch.gameNumber} · ${soloMatch.gamesLower}-${soloMatch.gamesUpper}"
                    } else {
                        ""
                    },
                    gamesLower = soloMatch?.gamesLower ?: 0,
                    gamesUpper = soloMatch?.gamesUpper ?: 0,
                    gameScoresSummary = if (soloMatch != null && soloMatch.bestOf == 3) {
                        soloMatch.gameScores.joinToString(", ")
                    } else {
                        ""
                    },
                )
                if (nextHud != hud) {
                    hud = nextHud
                }
                if (activePhase == RallyPhase.RALLY_LIVE || scoreLower > 0 || scoreUpper > 0) {
                    onControlsHintSeen()
                }
                if ((matchWinner != null || nextHud.hostDisconnected) && overlayState != MatchOverlayState.GAME_OVER) {
                    overlayState = MatchOverlayState.GAME_OVER
                    if (matchWinner != null && !matchSaveTriggered && matchKind != CourtMatchKind.NEARBY) {
                        matchSaveTriggered = true
                        saveState = MatchSaveState.SAVING
                        val mode = if (matchKind == CourtMatchKind.DOUBLES) {
                            MatchRecordMode.SOLO_2V2
                        } else {
                            MatchRecordMode.SOLO_1V1
                        }
                        scope.launch {
                            val outcome = statsRepository.recordFinishedMatch(
                                user = authUser,
                                input = FinishedMatchInput(
                                    mode = mode,
                                    scoringMode = scoringMode,
                                    playerCharacter = playerChar,
                                    partnerCharacter = if (mode == MatchRecordMode.SOLO_2V2) partnerChar else null,
                                    opponentCharacter = opponentChar,
                                    winnerSide = matchWinner,
                                    scoreLower = scoreLower,
                                    scoreUpper = scoreUpper,
                                    bestRally = nextHud.bestRally,
                                ),
                            ).getOrNull()
                            saveState = if (outcome?.savedToCloud == true) MatchSaveState.CLOUD else MatchSaveState.LOCAL
                        }
                    }
                }
                frameTicker += 1
            }
        }
    }

    val playerPalette = characterPalette(playerChar)
    val partnerPalette = characterPalette(partnerChar)
    val opponentPalette = characterPalette(
        if (matchKind == CourtMatchKind.NEARBY) nearbyRemoteChar else opponentChar,
    )
    val isDoubles = matchKind == CourtMatchKind.DOUBLES
    val isNearby = matchKind == CourtMatchKind.NEARBY

    val localLabel = when {
        isNearby -> if (nearbyIsHost) "Host" else "Guest"
        isDoubles -> "Your Team"
        else -> playerPalette.name
    }
    val remoteLabel = when {
        isNearby -> if (nearbyIsHost) "Guest" else "Host"
        isDoubles -> "Opponents"
        else -> opponentPalette.name
    }
    val scoreLocalLabel = if (isDoubles) "Us" else localLabel
    val scoreRemoteLabel = if (isDoubles) "Them" else remoteLabel

    val rulePhaseLabel = when {
        isNearby -> when {
            hud.hostDisconnected -> "Host disconnected"
            !nearbyOpponentConnected && nearbyIsHost -> "Waiting for nearby player…"
            else -> hud.lastShotLabel.ifBlank { "Nearby 1v1" }
        }
        hud.rallyPhase == RallyPhase.POINT_PAUSE -> hud.lastCallout.ifBlank { "Next point" }
        hud.rallyPhase == RallyPhase.WAITING_SERVE -> when {
            hud.servingSide == CourtSide.LOWER && isDoubles && hud.serverIndexInTeam != 0 ->
                "${partnerPalette.name} serving…${if (hud.botServeCountdownSec > 0) " ${hud.botServeCountdownSec}s" else ""}"
            hud.servingSide == CourtSide.LOWER ->
                "Serve diagonally · ${if (scoringMode == ScoringMode.RALLY) "Rally" else "Side-Out"} to ${if (!isDoubles) pointsToWin else 11}"
            else ->
                "$remoteLabel serving…${if (hud.botServeCountdownSec > 0) " ${hud.botServeCountdownSec}s" else ""}"
        }
        hud.rallyShotCount == 1 ->
            if (hud.ballBouncesOnSide == 0) "Return must bounce" else "Serve bounced · Hit return"
        hud.rallyShotCount == 2 -> "3rd shot must bounce"
        hud.inReach -> "In reach · Swipe!"
        isDoubles -> "2v2 · ${hud.formattedScoreCall}"
        else -> hud.lastShotLabel.ifBlank { "Open rally" }
    }

    val isFaultCallout = hud.lastCallout.contains("fault", ignoreCase = true) ||
        hud.lastCallout.contains("Net!") ||
        hud.lastCallout.contains("Out!") ||
        hud.lastCallout.contains("Double bounce!")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CourtApron),
    ) {
        // Top HUD Bar
        Surface(
            color = NavyDeep,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "$scoreLocalLabel ${hud.scoreLower}",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (hud.servingSide == CourtSide.LOWER) LimeAccent else TextPrimary,
                        )
                        Text(text = "–", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                        Text(
                            text = "${hud.scoreUpper} $scoreRemoteLabel",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (hud.servingSide == CourtSide.UPPER) OrangeAccent else TextPrimary,
                        )
                        if (hud.matchStatus.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.White.copy(alpha = 0.1f),
                            ) {
                                Text(
                                    text = hud.matchStatus,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Text(
                        text = rulePhaseLabel,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isNearby) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NavyCard,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(Icons.Filled.Wifi, contentDescription = null, tint = LimeAccent, modifier = Modifier.size(14.dp))
                                Text("Nearby", style = MaterialTheme.typography.labelSmall, color = LimeAccent)
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showDimensions = !showDimensions },
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("court_dimensions_button"),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (showDimensions) LimeAccent else Color.White.copy(alpha = 0.15f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (showDimensions) LimeAccent else NavyCard,
                                contentColor = if (showDimensions) NavyDeep else TextPrimary,
                            ),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Icon(Icons.Filled.Straighten, contentDescription = "Toggle dimensions", modifier = Modifier.size(18.dp))
                        }
                    }

                    OutlinedButton(
                        onClick = onToggleMute,
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("court_mute_button"),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = NavyCard,
                            contentColor = TextPrimary,
                        ),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(
                            imageVector = if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (muted) "Unmute" else "Mute",
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    OutlinedButton(
                        onClick = { overlayState = MatchOverlayState.PAUSED },
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("court_pause_button"),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = NavyCard,
                            contentColor = TextPrimary,
                        ),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(Icons.Filled.Pause, contentDescription = "Pause match", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Court Canvas Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { canvasSize = it }
                .pointerInput(resetNonce, matchKind) {
                    detectTapGestures { offset ->
                        if (overlayState != MatchOverlayState.PLAYING) return@detectTapGestures
                        val m = CourtViewportMetrics.compute(canvasSize.width.toFloat(), canvasSize.height.toFloat())
                        val (wx, wy) = m.screenToWorld(offset.x, offset.y)
                        if (wy <= CourtSpec.NET_Y + 2f) {
                            pushMove(wx, wy, true)
                        }
                    }
                }
                .pointerInput(resetNonce, matchKind) {
                    detectDragGestures(
                        onDragStart = { start ->
                            if (overlayState != MatchOverlayState.PLAYING) return@detectDragGestures
                            val m = CourtViewportMetrics.compute(canvasSize.width.toFloat(), canvasSize.height.toFloat())
                            val (wx, wy) = m.screenToWorld(start.x, start.y)
                            pointerActive = true
                            swipeConsumed = false
                            swipeStartX = wx
                            swipeStartY = wy
                            swipeCurX = wx
                            swipeCurY = wy
                            swipeStartTime = elapsedSec
                            if (wy <= CourtSpec.NET_Y + 2f) {
                                pushMove(wx, wy, true)
                            }
                        },
                        onDrag = { change, _ ->
                            if (!pointerActive || overlayState != MatchOverlayState.PLAYING) return@detectDragGestures
                            val m = CourtViewportMetrics.compute(canvasSize.width.toFloat(), canvasSize.height.toFloat())
                            val (wx, wy) = m.screenToWorld(change.position.x, change.position.y)
                            swipeCurX = wx
                            swipeCurY = wy
                            val dx = wx - swipeStartX
                            val dy = wy - swipeStartY
                            val dist = hypot(dx, dy)
                            val duration = max(0.04f, elapsedSec - swipeStartTime)
                            val speed = dist / duration
                            if (!swipeConsumed && dist >= 1.25f && speed >= 7.2f) {
                                if (attemptSwipe(dx, dy, duration)) {
                                    swipeConsumed = true
                                    return@detectDragGestures
                                }
                            }
                            if (wy <= CourtSpec.NET_Y + 2f) {
                                pushMove(wx, wy, false)
                            }
                        },
                        onDragEnd = {
                            if (pointerActive && overlayState == MatchOverlayState.PLAYING && !swipeConsumed) {
                                val dx = swipeCurX - swipeStartX
                                val dy = swipeCurY - swipeStartY
                                val dist = hypot(dx, dy)
                                val duration = max(0.04f, elapsedSec - swipeStartTime)
                                if (dist >= 1.1f) {
                                    attemptSwipe(dx, dy, duration)
                                }
                            }
                            pointerActive = false
                            swipeConsumed = false
                        },
                        onDragCancel = {
                            pointerActive = false
                            swipeConsumed = false
                        },
                    )
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Read frameTicker so Compose redraws smoothly each frame
                @Suppress("UNUSED_EXPRESSION")
                frameTicker

                val serveAssign = onlineMatch?.rules?.currentServeAssignment()
                    ?: doublesMatch?.rules?.currentServeAssignment()
                    ?: soloMatch?.rules?.currentServeAssignment()
                    ?: com.picklerally.app.game.ServeAssignment(
                        CourtSide.LOWER,
                        0,
                        1,
                        com.picklerally.app.game.ServiceBoxSide.RIGHT,
                        CourtSide.UPPER,
                        com.picklerally.app.game.ServiceBoxSide.LEFT,
                    )
                val upperPlayers = when {
                    onlineMatch != null -> listOf(onlineMatch.remotePlayer)
                    doublesMatch != null -> doublesMatch.upperTeam
                    soloMatch != null -> listOf(soloMatch.bot)
                    else -> emptyList()
                }
                val lowerPlayers = when {
                    onlineMatch != null -> listOf(onlineMatch.localPlayer)
                    doublesMatch != null -> doublesMatch.lowerTeam
                    soloMatch != null -> listOf(soloMatch.player)
                    else -> emptyList()
                }
                val ball = onlineMatch?.ball ?: doublesMatch?.ball ?: soloMatch?.ball ?: com.picklerally.app.game.BallState()
                val hitState = when {
                    onlineMatch != null -> PlayerHitState.NONE
                    doublesMatch != null -> doublesMatch.playerHitState()
                    soloMatch != null -> soloMatch.playerHitState()
                    else -> PlayerHitState.NONE
                }
                val shakeX = if (shakeIntensity > 0f) (Random.nextFloat() * 2f - 1f) * shakeIntensity else 0f
                val shakeY = if (shakeIntensity > 0f) (Random.nextFloat() * 2f - 1f) * shakeIntensity else 0f

                drawCourtScene(
                    CourtRenderScene(
                        rallyPhase = hud.rallyPhase,
                        serveAssignment = serveAssign,
                        mirrorServeHighlight = onlineMatch != null && !onlineMatch.isHost,
                        lastFaultReason = onlineMatch?.lastFaultReason
                            ?: doublesMatch?.lastFaultReason
                            ?: soloMatch?.lastFaultReason,
                        upperPlayers = upperPlayers,
                        lowerPlayers = lowerPlayers,
                        primaryLowerIndex = 0,
                        ball = ball,
                        inReach = hud.inReach,
                        hitState = hitState,
                        showDimensions = showDimensions,
                        elapsedSec = elapsedSec,
                        shakeX = shakeX,
                        shakeY = shakeY,
                        swipeActive = pointerActive && !swipeConsumed,
                        swipeStartX = swipeStartX,
                        swipeStartY = swipeStartY,
                        swipeCurX = swipeCurX,
                        swipeCurY = swipeCurY,
                        particles = particles,
                        lastCallout = hud.lastCallout,
                        calloutAlpha = calloutAlpha,
                    ),
                )
            }

            // First-serve hint
            if (!hasSeenControlsHint && !isNearby && overlayState == MatchOverlayState.PLAYING &&
                hud.rallyPhase == RallyPhase.WAITING_SERVE && hud.scoreLower == 0 && hud.scoreUpper == 0
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = NavyDeep.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, LimeAccent.copy(alpha = 0.55f)),
                    shadowElevation = 6.dp,
                ) {
                    Text(
                        text = "Tap to move · Stand in the ring to hit",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            // Point/Fault Announcement Banner
            if (hud.rallyPhase == RallyPhase.POINT_PAUSE && hud.lastCallout.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp, start = 16.dp, end = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isFaultCallout) RoseDarkBg.copy(alpha = 0.92f) else NavyDeep.copy(alpha = 0.92f),
                    border = BorderStroke(
                        1.dp,
                        if (isFaultCallout) RoseFault.copy(alpha = 0.5f) else LimeAccent.copy(alpha = 0.5f),
                    ),
                    shadowElevation = 6.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = hud.lastCallout,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isFaultCallout) TextPrimary else LimeAccent,
                        )
                        Text(
                            text = "$localLabel ${hud.scoreLower} – ${hud.scoreUpper} $remoteLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
            }

            // Pause Modal Overlay
            if (overlayState == MatchOverlayState.PAUSED) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NavyDeep.copy(alpha = 0.82f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = NavyCard,
                        border = BorderStroke(1.dp, BorderSlate),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = if (isNearby) "Nearby 1v1" else "Match Paused",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                            )
                            Text(
                                text = when {
                                    isNearby -> if (nearbyIsHost) {
                                        "Host runs official 60 Hz physics and syncs with guest."
                                    } else {
                                        "Guest predicts movement locally and syncs with Host."
                                    }
                                    isDoubles -> "2v2 Doubles (${playerPalette.name} & ${partnerPalette.name}) · ${if (scoringMode == ScoringMode.RALLY) "Rally" else "Side-Out"} to 11."
                                    else -> "${playerPalette.name} vs ${opponentPalette.name} · ${if (scoringMode == ScoringMode.RALLY) "Rally" else "Side-Out"} to $pointsToWin."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )

                            Spacer(Modifier.height(4.dp))

                            Button(
                                onClick = { overlayState = MatchOverlayState.PLAYING },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("resume_match_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LimeAccent,
                                    contentColor = NavyDeep,
                                ),
                            ) {
                                Icon(Icons.Filled.Fullscreen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.resume_match), style = MaterialTheme.typography.labelLarge)
                            }

                            if (!isNearby) {
                                OutlinedButton(
                                    onClick = {
                                        matchSaveTriggered = false
                                        saveState = MatchSaveState.IDLE
                                        resetNonce += 1
                                        overlayState = MatchOverlayState.PLAYING
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("restart_match_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, BorderSlate),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color(0xFF1E293B),
                                        contentColor = TextPrimary,
                                    ),
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.restart_match), style = MaterialTheme.typography.labelMedium)
                                }

                                OutlinedButton(
                                    onClick = onOpenCharacterSelect,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("pause_change_character_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, BorderSlate),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                ) {
                                    Text(stringResource(R.string.change_character), style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            OutlinedButton(
                                onClick = ::leaveCourt,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("pause_return_title_button"),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, BorderSlate),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                            ) {
                                Text(
                                    text = if (isNearby) "Leave Room" else stringResource(R.string.return_to_title),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }

            // Game Over Modal Overlay
            if (overlayState == MatchOverlayState.GAME_OVER) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NavyDeep.copy(alpha = 0.86f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = NavyCard,
                        border = BorderStroke(1.dp, BorderSlate),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (hud.hostDisconnected) {
                                Surface(
                                    modifier = Modifier.size(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = AmberGold.copy(alpha = 0.16f),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.EmojiEvents,
                                            contentDescription = null,
                                            tint = AmberGold,
                                        )
                                    }
                                }
                            } else {
                                CharacterPortrait(
                                    characterId = if (hud.matchWinner == CourtSide.LOWER) {
                                        playerChar
                                    } else {
                                        opponentPalette.id
                                    },
                                    size = 60.dp,
                                )
                            }

                            Text(
                                text = when {
                                    hud.hostDisconnected -> "Host Left Room"
                                    hud.matchWinner == CourtSide.LOWER -> "$localLabel Wins!"
                                    else -> "$remoteLabel Wins!"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                textAlign = TextAlign.Center,
                            )

                            if (hud.gameScoresSummary.isNotEmpty()) {
                                Text(
                                    text = "Games: ${hud.gamesLower} – ${hud.gamesUpper}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextPrimary,
                                )
                                Text(
                                    text = hud.gameScoresSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                            } else {
                                Text(
                                    text = "Score: ${hud.scoreLower} – ${hud.scoreUpper}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextPrimary,
                                )
                            }

                            Text(
                                text = if (hud.hostDisconnected) {
                                    "The host closed the room gracefully."
                                } else {
                                    "Longest rally: ${hud.bestRally} shots"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                            )

                            if (!hud.hostDisconnected && saveState != MatchSaveState.IDLE) {
                                Text(
                                    text = when (saveState) {
                                        MatchSaveState.SAVING -> "Saving match result..."
                                        MatchSaveState.CLOUD -> "Saved to Cloud History & Leaderboard"
                                        MatchSaveState.LOCAL -> "Saved to Local Match History"
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LimeAccent,
                                    textAlign = TextAlign.Center,
                                )
                            }

                            Spacer(Modifier.height(6.dp))

                            if (!isNearby) {
                                Button(
                                    onClick = {
                                        matchSaveTriggered = false
                                        saveState = MatchSaveState.IDLE
                                        resetNonce += 1
                                        overlayState = MatchOverlayState.PLAYING
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("play_again_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = LimeAccent,
                                        contentColor = NavyDeep,
                                    ),
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.play_again), style = MaterialTheme.typography.labelLarge)
                                }
                            }

                            OutlinedButton(
                                onClick = ::leaveCourt,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("game_over_return_button"),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, BorderSlate),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            ) {
                                Text(
                                    text = if (isNearby) "Back to Lobby" else stringResource(R.string.return_to_title),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Controls Bar
        Surface(
            color = NavyDeep,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                val canLocalServe = hud.rallyPhase == RallyPhase.WAITING_SERVE &&
                    hud.servingSide == CourtSide.LOWER &&
                    (!isDoubles || hud.serverIndexInTeam == 0)

                when {
                    canLocalServe -> {
                        Button(
                            onClick = ::triggerServeButton,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("serve_diagonal_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LimeAccent,
                                contentColor = NavyDeep,
                            ),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.serve_diagonal),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }

                    hud.rallyPhase == RallyPhase.WAITING_SERVE && !isNearby -> {
                        OutlinedButton(
                            onClick = ::triggerReceiveButton,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("receive_serve_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, OrangeAccent.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = NavyCard,
                                contentColor = TextPrimary,
                            ),
                        ) {
                            val servingText = buildString {
                                append(
                                    if (isDoubles && hud.servingSide == CourtSide.LOWER) {
                                        "${partnerPalette.name} serving…"
                                    } else {
                                        "$remoteLabel serving…"
                                    },
                                )
                                if (hud.botServeCountdownSec > 0) append(" ${hud.botServeCountdownSec}s")
                            }
                            Text(servingText, style = MaterialTheme.typography.labelMedium, color = OrangeAccent)
                            Text(" · ", color = TextSecondary)
                            Text(
                                text = if (isDoubles && hud.servingSide == CourtSide.LOWER) "Tap to start" else "Tap to receive",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary,
                            )
                        }
                    }

                    else -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = NavyCard,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val statusLeft = when {
                                    hud.rallyPhase == RallyPhase.WAITING_SERVE && isDoubles && hud.servingSide == CourtSide.LOWER ->
                                        "${partnerPalette.name} serving..."
                                    hud.servingSide == CourtSide.LOWER -> "$localLabel serving"
                                    else -> "$remoteLabel serving"
                                }
                                Text(
                                    text = if (hud.rallyPhase == RallyPhase.RALLY_LIVE) {
                                        "$statusLeft · $rulePhaseLabel"
                                    } else {
                                        statusLeft
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (hud.rallyPhase == RallyPhase.RALLY_LIVE) LimeAccent else TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (hud.rallyHits >= 3) {
                                    Text(
                                        text = "Rally ${hud.rallyHits}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
