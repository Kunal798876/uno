package com.example.uno.network

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class SocketConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * Robust Socket.IO client manager with automatic reconnect, ping/heartbeat,
 * candidate URL failover (Cloud Run public URL + emulator loopback),
 * and coroutine-friendly event handling.
 */
class SocketManager {

    private val tag = "UnoSocketManager"

    private var socket: Socket? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _connectionStatus = MutableStateFlow(SocketConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<SocketConnectionStatus> = _connectionStatus.asStateFlow()

    private val _activeServerUrl = MutableStateFlow(DEFAULT_SERVER_URL)
    val activeServerUrl: StateFlow<String> = _activeServerUrl.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Listeners for game and room events
    var onRoomUpdated: ((JSONObject) -> Unit)? = null
    var onCountdownStarted: ((Int) -> Unit)? = null
    var onCountdownCancelled: (() -> Unit)? = null
    var onGameStarted: ((JSONObject) -> Unit)? = null
    var onGameStateSync: ((JSONObject) -> Unit)? = null
    var onPlayerConnectionChanged: ((String, Boolean) -> Unit)? = null
    var onPlayerLeft: ((String, String) -> Unit)? = null
    var onEmoteReceived: ((String, String, String) -> Unit)? = null

    private var candidateIndex = 0
    private val candidateUrls = listOf(
        "https://ais-dev-d22w7zmtnx2amcd2cj2b56-511494901060.asia-northeast1.run.app",
        "http://10.0.2.2:3000",
        "http://10.0.2.2:8080"
    )

    fun connect(preferredUrl: String? = null) {
        if (socket != null && socket!!.connected()) {
            return
        }

        val urlToTry = preferredUrl?.trim()?.ifEmpty { null } ?: candidateUrls[candidateIndex % candidateUrls.size]
        _activeServerUrl.value = urlToTry

        scope.launch {
            try {
                _connectionStatus.value = SocketConnectionStatus.CONNECTING
                Log.d(tag, "Connecting to Socket.IO backend at: $urlToTry (candidate #$candidateIndex)")

                val options = IO.Options().apply {
                    reconnection = true
                    reconnectionAttempts = 10
                    reconnectionDelay = 1000
                    reconnectionDelayMax = 3000
                    timeout = 10000
                    transports = arrayOf("websocket", "polling")
                    extraHeaders = mapOf(
                        "Bypass-Tunnel-Reminder" to listOf("true"),
                        "User-Agent" to listOf("UnoRoyaleApp/1.0")
                    )
                }

                socket?.disconnect()
                socket?.off()
                val newSocket = IO.socket(urlToTry, options)
                socket = newSocket

                setupSocketListeners(newSocket, urlToTry)
                newSocket.connect()
            } catch (e: Exception) {
                Log.e(tag, "Failed to initialize Socket: ${e.message}", e)
                _connectionStatus.value = SocketConnectionStatus.ERROR
                _errorMessage.value = e.message
                tryNextCandidate()
            }
        }
    }

    private fun setupSocketListeners(s: Socket, connectedUrl: String) {
        s.on(Socket.EVENT_CONNECT) {
            Log.d(tag, "Socket.IO connected successfully to: $connectedUrl")
            _connectionStatus.value = SocketConnectionStatus.CONNECTED
            _errorMessage.value = null
        }

        s.on(Socket.EVENT_DISCONNECT) {
            Log.d(tag, "Socket.IO disconnected from: $connectedUrl")
            _connectionStatus.value = SocketConnectionStatus.DISCONNECTED
        }

        s.on(Socket.EVENT_CONNECT_ERROR) { args ->
            val err = args.firstOrNull()?.toString() ?: "Connection error"
            Log.w(tag, "Socket.IO connect error on $connectedUrl: $err")
            _connectionStatus.value = SocketConnectionStatus.ERROR
            _errorMessage.value = err
            tryNextCandidate()
        }

        s.on("room_updated") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            scope.launch { onRoomUpdated?.invoke(data) }
        }

        s.on("countdown_started") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            val seconds = data.optInt("durationSeconds", 5)
            scope.launch { onCountdownStarted?.invoke(seconds) }
        }

        s.on("countdown_cancelled") {
            scope.launch { onCountdownCancelled?.invoke() }
        }

        s.on("game_started") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            scope.launch { onGameStarted?.invoke(data) }
        }

        s.on("game_state_sync") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            scope.launch { onGameStateSync?.invoke(data) }
        }

        s.on("player_connection_changed") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            val guestId = data.optString("guestId")
            val isConnected = data.optBoolean("isConnected", true)
            scope.launch { onPlayerConnectionChanged?.invoke(guestId, isConnected) }
        }

        s.on("player_left") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            val guestId = data.optString("guestId")
            val name = data.optString("name")
            scope.launch { onPlayerLeft?.invoke(guestId, name) }
        }

        s.on("emote_received") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            val senderId = data.optString("senderId")
            val emoji = data.optString("emoji")
            val text = data.optString("text")
            scope.launch { onEmoteReceived?.invoke(senderId, emoji, text) }
        }
    }

    private fun tryNextCandidate() {
        if (_connectionStatus.value == SocketConnectionStatus.CONNECTED) return
        candidateIndex++
        if (candidateIndex < candidateUrls.size * 2) {
            scope.launch {
                delay(800)
                if (_connectionStatus.value != SocketConnectionStatus.CONNECTED) {
                    val nextUrl = candidateUrls[candidateIndex % candidateUrls.size]
                    Log.d(tag, "Failing over to candidate URL: $nextUrl")
                    connect(nextUrl)
                }
            }
        }
    }

    /**
     * Emits event with ACK. If socket is connecting, waits up to timeoutMs
     * for connection rather than failing immediately.
     */
    fun emitWithAck(
        event: String,
        payload: JSONObject,
        timeoutMs: Long = 7000L,
        callback: (Boolean, JSONObject?) -> Unit
    ) {
        scope.launch {
            if (socket == null || !socket!!.connected()) {
                connect()
            }

            // Wait for connected socket up to timeoutMs
            val start = System.currentTimeMillis()
            while ((socket == null || !socket!!.connected()) && System.currentTimeMillis() - start < timeoutMs) {
                delay(120)
            }

            val curSocket = socket
            if (curSocket == null || !curSocket.connected()) {
                callback(
                    false,
                    JSONObject().apply {
                        put("error", "Cannot reach game server. Check internet connection or tap retry.")
                    }
                )
                return@launch
            }

            curSocket.emit(event, arrayOf<Any>(payload), io.socket.client.Ack { args ->
                val res = (args as? Array<*>)?.firstOrNull() as? JSONObject
                val success = res?.optBoolean("success", false) ?: false
                callback(success, res)
            })
        }
    }

    fun emit(event: String, payload: JSONObject) {
        scope.launch {
            if (socket == null || !socket!!.connected()) {
                connect()
                val start = System.currentTimeMillis()
                while ((socket == null || !socket!!.connected()) && System.currentTimeMillis() - start < 4000) {
                    delay(100)
                }
            }
            socket?.emit(event, payload)
        }
    }

    fun disconnect() {
        try {
            socket?.disconnect()
            socket?.off()
            socket = null
            _connectionStatus.value = SocketConnectionStatus.DISCONNECTED
        } catch (e: Exception) {
            Log.e(tag, "Error during disconnect: ${e.message}")
        }
    }

    companion object {
        const val DEFAULT_SERVER_URL = "https://ais-dev-d22w7zmtnx2amcd2cj2b56-511494901060.asia-northeast1.run.app"
    }
}
