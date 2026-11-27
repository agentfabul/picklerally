package com.picklerally.android

import com.picklerally.core.net.ConnectionState
import com.picklerally.core.net.MatchStateSnapshot
import com.picklerally.core.net.NetworkService
import com.picklerally.core.net.PlayerInputSnapshot
import com.picklerally.core.net.PlayerSlot
import com.picklerally.core.net.RoomMetadata
import com.picklerally.core.net.RoomMode
import com.picklerally.core.net.RoomStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Offline fallback stub implementing the full [NetworkService] contract for instrumented tests or offline play.
 */
class LocalStubNetworkService : NetworkService {
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: Flow<ConnectionState> = _connectionState.asStateFlow()
    override val currentUid: String = "android-local-player"

    override suspend fun signInAnonymously(): Result<String> {
        _connectionState.value = ConnectionState.AUTHENTICATED
        return Result.success(currentUid)
    }

    override suspend fun createRoom(mode: RoomMode, characterId: String): Result<RoomMetadata> {
        _connectionState.value = ConnectionState.IN_LOBBY
        return Result.success(
            RoomMetadata(
                roomCode = "PKL-000",
                hostUid = currentUid,
                mode = mode,
                status = RoomStatus.WAITING,
                createdAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    override suspend fun joinRoom(roomCode: String, characterId: String): Result<RoomMetadata> =
        Result.failure(UnsupportedOperationException("Use FirebaseNetworkService for online rooms"))

    override suspend fun updateRoomStatus(roomCode: String, status: RoomStatus) = Unit

    override suspend fun leaveRoom() {
        _connectionState.value = ConnectionState.AUTHENTICATED
    }

    override suspend fun sendInput(roomCode: String, input: PlayerInputSnapshot) = Unit

    override suspend fun publishState(roomCode: String, state: MatchStateSnapshot) = Unit

    override fun observeRoomMeta(roomCode: String): Flow<RoomMetadata?> = flowOf(null)

    override fun observePlayers(roomCode: String): Flow<List<PlayerSlot>> = flowOf(emptyList())

    override fun observeInputs(roomCode: String): Flow<Map<String, PlayerInputSnapshot>> = flowOf(emptyMap())

    override fun observeMatchState(roomCode: String): Flow<MatchStateSnapshot?> = flowOf(null)

    override fun dispose() {
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
