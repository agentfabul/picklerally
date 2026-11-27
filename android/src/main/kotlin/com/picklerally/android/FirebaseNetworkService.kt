package com.picklerally.android

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.picklerally.core.net.ConnectionState
import com.picklerally.core.net.MatchStateSnapshot
import com.picklerally.core.net.NetworkService
import com.picklerally.core.net.PlayerInputSnapshot
import com.picklerally.core.net.PlayerSlot
import com.picklerally.core.net.RoomCodeGenerator
import com.picklerally.core.net.RoomMetadata
import com.picklerally.core.net.RoomMode
import com.picklerally.core.net.RoomStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Android Firebase implementation of [NetworkService] using Firebase Auth & Realtime Database.
 * Room paths:
 * - rooms/{roomCode}/meta
 * - rooms/{roomCode}/players/{uid}
 * - rooms/{roomCode}/inputs/{uid}
 * - rooms/{roomCode}/state
 */
class FirebaseNetworkService(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) : NetworkService {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: Flow<ConnectionState> = _connectionState.asStateFlow()

    override val currentUid: String?
        get() = auth.currentUser?.uid

    private var activeRoomCode: String? = null
    private var isHostInActiveRoom: Boolean = false

    override suspend fun signInAnonymously(): Result<String> = runCatching {
        _connectionState.value = ConnectionState.CONNECTING
        val existingUid = auth.currentUser?.uid
        val uid = existingUid ?: auth.signInAnonymously().await().user?.uid
            ?: error("Firebase anonymous auth returned null user")
        _connectionState.value = ConnectionState.AUTHENTICATED
        uid
    }.onFailure {
        _connectionState.value = ConnectionState.ERROR
    }

    override suspend fun createRoom(mode: RoomMode, characterId: String): Result<RoomMetadata> = runCatching {
        val uid = currentUid ?: signInAnonymously().getOrThrow()
        val roomCode = allocateAvailableRoomCode()
        val nowMs = System.currentTimeMillis()
        val roomRef = database.getReference("rooms").child(roomCode)

        val metaMap = mapOf(
            "host" to uid,
            "mode" to mode.name,
            "status" to RoomStatus.WAITING.name,
            "createdAt" to nowMs
        )
        roomRef.child("meta").setValue(metaMap).await()
        roomRef.child("meta").child("status").onDisconnect().setValue(RoomStatus.HOST_LEFT.name)

        val playerMap = mapOf(
            "uid" to uid,
            "team" to 0,
            "characterId" to characterId,
            "connected" to true
        )
        roomRef.child("players").child(uid).setValue(playerMap).await()
        roomRef.child("players").child(uid).child("connected").onDisconnect().setValue(false)

        activeRoomCode = roomCode
        isHostInActiveRoom = true
        _connectionState.value = ConnectionState.IN_LOBBY

        RoomMetadata(
            roomCode = roomCode,
            hostUid = uid,
            mode = mode,
            status = RoomStatus.WAITING,
            createdAtEpochMs = nowMs
        )
    }.onFailure {
        _connectionState.value = ConnectionState.ERROR
    }

    override suspend fun joinRoom(roomCode: String, characterId: String): Result<RoomMetadata> = runCatching {
        val normalized = roomCode.trim().uppercase()
        require(RoomCodeGenerator.isValid(normalized)) { "Invalid room code format (expected PKL-###)" }

        val uid = currentUid ?: signInAnonymously().getOrThrow()
        val roomRef = database.getReference("rooms").child(normalized)
        val metaSnap = roomRef.child("meta").get().await()
        require(metaSnap.exists()) { "Room $normalized not found" }

        val hostUid = metaSnap.child("host").getValue(String::class.java)
            ?: error("Room $normalized missing host")
        val mode = metaSnap.child("mode").getValue(String::class.java)
            ?.let { runCatching { RoomMode.valueOf(it) }.getOrNull() }
            ?: RoomMode.SINGLES_1V1
        val status = metaSnap.child("status").getValue(String::class.java)
            ?.let { runCatching { RoomStatus.valueOf(it) }.getOrNull() }
            ?: RoomStatus.WAITING
        val createdAt = metaSnap.child("createdAt").getValue(Long::class.java) ?: 0L

        require(status == RoomStatus.WAITING || status == RoomStatus.PLAYING) {
            "Room $normalized is closed ($status)"
        }

        val playersSnap = roomRef.child("players").get().await()
        val team0Count = playersSnap.children.count { (it.child("team").getValue(Long::class.java) ?: 0L) == 0L }
        val team1Count = playersSnap.children.count { (it.child("team").getValue(Long::class.java) ?: 0L) == 1L }
        val maxPerTeam = if (mode == RoomMode.DOUBLES_2V2) 2 else 1
        require(hostUid == uid || playersSnap.hasChild(uid) || team0Count + team1Count < maxPerTeam * 2) {
            "Room $normalized is full"
        }

        val team = when {
            hostUid == uid -> 0
            mode == RoomMode.DOUBLES_2V2 && team0Count < 2 -> 0
            else -> 1
        }
        val playerMap = mapOf(
            "uid" to uid,
            "team" to team,
            "characterId" to characterId,
            "connected" to true
        )
        roomRef.child("players").child(uid).setValue(playerMap).await()
        roomRef.child("players").child(uid).child("connected").onDisconnect().setValue(false)

        activeRoomCode = normalized
        isHostInActiveRoom = hostUid == uid
        _connectionState.value = ConnectionState.IN_MATCH

        RoomMetadata(
            roomCode = normalized,
            hostUid = hostUid,
            mode = mode,
            status = status,
            createdAtEpochMs = createdAt
        )
    }.onFailure {
        _connectionState.value = ConnectionState.ERROR
    }

    override suspend fun updateRoomStatus(roomCode: String, status: RoomStatus) {
        val normalized = roomCode.trim().uppercase()
        database.getReference("rooms").child(normalized)
            .child("meta").child("status")
            .setValue(status.name)
            .await()
        if (status == RoomStatus.PLAYING) {
            _connectionState.value = ConnectionState.IN_MATCH
        }
    }

    override suspend fun leaveRoom() {
        val code = activeRoomCode ?: return
        val uid = currentUid
        val roomRef = database.getReference("rooms").child(code)
        runCatching {
            if (uid != null) {
                roomRef.child("players").child(uid).child("connected").setValue(false).await()
            }
            if (isHostInActiveRoom) {
                roomRef.child("meta").child("status").setValue(RoomStatus.HOST_LEFT.name).await()
            }
        }
        activeRoomCode = null
        isHostInActiveRoom = false
        _connectionState.value = if (uid != null) ConnectionState.AUTHENTICATED else ConnectionState.DISCONNECTED
    }

    override suspend fun sendInput(roomCode: String, input: PlayerInputSnapshot) {
        val normalized = roomCode.trim().uppercase()
        val payload = mapOf(
            "uid" to input.uid,
            "sequence" to input.sequence,
            "targetX" to input.targetX.toDouble(),
            "targetY" to input.targetY.toDouble(),
            "swingType" to input.swingType,
            "aimX" to input.aimX.toDouble(),
            "aimY" to input.aimY.toDouble(),
            "swipeDurationSec" to input.swipeDurationSec.toDouble(),
            "timestampMs" to if (input.timestampMs > 0L) input.timestampMs else System.currentTimeMillis()
        )
        database.getReference("rooms").child(normalized)
            .child("inputs").child(input.uid)
            .setValue(payload)
            .await()
    }

    override suspend fun publishState(roomCode: String, state: MatchStateSnapshot) {
        val normalized = roomCode.trim().uppercase()
        val payload = mapOf(
            "tick" to state.tick,
            "matchMode" to state.matchMode,
            "ballX" to state.ballX.toDouble(),
            "ballY" to state.ballY.toDouble(),
            "ballZ" to state.ballZ.toDouble(),
            "ballVx" to state.ballVx.toDouble(),
            "ballVy" to state.ballVy.toDouble(),
            "ballVz" to state.ballVz.toDouble(),
            "ballActive" to state.ballActive,
            "hostX" to state.hostX.toDouble(),
            "hostY" to state.hostY.toDouble(),
            "guestX" to state.guestX.toDouble(),
            "guestY" to state.guestY.toDouble(),
            "hostPartnerX" to state.hostPartnerX.toDouble(),
            "hostPartnerY" to state.hostPartnerY.toDouble(),
            "guestPartnerX" to state.guestPartnerX.toDouble(),
            "guestPartnerY" to state.guestPartnerY.toDouble(),
            "hostSwingTimer" to state.hostSwingTimer.toDouble(),
            "guestSwingTimer" to state.guestSwingTimer.toDouble(),
            "hostPartnerSwingTimer" to state.hostPartnerSwingTimer.toDouble(),
            "guestPartnerSwingTimer" to state.guestPartnerSwingTimer.toDouble(),
            "scoreHost" to state.scoreHost,
            "scoreGuest" to state.scoreGuest,
            "servingSide" to state.servingSide,
            "serverNumberCall" to state.serverNumberCall,
            "lowerRightPlayerIndex" to state.lowerRightPlayerIndex,
            "upperRightPlayerIndex" to state.upperRightPlayerIndex,
            "rallyShotCount" to state.rallyShotCount,
            "rallyHits" to state.rallyHits,
            "lastEvent" to state.lastEvent,
            "matchWinner" to state.matchWinner,
            "publishedAtMs" to if (state.publishedAtMs > 0L) state.publishedAtMs else System.currentTimeMillis()
        )
        database.getReference("rooms").child(normalized)
            .child("state")
            .setValue(payload)
            .await()
    }

    override fun observeRoomMeta(roomCode: String): Flow<RoomMetadata?> = callbackFlow {
        val normalized = roomCode.trim().uppercase()
        val ref = database.getReference("rooms").child(normalized).child("meta")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    trySend(null)
                    return
                }
                val hostUid = snapshot.child("host").getValue(String::class.java) ?: ""
                val mode = snapshot.child("mode").getValue(String::class.java)
                    ?.let { runCatching { RoomMode.valueOf(it) }.getOrNull() } ?: RoomMode.SINGLES_1V1
                val status = snapshot.child("status").getValue(String::class.java)
                    ?.let { runCatching { RoomStatus.valueOf(it) }.getOrNull() } ?: RoomStatus.WAITING
                val createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L
                trySend(RoomMetadata(normalized, hostUid, mode, status, createdAt))
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override fun observePlayers(roomCode: String): Flow<List<PlayerSlot>> = callbackFlow {
        val normalized = roomCode.trim().uppercase()
        val ref = database.getReference("rooms").child(normalized).child("players")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { child ->
                    val uid = child.child("uid").getValue(String::class.java) ?: return@mapNotNull null
                    val team = (child.child("team").getValue(Long::class.java) ?: 0L).toInt()
                    val charId = child.child("characterId").getValue(String::class.java) ?: "RIO"
                    val connected = child.child("connected").getValue(Boolean::class.java) ?: false
                    PlayerSlot(uid = uid, team = team, characterId = charId, connected = connected)
                }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override fun observeInputs(roomCode: String): Flow<Map<String, PlayerInputSnapshot>> = callbackFlow {
        val normalized = roomCode.trim().uppercase()
        val ref = database.getReference("rooms").child(normalized).child("inputs")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val map = buildMap {
                    snapshot.children.forEach { child ->
                        val uid = child.child("uid").getValue(String::class.java) ?: return@forEach
                        val seq = child.child("sequence").getValue(Long::class.java) ?: 0L
                        val targetX = (child.child("targetX").getValue(Double::class.java) ?: 14.0).toFloat()
                        val targetY = (child.child("targetY").getValue(Double::class.java) ?: 6.0).toFloat()
                        val swingType = child.child("swingType").getValue(String::class.java)
                        val aimX = (child.child("aimX").getValue(Double::class.java) ?: 0.0).toFloat()
                        val aimY = (child.child("aimY").getValue(Double::class.java) ?: 0.0).toFloat()
                        val duration = (child.child("swipeDurationSec").getValue(Double::class.java) ?: 0.2).toFloat()
                        val ts = child.child("timestampMs").getValue(Long::class.java) ?: 0L
                        put(
                            uid,
                            PlayerInputSnapshot(
                                uid = uid,
                                sequence = seq,
                                targetX = targetX,
                                targetY = targetY,
                                swingType = swingType,
                                aimX = aimX,
                                aimY = aimY,
                                swipeDurationSec = duration,
                                timestampMs = ts
                            )
                        )
                    }
                }
                trySend(map)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override fun observeMatchState(roomCode: String): Flow<MatchStateSnapshot?> = callbackFlow {
        val normalized = roomCode.trim().uppercase()
        val ref = database.getReference("rooms").child(normalized).child("state")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    trySend(null)
                    return
                }
                fun floatAt(key: String, default: Float = 0f): Float =
                    (snapshot.child(key).getValue(Double::class.java) ?: default.toDouble()).toFloat()

                trySend(
                    MatchStateSnapshot(
                        tick = snapshot.child("tick").getValue(Long::class.java) ?: 0L,
                        matchMode = snapshot.child("matchMode").getValue(String::class.java) ?: "SINGLES_1V1",
                        ballX = floatAt("ballX", 14f),
                        ballY = floatAt("ballY", 7f),
                        ballZ = floatAt("ballZ", 2.4f),
                        ballVx = floatAt("ballVx"),
                        ballVy = floatAt("ballVy"),
                        ballVz = floatAt("ballVz"),
                        ballActive = snapshot.child("ballActive").getValue(Boolean::class.java) ?: false,
                        hostX = floatAt("hostX", 18.5f),
                        hostY = floatAt("hostY", 4.9f),
                        guestX = floatAt("guestX", 9.5f),
                        guestY = floatAt("guestY", 51.1f),
                        hostPartnerX = floatAt("hostPartnerX", 9.5f),
                        hostPartnerY = floatAt("hostPartnerY", 6.0f),
                        guestPartnerX = floatAt("guestPartnerX", 18.5f),
                        guestPartnerY = floatAt("guestPartnerY", 50.0f),
                        hostSwingTimer = floatAt("hostSwingTimer"),
                        guestSwingTimer = floatAt("guestSwingTimer"),
                        hostPartnerSwingTimer = floatAt("hostPartnerSwingTimer"),
                        guestPartnerSwingTimer = floatAt("guestPartnerSwingTimer"),
                        scoreHost = (snapshot.child("scoreHost").getValue(Long::class.java) ?: 0L).toInt(),
                        scoreGuest = (snapshot.child("scoreGuest").getValue(Long::class.java) ?: 0L).toInt(),
                        servingSide = snapshot.child("servingSide").getValue(String::class.java) ?: "HOST",
                        serverNumberCall = (snapshot.child("serverNumberCall").getValue(Long::class.java) ?: 1L).toInt(),
                        lowerRightPlayerIndex = (snapshot.child("lowerRightPlayerIndex").getValue(Long::class.java) ?: 0L).toInt(),
                        upperRightPlayerIndex = (snapshot.child("upperRightPlayerIndex").getValue(Long::class.java) ?: 0L).toInt(),
                        rallyShotCount = (snapshot.child("rallyShotCount").getValue(Long::class.java) ?: 0L).toInt(),
                        rallyHits = (snapshot.child("rallyHits").getValue(Long::class.java) ?: 0L).toInt(),
                        lastEvent = snapshot.child("lastEvent").getValue(String::class.java),
                        matchWinner = snapshot.child("matchWinner").getValue(String::class.java),
                        publishedAtMs = snapshot.child("publishedAtMs").getValue(Long::class.java) ?: 0L
                    )
                )
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override fun dispose() {
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private suspend fun allocateAvailableRoomCode(): String {
        repeat(6) {
            val candidate = RoomCodeGenerator.generate()
            val snap = database.getReference("rooms").child(candidate).child("meta").get().await()
            if (!snap.exists()) return candidate
        }
        return RoomCodeGenerator.generate()
    }
}
