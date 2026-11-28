package com.picklerally.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.picklerally.app.data.AppPreferencesSnapshot
import com.picklerally.app.data.LocalPrefsStore
import com.picklerally.app.data.NearbyConnectionsManager
import com.picklerally.app.data.StatsRepository
import com.picklerally.app.game.BotDifficulty
import com.picklerally.app.game.CharacterId
import com.picklerally.app.game.ScoringMode
import com.picklerally.app.game.SoundFxEngine
import com.picklerally.app.ui.auth.GoogleAuthManager
import com.picklerally.app.ui.screens.CharacterSelectScreen
import com.picklerally.app.ui.screens.CourtMatchKind
import com.picklerally.app.ui.screens.CourtScreen
import com.picklerally.app.ui.screens.LearnScreen
import com.picklerally.app.ui.screens.MainMenuScreen
import com.picklerally.app.ui.screens.SplashOverlay
import com.picklerally.app.ui.screens.StatsHubScreen
import com.picklerally.app.ui.screens.TitleModeTab
import com.picklerally.app.ui.theme.PickleRallyTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
private data object TitleMenuRoute

@Serializable
private data object CharacterSelectRoute

@Serializable
private data object LearnRoute

@Serializable
private data object StatsHubRoute

@Serializable
private data object CourtRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PickleRallyTheme {
                PickleRallyApp()
            }
        }
    }
}

@Composable
private fun PickleRallyApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()

    val localPrefs = remember(context) { LocalPrefsStore(context) }
    val statsRepo = remember(context, localPrefs) { StatsRepository(context, localPrefs) }
    val nearbyManager = remember(context) { NearbyConnectionsManager(context) }
    val soundFx = remember { SoundFxEngine() }
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val prefs by localPrefs.preferencesFlow.collectAsStateWithLifecycle(
        initialValue = AppPreferencesSnapshot(),
    )
    val authUser by GoogleAuthManager.authStateFlow().collectAsStateWithLifecycle(
        initialValue = Firebase.auth.currentUser,
    )
    val nearbyHosts by nearbyManager.discoveredHosts.collectAsStateWithLifecycle()
    val nearbySession by nearbyManager.session.collectAsStateWithLifecycle()
    val nearbyError by nearbyManager.errorMessage.collectAsStateWithLifecycle()

    var showSplash by remember { mutableStateOf(true) }
    var titleTab by remember { mutableStateOf(TitleModeTab.SOLO) }
    var scoringMode by remember { mutableStateOf(ScoringMode.RALLY) }
    var pointsToWin by remember { mutableIntStateOf(11) }
    var bestOf by remember { mutableIntStateOf(1) }
    var difficulty by remember { mutableStateOf(BotDifficulty.NORMAL) }
    var activeCourtKind by remember { mutableStateOf(CourtMatchKind.SOLO) }
    var nearbyHasPerms by remember { mutableStateOf(nearbyManager.hasAllPermissions()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, nearbyManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                nearbyHasPerms = nearbyManager.hasAllPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(prefs.muted) {
        soundFx.isMuted = prefs.muted
    }

    LaunchedEffect(Unit) {
        GoogleAuthManager.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            scope = scope,
            onAuthSuccess = {},
            onUnauthenticated = {},
        )
    }

    LaunchedEffect(authUser?.uid) {
        val user = authUser ?: return@LaunchedEffect
        statsRepo.ensureUserProfile(user, prefs.selectedCharacter).onSuccess { savedChar ->
            if (savedChar != prefs.selectedCharacter) {
                localPrefs.setSelectedCharacter(savedChar)
            }
            if (savedChar == prefs.opponentCharacter) {
                localPrefs.setOpponentCharacter(
                    if (savedChar == CharacterId.LOLA) CharacterId.RIO else CharacterId.LOLA,
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = TitleMenuRoute,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable<TitleMenuRoute> {
                MainMenuScreen(
                    titleTab = titleTab,
                    scoringMode = scoringMode,
                    pointsToWin = pointsToWin,
                    bestOf = bestOf,
                    difficulty = difficulty,
                    muted = prefs.muted,
                    playerChar = prefs.selectedCharacter,
                    partnerChar = prefs.partnerCharacter,
                    opponentChar = prefs.opponentCharacter,
                    authUser = authUser,
                    nearbyHosts = nearbyHosts,
                    nearbyError = nearbyError,
                    nearbyHasPermissions = nearbyHasPerms,
                    onSelectTitleTab = {
                        soundFx.playMenuClick()
                        titleTab = it
                    },
                    onSelectScoringMode = {
                        soundFx.playMenuClick()
                        scoringMode = it
                    },
                    onSelectPointsToWin = {
                        soundFx.playMenuClick()
                        pointsToWin = it
                    },
                    onSelectBestOf = {
                        soundFx.playMenuClick()
                        bestOf = it
                    },
                    onSelectDifficulty = {
                        soundFx.playMenuClick()
                        difficulty = it
                    },
                    onToggleMute = {
                        scope.launch { localPrefs.setMuted(!prefs.muted) }
                    },
                    onOpenStatsHub = {
                        soundFx.playMenuClick()
                        navController.navigate(StatsHubRoute)
                    },
                    onOpenLearn = {
                        soundFx.playMenuClick()
                        navController.navigate(LearnRoute)
                    },
                    onOpenCharacterSelect = {
                        soundFx.playMenuClick()
                        navController.navigate(CharacterSelectRoute)
                    },
                    onStartSoloOrDoubles = {
                        soundFx.playEnterCourt()
                        activeCourtKind = if (titleTab == TitleModeTab.DOUBLES) {
                            CourtMatchKind.DOUBLES
                        } else {
                            CourtMatchKind.SOLO
                        }
                        navController.navigate(CourtRoute)
                    },
                    onStartNearbyDiscovery = {
                        nearbyManager.startDiscovery()
                    },
                    onStopNearbyDiscovery = {
                        nearbyManager.stopDiscovery()
                    },
                    onHostNearby = {
                        soundFx.playEnterCourt()
                        nearbyManager.startHosting(prefs.selectedCharacter)
                        activeCourtKind = CourtMatchKind.NEARBY
                        navController.navigate(CourtRoute)
                    },
                    onJoinNearby = { host ->
                        soundFx.playEnterCourt()
                        nearbyManager.joinHost(host, prefs.selectedCharacter)
                        activeCourtKind = CourtMatchKind.NEARBY
                        navController.navigate(CourtRoute)
                    },
                )
            }

            composable<CharacterSelectRoute> {
                CharacterSelectScreen(
                    playerChar = prefs.selectedCharacter,
                    partnerChar = prefs.partnerCharacter,
                    opponentChar = prefs.opponentCharacter,
                    dillUnlocked = prefs.dillUnlocked,
                    onSelectPlayerChar = { id ->
                        if (id == CharacterId.DILL && !prefs.dillUnlocked) return@CharacterSelectScreen
                        soundFx.playMenuClick()
                        scope.launch {
                            statsRepo.saveUserCharacter(authUser, id)
                            if (id == prefs.opponentCharacter) {
                                localPrefs.setOpponentCharacter(
                                    if (id == CharacterId.LOLA) CharacterId.RIO else CharacterId.LOLA,
                                )
                            }
                        }
                    },
                    onSelectPartnerChar = { id ->
                        soundFx.playMenuClick()
                        scope.launch { localPrefs.setPartnerCharacter(id) }
                    },
                    onSelectOpponentChar = { id ->
                        soundFx.playMenuClick()
                        scope.launch { localPrefs.setOpponentCharacter(id) }
                    },
                    onToggleDillUnlock = {
                        soundFx.playMenuClick()
                        val next = !prefs.dillUnlocked
                        scope.launch {
                            localPrefs.setDillUnlocked(next)
                            if (next) {
                                statsRepo.saveUserCharacter(authUser, CharacterId.DILL)
                                if (prefs.opponentCharacter == CharacterId.DILL) {
                                    localPrefs.setOpponentCharacter(CharacterId.LOLA)
                                }
                            }
                        }
                    },
                    onBack = {
                        soundFx.playMenuClick()
                        navController.popBackStack()
                    },
                )
            }

            composable<LearnRoute> {
                LearnScreen(
                    onBack = {
                        soundFx.playMenuClick()
                        navController.popBackStack()
                    },
                )
            }

            composable<StatsHubRoute> {
                StatsHubScreen(
                    authUser = authUser,
                    playerChar = prefs.selectedCharacter,
                    repository = statsRepo,
                    onBack = {
                        soundFx.playMenuClick()
                        navController.popBackStack()
                    },
                )
            }

            composable<CourtRoute> {
                CourtScreen(
                    matchKind = activeCourtKind,
                    scoringMode = scoringMode,
                    pointsToWin = pointsToWin,
                    bestOf = bestOf,
                    difficulty = difficulty,
                    playerChar = prefs.selectedCharacter,
                    partnerChar = prefs.partnerCharacter,
                    opponentChar = prefs.opponentCharacter,
                    muted = prefs.muted,
                    hasSeenControlsHint = prefs.hasSeenControlsHint,
                    authUser = authUser,
                    soundFx = soundFx,
                    statsRepository = statsRepo,
                    nearbyManager = nearbyManager,
                    nearbyIsHost = nearbySession.isHost,
                    nearbyRemoteChar = nearbySession.remoteChar,
                    nearbyOpponentConnected = nearbySession.opponentConnected,
                    nearbyRoomCode = nearbySession.roomCode,
                    nearbyRemoteName = nearbySession.remoteName,
                    nearbyStatusMessage = nearbySession.statusMessage,
                    nearbyError = nearbyError,
                    onToggleMute = {
                        scope.launch { localPrefs.setMuted(!prefs.muted) }
                    },
                    onControlsHintSeen = {
                        scope.launch { localPrefs.markControlsHintSeen() }
                    },
                    onOpenCharacterSelect = {
                        soundFx.playMenuClick()
                        navController.navigate(CharacterSelectRoute)
                    },
                    onExitToMenu = {
                        navController.popBackStack(TitleMenuRoute, inclusive = false)
                    },
                )
            }
        }

        if (showSplash) {
            SplashOverlay(onFinished = { showSplash = false })
        }
    }
}
