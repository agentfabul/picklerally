package com.picklerally.app.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.picklerally.app.game.CharacterId
import com.picklerally.app.game.CourtSpec
import com.picklerally.app.game.MatchStateSnapshot
import com.picklerally.app.game.PlayerInputSnapshot
import com.picklerally.app.game.RoomCodeGenerator
import com.picklerally.app.game.RoomStatus
import com.picklerally.app.game.characterPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlin.math.abs

data class OnlineSessionState(
    val isActive: Boolean = false,
    val isHost: Boolean = false,
    val roomCode: String = "",
    val localUid: String = "",
    val localChar: CharacterId = CharacterId.RIO,
    val localName: String = "",
    val remoteUid: String = "",
    val remoteChar: CharacterId = CharacterId.LOLA,
    val remoteName: String = "",
    val remoteRating: Int? = null,
    val opponentConnected: Boolean = false,
    val statusMessage: String = "",
)

data class QueueCandidate(
    val code: String,
    val host: String,
    val name: String,
    val characterId: String,
    val rating: Int,
    val createdAtMs: Long,
    val claimedBy: String? = null,
)

class OnlineRoomManager(
    private val auth: FirebaseAuth = Firebase.auth,
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(RTDB_URL),
) {
    companion object {
        private const val TAG = "OnlineRoomManager"
        const val RTDB_URL = "https://gen-lang-client-0455784437-default-rtdb.firebaseio.com"
        const val MAX_PROFILE_NAME_LEN = 14
        const val QUEUE_TTL_MS = 90_000L

        fun sanitizeProfileName(raw: String, fallbackChar: CharacterId): String {
            val cleaned = raw.trim().take(MAX_PROFILE_NAME_LEN)
            return cleaned.ifBlank { characterPalette(fallbackChar).name.take(MAX_PROFILE_NAME_LEN) }
        }

        fun normalizeRoomCode(raw: String): String {
            val trimmed = raw.trim().uppercase()
            return if (trimmed.length == 3 && trimmed.all { it.isDigit() }) {
                "PKL-$trimmed"
            } else {
                trimmed
            }
        }

        fun selectAndSortQueueCandidates(
            candidates: List<QueueCandidate>,
            myUid: String,
            myRating: Int,
            nowMs: Long,
        ): List<QueueCandidate> = candidates
            .filter { cand ->
                RoomCodeGenerator.isValid(cand.code) &&
                    cand.host.isNotBlank() &&
                    cand.host != myUid &&
                    cand.claimedBy.isNullOrBlank() &&
                    cand.createdAtMs > 0L &&
                    (nowMs - cand.createdAtMs) <= QUEUE_TTL_MS
            }
            .sortedWith(
                compareBy<QueueCandidate> { abs(it.rating - myRating) }
                    .thenBy { it.createdAtMs },
            )
    }

    private val roomsRoot: DatabaseReference by lazy { database.reference.child("rooms") }
    private val queueRoot: DatabaseReference by lazy { database.reference.child("queue") }

    private val _session = MutableStateFlow(OnlineSessionState())
    val session: StateFlow<OnlineSessionState> = _session.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _isSearchingRandom = MutableStateFlow(false)
    val isSearchingRandom: StateFlow<Boolean> = _isSearchingRandom.asStateFlow()

    var onRemoteInput: ((PlayerInputSnapshot) -> Unit)? = null
    var onHostSnapshot: ((MatchStateSnapshot) -> Unit)? = null
    var onRoomStatusChange: ((RoomStatus) -> Unit)? = null
    var onRemotePeerInfoReceived: ((CharacterId, String) -> Unit)? = null

    private var activeRoomRef: DatabaseReference? = null
    private var activeQueueRef: DatabaseReference? = null
    private var playersListener: ValueEventListener? = null
    private var metaStatusListener: ValueEventListener? = null
    private var inputsListener: ValueEventListener? = null
    private var stateListener: ValueEventListener? = null

    private var lastStatePushMs: Long = 0L
    private var lastInputPushMs: Long = 0L
    private var lastSeenInputSeq: Long = -1L
    private var matchFinished: Boolean = false
    private var lastPublishedMetaStatus: String = ""

    fun clearError() {
        _errorMessage.value = null
    }

    private suspend fun ensureSignedInUser(): FirebaseUser {
        val current = auth.currentUser
        if (current != null && current.uid.isNotBlank()) return current
        val res = auth.signInAnonymously().await()
        return res.user ?: throw IllegalStateException("Could not sign in to Firebase Auth.")
    }

    suspend fun createRoom(
        hostCharacter: CharacterId,
        hostProfileName: String,
        hostRating: Int = 1000,
    ): Result<String> {
        if (_isBusy.value) return Result.failure(IllegalStateException("Already connecting…"))
        _isBusy.value = true
        _isSearchingRandom.value = false
        _errorMessage.value = null
        detachRoom(markDisconnected = true)

        return try {
            createRoomInternal(hostCharacter, hostProfileName, hostRating)
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun joinRoom(
        rawRoomCode: String,
        localCharacter: CharacterId,
        localProfileName: String,
        localRating: Int = 1000,
    ): Result<String> {
        if (_isBusy.value) return Result.failure(IllegalStateException("Already connecting…"))
        val code = normalizeRoomCode(rawRoomCode)
        if (!RoomCodeGenerator.isValid(code)) {
            val msg = "Enter a valid room code (e.g. PKL-427)."
            _errorMessage.value = msg
            return Result.failure(IllegalArgumentException(msg))
        }

        _isBusy.value = true
        _isSearchingRandom.value = false
        _errorMessage.value = null
        detachRoom(markDisconnected = true)

        return try {
            joinRoomInternal(
                rawRoomCode = code,
                localCharacter = localCharacter,
                localProfileName = localProfileName,
                localRating = localRating,
                suppressErrorState = false,
            )
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun findRandomOpponent(
        localCharacter: CharacterId,
        localProfileName: String,
        localRating: Int = 1000,
    ): Result<String> {
        if (_isBusy.value) return Result.failure(IllegalStateException("Already connecting…"))
        _isBusy.value = true
        _isSearchingRandom.value = true
        _errorMessage.value = null
        detachRoom(markDisconnected = true)

        return try {
            val user = ensureSignedInUser()
            val uid = user.uid
            val safeName = sanitizeProfileName(localProfileName, localCharacter)
            val safeRating = localRating.coerceIn(800, 5000)

            val queueSnap = queueRoot.get().await()
            val nowMs = System.currentTimeMillis()
            val rawCandidates = queueSnap.children.mapNotNull { child ->
                val code = child.key ?: return@mapNotNull null
                val host = child.child("host").getValue(String::class.java) ?: return@mapNotNull null
                val name = child.child("name").getValue(String::class.java).orEmpty()
                val characterId = child.child("characterId").getValue(String::class.java) ?: CharacterId.RIO.name
                val rating = (child.child("rating").value as? Number)?.toInt() ?: 1000
                val createdAtMs = (child.child("createdAt").value as? Number)?.toLong() ?: 0L
                val claimedBy = child.child("claimedBy").getValue(String::class.java)
                QueueCandidate(
                    code = code,
                    host = host,
                    name = name,
                    characterId = characterId,
                    rating = rating,
                    createdAtMs = createdAtMs,
                    claimedBy = claimedBy,
                )
            }
            val sortedCandidates = selectAndSortQueueCandidates(
                candidates = rawCandidates,
                myUid = uid,
                myRating = safeRating,
                nowMs = nowMs,
            )

            for (cand in sortedCandidates) {
                val claimed = tryClaimQueueEntry(cand.code, uid)
                if (!claimed) continue

                val joined = joinRoomInternal(
                    rawRoomCode = cand.code,
                    localCharacter = localCharacter,
                    localProfileName = safeName,
                    localRating = safeRating,
                    suppressErrorState = true,
                )
                try {
                    queueRoot.child(cand.code).removeValue().await()
                } catch (_: Exception) {
                }
                if (joined.isSuccess) {
                    return joined
                }
            }

            // No queued candidate worked -> create room as host and publish queue entry
            val hostRes = createRoomInternal(
                hostCharacter = localCharacter,
                hostProfileName = safeName,
                hostRating = safeRating,
            )
            if (hostRes.isSuccess) {
                val code = hostRes.getOrThrow()
                val queueRef = queueRoot.child(code)
                val queuePayload = mapOf<String, Any>(
                    "host" to uid,
                    "name" to safeName,
                    "characterId" to localCharacter.name,
                    "rating" to safeRating,
                    "createdAt" to ServerValue.TIMESTAMP,
                )
                queueRef.setValue(queuePayload).await()
                queueRef.onDisconnect().removeValue().await()
                if (_session.value.opponentConnected) {
                    queueRef.onDisconnect().cancel()
                    queueRef.removeValue()
                } else {
                    activeQueueRef = queueRef
                }
            }
            hostRes
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "findRandomOpponent failed", e)
            val msg = e.localizedMessage ?: "Could not find an online match."
            _errorMessage.value = msg
            Result.failure(e)
        } finally {
            _isSearchingRandom.value = false
            _isBusy.value = false
        }
    }

    private suspend fun tryClaimQueueEntry(code: String, myUid: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        queueRoot.child(code).runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val raw = currentData.value ?: return Transaction.success(currentData)
                val map = raw as? Map<*, *> ?: return Transaction.abort()
                val host = map["host"] as? String
                val claimedBy = map["claimedBy"] as? String
                if (!claimedBy.isNullOrBlank() || host == myUid) {
                    return Transaction.abort()
                }
                val updated = HashMap<String, Any?>()
                for ((k, v) in map) {
                    if (k is String) updated[k] = v
                }
                updated["claimedBy"] = myUid
                currentData.value = updated
                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?,
            ) {
                if (error != null || !committed) {
                    deferred.complete(false)
                    return
                }
                val resultClaimedBy = currentData?.child("claimedBy")?.getValue(String::class.java)
                deferred.complete(resultClaimedBy == myUid)
            }
        })
        return deferred.await()
    }

    private suspend fun createRoomInternal(
        hostCharacter: CharacterId,
        hostProfileName: String,
        hostRating: Int,
    ): Result<String> {
        return try {
            val user = ensureSignedInUser()
            val uid = user.uid
            val safeName = sanitizeProfileName(hostProfileName, hostCharacter)
            val safeRating = hostRating.coerceIn(800, 5000)

            var chosenCode: String? = null
            for (attempt in 0 until 6) {
                val candidate = RoomCodeGenerator.generate()
                val metaSnap = roomsRoot.child(candidate).child("meta").get().await()
                if (!metaSnap.exists()) {
                    chosenCode = candidate
                    break
                }
            }
            val code = chosenCode ?: RoomCodeGenerator.generate()
            val roomRef = roomsRoot.child(code)
            val metaRef = roomRef.child("meta")
            val playerRef = roomRef.child("players").child(uid)

            val metaPayload = mapOf<String, Any>(
                "host" to uid,
                "mode" to "SINGLES_1V1",
                "status" to RoomStatus.WAITING.name,
                "createdAt" to ServerValue.TIMESTAMP,
            )
            metaRef.setValue(metaPayload).await()
            metaRef.child("status").onDisconnect().setValue(RoomStatus.HOST_LEFT.name).await()

            val playerPayload = mapOf<String, Any>(
                "uid" to uid,
                "team" to 0,
                "characterId" to hostCharacter.name,
                "connected" to true,
                "name" to safeName,
                "rating" to safeRating,
            )
            playerRef.setValue(playerPayload).await()
            playerRef.child("connected").onDisconnect().setValue(false).await()

            activeRoomRef = roomRef
            matchFinished = false
            lastPublishedMetaStatus = RoomStatus.WAITING.name
            lastSeenInputSeq = -1L

            _session.value = OnlineSessionState(
                isActive = true,
                isHost = true,
                roomCode = code,
                localUid = uid,
                localChar = hostCharacter,
                localName = safeName,
                remoteChar = if (hostCharacter == CharacterId.LOLA) CharacterId.RIO else CharacterId.LOLA,
                remoteName = "",
                remoteRating = null,
                opponentConnected = false,
                statusMessage = "Waiting for opponent in $code…",
            )
            attachRoomListeners(roomRef, myUid = uid, isHost = true)
            Result.success(code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "createRoom failed", e)
            val msg = e.localizedMessage ?: "Failed to create online room."
            _errorMessage.value = msg
            Result.failure(e)
        }
    }

    private suspend fun joinRoomInternal(
        rawRoomCode: String,
        localCharacter: CharacterId,
        localProfileName: String,
        localRating: Int,
        suppressErrorState: Boolean,
    ): Result<String> {
        val code = normalizeRoomCode(rawRoomCode)
        return try {
            val user = ensureSignedInUser()
            val uid = user.uid
            val safeName = sanitizeProfileName(localProfileName, localCharacter)
            val safeRating = localRating.coerceIn(800, 5000)

            val roomRef = roomsRoot.child(code)
            val metaSnap = roomRef.child("meta").get().await()
            if (!metaSnap.exists()) {
                val msg = "Room $code not found."
                if (!suppressErrorState) _errorMessage.value = msg
                return Result.failure(IllegalStateException(msg))
            }

            val hostUid = metaSnap.child("host").getValue(String::class.java).orEmpty()
            val metaStatus = RoomStatus.fromStringOrDefault(
                metaSnap.child("status").getValue(String::class.java),
                RoomStatus.WAITING,
            )
            if (metaStatus == RoomStatus.HOST_LEFT || metaStatus == RoomStatus.FINISHED) {
                val msg = "Room $code has already ended."
                if (!suppressErrorState) _errorMessage.value = msg
                return Result.failure(IllegalStateException(msg))
            }
            if (hostUid == uid) {
                val msg = "You cannot join your own room as guest."
                if (!suppressErrorState) _errorMessage.value = msg
                return Result.failure(IllegalStateException(msg))
            }

            val playersSnap = roomRef.child("players").get().await()
            var hostChar = if (localCharacter == CharacterId.LOLA) CharacterId.RIO else CharacterId.LOLA
            var hostName = ""
            var hostRating: Int? = null
            var hostConnected = false
            for (child in playersSnap.children) {
                val childUid = child.child("uid").getValue(String::class.java) ?: child.key.orEmpty()
                val team = (child.child("team").value as? Number)?.toInt() ?: -1
                val connected = child.child("connected").getValue(Boolean::class.java) ?: false
                if (childUid != uid && team == 1 && connected) {
                    val msg = "Room $code already has two players."
                    if (!suppressErrorState) _errorMessage.value = msg
                    return Result.failure(IllegalStateException(msg))
                }
                if (childUid == hostUid || (childUid != uid && team == 0)) {
                    hostChar = CharacterId.fromStringOrDefault(
                        child.child("characterId").getValue(String::class.java),
                        hostChar,
                    )
                    hostName = child.child("name").getValue(String::class.java)
                        ?.trim()
                        ?.take(MAX_PROFILE_NAME_LEN)
                        .orEmpty()
                    hostRating = (child.child("rating").value as? Number)?.toInt()?.takeIf { it in 800..5000 }
                    hostConnected = connected
                }
            }

            val playerRef = roomRef.child("players").child(uid)
            val playerPayload = mapOf<String, Any>(
                "uid" to uid,
                "team" to 1,
                "characterId" to localCharacter.name,
                "connected" to true,
                "name" to safeName,
                "rating" to safeRating,
            )
            playerRef.setValue(playerPayload).await()
            playerRef.child("connected").onDisconnect().setValue(false).await()

            activeRoomRef = roomRef
            matchFinished = false
            lastPublishedMetaStatus = metaStatus.name
            lastSeenInputSeq = -1L

            val resolvedHostName = hostName.ifBlank { characterPalette(hostChar).name }
            _session.value = OnlineSessionState(
                isActive = true,
                isHost = false,
                roomCode = code,
                localUid = uid,
                localChar = localCharacter,
                localName = safeName,
                remoteUid = hostUid,
                remoteChar = hostChar,
                remoteName = resolvedHostName,
                remoteRating = hostRating,
                opponentConnected = hostConnected,
                statusMessage = if (hostConnected) "Connected" else "Connecting to $resolvedHostName…",
            )
            attachRoomListeners(roomRef, myUid = uid, isHost = false)
            Result.success(code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "joinRoom failed", e)
            val msg = e.localizedMessage ?: "Failed to join room $code."
            if (!suppressErrorState) _errorMessage.value = msg
            Result.failure(e)
        }
    }

    fun sendInput(input: PlayerInputSnapshot) {
        val current = _session.value
        val roomRef = activeRoomRef ?: return
        if (!current.isActive || current.isHost || current.localUid.isBlank()) return

        val now = System.currentTimeMillis()
        if (input.swingType == null && now - lastInputPushMs < 45L) return
        lastInputPushMs = now

        val payload = mapOf<String, Any?>(
            "uid" to current.localUid,
            "sequence" to input.sequence,
            "targetX" to input.targetX.toDouble(),
            "targetY" to input.targetY.toDouble(),
            "swingType" to input.swingType,
            "aimX" to input.aimX.toDouble(),
            "aimY" to input.aimY.toDouble(),
            "swipeDurationSec" to input.swipeDurationSec.toDouble(),
            "timestampMs" to input.timestampMs,
        )
        roomRef.child("inputs").child(current.localUid).setValue(payload)
    }

    fun publishState(snapshot: MatchStateSnapshot, force: Boolean = false) {
        val current = _session.value
        val roomRef = activeRoomRef ?: return
        if (!current.isActive || !current.isHost) return

        val now = System.currentTimeMillis()
        if (!force && snapshot.matchWinner == null && now - lastStatePushMs < 48L) return
        lastStatePushMs = now

        val payload = mapOf<String, Any?>(
            "tick" to snapshot.tick,
            "ballX" to snapshot.ballX.toDouble(),
            "ballY" to snapshot.ballY.toDouble(),
            "ballZ" to snapshot.ballZ.toDouble(),
            "ballVx" to snapshot.ballVx.toDouble(),
            "ballVy" to snapshot.ballVy.toDouble(),
            "ballVz" to snapshot.ballVz.toDouble(),
            "ballActive" to snapshot.ballActive,
            "hostX" to snapshot.hostX.toDouble(),
            "hostY" to snapshot.hostY.toDouble(),
            "guestX" to snapshot.guestX.toDouble(),
            "guestY" to snapshot.guestY.toDouble(),
            "hostSwingTimer" to snapshot.hostSwingTimer.toDouble(),
            "guestSwingTimer" to snapshot.guestSwingTimer.toDouble(),
            "scoreHost" to snapshot.scoreHost,
            "scoreGuest" to snapshot.scoreGuest,
            "servingSide" to snapshot.servingSide,
            "rallyShotCount" to snapshot.rallyShotCount,
            "rallyHits" to snapshot.rallyHits,
            "lastEvent" to snapshot.lastEvent,
            "matchWinner" to snapshot.matchWinner,
            "publishedAtMs" to snapshot.publishedAtMs,
        )
        roomRef.child("state").setValue(payload)
    }

    fun markMatchFinished() {
        val current = _session.value
        val roomRef = activeRoomRef ?: return
        if (!current.isActive || !current.isHost || matchFinished) return
        matchFinished = true
        lastPublishedMetaStatus = RoomStatus.FINISHED.name
        roomRef.child("meta").child("status").onDisconnect().cancel()
        roomRef.child("meta").child("status").setValue(RoomStatus.FINISHED.name)
    }

    fun leaveRoom() {
        detachRoom(markDisconnected = true)
        _session.value = OnlineSessionState()
        _errorMessage.value = null
    }

    private fun detachRoom(markDisconnected: Boolean) {
        val roomRef = activeRoomRef
        val current = _session.value

        playersListener?.let { listener -> roomRef?.child("players")?.removeEventListener(listener) }
        metaStatusListener?.let { listener -> roomRef?.child("meta")?.child("status")?.removeEventListener(listener) }
        inputsListener?.let { listener -> roomRef?.child("inputs")?.removeEventListener(listener) }
        stateListener?.let { listener -> roomRef?.child("state")?.removeEventListener(listener) }

        playersListener = null
        metaStatusListener = null
        inputsListener = null
        stateListener = null
        activeRoomRef = null
        activeQueueRef?.let { qRef ->
            qRef.onDisconnect().cancel()
            qRef.removeValue()
        }
        activeQueueRef = null

        if (markDisconnected && roomRef != null && current.isActive && current.localUid.isNotBlank()) {
            val playerConnRef = roomRef.child("players").child(current.localUid).child("connected")
            playerConnRef.onDisconnect().cancel()
            playerConnRef.setValue(false)

            if (current.isHost && !matchFinished) {
                val metaStatusRef = roomRef.child("meta").child("status")
                metaStatusRef.onDisconnect().cancel()
                metaStatusRef.setValue(RoomStatus.HOST_LEFT.name)
            }
        }
    }

    private fun attachRoomListeners(roomRef: DatabaseReference, myUid: String, isHost: Boolean) {
        val pListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var foundOpponent = false
                var oppUid = ""
                var oppChar = _session.value.remoteChar
                var oppName = _session.value.remoteName
                var oppRating: Int? = _session.value.remoteRating

                for (child in snapshot.children) {
                    val childUid = child.child("uid").getValue(String::class.java) ?: child.key.orEmpty()
                    val connected = child.child("connected").getValue(Boolean::class.java) ?: false
                    if (childUid.isNotBlank() && childUid != myUid && connected) {
                        foundOpponent = true
                        oppUid = childUid
                        oppChar = CharacterId.fromStringOrDefault(
                            child.child("characterId").getValue(String::class.java),
                            oppChar,
                        )
                        val rawName = child.child("name").getValue(String::class.java)
                            ?.trim()
                            ?.take(MAX_PROFILE_NAME_LEN)
                            .orEmpty()
                        oppName = rawName.ifBlank { characterPalette(oppChar).name }
                        oppRating = (child.child("rating").value as? Number)?.toInt()?.takeIf { it in 800..5000 }
                        break
                    }
                }

                val current = _session.value
                if (foundOpponent) {
                    if (!current.opponentConnected || oppUid != current.remoteUid) {
                        lastSeenInputSeq = -1L
                    }
                    activeQueueRef?.let { qRef ->
                        qRef.onDisconnect().cancel()
                        qRef.removeValue()
                        activeQueueRef = null
                    }
                    _session.value = current.copy(
                        remoteUid = oppUid,
                        remoteChar = oppChar,
                        remoteName = oppName,
                        remoteRating = oppRating,
                        opponentConnected = true,
                        statusMessage = "Connected",
                    )
                    onRemotePeerInfoReceived?.invoke(oppChar, oppName)
                    if (isHost && !matchFinished && lastPublishedMetaStatus != RoomStatus.PLAYING.name) {
                        lastPublishedMetaStatus = RoomStatus.PLAYING.name
                        roomRef.child("meta").child("status").setValue(RoomStatus.PLAYING.name)
                    }
                } else {
                    _session.value = current.copy(
                        opponentConnected = false,
                        statusMessage = if (isHost) {
                            "Waiting for opponent in ${current.roomCode}…"
                        } else {
                            "Waiting for host…"
                        },
                    )
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "players listener cancelled: ${error.message}")
            }
        }
        playersListener = pListener
        roomRef.child("players").addValueEventListener(pListener)

        val mListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val raw = snapshot.getValue(String::class.java) ?: return
                val status = RoomStatus.fromStringOrDefault(raw, RoomStatus.WAITING)
                lastPublishedMetaStatus = status.name
                if (!isHost && status == RoomStatus.HOST_LEFT) {
                    _session.value = _session.value.copy(
                        opponentConnected = false,
                        statusMessage = "Host left room",
                    )
                    onRoomStatusChange?.invoke(RoomStatus.HOST_LEFT)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "meta listener cancelled: ${error.message}")
            }
        }
        metaStatusListener = mListener
        roomRef.child("meta").child("status").addValueEventListener(mListener)

        if (isHost) {
            val iListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) {
                        val keyUid = child.key.orEmpty()
                        if (keyUid.isBlank() || keyUid == myUid) continue
                        val inp = parseInputSnapshot(keyUid, child) ?: continue
                        if (inp.sequence > lastSeenInputSeq || lastSeenInputSeq < 0L) {
                            lastSeenInputSeq = inp.sequence
                            onRemoteInput?.invoke(inp)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "inputs listener cancelled: ${error.message}")
                }
            }
            inputsListener = iListener
            roomRef.child("inputs").addValueEventListener(iListener)
        } else {
            val sListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) return
                    val state = parseStateSnapshot(snapshot) ?: return
                    onHostSnapshot?.invoke(state)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "state listener cancelled: ${error.message}")
                }
            }
            stateListener = sListener
            roomRef.child("state").addValueEventListener(sListener)
        }
    }

    private fun parseInputSnapshot(fallbackUid: String, child: DataSnapshot): PlayerInputSnapshot? {
        if (!child.exists()) return null
        val uid = child.child("uid").getValue(String::class.java) ?: fallbackUid
        val seq = (child.child("sequence").value as? Number)?.toLong() ?: 0L
        val targetX = (child.child("targetX").value as? Number)?.toFloat() ?: CourtSpec.COURT_CENTER_X
        val targetY = (child.child("targetY").value as? Number)?.toFloat() ?: CourtSpec.COURT_BOTTOM_Y
        val swingType = child.child("swingType").getValue(String::class.java)
        val aimX = (child.child("aimX").value as? Number)?.toFloat() ?: 0f
        val aimY = (child.child("aimY").value as? Number)?.toFloat() ?: 0f
        val swipeDurationSec = (child.child("swipeDurationSec").value as? Number)?.toFloat() ?: 0.2f
        val timestampMs = (child.child("timestampMs").value as? Number)?.toLong() ?: 0L
        return PlayerInputSnapshot(
            uid = uid,
            sequence = seq,
            targetX = targetX,
            targetY = targetY,
            swingType = swingType,
            aimX = aimX,
            aimY = aimY,
            swipeDurationSec = swipeDurationSec,
            timestampMs = timestampMs,
        )
    }

    private fun parseStateSnapshot(snapshot: DataSnapshot): MatchStateSnapshot? {
        val tick = (snapshot.child("tick").value as? Number)?.toLong() ?: 0L
        val ballX = (snapshot.child("ballX").value as? Number)?.toFloat() ?: return null
        val ballY = (snapshot.child("ballY").value as? Number)?.toFloat() ?: return null
        val ballZ = (snapshot.child("ballZ").value as? Number)?.toFloat() ?: 2.4f
        val ballVx = (snapshot.child("ballVx").value as? Number)?.toFloat() ?: 0f
        val ballVy = (snapshot.child("ballVy").value as? Number)?.toFloat() ?: 0f
        val ballVz = (snapshot.child("ballVz").value as? Number)?.toFloat() ?: 0f
        val ballActive = snapshot.child("ballActive").getValue(Boolean::class.java) ?: false
        val hostX = (snapshot.child("hostX").value as? Number)?.toFloat() ?: CourtSpec.COURT_CENTER_X
        val hostY = (snapshot.child("hostY").value as? Number)?.toFloat() ?: CourtSpec.COURT_BOTTOM_Y
        val guestX = (snapshot.child("guestX").value as? Number)?.toFloat() ?: CourtSpec.COURT_CENTER_X
        val guestY = (snapshot.child("guestY").value as? Number)?.toFloat() ?: CourtSpec.COURT_TOP_Y
        val hostSwingTimer = (snapshot.child("hostSwingTimer").value as? Number)?.toFloat() ?: 0f
        val guestSwingTimer = (snapshot.child("guestSwingTimer").value as? Number)?.toFloat() ?: 0f
        val scoreHost = (snapshot.child("scoreHost").value as? Number)?.toInt() ?: 0
        val scoreGuest = (snapshot.child("scoreGuest").value as? Number)?.toInt() ?: 0
        val servingSide = snapshot.child("servingSide").getValue(String::class.java) ?: "HOST"
        val rallyShotCount = (snapshot.child("rallyShotCount").value as? Number)?.toInt() ?: 0
        val rallyHits = (snapshot.child("rallyHits").value as? Number)?.toInt() ?: 0
        val lastEvent = snapshot.child("lastEvent").getValue(String::class.java)
        val matchWinner = snapshot.child("matchWinner").getValue(String::class.java)
        val publishedAtMs = (snapshot.child("publishedAtMs").value as? Number)?.toLong() ?: 0L
        return MatchStateSnapshot(
            tick = tick,
            ballX = ballX,
            ballY = ballY,
            ballZ = ballZ,
            ballVx = ballVx,
            ballVy = ballVy,
            ballVz = ballVz,
            ballActive = ballActive,
            hostX = hostX,
            hostY = hostY,
            guestX = guestX,
            guestY = guestY,
            hostSwingTimer = hostSwingTimer,
            guestSwingTimer = guestSwingTimer,
            scoreHost = scoreHost,
            scoreGuest = scoreGuest,
            servingSide = servingSide,
            rallyShotCount = rallyShotCount,
            rallyHits = rallyHits,
            lastEvent = lastEvent,
            matchWinner = matchWinner,
            publishedAtMs = publishedAtMs,
        )
    }
}
