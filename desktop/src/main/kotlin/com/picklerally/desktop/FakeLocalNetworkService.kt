package com.picklerally.desktop

import com.picklerally.core.net.ConnectionState
import com.picklerally.core.net.MatchStateSnapshot
import com.picklerally.core.net.NetworkService
import com.picklerally.core.net.PlayerInputSnapshot
import com.picklerally.core.net.PlayerSlot
import com.picklerally.core.net.RoomCodeGenerator
import com.picklerally.core.net.RoomMetadata
import com.picklerally.core.net.RoomMode
import com.picklerally.core.net.RoomStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * In-memory multi-client room hub for local Desktop testing without Firebase.
 * Multiple FakeLocalNetworkService instances in the same JVM can create and join rooms seamlessly.
 */
class FakeLocalNetworkService(
    private val playerUid: String = "desktop-player-${Random.nextInt(100, 999)}"
) : NetworkService {

    private data class InMemoryRoom(
        val meta: MutableStateFlow<RoomMetadata>,
        val players: MutableStateFlow<List<PlayerSlot>>,
        val inputs: MutableStateFlow<Map<String, PlayerInputSnapshot>>,
        val state: MutableStateFlow<MatchStateSnapshot?>
    )

    companion object {
        private val rooms = ConcurrentHashMap<String, InMemoryRoom>()
    }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: Flow<ConnectionState> = _connectionState.asStateFlow()
    override val currentUid: String = playerUid

    private var activeRoomCode: String? = null
    private var isHost: Boolean = false

    override suspend fun signInAnonymously(): Result<String> {
        _connectionState.value = ConnectionState.AUTHENTICATED
        return Result.success(currentUid)
    }

    override suspend fun createRoom(mode: RoomMode, characterId: String): Result<RoomMetadata> {
        val code = RoomCodeGenerator.generate()
        val meta = RoomMetadata(
            roomCode = code,
            hostUid = currentUid,
            mode = mode,
            status = RoomStatus.WAITING,
            createdAtEpochMs = System.currentTimeMillis()
        )
        val hostSlot = PlayerSlot(currentUid, team = 0, characterId = characterId, connected = true)
        rooms[code] = InMemoryRoom(
            meta = MutableStateFlow(meta),
            players = MutableStateFlow(listOf(hostSlot)),
            inputs = MutableStateFlow(emptyMap()),
            state = MutableStateFlow(null)
        )
        activeRoomCode = code
        isHost = true
        _connectionState.value = ConnectionState.IN_LOBBY
        return Result.success(meta)
    }

    override suspend fun joinRoom(roomCode: String, characterId: String): Result<RoomMetadata> {
        val normalized = roomCode.trim().uppercase()
        val room = rooms[normalized]
            ?: return Result.failure(IllegalArgumentException("Room $normalized not found"))
        val guestSlot = PlayerSlot(currentUid, team = 1, characterId = characterId, connected = true)
        room.players.value = room.players.value.filterNot { it.uid == currentUid } + guestSlot
        activeRoomCode = normalized
        isHost = room.meta.value.hostUid == currentUid
        _connectionState.value = ConnectionState.IN_MATCH
        return Result.success(room.meta.value)
    }

    override suspend fun updateRoomStatus(roomCode: String, status: RoomStatus) {
        val normalized = roomCode.trim().uppercase()
        val room = rooms[normalized] ?: return
        room.meta.value = room.meta.value.copy(status = status)
        if (status == RoomStatus.PLAYING) {
            _connectionState.value = ConnectionState.IN_MATCH
        }
    }

    override suspend fun leaveRoom() {
        val code = activeRoomCode ?: return
        val room = rooms[code]
        if (room != null) {
            room.players.value = room.players.value.map {
                if (it.uid == currentUid) it.copy(connected = false) else it
            }
            if (isHost) {
                room.meta.value = room.meta.value.copy(status = RoomStatus.HOST_LEFT)
            }
        }
        activeRoomCode = null
        isHost = false
        _connectionState.value = ConnectionState.AUTHENTICATED
    }

    override suspend fun sendInput(roomCode: String, input: PlayerInputSnapshot) {
        val room = rooms[roomCode.trim().uppercase()] ?: return
        room.inputs.value = room.inputs.value + (input.uid to input)
    }

    override suspend fun publishState(roomCode: String, state: MatchStateSnapshot) {
        val room = rooms[roomCode.trim().uppercase()] ?: return
        room.state.value = state
    }

    override fun observeRoomMeta(roomCode: String): Flow<RoomMetadata?> =
        rooms[roomCode.trim().uppercase()]?.meta?.asStateFlow() ?: MutableStateFlow(null)

    override fun observePlayers(roomCode: String): Flow<List<PlayerSlot>> =
        rooms[roomCode.trim().uppercase()]?.players?.asStateFlow() ?: MutableStateFlow(emptyList())

    override fun observeInputs(roomCode: String): Flow<Map<String, PlayerInputSnapshot>> =
        rooms[roomCode.trim().uppercase()]?.inputs?.asStateFlow() ?: MutableStateFlow(emptyMap())

    override fun observeMatchState(roomCode: String): Flow<MatchStateSnapshot?> =
        rooms[roomCode.trim().uppercase()]?.state?.asStateFlow() ?: MutableStateFlow(null)

    override fun dispose() {
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
