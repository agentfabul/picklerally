package com.picklerally.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.picklerally.app.game.CharacterId
import com.picklerally.app.game.MatchStateSnapshot
import com.picklerally.app.game.PlayerInputSnapshot
import com.picklerally.app.game.RoomCodeGenerator
import com.picklerally.app.game.RoomStatus
import com.picklerally.app.game.characterPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets

@Serializable
data class NearbyHostInfo(
    val endpointId: String,
    val roomCode: String,
    val hostName: String,
    val characterId: CharacterId,
)

@Serializable
data class NearbyWirePacket(
    val type: String,
    val charId: String? = null,
    val input: PlayerInputSnapshot? = null,
    val snapshot: MatchStateSnapshot? = null,
    val status: String? = null,
)

data class NearbySessionState(
    val isActive: Boolean = false,
    val isHost: Boolean = false,
    val roomCode: String = "",
    val localChar: CharacterId = CharacterId.RIO,
    val remoteChar: CharacterId = CharacterId.LOLA,
    val connectedEndpointId: String? = null,
    val opponentConnected: Boolean = false,
    val statusMessage: String = "",
)

class NearbyConnectionsManager(private val context: Context) {
    companion object {
        private const val TAG = "NearbyConnections"
        const val SERVICE_ID = "com.picklerally.nearby"

        fun requiredPermissions(): Array<String> {
            val perms = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                perms.add(Manifest.permission.BLUETOOTH_ADVERTISE)
                perms.add(Manifest.permission.BLUETOOTH_CONNECT)
                perms.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
                perms.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            return perms.distinct().toTypedArray()
        }
    }

    private val client: ConnectionsClient by lazy {
        Nearby.getConnectionsClient(context.applicationContext)
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _discoveredHosts = MutableStateFlow<List<NearbyHostInfo>>(emptyList())
    val discoveredHosts: StateFlow<List<NearbyHostInfo>> = _discoveredHosts.asStateFlow()

    private val _session = MutableStateFlow(NearbySessionState())
    val session: StateFlow<NearbySessionState> = _session.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    var onRemoteInput: ((PlayerInputSnapshot) -> Unit)? = null
    var onHostSnapshot: ((MatchStateSnapshot) -> Unit)? = null
    var onRoomStatusChange: ((RoomStatus) -> Unit)? = null
    var onRemoteCharacterReceived: ((CharacterId) -> Unit)? = null

    private var activeEndpointId: String? = null
    private var lastSnapshotPushMs: Long = 0L

    fun hasAllPermissions(): Boolean {
        return requiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun startDiscovery() {
        if (!hasAllPermissions()) return
        if (_isDiscovering.value) return
        _errorMessage.value = null
        _discoveredHosts.value = emptyList()

        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_POINT_TO_POINT)
            .build()

        client.startDiscovery(SERVICE_ID, discoveryCallback, options)
            .addOnSuccessListener {
                _isDiscovering.value = true
            }
            .addOnFailureListener { err ->
                Log.w(TAG, "Nearby discovery failed: ${err.message}")
                _isDiscovering.value = false
                _errorMessage.value = err.localizedMessage ?: "Nearby search unavailable on this device."
            }
    }

    fun stopDiscovery() {
        if (_isDiscovering.value) {
            client.stopDiscovery()
            _isDiscovering.value = false
        }
        _discoveredHosts.value = emptyList()
    }

    fun startHosting(hostCharacter: CharacterId) {
        if (!hasAllPermissions()) {
            _errorMessage.value = "Grant Nearby permissions to host."
            return
        }
        leaveSession()
        _errorMessage.value = null

        val code = RoomCodeGenerator.generate()
        val palette = characterPalette(hostCharacter)
        val endpointName = "${palette.name}|${hostCharacter.name}|$code"
        _session.value = NearbySessionState(
            isActive = true,
            isHost = true,
            roomCode = code,
            localChar = hostCharacter,
            remoteChar = if (hostCharacter == CharacterId.LOLA) CharacterId.RIO else CharacterId.LOLA,
            opponentConnected = false,
            statusMessage = "Waiting for nearby player ($code)…",
        )

        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_POINT_TO_POINT)
            .build()

        client.startAdvertising(endpointName, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnFailureListener { err ->
                Log.w(TAG, "Nearby advertising failed: ${err.message}")
                _errorMessage.value = err.localizedMessage ?: "Could not start Nearby host."
                _session.value = NearbySessionState()
            }
    }

    fun joinHost(host: NearbyHostInfo, localCharacter: CharacterId) {
        if (!hasAllPermissions()) {
            _errorMessage.value = "Grant Nearby permissions to join."
            return
        }
        stopDiscovery()
        leaveSession()
        _errorMessage.value = null

        val palette = characterPalette(localCharacter)
        _session.value = NearbySessionState(
            isActive = true,
            isHost = false,
            roomCode = host.roomCode,
            localChar = localCharacter,
            remoteChar = host.characterId,
            opponentConnected = false,
            statusMessage = "Connecting to ${host.hostName}…",
        )

        client.requestConnection(
            "${palette.name}|${localCharacter.name}",
            host.endpointId,
            connectionLifecycleCallback,
        ).addOnFailureListener { err ->
            Log.w(TAG, "Nearby connection request failed: ${err.message}")
            _errorMessage.value = err.localizedMessage ?: "Failed to connect to host."
            _session.value = NearbySessionState()
        }
    }

    fun sendInput(input: PlayerInputSnapshot) {
        sendPacket(NearbyWirePacket(type = "input", input = input))
    }

    fun publishState(snapshot: MatchStateSnapshot) {
        val now = System.currentTimeMillis()
        if (now - lastSnapshotPushMs < 48L) return
        lastSnapshotPushMs = now
        sendPacket(NearbyWirePacket(type = "state", snapshot = snapshot))
    }

    fun leaveSession() {
        val current = _session.value
        if (current.isActive && current.isHost && current.opponentConnected) {
            sendPacket(NearbyWirePacket(type = "meta", status = RoomStatus.HOST_LEFT.name))
        }
        activeEndpointId = null
        try {
            client.stopAdvertising()
            client.stopAllEndpoints()
        } catch (_: Exception) {
        }
        _session.value = NearbySessionState()
    }

    private fun sendPacket(packet: NearbyWirePacket) {
        val target = activeEndpointId ?: return
        val bytes = json.encodeToString(NearbyWirePacket.serializer(), packet)
            .toByteArray(StandardCharsets.UTF_8)
        client.sendPayload(target, Payload.fromBytes(bytes))
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val parts = info.endpointName.split("|")
            val hostName = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "Player"
            val charId = CharacterId.fromStringOrDefault(parts.getOrNull(1), CharacterId.RIO)
            val code = parts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "PKL-000"
            val host = NearbyHostInfo(
                endpointId = endpointId,
                roomCode = code,
                hostName = hostName,
                characterId = charId,
            )
            _discoveredHosts.value = _discoveredHosts.value
                .filterNot { it.endpointId == endpointId || it.roomCode == code } + host
        }

        override fun onEndpointLost(endpointId: String) {
            _discoveredHosts.value = _discoveredHosts.value.filterNot { it.endpointId == endpointId }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                activeEndpointId = endpointId
                try {
                    client.stopAdvertising()
                } catch (_: Exception) {
                }
                val current = _session.value
                _session.value = current.copy(
                    connectedEndpointId = endpointId,
                    opponentConnected = true,
                    statusMessage = "Connected",
                )
                sendPacket(
                    NearbyWirePacket(
                        type = "hello",
                        charId = current.localChar.name,
                    ),
                )
            } else {
                _errorMessage.value = "Nearby connection rejected (${result.status.statusCode})."
                _session.value = NearbySessionState()
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (activeEndpointId == endpointId) {
                activeEndpointId = null
                val current = _session.value
                _session.value = current.copy(
                    connectedEndpointId = null,
                    opponentConnected = false,
                    statusMessage = "Disconnected",
                )
                if (!current.isHost) {
                    onRoomStatusChange?.invoke(RoomStatus.HOST_LEFT)
                }
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            val raw = String(bytes, StandardCharsets.UTF_8)
            val packet = try {
                json.decodeFromString(NearbyWirePacket.serializer(), raw)
            } catch (_: Exception) {
                return
            }
            when (packet.type) {
                "hello" -> {
                    val remoteChar = CharacterId.fromStringOrDefault(packet.charId, CharacterId.LOLA)
                    _session.value = _session.value.copy(remoteChar = remoteChar)
                    onRemoteCharacterReceived?.invoke(remoteChar)
                }
                "input" -> packet.input?.let { onRemoteInput?.invoke(it) }
                "state" -> packet.snapshot?.let { onHostSnapshot?.invoke(it) }
                "meta" -> {
                    val status = RoomStatus.entries.firstOrNull { it.name == packet.status }
                    if (status != null) {
                        onRoomStatusChange?.invoke(status)
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
        }
    }
}
