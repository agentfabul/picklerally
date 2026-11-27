package com.picklerally.core.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.FitViewport
import com.picklerally.core.PickleRallyGame
import com.picklerally.core.characters.CharacterCatalog
import com.picklerally.core.characters.CharacterId
import com.picklerally.core.court.CourtColors
import com.picklerally.core.court.CourtSide
import com.picklerally.core.court.CourtSpec
import com.picklerally.core.court.ServiceBoxSide
import com.picklerally.core.net.ConnectionState
import com.picklerally.core.net.RoomMetadata
import com.picklerally.core.net.NetworkService
import com.picklerally.core.net.RoomMode
import com.picklerally.core.net.RoomStatus
import com.picklerally.core.physics.BallPhysics
import com.picklerally.core.physics.BallState
import com.picklerally.core.physics.ShotType
import com.picklerally.core.physics.SwipeVector
import com.picklerally.core.rules.FaultReason
import com.picklerally.core.rules.MatchMode
import com.picklerally.core.rules.ScoringMode
import com.picklerally.core.sim.DoublesMatch
import com.picklerally.core.sim.OnlineMatch1v1
import com.picklerally.core.sim.PlayerEntity
import com.picklerally.core.sim.RallyPhase
import com.picklerally.core.sim.SinglePlayerMatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ktx.app.KtxScreen
import ktx.graphics.use
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Phase 5 polished CourtScreen supporting selected characters (Rio, Lola, Bert, Dill),
 * synthesized audio cues, animated fault/score callout banner, and Solo/Online 1v1 modes.
 */
class CourtScreen(
    private val game: PickleRallyGame
) : KtxScreen {
    // Resolved on each use so Nearby (offline) and online play share this screen.
    private val networkService: NetworkService
        get() = game.activeNetworkService
    private val soundService = game.soundService

    private val camera = OrthographicCamera()
    private val viewport = FitViewport(CourtSpec.WORLD_WIDTH_FT, CourtSpec.WORLD_HEIGHT_FT, camera)
    private val hudCamera = OrthographicCamera()
    private val hudViewport = FitViewport(450f, 900f, hudCamera)
    private val shapeRenderer = ShapeRenderer()
    private val batch = SpriteBatch()
    private val hudFont = BitmapFont()
    private val hudSmallFont = BitmapFont()
    private val calloutFont = BitmapFont()

    private val menuRect = Rectangle(364f, 846f, 74f, 38f)
    private val muteRect = Rectangle(294f, 846f, 62f, 38f)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var netJob: Job? = null
    private var leaveJob: Job? = null

    private var soloMatch = SinglePlayerMatch(CharacterId.RIO, CharacterId.LOLA, ScoringMode.RALLY)
    private var doublesMatch: DoublesMatch? = null
    private var onlineMatch: OnlineMatch1v1? = null
    private var activeRoom: RoomMetadata? = null
    private var onlineStatusText: String = "Solo"
    private var remoteCharName: String? = null

    private val touchWorld = Vector2()
    private val touchHud = Vector2()
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeStartTimeSec = 0f
    private var elapsedSec = 0f
    private var pointerActive = false
    private var pressedHudButton: String? = null
    private var swipeConsumed = false
    private var lastMovePushSec = 0f
    private var calloutBannerTimerSec = 0f

    fun configureMatch(
        playerChar: CharacterId,
        botChar: CharacterId,
        scoringMode: ScoringMode,
        matchMode: MatchMode = MatchMode.SINGLES,
        startOnlineHost: Boolean,
        joinRoomCode: String? = null
    ) {
        leaveOnlineRoom()
        remoteCharName = null
        if (matchMode == MatchMode.DOUBLES && !startOnlineHost) {
            doublesMatch = DoublesMatch(
                playerCharacter = playerChar,
                partnerCharacter = CharacterId.DILL,
                upperRightChar = botChar,
                upperLeftChar = CharacterId.BERT,
                scoringMode = scoringMode
            )
        } else {
            doublesMatch = null
            soloMatch = SinglePlayerMatch(playerChar, botChar, scoringMode)
        }
        calloutBannerTimerSec = 1.4f
        if (startOnlineHost) {
            startOnlineHostRoom()
        } else if (joinRoomCode != null) {
            startOnlineGuestRoom(joinRoomCode)
        }
    }

    fun startOnlineHostRoom() {
        netJob?.cancel()
        val pendingLeave = leaveJob
        onlineStatusText = "Starting room..."
        netJob = scope.launch {
            pendingLeave?.join()
            val uid = networkService.currentUid ?: networkService.signInAnonymously().getOrNull()
            if (uid == null) {
                onlineStatusText = "Could not start room"
                return@launch
            }
            val createResult = networkService.createRoom(RoomMode.SINGLES_1V1, game.selectedCharacter.name)
            val room = createResult.getOrElse { err ->
                onlineStatusText = err.message ?: "Could not advertise nearby"
                return@launch
            }
            activeRoom = room
            onlineStatusText = "Room ${room.roomCode} · Waiting"
            val match = OnlineMatch1v1(
                isHost = true,
                localUid = uid,
                localChar = game.selectedCharacter,
                remoteChar = game.opponentCharacter,
                scoringMode = game.scoringMode
            )
            onlineMatch = match
            var markedPlaying = false

            launch {
                networkService.observePlayers(room.roomCode).collect { players ->
                    val remote = players.firstOrNull { it.uid != uid }
                    if (remote != null) {
                        val parsedChar = runCatching { CharacterId.valueOf(remote.characterId) }.getOrNull()
                        if (parsedChar != null) {
                            match.remotePlayer.characterId = parsedChar
                            remoteCharName = parsedChar.displayName
                        }
                    }
                    if (!markedPlaying && players.count { it.connected } >= 2) {
                        markedPlaying = true
                        networkService.updateRoomStatus(room.roomCode, RoomStatus.PLAYING)
                        onlineStatusText = "Room ${room.roomCode} · Live"
                    }
                }
            }
            launch {
                networkService.connectionState.collect { conn ->
                    if (markedPlaying && conn == ConnectionState.DISCONNECTED) {
                        onlineStatusText = "Opponent left · Tap Menu"
                    } else if (conn == ConnectionState.ERROR) {
                        onlineStatusText = "Nearby error · Tap Menu"
                    }
                }
            }
            launch {
                networkService.observeInputs(room.roomCode).collect { inputs ->
                    inputs.values.firstOrNull { it.uid != uid }?.let { guestInput ->
                        match.applyRemoteGuestInput(guestInput)
                    }
                }
            }
        }
    }

    /** Join a room as the guest (used by Nearby "Find Players"). The host stays authoritative. */
    fun startOnlineGuestRoom(roomCode: String) {
        netJob?.cancel()
        val pendingLeave = leaveJob
        onlineStatusText = "Joining $roomCode..."
        netJob = scope.launch {
            pendingLeave?.join()
            val uid = networkService.currentUid ?: networkService.signInAnonymously().getOrNull()
            if (uid == null) {
                onlineStatusText = "Could not initialize"
                return@launch
            }
            val joinResult = networkService.joinRoom(roomCode, game.selectedCharacter.name)
            val room = joinResult.getOrElse { err ->
                onlineStatusText = err.message ?: "Could not join $roomCode"
                return@launch
            }
            activeRoom = room
            onlineStatusText = "Room ${room.roomCode} · Connecting..."
            val match = OnlineMatch1v1(
                isHost = false,
                localUid = uid,
                localChar = game.selectedCharacter,
                remoteChar = game.opponentCharacter,
                scoringMode = game.scoringMode
            )
            onlineMatch = match

            launch {
                networkService.connectionState.collect { conn ->
                    onlineStatusText = when (conn) {
                        ConnectionState.CONNECTING -> "Room ${room.roomCode} · Connecting..."
                        ConnectionState.IN_MATCH -> "Room ${room.roomCode} · Live"
                        ConnectionState.ERROR -> "Connection failed · Tap Menu"
                        ConnectionState.DISCONNECTED -> "Disconnected · Tap Menu"
                        else -> onlineStatusText
                    }
                }
            }
            launch {
                networkService.observePlayers(room.roomCode).collect { players ->
                    val remote = players.firstOrNull { it.uid != uid }
                    if (remote != null) {
                        val parsedChar = runCatching { CharacterId.valueOf(remote.characterId) }.getOrNull()
                        if (parsedChar != null) {
                            match.remotePlayer.characterId = parsedChar
                            remoteCharName = parsedChar.displayName
                        }
                    }
                }
            }
            launch {
                networkService.observeMatchState(room.roomCode).collect { snapshot ->
                    if (snapshot != null) match.applyHostSnapshot(snapshot)
                }
            }
            launch {
                networkService.observeRoomMeta(room.roomCode).collect { meta ->
                    if (meta != null) match.onRoomMetaUpdated(meta.status)
                }
            }
        }
    }

    fun leaveOnlineRoom() {
        val hadSession = netJob != null || onlineMatch != null || activeRoom != null
        netJob?.cancel()
        netJob = null
        onlineMatch = null
        activeRoom = null
        remoteCharName = null
        onlineStatusText = "Solo vs ${game.opponentCharacter.displayName}"
        if (hadSession) {
            val service = networkService
            leaveJob = scope.launch { service.leaveRoom() }
        }
    }

    override fun show() {
        camera.position.set(CourtSpec.WORLD_WIDTH_FT / 2f, CourtSpec.WORLD_HEIGHT_FT / 2f, 0f)
        camera.update()
        hudCamera.position.set(225f, 450f, 0f)
        hudCamera.update()
        hudFont.data.setScale(1.15f)
        hudSmallFont.data.setScale(1.0f)
        calloutFont.data.setScale(1.5f)
        Gdx.input.setCatchKey(Input.Keys.BACK, true)

        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                unprojectHud(screenX, screenY)
                if (menuRect.contains(touchHud.x, touchHud.y)) {
                    pressedHudButton = "menu"
                    pointerActive = false
                    return true
                }
                if (muteRect.contains(touchHud.x, touchHud.y)) {
                    pressedHudButton = "mute"
                    pointerActive = false
                    return true
                }
                pressedHudButton = null

                unprojectTouch(screenX, screenY)
                val om = onlineMatch
                val dm = doublesMatch
                if (om == null && dm != null && dm.rallyPhase == RallyPhase.MATCH_OVER) {
                    dm.reset()
                    soundService.playEnterCourt()
                    return true
                }
                if (om == null && dm == null && soloMatch.rallyPhase == RallyPhase.MATCH_OVER) {
                    soloMatch.resetMatch()
                    soundService.playEnterCourt()
                    return true
                }
                pointerActive = true
                swipeConsumed = false
                swipeStartX = touchWorld.x
                swipeStartY = touchWorld.y
                swipeStartTimeSec = elapsedSec

                if (touchWorld.y <= CourtSpec.NET_Y + 2f) {
                    if (om != null) {
                        val input = om.setLocalMoveTarget(touchWorld.x, touchWorld.y)
                        activeRoom?.let { room -> scope.launch { networkService.sendInput(room.roomCode, input) } }
                    } else if (dm != null) {
                        dm.setPlayerMoveTarget(touchWorld.x, touchWorld.y)
                    } else {
                        soloMatch.setPlayerMoveTarget(touchWorld.x, touchWorld.y)
                    }
                }
                return true
            }

            override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
                if (!pointerActive) return false
                unprojectTouch(screenX, screenY)

                val dx = touchWorld.x - swipeStartX
                val dy = touchWorld.y - swipeStartY
                val duration = (elapsedSec - swipeStartTimeSec).coerceAtLeast(0.04f)
                val swipe = SwipeVector(dx, dy, duration)
                val om = onlineMatch
                val dm = doublesMatch

                if (!swipeConsumed && swipe.distanceFt >= 1.3f && swipe.speedFtPerSec >= 7.5f) {
                    if (om != null) {
                        val canHitOrServe = (om.rallyPhase == RallyPhase.WAITING_SERVE && om.servingLocal && swipe.dyFt > 1.0f) ||
                            (om.rallyPhase == RallyPhase.RALLY_LIVE && om.ball.active &&
                                om.ball.lastHitterSide != CourtSide.LOWER &&
                                om.ball.y <= CourtSpec.NET_Y + 1.5f &&
                                hypot(om.ball.x - om.localPlayer.x, om.ball.y - om.localPlayer.y) <= OnlineMatch1v1.LOCAL_HIT_REACH_FT + 0.6f)
                        val guestInput = om.triggerLocalSwipe(swipe)
                        if (guestInput != null) {
                            activeRoom?.let { room -> scope.launch { networkService.sendInput(room.roomCode, guestInput) } }
                        }
                        if (canHitOrServe) {
                            soundService.playPaddleHit(
                                if (om.rallyPhase == RallyPhase.WAITING_SERVE) ShotType.SERVE else BallPhysics.classifySwipe(swipe),
                                false
                            )
                            calloutBannerTimerSec = 0.9f
                        }
                        swipeConsumed = true
                        return true
                    } else if (dm != null) {
                        if (dm.attemptPlayerSwipeHit(swipe)) {
                            soundService.playPaddleHit(BallPhysics.classifySwipe(swipe), false)
                            calloutBannerTimerSec = 0.9f
                            swipeConsumed = true
                            return true
                        }
                    } else {
                        val hit = soloMatch.attemptPlayerSwipeHit(swipe)
                        if (hit != null) {
                            soundService.playPaddleHit(hit.shotType, false)
                            calloutBannerTimerSec = 0.9f
                            swipeConsumed = true
                            return true
                        }
                    }
                }

                if (touchWorld.y <= CourtSpec.NET_Y + 2f) {
                    if (om != null) {
                        val input = om.setLocalMoveTarget(touchWorld.x, touchWorld.y)
                        if (elapsedSec - lastMovePushSec >= 0.045f) {
                            lastMovePushSec = elapsedSec
                            activeRoom?.let { room -> scope.launch { networkService.sendInput(room.roomCode, input) } }
                        }
                    } else if (dm != null) {
                        dm.setPlayerMoveTarget(touchWorld.x, touchWorld.y)
                    } else {
                        soloMatch.setPlayerMoveTarget(touchWorld.x, touchWorld.y)
                    }
                }
                return true
            }

            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                val hudBtn = pressedHudButton
                pressedHudButton = null
                if (hudBtn != null) {
                    unprojectHud(screenX, screenY)
                    if (hudBtn == "menu" && menuRect.contains(touchHud.x, touchHud.y)) {
                        game.soundService.playMenuClick()
                        game.openMainMenu()
                    } else if (hudBtn == "mute" && muteRect.contains(touchHud.x, touchHud.y)) {
                        soundService.isMuted = !soundService.isMuted
                        game.soundService.playMenuClick()
                    }
                    return true
                }
                if (!pointerActive) return false
                unprojectTouch(screenX, screenY)
                val om = onlineMatch
                val dm = doublesMatch
                if (!swipeConsumed) {
                    val dx = touchWorld.x - swipeStartX
                    val dy = touchWorld.y - swipeStartY
                    val duration = (elapsedSec - swipeStartTimeSec).coerceAtLeast(0.04f)
                    val swipe = SwipeVector(dx, dy, duration)
                    if (om != null) {
                        if (swipe.distanceFt >= 1.1f) {
                            val canHitOrServe = (om.rallyPhase == RallyPhase.WAITING_SERVE && om.servingLocal && swipe.dyFt > 1.0f) ||
                                (om.rallyPhase == RallyPhase.RALLY_LIVE && om.ball.active &&
                                    om.ball.lastHitterSide != CourtSide.LOWER &&
                                    om.ball.y <= CourtSpec.NET_Y + 1.5f &&
                                    hypot(om.ball.x - om.localPlayer.x, om.ball.y - om.localPlayer.y) <= OnlineMatch1v1.LOCAL_HIT_REACH_FT + 0.6f)
                            val guestInput = om.triggerLocalSwipe(swipe)
                            if (guestInput != null) {
                                activeRoom?.let { room -> scope.launch { networkService.sendInput(room.roomCode, guestInput) } }
                            }
                            if (canHitOrServe) {
                                soundService.playPaddleHit(
                                    if (om.rallyPhase == RallyPhase.WAITING_SERVE) ShotType.SERVE else BallPhysics.classifySwipe(swipe),
                                    false
                                )
                                calloutBannerTimerSec = 0.9f
                            }
                        } else if (om.rallyPhase == RallyPhase.WAITING_SERVE && om.servingLocal) {
                            val guestInput = om.triggerLocalSwipe(SwipeVector(0f, 3.2f, 0.2f))
                            if (guestInput != null) {
                                activeRoom?.let { room -> scope.launch { networkService.sendInput(room.roomCode, guestInput) } }
                            }
                            soundService.playPaddleHit(ShotType.SERVE, false)
                            calloutBannerTimerSec = 0.9f
                        }
                    } else if (dm != null) {
                        if (swipe.distanceFt >= 1.1f) {
                            if (dm.attemptPlayerSwipeHit(swipe)) {
                                soundService.playPaddleHit(BallPhysics.classifySwipe(swipe), false)
                                calloutBannerTimerSec = 0.9f
                            }
                        } else if (dm.rallyPhase == RallyPhase.WAITING_SERVE &&
                            dm.activeServerSide() == CourtSide.LOWER &&
                            dm.activeServerIndex() == 0
                        ) {
                            if (dm.launchHumanServe() == null) {
                                soundService.playPaddleHit(BallPhysics.classifySwipe(SwipeVector(0f, 3f, 0.2f)), false)
                                calloutBannerTimerSec = 0.9f
                            }
                        }
                    } else {
                        if (swipe.distanceFt >= 1.1f) {
                            val hit = soloMatch.attemptPlayerSwipeHit(swipe)
                            if (hit != null) {
                                soundService.playPaddleHit(hit.shotType, false)
                                calloutBannerTimerSec = 0.9f
                            }
                        } else if (soloMatch.rallyPhase == RallyPhase.WAITING_SERVE &&
                            soloMatch.rules.servingSide == CourtSide.LOWER
                        ) {
                            val hit = soloMatch.launchServe()
                            if (hit != null) {
                                soundService.playPaddleHit(hit.shotType, false)
                                calloutBannerTimerSec = 0.9f
                            }
                        }
                    }
                }
                pointerActive = false
                return true
            }

            override fun keyDown(keycode: Int): Boolean {
                if (keycode == Input.Keys.M) {
                    soundService.isMuted = !soundService.isMuted
                    return true
                }
                if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) {
                    game.openMainMenu()
                    return true
                }
                return false
            }
        }
    }

    override fun hide() {
        Gdx.input.setCatchKey(Input.Keys.BACK, false)
    }

    private fun unprojectTouch(screenX: Int, screenY: Int) {
        touchWorld.set(screenX.toFloat(), screenY.toFloat())
        viewport.unproject(touchWorld)
    }

    private fun unprojectHud(screenX: Int, screenY: Int) {
        touchHud.set(screenX.toFloat(), screenY.toFloat())
        hudViewport.unproject(touchHud)
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
        hudViewport.update(width, height, true)
    }

    override fun render(delta: Float) {
        elapsedSec += delta
        if (calloutBannerTimerSec > 0f) {
            calloutBannerTimerSec = max(0f, calloutBannerTimerSec - delta)
        }

        val om = onlineMatch
        val dm = doublesMatch
        if (om != null) {
            val snap = om.update(delta)
            if (snap != null && om.isHost) {
                activeRoom?.let { room ->
                    scope.launch { networkService.publishState(room.roomCode, snap) }
                }
            }
        } else if (dm != null) {
            val res = dm.update(delta)
            if (res != null) {
                soundService.playFault(res.faultReason)
                if (res.matchWinner != null) {
                    soundService.playMatchWin(res.matchWinner == CourtSide.LOWER)
                } else if (res.pointAwardedTo != null) {
                    soundService.playPointScored(res.pointAwardedTo == CourtSide.LOWER)
                }
                calloutBannerTimerSec = 1.55f
            }
        } else {
            val ticks = soloMatch.update(delta)
            for (tick in ticks) {
                if (tick.stepEvents.bounced) {
                    soundService.playBounce(tick.stepEvents.bounceZSpeed)
                }
                tick.hitFeedback?.let { hit ->
                    soundService.playPaddleHit(hit.shotType, hit.hitterSide == CourtSide.UPPER)
                    calloutBannerTimerSec = 0.8f
                }
                tick.pointResolution?.let { res ->
                    soundService.playFault(res.faultReason)
                    if (res.matchWinner != null) {
                        soundService.playMatchWin(res.matchWinner == CourtSide.LOWER)
                    } else if (res.pointAwardedTo != null) {
                        soundService.playPointScored(res.pointAwardedTo == CourtSide.LOWER)
                    }
                    calloutBannerTimerSec = 1.55f
                }
            }
        }

        Gdx.gl.glClearColor(
            CourtColors.SURROUND_NAVY.r,
            CourtColors.SURROUND_NAVY.g,
            CourtColors.SURROUND_NAVY.b,
            1f
        )
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        viewport.apply()
        shapeRenderer.projectionMatrix = camera.combined

        drawCourtSurfaces()
        drawServeAndFaultHighlights(om, dm)
        drawCourtLines()

        if (dm != null) {
            drawPlayer(dm.upperTeam[0], false)
            drawPlayer(dm.upperTeam[1], false)
            drawNet()
            drawPlayer(dm.partner, false)
            drawPlayer(dm.player, dm.canPlayerReachBall())
            drawBall(dm.ball)
        } else {
            val upperPlayer = om?.remotePlayer ?: soloMatch.bot
            val lowerPlayer = om?.localPlayer ?: soloMatch.player
            val activeBall = om?.ball ?: soloMatch.ball
            val canLocalHit = if (om != null) {
                om.rallyPhase == RallyPhase.RALLY_LIVE && om.ball.active &&
                    om.ball.lastHitterSide != CourtSide.LOWER &&
                    om.ball.y <= CourtSpec.NET_Y + 0.8f &&
                    hypot(om.ball.x - om.localPlayer.x, om.ball.y - om.localPlayer.y) <= OnlineMatch1v1.LOCAL_HIT_REACH_FT
            } else {
                soloMatch.canPlayerReachBall()
            }

            drawPlayer(upperPlayer, false)
            drawNet()
            drawPlayer(lowerPlayer, canLocalHit)
            drawBall(activeBall)
        }
        drawHud(om, dm)
    }

    private fun drawCourtSurfaces() {
        shapeRenderer.use(ShapeRenderer.ShapeType.Filled) { sr ->
            sr.color = CourtColors.SURROUND_NAVY
            sr.rect(0f, 0f, CourtSpec.WORLD_WIDTH_FT, CourtSpec.WORLD_HEIGHT_FT)

            sr.color = CourtColors.COURT_BLUE
            sr.rect(
                CourtSpec.COURT_LEFT_X,
                CourtSpec.COURT_BOTTOM_Y,
                CourtSpec.COURT_WIDTH_FT,
                CourtSpec.COURT_LENGTH_FT
            )

            sr.color = CourtColors.KITCHEN_BLUE
            sr.rect(
                CourtSpec.COURT_LEFT_X,
                CourtSpec.LOWER_NVZ_Y,
                CourtSpec.COURT_WIDTH_FT,
                CourtSpec.KITCHEN_DEPTH_FT * 2f
            )
        }
    }

    private fun drawServeAndFaultHighlights(om: OnlineMatch1v1?, dm: DoublesMatch?) {
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        val rallyPhase = om?.rallyPhase ?: dm?.rallyPhase ?: soloMatch.rallyPhase
        val rules = om?.rules ?: dm?.rules ?: soloMatch.rules

        shapeRenderer.use(ShapeRenderer.ShapeType.Filled) { sr ->
            if (rallyPhase == RallyPhase.WAITING_SERVE) {
                val assign = rules.currentServeAssignment()
                val boxX = if (assign.targetBox == ServiceBoxSide.LEFT) {
                    CourtSpec.COURT_LEFT_X
                } else {
                    CourtSpec.COURT_CENTER_X
                }
                val boxY = if (assign.targetCourtSide == CourtSide.UPPER) {
                    CourtSpec.UPPER_NVZ_Y
                } else {
                    CourtSpec.COURT_BOTTOM_Y
                }
                sr.color = Color(0.52f, 0.80f, 0.09f, 0.18f)
                sr.rect(boxX, boxY, CourtSpec.COURT_WIDTH_FT / 2f, CourtSpec.SERVICE_DEPTH_FT)
            } else if (om == null &&
                soloMatch.rallyPhase == RallyPhase.POINT_PAUSE &&
                soloMatch.lastFaultReason == FaultReason.KITCHEN_VOLLEY
            ) {
                sr.color = Color(0.86f, 0.15f, 0.15f, 0.25f)
                sr.rect(
                    CourtSpec.COURT_LEFT_X,
                    CourtSpec.LOWER_NVZ_Y,
                    CourtSpec.COURT_WIDTH_FT,
                    CourtSpec.KITCHEN_DEPTH_FT
                )
            }
        }
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    private fun drawCourtLines() {
        val lw = CourtSpec.LINE_WIDTH_FT
        val halfLw = lw / 2f

        shapeRenderer.use(ShapeRenderer.ShapeType.Filled) { sr ->
            sr.color = CourtColors.LINE_WHITE
            sr.rect(CourtSpec.COURT_LEFT_X - halfLw, CourtSpec.COURT_BOTTOM_Y - halfLw, lw, CourtSpec.COURT_LENGTH_FT + lw)
            sr.rect(CourtSpec.COURT_RIGHT_X - halfLw, CourtSpec.COURT_BOTTOM_Y - halfLw, lw, CourtSpec.COURT_LENGTH_FT + lw)
            sr.rect(CourtSpec.COURT_LEFT_X - halfLw, CourtSpec.COURT_BOTTOM_Y - halfLw, CourtSpec.COURT_WIDTH_FT + lw, lw)
            sr.rect(CourtSpec.COURT_LEFT_X - halfLw, CourtSpec.COURT_TOP_Y - halfLw, CourtSpec.COURT_WIDTH_FT + lw, lw)
            sr.rect(CourtSpec.COURT_LEFT_X, CourtSpec.LOWER_NVZ_Y - halfLw, CourtSpec.COURT_WIDTH_FT, lw)
            sr.rect(CourtSpec.COURT_LEFT_X, CourtSpec.UPPER_NVZ_Y - halfLw, CourtSpec.COURT_WIDTH_FT, lw)
            sr.rect(CourtSpec.COURT_CENTER_X - halfLw, CourtSpec.COURT_BOTTOM_Y, lw, CourtSpec.SERVICE_DEPTH_FT)
            sr.rect(CourtSpec.COURT_CENTER_X - halfLw, CourtSpec.UPPER_NVZ_Y, lw, CourtSpec.SERVICE_DEPTH_FT)
        }
    }

    private fun drawNet() {
        val postLeftX = CourtSpec.COURT_LEFT_X - CourtSpec.NET_POST_OFFSET_FT
        val postRightX = CourtSpec.COURT_RIGHT_X + CourtSpec.NET_POST_OFFSET_FT
        val netSpan = postRightX - postLeftX

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        shapeRenderer.use(ShapeRenderer.ShapeType.Filled) { sr ->
            sr.color = CourtColors.NET_SHADOW
            sr.rect(postLeftX, CourtSpec.NET_Y - 0.35f, netSpan, 0.35f)
            sr.color = CourtColors.NET_MESH
            sr.rect(postLeftX, CourtSpec.NET_Y - 0.14f, netSpan, 0.28f)
            sr.color = CourtColors.NET_TAPE
            sr.rect(postLeftX, CourtSpec.NET_Y - 0.06f, netSpan, 0.12f)
            sr.color = CourtColors.NET_POST
            sr.circle(postLeftX, CourtSpec.NET_Y, 0.28f, 16)
            sr.circle(postRightX, CourtSpec.NET_Y, 0.28f, 16)
        }
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    private fun drawPlayer(entity: PlayerEntity, showReachRing: Boolean) {
        val palette = CharacterCatalog.get(entity.characterId)
        val facingSign = if (entity.side == CourtSide.LOWER) 1f else -1f

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        shapeRenderer.use(ShapeRenderer.ShapeType.Filled) { sr ->
            sr.color = Color(0f, 0f, 0f, 0.28f)
            sr.ellipse(entity.x - 1.18f, entity.y - 0.62f, 2.36f, 1.12f, 18)

            if (entity.side == CourtSide.LOWER && showReachRing) {
                val pulse = 0.5f + 0.5f * kotlin.math.sin(elapsedSec * 12f)
                sr.color = Color(0.52f, 0.80f, 0.09f, 0.16f + pulse * 0.16f)
                sr.circle(entity.x, entity.y, SinglePlayerMatch.HIT_REACH_FT + pulse * 0.42f, 28)
            }

            val swingProgress = if (entity.swingTimer > 0f) 1f - (entity.swingTimer / 0.24f) else 0f
            val baseAngle = if (entity.side == CourtSide.LOWER) 0.25f else -2.89f
            val paddleAngle = baseAngle + facingSign * swingProgress * 1.45f
            val px = entity.x + cos(paddleAngle) * 1.48f
            val py = entity.y + sin(paddleAngle) * 1.48f

            sr.color = palette.paddle
            sr.circle(px, py, 0.54f, 16)

            sr.color = palette.shirt
            sr.circle(entity.x, entity.y, palette.bodyRadiusFt, 20)

            sr.color = palette.skin
            sr.circle(entity.x, entity.y + facingSign * 0.18f, 0.72f, 20)

            if (palette.hasBeard) {
                sr.color = palette.hair
                sr.circle(entity.x, entity.y + facingSign * 0.42f, 0.46f, 14)
            }

            sr.color = palette.headwear
            sr.rect(entity.x - 0.68f, entity.y + facingSign * 0.36f - 0.14f, 1.36f, 0.28f)
        }
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    private fun drawBall(ball: BallState) {
        val scale = BallPhysics.spriteScaleForHeight(ball.z)
        val radius = BallPhysics.BALL_RADIUS_FT * scale

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        shapeRenderer.use(ShapeRenderer.ShapeType.Filled) { sr ->
            val shadowAlpha = (0.42f - ball.z * 0.025f).coerceIn(0.12f, 0.42f)
            val shadowRadius = (BallPhysics.BALL_RADIUS_FT * (1f + ball.z * 0.04f)).coerceAtMost(0.45f)
            sr.color = Color(0.03f, 0.05f, 0.10f, shadowAlpha)
            sr.circle(ball.x, ball.y, shadowRadius, 14)

            val visualY = ball.y + ball.z * 0.32f
            sr.color = Color.valueOf("1E293B")
            sr.circle(ball.x, visualY, radius + 0.05f, 16)
            sr.color = Color.valueOf("FACC15")
            sr.circle(ball.x, visualY, radius, 16)
        }
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    private fun drawHud(om: OnlineMatch1v1?, dm: DoublesMatch?) {
        val localName = game.selectedCharacter.displayName
        val remoteName = remoteCharName ?: game.opponentCharacter.displayName
        val scoreLine = when {
            om != null -> "$localName ${om.scoreLocal} - ${om.scoreRemote} ${remoteCharName ?: "Opp"}"
            dm != null -> "2v2 Doubles ${dm.rules.formattedScoreCall()}"
            else -> "$localName ${soloMatch.rules.scoreLower} - ${soloMatch.rules.scoreUpper} $remoteName"
        }
        val statusLine = when {
            om != null -> onlineStatusText
            else -> if (game.scoringMode == ScoringMode.RALLY) "Rally to 11" else "Side-Out to 11"
        }
        val callout = om?.lastCallout ?: dm?.lastCallout ?: soloMatch.lastCallout

        hudViewport.apply()
        shapeRenderer.projectionMatrix = hudCamera.combined
        shapeRenderer.use(ShapeRenderer.ShapeType.Filled) { sr ->
            sr.color = Color.valueOf("1E293B")
            sr.rect(muteRect.x, muteRect.y, muteRect.width, muteRect.height)
            sr.rect(menuRect.x, menuRect.y, menuRect.width, menuRect.height)
        }

        batch.projectionMatrix = hudCamera.combined
        batch.use { sb ->
            hudFont.color = Color.WHITE
            hudFont.draw(sb, scoreLine, 14f, 882f)
            hudSmallFont.color = Color.valueOf("94A3B8")
            hudSmallFont.draw(sb, statusLine, 14f, 860f)

            hudSmallFont.color = Color.WHITE
            hudSmallFont.draw(sb, if (soundService.isMuted) "Muted" else "Mute", muteRect.x + 12f, muteRect.y + 25f)
            hudSmallFont.color = Color.valueOf("84CC16")
            hudSmallFont.draw(sb, "Menu", menuRect.x + 17f, menuRect.y + 25f)

            val showPersistentCallout = om != null && (om.rallyPhase == RallyPhase.WAITING_SERVE || om.rallyPhase == RallyPhase.MATCH_OVER)
            if ((calloutBannerTimerSec > 0f || showPersistentCallout) && callout.isNotBlank()) {
                calloutFont.color = if (callout.contains("fault") || callout.contains("Out") || callout.contains("Net") || callout.contains("disconnected")) {
                    Color.valueOf("FCA5A5")
                } else {
                    Color.valueOf("84CC16")
                }
                calloutFont.draw(sb, callout, 24f, 485f)
            }
        }
    }

    override fun dispose() {
        netJob?.cancel()
        scope.launch { networkService.leaveRoom() }
        scope.cancel()
        shapeRenderer.dispose()
        batch.dispose()
        hudFont.dispose()
        hudSmallFont.dispose()
        calloutFont.dispose()
    }
}
