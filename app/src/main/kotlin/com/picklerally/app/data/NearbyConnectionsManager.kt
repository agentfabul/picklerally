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
    val playerName: String? = null,
    val input: PlayerInputSnapshot? = null,
    val snapshot: MatchStateSnapshot? = null,
    val status: String? = null,
)

data class NearbySessionState(
    val isActive: Boolean = false,
    val isHost: Boolean = false,
    val roomCode: String = "",
    val localChar: CharacterId = CharacterId.RIO,
    val localName: String = "",
    val remoteChar: CharacterId = CharacterId.LOLA,
    val remoteName: String = "",
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
    var onRemotePeerInfoReceived: ((CharacterId, String) -> Unit)? = null

    private var activeEndpointId: String? = null
    private var pendingEndpointId: String? = null
    private var isAdvertising = false
    private var discoveryRequested = false
    private var lastSnapshotPushMs: Long = 0L

    fun hasAllPermissions(): Boolean {
        return requiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun startDiscovery() {
        if (!hasAllPermissions()) return
        if (_session.value.isActive) return
        if (discoveryRequested || _isDiscovering.value) return
        _errorMessage.value = null
        _discoveredHosts.value = emptyList()
        discoveryRequested = true

        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_POINT_TO_POINT)
            .build()

        client.startDiscovery(SERVICE_ID, discoveryCallback, options)
            .addOnSuccessListener {
                if (discoveryRequested) {
                    _isDiscovering.value = true
                } else {
                    try {
                        client.stopDiscovery()
                    } catch (_: Exception) {
                    }
                }
            }
            .addOnFailureListener { err ->
                Log.w(TAG, "Nearby discovery failed: ${err.message}")
                discoveryRequested = false
                _isDiscovering.value = false
                _errorMessage.value = err.localizedMessage ?: "Nearby search unavailable on this device."
            }
    }

    fun stopDiscovery() {
        discoveryRequested = false
        if (_isDiscovering.value) {
            try {
                client.stopDiscovery()
            } catch (_: Exception) {
            }
            _isDiscovering.value = false
        }
        _discoveredHosts.value = emptyList()
    }

    private fun sanitizePeerName(rawName: String, fallbackChar: CharacterId): String {
        val cleaned = rawName.replace("|", " ").trim().take(24)
        return cleaned.ifBlank { characterPalette(fallbackChar).name }
    }

    fun startHosting(
        hostCharacter: CharacterId,
        hostProfileName: String = characterPalette(hostCharacter).name,
    ) {
        if (!hasAllPermissions()) {
            _errorMessage.value = "Grant Nearby permissions to host."
            return
        }
        stopDiscovery()
        disconnectActiveOnly()
        if (isAdvertising) {
            try {
                client.stopAdvertising()
            } catch (_: Exception) {
            }
            isAdvertising = false
        }
        _errorMessage.value = null

        val code = RoomCodeGenerator.generate()
        val safeName = sanitizePeerName(hostProfileName, hostCharacter)
        val endpointName = "$safeName|${hostCharacter.name}|$code"
        _session.value = NearbySessionState(
            isActive = true,
            isHost = true,
            roomCode = code,
            localChar = hostCharacter,
            localName = safeName,
            remoteChar = if (hostCharacter == CharacterId.LOLA) CharacterId.RIO else CharacterId.LOLA,
            remoteName = "",
            opponentConnected = false,
            statusMessage = "Waiting for nearby player ($code)…",
        )

        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_POINT_TO_POINT)
            .build()

        client.startAdvertising(endpointName, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnSuccessListener {
                isAdvertising = true
            }
            .addOnFailureListener { err ->
                Log.w(TAG, "Nearby advertising failed: ${err.message}")
                isAdvertising = false
                _errorMessage.value = err.localizedMessage ?: "Could not start Nearby host."
                _session.value = _session.value.copy(
                    isActive = false,
                    opponentConnected = false,
                    statusMessage = "Host failed to start",
                )
            }
    }

    fun joinHost(
        host: NearbyHostInfo,
        localCharacter: CharacterId,
        localProfileName: String = characterPalette(localCharacter).name,
    ) {
        if (!hasAllPermissions()) {
            _errorMessage.value = "Grant Nearby permissions to join."
            return
        }
        // Keep Play Services endpoint table intact: only stop discovery and any prior peer, NOT stopAllEndpoints()!
        stopDiscovery()
        disconnectActiveOnly()
        if (isAdvertising) {
            try {
                client.stopAdvertising()
            } catch (_: Exception) {
            }
            isAdvertising = false
        }
        _errorMessage.value = null

        val safeName = sanitizePeerName(localProfileName, localCharacter)
        pendingEndpointId = host.endpointId
        _session.value = NearbySessionState(
            isActive = true,
            isHost = false,
            roomCode = host.roomCode,
            localChar = localCharacter,
            localName = safeName,
            remoteChar = host.characterId,
            remoteName = host.hostName,
            opponentConnected = false,
            statusMessage = "Connecting to ${host.hostName} (${host.roomCode})…",
        )

        client.requestConnection(
            "$safeName|${localCharacter.name}",
            host.endpointId,
            connectionLifecycleCallback,
        ).addOnFailureListener { err ->
            Log.w(TAG, "Nearby connection request failed: ${err.message}")
            pendingEndpointId = null
            val msg = err.localizedMessage ?: "Failed to connect to ${host.hostName}."
            _errorMessage.value = msg
            _session.value = _session.value.copy(
                isActive = false,
                opponentConnected = false,
                statusMessage = msg,
            )
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
        pendingEndpointId = null
        isAdvertising = false
        try {
            client.stopAdvertising()
            client.stopAllEndpoints()
        } catch (_: Exception) {
        }
        _session.value = NearbySessionState()
    }

    private fun disconnectActiveOnly() {
        val prevActive = activeEndpointId
        val prevPending = pendingEndpointId
        activeEndpointId = null
        pendingEndpointId = null
        if (prevActive != null) {
            try {
                client.disconnectFromEndpoint(prevActive)
            } catch (_: Exception) {
            }
        }
        if (prevPending != null && prevPending != prevActive) {
            try {
                client.disconnectFromEndpoint(prevPending)
            } catch (_: Exception) {
            }
        }
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
            val current = _session.value
            if (!current.isActive) {
                client.rejectConnection(endpointId)
                return
            }
            if (activeEndpointId != null && activeEndpointId != endpointId) {
                client.rejectConnection(endpointId)
                return
            }
            pendingEndpointId = endpointId
            val parts = connectionInfo.endpointName.split("|")
            val peerName = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
            val peerChar = parts.getOrNull(1)?.let { CharacterId.fromStringOrDefault(it, current.remoteChar) }
            val resolvedChar = peerChar ?: current.remoteChar
            val resolvedName = peerName ?: current.remoteName
            _session.value = current.copy(
                remoteChar = resolvedChar,
                remoteName = resolvedName,
                statusMessage = "Accepting ${peerName ?: "opponent"}…",
            )
            peerChar?.let { onRemoteCharacterReceived?.invoke(it) }
            onRemotePeerInfoReceived?.invoke(resolvedChar, resolvedName)
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (pendingEndpointId == endpointId) {
                pendingEndpointId = null
            }
            if (result.status.isSuccess) {
                activeEndpointId = endpointId
                if (isAdvertising) {
                    try {
                        client.stopAdvertising()
                    } catch (_: Exception) {
                    }
                    isAdvertising = false
                }
                val current = _session.value
                _errorMessage.value = null
                _session.value = current.copy(
                    connectedEndpointId = endpointId,
                    opponentConnected = true,
                    statusMessage = "Connected",
                )
                sendPacket(
                    NearbyWirePacket(
                        type = "hello",
                        charId = current.localChar.name,
                        playerName = current.localName.ifBlank { characterPalette(current.localChar).name },
                    ),
                )
            } else {
                val msg = "Nearby connection failed (code ${result.status.statusCode})."
                _errorMessage.value = msg
                _session.value = _session.value.copy(
                    isActive = false,
                    connectedEndpointId = null,
                    opponentConnected = false,
                    statusMessage = msg,
                )
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (activeEndpointId == endpointId || pendingEndpointId == endpointId) {
                activeEndpointId = null
                pendingEndpointId = null
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
                    val current = _session.value
                    val remoteChar = CharacterId.fromStringOrDefault(packet.charId, current.remoteChar)
                    val remoteName = packet.playerName
                        ?.replace("|", " ")
                        ?.trim()
                        ?.take(24)
                        ?.takeIf { it.isNotBlank() }
                        ?: current.remoteName.takeIf { it.isNotBlank() }
                        ?: characterPalette(remoteChar).name
                    _session.value = current.copy(
                        remoteChar = remoteChar,
                        remoteName = remoteName,
                    )
                    onRemoteCharacterReceived?.invoke(remoteChar)
                    onRemotePeerInfoReceived?.invoke(remoteChar, remoteName)
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
