package com.picklerally.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseUser
import com.picklerally.app.R
import com.picklerally.app.data.DominantHand
import com.picklerally.app.data.FinishedMatchInput
import com.picklerally.app.data.NearbyConnectionsManager
import com.picklerally.app.data.OnlineRoomManager
import com.picklerally.app.data.OnlineSessionState
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
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

enum class CourtMatchKind {
    SOLO,
    DOUBLES,
    ONLINE,
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
    profileName: String = "",
    playerChar: CharacterId,
    partnerChar: CharacterId,
    opponentChar: CharacterId,
    muted: Boolean,
    dominantHand: DominantHand = DominantHand.RIGHT,
    hasSeenControlsHint: Boolean,
    authUser: FirebaseUser?,
    soundFx: SoundFxEngine,
    statsRepository: StatsRepository,
    nearbyManager: NearbyConnectionsManager,
    nearbyIsHost: Boolean,
    nearbyRemoteChar: CharacterId,
    nearbyOpponentConnected: Boolean,
    nearbyRoomCode: String = "",
    nearbyRemoteName: String = "",
    nearbyStatusMessage: String = "",
    nearbyError: String? = null,
    onlineRoomManager: OnlineRoomManager,
    onlineSession: OnlineSessionState,
    onlineError: String? = null,
    onToggleMute: () -> Unit,
    onSelectDominantHand: (DominantHand) -> Unit = {},
    onControlsHintSeen: () -> Unit,
    onOpenCharacterSelect: () -> Unit,
    onExitToMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var overlayState by remember { mutableStateOf(MatchOverlayState.PLAYING) }
    var showDimensions by remember { mutableStateOf(false) }
    var saveState by remember { mutableStateOf(MatchSaveState.IDLE) }
    var matchSaveTriggered by remember { mutableStateOf(false) }
    var resetNonce by remember { mutableIntStateOf(0) }
    var frameTicker by remember { mutableIntStateOf(0) }
    var topHudHeightPx by remember { mutableFloatStateOf(0f) }

    val customProfileLabel = profileName.trim().takeIf { it.isNotBlank() && it != "Local Player" }

    val soloMatch = remember(resetNonce, matchKind, scoringMode, pointsToWin, bestOf, difficulty, playerChar, opponentChar) {
        if (matchKind == CourtMatchKind.SOLO) {
            SinglePlayerMatch(
                playerChar = playerChar,
                botChar = opponentChar,
                scoringMode = scoringMode,
                format = MatchFormatConfig(pointsToWin, bestOf, difficulty),
            ).apply {
                player.displayName = customProfileLabel
            }
        } else {
            null
        }
    }

    LaunchedEffect(soloMatch, customProfileLabel) {
        soloMatch?.player?.displayName = customProfileLabel
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
            ).apply {
                player.displayName = customProfileLabel
            }
        } else {
            null
        }
    }

    LaunchedEffect(doublesMatch, customProfileLabel) {
        doublesMatch?.player?.displayName = customProfileLabel
    }

    val isDoubles = matchKind == CourtMatchKind.DOUBLES
    val isOnline = matchKind == CourtMatchKind.ONLINE
    val isNearby = matchKind == CourtMatchKind.NEARBY
    val isMultiplayer = isOnline || isNearby

    val mpIsHost = if (isOnline) onlineSession.isHost else nearbyIsHost
    val mpRemoteChar = if (isOnline) onlineSession.remoteChar else nearbyRemoteChar
    val mpOpponentConnected = if (isOnline) onlineSession.opponentConnected else nearbyOpponentConnected
    val mpRoomCode = if (isOnline) onlineSession.roomCode else nearbyRoomCode
    val mpRemoteName = if (isOnline) onlineSession.remoteName else nearbyRemoteName
    val mpStatusMessage = if (isOnline) onlineSession.statusMessage else nearbyStatusMessage
    val mpError = if (isOnline) onlineError else nearbyError

    val onlineMatch = remember(resetNonce, matchKind, scoringMode, mpIsHost, playerChar) {
        if (isMultiplayer) {
            val localDisp = customProfileLabel ?: characterPalette(playerChar).name
            val remoteDisp = mpRemoteName.ifBlank { characterPalette(mpRemoteChar).name }
            OnlineMatch1v1(
                isHost = mpIsHost,
                localUid = if (isOnline) {
                    onlineSession.localUid.ifBlank { if (mpIsHost) "online-host" else "online-guest" }
                } else {
                    if (mpIsHost) "nearby-host" else "nearby-guest"
                },
                localChar = playerChar,
                remoteChar = mpRemoteChar,
                scoringMode = scoringMode,
                localName = localDisp,
                remoteName = remoteDisp,
            )
        } else {
            null
        }
    }

    LaunchedEffect(onlineMatch, mpRemoteChar, mpRemoteName, customProfileLabel) {
        if (onlineMatch != null) {
            val localDisp = customProfileLabel ?: characterPalette(playerChar).name
            val remoteDisp = mpRemoteName.ifBlank { characterPalette(mpRemoteChar).name }
            onlineMatch.remotePlayer.characterId = mpRemoteChar
            onlineMatch.localDisplayName = localDisp
            onlineMatch.localPlayer.displayName = localDisp
            onlineMatch.remoteDisplayName = remoteDisp
            onlineMatch.remotePlayer.displayName = remoteDisp
        }
    }

    LaunchedEffect(onlineMatch, mpOpponentConnected, matchKind) {
        if (onlineMatch != null && onlineMatch.isHost && mpOpponentConnected) {
            val snap = onlineMatch.buildSnapshot()
            if (isOnline) {
                onlineRoomManager.publishState(snap, force = true)
            } else if (isNearby) {
                nearbyManager.publishState(snap)
            }
        }
    }

    DisposableEffect(matchKind) {
        onDispose {
            if (matchKind == CourtMatchKind.NEARBY) {
                nearbyManager.leaveSession()
            } else if (matchKind == CourtMatchKind.ONLINE) {
                onlineRoomManager.leaveRoom()
            }
        }
    }

    // Wire Online or Nearby callbacks to OnlineMatch1v1
    DisposableEffect(onlineMatch, matchKind) {
        if (onlineMatch != null && isOnline) {
            onlineRoomManager.onRemoteInput = { input -> onlineMatch.applyRemoteGuestInput(input) }
            onlineRoomManager.onHostSnapshot = { snap -> onlineMatch.applyHostSnapshot(snap) }
            onlineRoomManager.onRoomStatusChange = { status -> onlineMatch.onRoomMetaUpdated(status) }
            onlineRoomManager.onRemotePeerInfoReceived = { ch, name ->
                onlineMatch.remotePlayer.characterId = ch
                val disp = name.ifBlank { characterPalette(ch).name }
                onlineMatch.remoteDisplayName = disp
                onlineMatch.remotePlayer.displayName = disp
            }
        } else if (onlineMatch != null && isNearby) {
            nearbyManager.onRemoteInput = { input -> onlineMatch.applyRemoteGuestInput(input) }
            nearbyManager.onHostSnapshot = { snap -> onlineMatch.applyHostSnapshot(snap) }
            nearbyManager.onRoomStatusChange = { status -> onlineMatch.onRoomMetaUpdated(status) }
            nearbyManager.onRemoteCharacterReceived = { ch -> onlineMatch.remotePlayer.characterId = ch }
            nearbyManager.onRemotePeerInfoReceived = { ch, name ->
                onlineMatch.remotePlayer.characterId = ch
                val disp = name.ifBlank { characterPalette(ch).name }
                onlineMatch.remoteDisplayName = disp
                onlineMatch.remotePlayer.displayName = disp
            }
        }
        onDispose {
            onlineRoomManager.onRemoteInput = null
            onlineRoomManager.onHostSnapshot = null
            onlineRoomManager.onRoomStatusChange = null
            onlineRoomManager.onRemotePeerInfoReceived = null
            nearbyManager.onRemoteInput = null
            nearbyManager.onHostSnapshot = null
            nearbyManager.onRoomStatusChange = null
            nearbyManager.onRemoteCharacterReceived = null
            nearbyManager.onRemotePeerInfoReceived = null
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
    var lastGuestAutoSwingSec by remember(resetNonce, matchKind) { mutableStateOf(-1f) }

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

    fun sendGuestInput(input: com.picklerally.app.game.PlayerInputSnapshot) {
        if (isOnline) {
            onlineRoomManager.sendInput(input)
        } else if (isNearby) {
            nearbyManager.sendInput(input)
        }
    }

    fun pushMove(wx: Float, wy: Float, force: Boolean) {
        if (onlineMatch != null) {
            val inp = onlineMatch.setLocalMoveTarget(wx, wy)
            if (!onlineMatch.isHost && (force || elapsedSec - lastInputPushSec >= 0.045f)) {
                lastInputPushSec = elapsedSec
                sendGuestInput(inp)
            }
        } else if (doublesMatch != null) {
            doublesMatch.setPlayerMoveTarget(wx, wy)
        } else {
            soloMatch?.setPlayerMoveTarget(wx, wy)
        }
    }

    fun pushJoystick(normX: Float, normY: Float) {
        if (onlineMatch != null) {
            val releaseInp = onlineMatch.setJoystickInput(normX, normY)
            if (releaseInp != null) {
                lastInputPushSec = elapsedSec
                sendGuestInput(releaseInp)
            }
        } else if (doublesMatch != null) {
            doublesMatch.setJoystickInput(normX, normY)
        } else {
            soloMatch?.setJoystickInput(normX, normY)
        }
    }

    fun attemptSwipe(dx: Float, dy: Float, duration: Float): Boolean {
        if (onlineMatch != null) {
            val res = onlineMatch.triggerLocalSwipe(SwipeVector(dx, dy, duration))
            res.guestInput?.let(::sendGuestInput)
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
                res.guestInput?.let(::sendGuestInput)
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
        } else if (matchKind == CourtMatchKind.ONLINE) {
            onlineRoomManager.leaveRoom()
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
    LaunchedEffect(overlayState, resetNonce, matchKind, mpOpponentConnected) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos == 0L) {
                    lastNanos = frameNanos
                }
                val rawDt = (frameNanos - lastNanos) / 1_000_000_000f
                lastNanos = frameNanos
                val dt = min(rawDt.coerceAtLeast(0f), 0.05f)
                val mpWaiting = isMultiplayer && !mpOpponentConnected

                if (overlayState == MatchOverlayState.PLAYING && !mpWaiting) {
                    elapsedSec += dt
                    if (hitstopTimer > 0f) {
                        hitstopTimer = max(0f, hitstopTimer - dt)
                    } else if (onlineMatch != null) {
                        if (!onlineMatch.isHost &&
                            onlineMatch.shouldGuestAutoSwingLocal() &&
                            elapsedSec - lastGuestAutoSwingSec >= 0.42f
                        ) {
                            lastGuestAutoSwingSec = elapsedSec
                            val swipeRes = onlineMatch.triggerLocalSwipe(SwipeVector(0f, 6f, 0.15f))
                            swipeRes.guestInput?.let(::sendGuestInput)
                            swipeRes.hit?.let { triggerHitEffects(it.shotType, it.hitX, it.hitY, false) }
                        }
                        val res = onlineMatch.update(dt)
                        if (!onlineMatch.isHost && elapsedSec - lastInputPushSec >= 0.045f) {
                            onlineMatch.buildJoystickInputSnapshot()?.let { joyInp ->
                                lastInputPushSec = elapsedSec
                                sendGuestInput(joyInp)
                            }
                        }
                        if (res.snapshotToPublish != null && onlineMatch.isHost) {
                            if (isOnline) {
                                val forcePush = res.pointResolution != null || res.snapshotToPublish.matchWinner != null
                                onlineRoomManager.publishState(res.snapshotToPublish, force = forcePush)
                                if (res.snapshotToPublish.matchWinner != null) {
                                    onlineRoomManager.markMatchFinished()
                                }
                            } else if (isNearby) {
                                nearbyManager.publishState(res.snapshotToPublish)
                            }
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
                    rallyShotCount = onlineMatch?.activeRallyShotCount()
                        ?: doublesMatch?.rules?.rallyShotCount
                        ?: soloMatch?.rules?.rallyShotCount ?: 0,
                    ballBouncesOnSide = onlineMatch?.activeBallBouncesOnSide()
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
                    if (isOnline && onlineMatch?.isHost == true) {
                        onlineRoomManager.publishState(onlineMatch.buildSnapshot(), force = true)
                        if (matchWinner != null) {
                            onlineRoomManager.markMatchFinished()
                        }
                    }
                    if (matchWinner != null && !matchSaveTriggered && !isNearby) {
                        matchSaveTriggered = true
                        saveState = MatchSaveState.SAVING
                        val mode = when (matchKind) {
                            CourtMatchKind.DOUBLES -> MatchRecordMode.SOLO_2V2
                            CourtMatchKind.ONLINE -> MatchRecordMode.SINGLES_1V1
                            else -> MatchRecordMode.SOLO_1V1
                        }
                        scope.launch {
                            val outcome = statsRepository.recordFinishedMatch(
                                user = authUser,
                                input = FinishedMatchInput(
                                    mode = mode,
                                    scoringMode = scoringMode,
                                    playerCharacter = playerChar,
                                    partnerCharacter = if (mode == MatchRecordMode.SOLO_2V2) partnerChar else null,
                                    opponentCharacter = if (isOnline) mpRemoteChar else opponentChar,
                                    winnerSide = matchWinner,
                                    scoreLower = scoreLower,
                                    scoreUpper = scoreUpper,
                                    bestRally = nextHud.bestRally,
                                    roomCode = if (isOnline) mpRoomCode.takeIf { it.isNotBlank() } else null,
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
        if (isMultiplayer) mpRemoteChar else opponentChar,
    )

    val localLabel = when {
        isMultiplayer -> customProfileLabel ?: playerPalette.name
        isDoubles -> "Your Team"
        else -> customProfileLabel ?: playerPalette.name
    }
    val remoteLabel = when {
        isMultiplayer -> mpRemoteName.ifBlank { opponentPalette.name }
        isDoubles -> "Opponents"
        else -> opponentPalette.name
    }
    val scoreLocalLabel = if (isDoubles) "Us" else localLabel
    val scoreRemoteLabel = when {
        isDoubles -> "Them"
        isOnline && onlineSession.remoteRating != null -> "$remoteLabel · ${onlineSession.remoteRating}"
        else -> remoteLabel
    }

    val rulePhaseLabel = when {
        isMultiplayer -> when {
            hud.hostDisconnected -> "Host disconnected"
            !mpOpponentConnected -> if (mpIsHost) {
                val codeSuffix = if (mpRoomCode.isNotBlank()) " ($mpRoomCode)" else ""
                if (isOnline) "Waiting for online player$codeSuffix…" else "Waiting for nearby player$codeSuffix…"
            } else {
                mpStatusMessage.ifBlank { "Connecting to $remoteLabel…" }
            }
            hud.rallyPhase == RallyPhase.POINT_PAUSE -> hud.lastCallout.ifBlank { "Next point" }
            hud.rallyPhase == RallyPhase.WAITING_SERVE -> if (hud.servingSide == CourtSide.LOWER) {
                "Serve diagonally · ${if (scoringMode == ScoringMode.RALLY) "Rally" else "Side-Out"} to $pointsToWin"
            } else {
                "$remoteLabel serving…"
            }
            hud.rallyShotCount == 1 ->
                if (hud.ballBouncesOnSide == 0) "Return must bounce" else "Serve bounced · Hit return"
            hud.rallyShotCount == 2 -> "3rd shot must bounce"
            hud.inReach -> "In reach · Swipe!"
            else -> hud.lastShotLabel.ifBlank { if (isOnline) "Online 1v1" else "Nearby 1v1" }
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

    val safeDrawing = WindowInsets.safeDrawing
    val layoutDirection = LocalLayoutDirection.current
    val safeTopPx = safeDrawing.getTop(density).toFloat()
    val safeBottomPx = safeDrawing.getBottom(density).toFloat()
    val safeLeftPx = safeDrawing.getLeft(density, layoutDirection).toFloat()
    val safeRightPx = safeDrawing.getRight(density, layoutDirection).toFloat()
    val effectiveTopInsetPx = max(topHudHeightPx, safeTopPx + with(density) { 64.dp.toPx() }) + with(density) { 6.dp.toPx() }
    val effectiveBottomInsetPx = safeBottomPx + with(density) { 8.dp.toPx() }
    val effectiveLeftInsetPx = safeLeftPx + with(density) { 4.dp.toPx() }
    val effectiveRightInsetPx = safeRightPx + with(density) { 4.dp.toPx() }

    val latestTopInsetPx by rememberUpdatedState(effectiveTopInsetPx)
    val latestBottomInsetPx by rememberUpdatedState(effectiveBottomInsetPx)
    val latestLeftInsetPx by rememberUpdatedState(effectiveLeftInsetPx)
    val latestRightInsetPx by rememberUpdatedState(effectiveRightInsetPx)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CourtApron),
    ) {
        // Full-screen Court Canvas (tap & swipe gestures sit directly on Canvas so overlay buttons/joystick capture independently)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(resetNonce, matchKind, mpOpponentConnected) {
                    detectTapGestures { offset ->
                        if (overlayState != MatchOverlayState.PLAYING || (isMultiplayer && !mpOpponentConnected)) {
                            return@detectTapGestures
                        }
                        val m = CourtViewportMetrics.compute(
                            canvasWidth = canvasSize.width.toFloat(),
                            canvasHeight = canvasSize.height.toFloat(),
                            topInsetPx = latestTopInsetPx,
                            bottomInsetPx = latestBottomInsetPx,
                            leftInsetPx = latestLeftInsetPx,
                            rightInsetPx = latestRightInsetPx,
                        )
                        val (wx, wy) = m.screenToWorld(offset.x, offset.y)
                        if (wy <= CourtSpec.NET_Y + 2f) {
                            pushMove(wx, wy, true)
                        }
                    }
                }
                .pointerInput(resetNonce, matchKind, mpOpponentConnected) {
                    detectDragGestures(
                        onDragStart = { start ->
                            if (overlayState != MatchOverlayState.PLAYING || (isMultiplayer && !mpOpponentConnected)) {
                                return@detectDragGestures
                            }
                            val m = CourtViewportMetrics.compute(
                                canvasWidth = canvasSize.width.toFloat(),
                                canvasHeight = canvasSize.height.toFloat(),
                                topInsetPx = latestTopInsetPx,
                                bottomInsetPx = latestBottomInsetPx,
                                leftInsetPx = latestLeftInsetPx,
                                rightInsetPx = latestRightInsetPx,
                            )
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
                            if (!pointerActive || overlayState != MatchOverlayState.PLAYING || (isMultiplayer && !mpOpponentConnected)) {
                                return@detectDragGestures
                            }
                            val m = CourtViewportMetrics.compute(
                                canvasWidth = canvasSize.width.toFloat(),
                                canvasHeight = canvasSize.height.toFloat(),
                                topInsetPx = latestTopInsetPx,
                                bottomInsetPx = latestBottomInsetPx,
                                leftInsetPx = latestLeftInsetPx,
                                rightInsetPx = latestRightInsetPx,
                            )
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
            // Read frameTicker so Compose redraws smoothly each frame
            @Suppress("UNUSED_EXPRESSION")
            frameTicker

            val serveAssign = onlineMatch?.currentServeAssignment()
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
                onlineMatch != null -> onlineMatch.localPlayerHitState()
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
                    mirrorServeHighlight = false,
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
                    topInsetPx = effectiveTopInsetPx,
                    bottomInsetPx = effectiveBottomInsetPx,
                    leftInsetPx = effectiveLeftInsetPx,
                    rightInsetPx = effectiveRightInsetPx,
                ),
            )
        }

        // Floating Edge-to-Edge Top HUD Scoreboard
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { topHudHeightPx = it.height.toFloat() },
            color = NavyDeep.copy(alpha = 0.88f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            text = scoreLocalLabel,
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                            color = if (hud.servingSide == CourtSide.LOWER) LimeAccent else TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            text = "${hud.scoreLower}",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
                            color = if (hud.servingSide == CourtSide.LOWER) LimeAccent else TextPrimary,
                        )
                        Text(
                            text = "–",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                            color = TextSecondary,
                        )
                        Text(
                            text = "${hud.scoreUpper}",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
                            color = if (hud.servingSide == CourtSide.UPPER) OrangeAccent else TextPrimary,
                        )
                        Text(
                            text = scoreRemoteLabel,
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                            color = if (hud.servingSide == CourtSide.UPPER) OrangeAccent else TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = rulePhaseLabel,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                            color = if (hud.inReach) LimeAccent else TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (hud.rallyHits >= 2) {
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = NavyCard,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            ) {
                                Text(
                                    text = "Rally ${hud.rallyHits}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                    color = LimeAccent,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        if (hud.matchStatus.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = Color.White.copy(alpha = 0.10f),
                            ) {
                                Text(
                                    text = hud.matchStatus,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = TextPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isMultiplayer) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = NavyCard,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = if (isOnline) Icons.Filled.Public else Icons.Filled.Wifi,
                                    contentDescription = null,
                                    tint = LimeAccent,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = if (isOnline) "Online" else "Nearby",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = LimeAccent,
                                )
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showDimensions = !showDimensions },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("court_dimensions_button"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (showDimensions) LimeAccent else Color.White.copy(alpha = 0.16f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (showDimensions) LimeAccent else NavyCard,
                                contentColor = if (showDimensions) NavyDeep else TextPrimary,
                            ),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Icon(Icons.Filled.Straighten, contentDescription = "Toggle dimensions", modifier = Modifier.size(21.dp))
                        }
                    }

                    OutlinedButton(
                        onClick = onToggleMute,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("court_mute_button"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = NavyCard,
                            contentColor = TextPrimary,
                        ),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(
                            imageVector = if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (muted) "Unmute" else "Mute",
                            modifier = Modifier.size(21.dp),
                        )
                    }

                    OutlinedButton(
                        onClick = { overlayState = MatchOverlayState.PAUSED },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("court_pause_button"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = NavyCard,
                            contentColor = TextPrimary,
                        ),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(Icons.Filled.Pause, contentDescription = "Pause match", modifier = Modifier.size(21.dp))
                    }
                }
            }
        }

        // First-serve hint
        if (!hasSeenControlsHint && !isMultiplayer && overlayState == MatchOverlayState.PLAYING &&
            hud.rallyPhase == RallyPhase.WAITING_SERVE && hud.scoreLower == 0 && hud.scoreUpper == 0
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(14.dp),
                color = NavyDeep.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, LimeAccent.copy(alpha = 0.55f)),
                shadowElevation = 6.dp,
            ) {
                Text(
                    text = "Use joystick or tap court to move · Stand in ring to hit",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }

        // Point/Fault Announcement Banner
        if (hud.rallyPhase == RallyPhase.POINT_PAUSE && hud.lastCallout.isNotBlank()) {
            val bannerTopDp = with(density) { effectiveTopInsetPx.toDp() } + 8.dp
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = bannerTopDp, start = 16.dp, end = 16.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (isFaultCallout) RoseDarkBg.copy(alpha = 0.94f) else NavyDeep.copy(alpha = 0.94f),
                border = BorderStroke(
                    1.dp,
                    if (isFaultCallout) RoseFault.copy(alpha = 0.55f) else LimeAccent.copy(alpha = 0.55f),
                ),
                shadowElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = hud.lastCallout,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isFaultCallout) TextPrimary else LimeAccent,
                    )
                    Text(
                        text = "$localLabel ${hud.scoreLower} – ${hud.scoreUpper} $remoteLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
        }

        // Floating Serve / Receive Button on opposite lower corner from Joystick
        if (overlayState == MatchOverlayState.PLAYING && (!isMultiplayer || mpOpponentConnected)) {
            val canLocalServe = hud.rallyPhase == RallyPhase.WAITING_SERVE &&
                hud.servingSide == CourtSide.LOWER &&
                (!isDoubles || hud.serverIndexInTeam == 0)
            val canLocalReceive = hud.rallyPhase == RallyPhase.WAITING_SERVE &&
                !isMultiplayer &&
                !canLocalServe
            val actionCorner = if (dominantHand == DominantHand.RIGHT) Alignment.BottomStart else Alignment.BottomEnd
            val joystickCorner = if (dominantHand == DominantHand.RIGHT) Alignment.BottomEnd else Alignment.BottomStart

            when {
                canLocalServe -> {
                    Button(
                        onClick = ::triggerServeButton,
                        modifier = Modifier
                            .align(actionCorner)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                            .padding(horizontal = 16.dp, vertical = 20.dp)
                            .heightIn(min = 56.dp)
                            .testTag("serve_diagonal_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LimeAccent,
                            contentColor = NavyDeep,
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.serve_diagonal),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }

                canLocalReceive -> {
                    OutlinedButton(
                        onClick = ::triggerReceiveButton,
                        modifier = Modifier
                            .align(actionCorner)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                            .padding(horizontal = 16.dp, vertical = 20.dp)
                            .heightIn(min = 56.dp)
                            .testTag("receive_serve_button"),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, OrangeAccent.copy(alpha = 0.75f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = NavyDeep.copy(alpha = 0.90f),
                            contentColor = TextPrimary,
                        ),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.Start) {
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
                            Text(
                                text = servingText,
                                style = MaterialTheme.typography.labelMedium,
                                color = OrangeAccent,
                            )
                            Text(
                                text = if (isDoubles && hud.servingSide == CourtSide.LOWER) "Tap to start" else "Tap to receive",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary,
                            )
                        }
                    }
                }

                hud.rallyPhase == RallyPhase.WAITING_SERVE && isMultiplayer -> {
                    Surface(
                        modifier = Modifier
                            .align(actionCorner)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                            .padding(horizontal = 16.dp, vertical = 24.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = NavyDeep.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, OrangeAccent.copy(alpha = 0.45f)),
                    ) {
                        Text(
                            text = "$remoteLabel serving…",
                            style = MaterialTheme.typography.labelLarge,
                            color = OrangeAccent,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }

            OnScreenJoystick(
                onMove = ::pushJoystick,
                onRelease = { pushJoystick(0f, 0f) },
                modifier = Modifier
                    .align(joystickCorner)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }

        // Online / Nearby Waiting / Connecting / Error Overlay
        if (isMultiplayer && !mpOpponentConnected && overlayState == MatchOverlayState.PLAYING) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NavyDeep.copy(alpha = 0.84f))
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 310.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = NavyCard,
                    border = BorderStroke(1.dp, BorderSlate),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (mpError != null) {
                            Icon(
                                imageVector = if (isOnline) Icons.Filled.Public else Icons.Filled.Wifi,
                                contentDescription = null,
                                tint = RoseFault,
                                modifier = Modifier.size(32.dp),
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = LimeAccent,
                                strokeWidth = 3.dp,
                            )
                        }

                        Text(
                            text = when {
                                mpError != null -> if (isOnline) "Online Connection Issue" else "Nearby Connection Issue"
                                mpIsHost -> if (isOnline) "Waiting for Online Opponent" else "Waiting for Nearby Opponent"
                                else -> if (isOnline) "Connecting Online…" else "Connecting Nearby…"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            textAlign = TextAlign.Center,
                        )

                        if (mpRoomCode.isNotBlank() && mpError == null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = NavyDeep,
                                border = BorderStroke(1.dp, LimeAccent.copy(alpha = 0.45f)),
                            ) {
                                Text(
                                    text = if (isOnline) "Room Code: $mpRoomCode" else "Court Code: $mpRoomCode",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = LimeAccent,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }

                        Text(
                            text = mpError ?: mpStatusMessage.ifBlank {
                                if (mpIsHost) {
                                    if (isOnline) {
                                        "Share this PKL room code with an Android or web friend to join."
                                    } else {
                                        "Ask your nearby friend to open Nearby and tap your court to join."
                                    }
                                } else {
                                    "Connecting with $remoteLabel…"
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (mpError != null) RoseFault else TextSecondary,
                            textAlign = TextAlign.Center,
                        )

                        OutlinedButton(
                            onClick = ::leaveCourt,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag(if (isOnline) "online_cancel_button" else "nearby_cancel_button"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BorderSlate),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        ) {
                            Text(
                                text = "Cancel & Back to Lobby",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }

        // Pause Modal Overlay
        if (overlayState == MatchOverlayState.PAUSED) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NavyDeep.copy(alpha = 0.82f))
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = NavyCard,
                    border = BorderStroke(1.dp, BorderSlate),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = when {
                                isOnline -> "Online 1v1${if (mpRoomCode.isNotBlank()) " · $mpRoomCode" else ""}"
                                isNearby -> "Nearby 1v1"
                                else -> "Match Paused"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                        )
                        Text(
                            text = when {
                                isMultiplayer -> if (mpIsHost) {
                                    "Host runs official 60 Hz physics and syncs with guest."
                                } else {
                                    "Guest predicts movement and hits locally, synced with Host."
                                }
                                isDoubles -> "2v2 Doubles (${playerPalette.name} & ${partnerPalette.name}) · ${if (scoringMode == ScoringMode.RALLY) "Rally" else "Side-Out"} to 11."
                                else -> "${playerPalette.name} vs ${opponentPalette.name} · ${if (scoringMode == ScoringMode.RALLY) "Rally" else "Side-Out"} to $pointsToWin."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )

                        // Quick Dominant Hand toggle for Joystick side
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "DOMINANT HAND (JOYSTICK)",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                DominantHand.entries.forEach { hand ->
                                    val selected = hand == dominantHand
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onSelectDominantHand(hand) }
                                            .testTag("pause_dominant_hand_${hand.name.lowercase()}"),
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selected) LimeAccent else NavyDeep,
                                        border = BorderStroke(1.dp, if (selected) LimeAccent else BorderSlate),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = hand.label,
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                color = if (selected) NavyDeep else TextPrimary,
                                            )
                                        }
                                    }
                                }
                            }
                        }

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
                            Icon(Icons.Filled.Fullscreen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.resume_match), style = MaterialTheme.typography.labelLarge)
                        }

                        if (!isMultiplayer) {
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
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
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
                                text = if (isMultiplayer) "Leave Room" else stringResource(R.string.return_to_title),
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
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth(),
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

                        if (!isMultiplayer) {
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
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
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
                                text = if (isMultiplayer) "Back to Lobby" else stringResource(R.string.return_to_title),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnScreenJoystick(
    onMove: (normX: Float, normY: Float) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val baseSize = 118.dp
    val knobSize = 46.dp
    val density = LocalDensity.current
    val baseSizePx = with(density) { baseSize.toPx() }
    val knobSizePx = with(density) { knobSize.toPx() }
    val maxRadiusPx = max(1f, (baseSizePx - knobSizePx) * 0.5f)
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }

    val latestOnMove by rememberUpdatedState(onMove)
    val latestOnRelease by rememberUpdatedState(onRelease)

    DisposableEffect(Unit) {
        onDispose {
            latestOnRelease()
        }
    }

    fun updateFromPointer(pointerPos: Offset) {
        val center = Offset(baseSizePx * 0.5f, baseSizePx * 0.5f)
        val raw = pointerPos - center
        val dist = hypot(raw.x, raw.y)
        val clamped = if (dist > maxRadiusPx) {
            val scale = maxRadiusPx / dist
            Offset(raw.x * scale, raw.y * scale)
        } else {
            raw
        }
        knobOffset = clamped
        val normX = (clamped.x / maxRadiusPx).coerceIn(-1f, 1f)
        // Screen Y increases downward, while court world Y increases upward toward the net.
        val normY = (-clamped.y / maxRadiusPx).coerceIn(-1f, 1f)
        latestOnMove(normX, normY)
    }

    Box(
        modifier = modifier
            .size(baseSize)
            .clip(CircleShape)
            .background(NavyDeep.copy(alpha = 0.52f))
            .testTag("court_joystick")
            .pointerInput(baseSizePx, maxRadiusPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isDragging = true
                    updateFromPointer(down.position)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull()
                                ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            if (change.positionChanged()) {
                                change.consume()
                                updateFromPointer(change.position)
                            }
                        }
                    } finally {
                        knobOffset = Offset.Zero
                        isDragging = false
                        latestOnRelease()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension * 0.5f
            drawCircle(
                color = Color.White.copy(alpha = if (isDragging) 0.36f else 0.22f),
                radius = radius - 1.5f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.12f),
                radius = radius * 0.52f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f),
            )
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(knobOffset.x.roundToInt(), knobOffset.y.roundToInt()) }
                .size(knobSize)
                .clip(CircleShape)
                .background(
                    if (isDragging) LimeAccent.copy(alpha = 0.94f) else LimeAccent.copy(alpha = 0.72f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(NavyDeep.copy(alpha = 0.45f)),
            )
        }
    }
}
