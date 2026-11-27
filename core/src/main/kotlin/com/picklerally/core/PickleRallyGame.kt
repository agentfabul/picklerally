package com.picklerally.core

import com.picklerally.core.audio.GdxSoundService
import com.picklerally.core.audio.SoundService
import com.picklerally.core.characters.CharacterAssetLoader
import com.picklerally.core.characters.CharacterId
import com.picklerally.core.net.NetworkService
import com.picklerally.core.rules.MatchMode
import com.picklerally.core.rules.ScoringMode
import com.picklerally.core.screens.CharacterSelectScreen
import com.picklerally.core.screens.CourtScreen
import com.picklerally.core.screens.MainMenuScreen
import com.picklerally.core.screens.NearbyLobbyScreen
import ktx.app.KtxGame
import ktx.app.KtxScreen

/**
 * Main libGDX game entry point shared by Android and Desktop launchers.
 */
class PickleRallyGame(
    val networkService: NetworkService,
    val soundService: SoundService = GdxSoundService(),
    /** Offline Nearby 1v1 transport (Android only); null on desktop. */
    val nearbyService: NetworkService? = null
) : KtxGame<KtxScreen>() {

    /** True while a Nearby (offline) match is being set up or played. */
    var useNearby: Boolean = false

    /** The transport the court should use right now: Nearby or online. */
    val activeNetworkService: NetworkService
        get() = if (useNearby) nearbyService ?: networkService else networkService

    var selectedCharacter: CharacterId = CharacterId.RIO
    var opponentCharacter: CharacterId = CharacterId.LOLA
    var scoringMode: ScoringMode = ScoringMode.RALLY
    var matchMode: MatchMode = MatchMode.SINGLES
    var dillUnlocked: Boolean = false

    val assetLoader: CharacterAssetLoader by lazy { CharacterAssetLoader() }

    private lateinit var courtScreen: CourtScreen

    override fun create() {
        courtScreen = CourtScreen(this)
        addScreen(MainMenuScreen(this))
        addScreen(CharacterSelectScreen(this))
        addScreen(courtScreen)
        addScreen(NearbyLobbyScreen(this))
        setScreen<MainMenuScreen>()
    }

    fun openMainMenu() {
        courtScreen.leaveOnlineRoom()
        useNearby = false
        setScreen<MainMenuScreen>()
    }

    fun openNearbyLobby() {
        setScreen<NearbyLobbyScreen>()
    }

    fun openCharacterSelect() {
        setScreen<CharacterSelectScreen>()
    }

    fun openCourt(
        onlineHost: Boolean,
        doubles: Boolean = (matchMode == MatchMode.DOUBLES),
        joinRoomCode: String? = null
    ) {
        courtScreen.configureMatch(
            playerChar = selectedCharacter,
            botChar = opponentCharacter,
            scoringMode = scoringMode,
            matchMode = if (doubles) MatchMode.DOUBLES else MatchMode.SINGLES,
            startOnlineHost = onlineHost,
            joinRoomCode = joinRoomCode
        )
        setScreen<CourtScreen>()
    }

    override fun dispose() {
        super.dispose()
        assetLoader.dispose()
        soundService.dispose()
        networkService.dispose()
        nearbyService?.dispose()
    }
}
