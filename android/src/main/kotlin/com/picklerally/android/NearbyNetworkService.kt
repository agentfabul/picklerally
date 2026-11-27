package com.picklerally.android

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.picklerally.core.characters.CharacterId
import com.picklerally.core.net.ConnectionState
import com.picklerally.core.net.MatchStateSnapshot
import com.picklerally.core.net.NearbyDiscovery
import com.picklerally.core.net.NearbyHost
import com.picklerally.core.net.NetworkService
import com.picklerally.core.net.PlayerInputSnapshot
import com.picklerally.core.net.PlayerSlot
import com.picklerally.core.net.RoomCodeGenerator
import com.picklerally.core.net.RoomMetadata
import com.picklerally.core.net.RoomMode
import com.picklerally.core.net.RoomStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Offline 1v1 for players next to each other, using Google Nearby Connections.
 * Works over Wi-Fi, a phone hotspot or Bluetooth with no internet, no account and no server.
 *
 * Flow: the host advertises "<name>|<room code>"; guests discover hosts and connect; then the
 * same input / state snapshots as online play are exchanged as small JSON byte payloads.
 * The host device stays authoritative (runs the official simulation).
 */
class NearbyNetworkService(
    context: Context,
    private val hasPermissions: () -> Boolean = { true },
    private val requestPermissions: () -> Unit = {}
) : NetworkService, NearbyDiscovery {

    private companion object {
        const val SERVICE_ID = "com.picklerally.nearby"
    }

    private val client = Nearby.getConnectionsClient(context.applicationContext)
    private val strategy = Strategy.P2P_POINT_TO_POINT
    private val localUid = "nearby-" + UUID.randomUUID().toString().take(8)

    private val state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: Flow<ConnectionState> = state
    override val currentUid: String? get() = localUid

    private val hosts = MutableStateFlow<List<NearbyHost>>(emptyList())
    override val nearbyHosts: StateFlow<List<NearbyHost>> = hosts

    private val errorMsg = MutableStateFlow<String?>(null)
    override val nearbyError: StateFlow<String?> = errorMsg

    private val meta = MutableStateFlow<RoomMetadata?>(null)
    private val players = MutableStateFlow<List<PlayerSlot>>(emptyList())
    private val inputs = MutableStateFlow<Map<String, PlayerInputSnapshot>>(emptyMap())
    private val matchState = MutableStateFlow<MatchStateSnapshot?>(null)

    @Volatile private var peerEndpointId: String? = null
    @Volatile private var isHost = false
    @Volatile private var wantsDiscovery = false
    private var localCharacterId = "RIO"

    // ---------------------------------------------------------------- Nearby callbacks

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            runCatching { handleMessage(JSONObject(String(bytes, StandardCharsets.UTF_8))) }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val lifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Quick pick-up games: accept automatically. Both phones can show
            // info.authenticationDigits if you later want a "codes match?" confirmation.
            if (peerEndpointId == null) client.acceptConnection(endpointId, payloadCallback)
            else client.rejectConnection(endpointId)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (!result.status.isSuccess) {
                errorMsg.value = "Connection failed (${result.status.statusMessage ?: result.status.statusCode})"
                if (!isHost) state.value = ConnectionState.ERROR
                return
            }
            errorMsg.value = null
            peerEndpointId = endpointId
            if (isHost) client.stopAdvertising() else client.stopDiscovery()
            state.value = ConnectionState.IN_MATCH
            send(
                JSONObject()
                    .put("t", "hello")
                    .put("uid", localUid)
                    .put("char", localCharacterId)
                    .put("host", isHost)
            )
            if (isHost) sendMeta()
        }

        override fun onDisconnected(endpointId: String) {
            if (endpointId != peerEndpointId) return
            peerEndpointId = null
            players.value = players.value.map { if (it.uid == localUid) it else it.copy(connected = false) }
            if (!isHost) meta.value = meta.value?.copy(status = RoomStatus.HOST_LEFT)
            state.value = ConnectionState.DISCONNECTED
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId != SERVICE_ID) return
            val parts = info.endpointName.split('|')
            val host = NearbyHost(
                endpointId = endpointId,
                hostName = parts.getOrElse(0) { "Player" },
                roomCode = parts.getOrElse(1) { "" }
            )
            hosts.value = hosts.value.filter { it.endpointId != endpointId } + host
        }

        override fun onEndpointLost(endpointId: String) {
            hosts.value = hosts.value.filter { it.endpointId != endpointId }
        }
    }

    fun onPermissionsUpdated(granted: Boolean) {
        if (granted) {
            errorMsg.value = null
            if (wantsDiscovery) startDiscovery()
        } else if (wantsDiscovery) {
            errorMsg.value = "Nearby permissions denied"
            state.value = ConnectionState.ERROR
        }
    }

    // ---------------------------------------------------------------- NearbyDiscovery

    override fun startDiscovery() {
        wantsDiscovery = true
        hosts.value = emptyList()
        client.stopDiscovery()
        if (!hasPermissions()) {
            errorMsg.value = "Allow Nearby / Bluetooth permissions"
            state.value = ConnectionState.ERROR
            requestPermissions()
            return
        }
        errorMsg.value = null
        client.startDiscovery(
            SERVICE_ID,
            discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(strategy).build()
        ).addOnFailureListener { err ->
            errorMsg.value = err.message ?: "Scan failed"
            state.value = ConnectionState.ERROR
        }
    }

    override fun stopDiscovery() {
        wantsDiscovery = false
        client.stopDiscovery()
    }

    // ---------------------------------------------------------------- NetworkService

    override suspend fun signInAnonymously(): Result<String> {
        state.value = ConnectionState.AUTHENTICATED
        return Result.success(localUid)
    }

    override suspend fun createRoom(mode: RoomMode, characterId: String): Result<RoomMetadata> = runCatching {
        if (!hasPermissions()) {
            requestPermissions()
            error("Allow Nearby / Bluetooth permissions")
        }
        client.stopDiscovery()
        client.stopAdvertising()
        wantsDiscovery = false
        errorMsg.value = null
        isHost = true
        localCharacterId = characterId
        val room = RoomMetadata(
            roomCode = RoomCodeGenerator.generate(),
            hostUid = localUid,
            mode = mode,
            status = RoomStatus.WAITING,
            createdAtEpochMs = System.currentTimeMillis()
        )
        meta.value = room
        players.value = listOf(PlayerSlot(localUid, 0, characterId, true))
        state.value = ConnectionState.IN_LOBBY
        client.startAdvertising(
            "${displayNameFor(characterId)}|${room.roomCode}",
            SERVICE_ID,
            lifecycleCallback,
            AdvertisingOptions.Builder().setStrategy(strategy).build()
        ).await()
        room
    }.onFailure {
        isHost = false
        state.value = ConnectionState.ERROR
    }

    override suspend fun joinRoom(roomCode: String, characterId: String): Result<RoomMetadata> = runCatching {
        isHost = false
        localCharacterId = characterId
        val host = hosts.value.firstOrNull { it.roomCode.equals(roomCode.trim(), ignoreCase = true) }
            ?: error("That nearby game is no longer available")
        state.value = ConnectionState.CONNECTING
        client.requestConnection(displayNameFor(characterId), host.endpointId, lifecycleCallback).await()
        val room = RoomMetadata(
            roomCode = host.roomCode,
            hostUid = host.endpointId,
            mode = RoomMode.SINGLES_1V1,
            status = RoomStatus.PLAYING,
            createdAtEpochMs = System.currentTimeMillis()
        )
        meta.value = room
        players.value = listOf(PlayerSlot(localUid, 1, characterId, true))
        room
    }

    override suspend fun updateRoomStatus(roomCode: String, status: RoomStatus) {
        meta.value = meta.value?.copy(status = status)
        if (isHost) sendMeta()
    }

    override suspend fun leaveRoom() {
        wantsDiscovery = false
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        isHost = false
        peerEndpointId = null
        errorMsg.value = null
        meta.value = null
        players.value = emptyList()
        inputs.value = emptyMap()
        matchState.value = null
        state.value = ConnectionState.DISCONNECTED
    }

    override suspend fun sendInput(roomCode: String, input: PlayerInputSnapshot) {
        if (isHost) {
            inputs.value = inputs.value + (input.uid to input)
        } else {
            send(JSONObject().put("t", "input").put("d", encodeInput(input)))
        }
    }

    override suspend fun publishState(roomCode: String, state: MatchStateSnapshot) {
        if (!isHost) return
        matchState.value = state
        send(JSONObject().put("t", "state").put("d", encodeState(state)))
    }

    override fun observeRoomMeta(roomCode: String): Flow<RoomMetadata?> = meta
    override fun observePlayers(roomCode: String): Flow<List<PlayerSlot>> = players
    override fun observeInputs(roomCode: String): Flow<Map<String, PlayerInputSnapshot>> = inputs
    override fun observeMatchState(roomCode: String): Flow<MatchStateSnapshot?> = matchState

    override fun dispose() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
    }

    // ---------------------------------------------------------------- messages

    private fun handleMessage(msg: JSONObject) {
        when (msg.optString("t")) {
            "hello" -> {
                val uid = msg.getString("uid")
                val slot = PlayerSlot(
                    uid = uid,
                    team = if (msg.optBoolean("host")) 0 else 1,
                    characterId = msg.optString("char", "RIO"),
                    connected = true
                )
                players.value = players.value.filter { it.uid != uid } + slot
            }
            "meta" -> meta.value = RoomMetadata(
                roomCode = msg.getString("code"),
                hostUid = msg.getString("host"),
                mode = RoomMode.valueOf(msg.getString("mode")),
                status = RoomStatus.valueOf(msg.getString("status")),
                createdAtEpochMs = msg.getLong("created")
            )
            "input" -> {
                val input = decodeInput(msg.getJSONObject("d"))
                inputs.value = inputs.value + (input.uid to input)
            }
            "state" -> matchState.value = decodeState(msg.getJSONObject("d"))
        }
    }

    private fun sendMeta() {
        val m = meta.value ?: return
        send(
            JSONObject()
                .put("t", "meta")
                .put("code", m.roomCode)
                .put("host", m.hostUid)
                .put("mode", m.mode.name)
                .put("status", m.status.name)
                .put("created", m.createdAtEpochMs)
        )
    }

    private fun send(json: JSONObject) {
        val endpoint = peerEndpointId ?: return
        client.sendPayload(endpoint, Payload.fromBytes(json.toString().toByteArray(StandardCharsets.UTF_8)))
    }

    private fun displayNameFor(characterId: String): String =
        runCatching { CharacterId.valueOf(characterId).displayName }.getOrDefault("Player")

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key)

    private fun encodeInput(i: PlayerInputSnapshot): JSONObject = JSONObject()
        .put("uid", i.uid)
        .put("sequence", i.sequence)
        .put("targetX", i.targetX.toDouble())
        .put("targetY", i.targetY.toDouble())
        .put("swingType", i.swingType ?: JSONObject.NULL)
        .put("aimX", i.aimX.toDouble())
        .put("aimY", i.aimY.toDouble())
        .put("swipeDurationSec", i.swipeDurationSec.toDouble())
        .put("timestampMs", i.timestampMs)

    private fun decodeInput(j: JSONObject): PlayerInputSnapshot = PlayerInputSnapshot(
        uid = j.getString("uid"),
        sequence = j.getLong("sequence"),
        targetX = j.getDouble("targetX").toFloat(),
        targetY = j.getDouble("targetY").toFloat(),
        swingType = j.optNullableString("swingType"),
        aimX = j.getDouble("aimX").toFloat(),
        aimY = j.getDouble("aimY").toFloat(),
        swipeDurationSec = j.getDouble("swipeDurationSec").toFloat(),
        timestampMs = j.getLong("timestampMs")
    )

    private fun encodeState(s: MatchStateSnapshot): JSONObject =
        JSONObject()
            .put("tick", s.tick)
            .put("matchMode", s.matchMode)
            .put("ballX", s.ballX.toDouble())
            .put("ballY", s.ballY.toDouble())
            .put("ballZ", s.ballZ.toDouble())
            .put("ballVx", s.ballVx.toDouble())
            .put("ballVy", s.ballVy.toDouble())
            .put("ballVz", s.ballVz.toDouble())
            .put("ballActive", s.ballActive)
            .put("hostX", s.hostX.toDouble())
            .put("hostY", s.hostY.toDouble())
            .put("guestX", s.guestX.toDouble())
            .put("guestY", s.guestY.toDouble())
            .put("hostPartnerX", s.hostPartnerX.toDouble())
            .put("hostPartnerY", s.hostPartnerY.toDouble())
            .put("guestPartnerX", s.guestPartnerX.toDouble())
            .put("guestPartnerY", s.guestPartnerY.toDouble())
            .put("hostSwingTimer", s.hostSwingTimer.toDouble())
            .put("guestSwingTimer", s.guestSwingTimer.toDouble())
            .put("hostPartnerSwingTimer", s.hostPartnerSwingTimer.toDouble())
            .put("guestPartnerSwingTimer", s.guestPartnerSwingTimer.toDouble())
            .put("scoreHost", s.scoreHost)
            .put("scoreGuest", s.scoreGuest)
            .put("servingSide", s.servingSide)
            .put("serverNumberCall", s.serverNumberCall)
            .put("lowerRightPlayerIndex", s.lowerRightPlayerIndex)
            .put("upperRightPlayerIndex", s.upperRightPlayerIndex)
            .put("rallyShotCount", s.rallyShotCount)
            .put("rallyHits", s.rallyHits)
            .put("lastEvent", s.lastEvent ?: JSONObject.NULL)
            .put("matchWinner", s.matchWinner ?: JSONObject.NULL)
            .put("publishedAtMs", s.publishedAtMs)

    private fun decodeState(j: JSONObject): MatchStateSnapshot =
        MatchStateSnapshot(
            tick = j.getLong("tick"),
            matchMode = j.getString("matchMode"),
            ballX = j.getDouble("ballX").toFloat(),
            ballY = j.getDouble("ballY").toFloat(),
            ballZ = j.getDouble("ballZ").toFloat(),
            ballVx = j.getDouble("ballVx").toFloat(),
            ballVy = j.getDouble("ballVy").toFloat(),
            ballVz = j.getDouble("ballVz").toFloat(),
            ballActive = j.getBoolean("ballActive"),
            hostX = j.getDouble("hostX").toFloat(),
            hostY = j.getDouble("hostY").toFloat(),
            guestX = j.getDouble("guestX").toFloat(),
            guestY = j.getDouble("guestY").toFloat(),
            hostPartnerX = j.getDouble("hostPartnerX").toFloat(),
            hostPartnerY = j.getDouble("hostPartnerY").toFloat(),
            guestPartnerX = j.getDouble("guestPartnerX").toFloat(),
            guestPartnerY = j.getDouble("guestPartnerY").toFloat(),
            hostSwingTimer = j.getDouble("hostSwingTimer").toFloat(),
            guestSwingTimer = j.getDouble("guestSwingTimer").toFloat(),
            hostPartnerSwingTimer = j.getDouble("hostPartnerSwingTimer").toFloat(),
            guestPartnerSwingTimer = j.getDouble("guestPartnerSwingTimer").toFloat(),
            scoreHost = j.getInt("scoreHost"),
            scoreGuest = j.getInt("scoreGuest"),
            servingSide = j.getString("servingSide"),
            serverNumberCall = j.getInt("serverNumberCall"),
            lowerRightPlayerIndex = j.getInt("lowerRightPlayerIndex"),
            upperRightPlayerIndex = j.getInt("upperRightPlayerIndex"),
            rallyShotCount = j.getInt("rallyShotCount"),
            rallyHits = j.getInt("rallyHits"),
            lastEvent = j.optNullableString("lastEvent"),
            matchWinner = j.optNullableString("matchWinner"),
            publishedAtMs = j.getLong("publishedAtMs")
        )
}
