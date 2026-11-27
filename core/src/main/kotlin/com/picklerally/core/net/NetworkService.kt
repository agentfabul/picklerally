package com.picklerally.core.net

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

enum class RoomMode {
    SINGLES_1V1,
    DOUBLES_2V2
}

enum class RoomStatus {
    WAITING,
    PLAYING,
    HOST_LEFT,
    FINISHED
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATED,
    IN_LOBBY,
    IN_MATCH,
    ERROR
}

data class RoomMetadata(
    val roomCode: String,
    val hostUid: String,
    val mode: RoomMode,
    val status: RoomStatus = RoomStatus.WAITING,
    val createdAtEpochMs: Long
)

data class PlayerSlot(
    val uid: String,
    val team: Int,
    val characterId: String,
    val connected: Boolean
)

data class PlayerInputSnapshot(
    val uid: String,
    val sequence: Long,
    val targetX: Float,
    val targetY: Float,
    val swingType: String? = null,
    val aimX: Float = 0f,
    val aimY: Float = 0f,
    val swipeDurationSec: Float = 0.2f,
    val timestampMs: Long = 0L
)

data class MatchStateSnapshot(
    val tick: Long = 0L,
    val matchMode: String = "SINGLES_1V1",
    val ballX: Float = 14f,
    val ballY: Float = 7f,
    val ballZ: Float = 2.4f,
    val ballVx: Float = 0f,
    val ballVy: Float = 0f,
    val ballVz: Float = 0f,
    val ballActive: Boolean = false,
    val hostX: Float = 18.5f,
    val hostY: Float = 4.9f,
    val guestX: Float = 9.5f,
    val guestY: Float = 51.1f,
    val hostPartnerX: Float = 9.5f,
    val hostPartnerY: Float = 6.0f,
    val guestPartnerX: Float = 18.5f,
    val guestPartnerY: Float = 50.0f,
    val hostSwingTimer: Float = 0f,
    val guestSwingTimer: Float = 0f,
    val hostPartnerSwingTimer: Float = 0f,
    val guestPartnerSwingTimer: Float = 0f,
    val scoreHost: Int = 0,
    val scoreGuest: Int = 0,
    val servingSide: String = "HOST",
    val serverNumberCall: Int = 1,
    val lowerRightPlayerIndex: Int = 0,
    val upperRightPlayerIndex: Int = 0,
    val rallyShotCount: Int = 0,
    val rallyHits: Int = 0,
    val lastEvent: String? = null,
    val matchWinner: String? = null,
    val publishedAtMs: Long = 0L
)

object RoomCodeGenerator {
    private val roomRegex = Regex("^PKL-[0-9]{3}$")

    fun generate(rng: Random = Random.Default): String {
        val code = rng.nextInt(100, 1000)
        return "PKL-$code"
    }

    fun isValid(code: String): Boolean = roomRegex.matches(code.trim().uppercase())
}

/**
 * Pure Kotlin multiplayer abstraction in core/.
 * Implemented with Firebase Auth & Realtime Database in android/ and in-memory fake in desktop/.
 */
interface NetworkService {
    val connectionState: Flow<ConnectionState>
    val currentUid: String?

    suspend fun signInAnonymously(): Result<String>
    suspend fun createRoom(mode: RoomMode, characterId: String): Result<RoomMetadata>
    suspend fun joinRoom(roomCode: String, characterId: String): Result<RoomMetadata>
    suspend fun updateRoomStatus(roomCode: String, status: RoomStatus)
    suspend fun leaveRoom()

    suspend fun sendInput(roomCode: String, input: PlayerInputSnapshot)
    suspend fun publishState(roomCode: String, state: MatchStateSnapshot)

    fun observeRoomMeta(roomCode: String): Flow<RoomMetadata?>
    fun observePlayers(roomCode: String): Flow<List<PlayerSlot>>
    fun observeInputs(roomCode: String): Flow<Map<String, PlayerInputSnapshot>>
    fun observeMatchState(roomCode: String): Flow<MatchStateSnapshot?>

    fun dispose()
}

/** A nearby game found by "Find Players" (Nearby / offline mode). */
data class NearbyHost(
    val endpointId: String,
    val hostName: String,
    val roomCode: String
)

/**
 * Optional capability for local, offline play: lets the menu search for nearby hosts.
 * Implemented on Android with Google Nearby Connections (Wi-Fi, hotspot or Bluetooth).
 */
interface NearbyDiscovery {
    val nearbyHosts: StateFlow<List<NearbyHost>>
    val nearbyError: StateFlow<String?>
    fun startDiscovery()
    fun stopDiscovery()
}
