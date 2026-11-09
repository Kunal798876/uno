package com.example.uno.network

import android.content.Context
import android.util.Log
import com.example.uno.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class RoomPlayer(
    val guestId: String,
    val name: String,
    val avatar: String,
    val isReady: Boolean,
    val isConnected: Boolean,
    val isHost: Boolean
)

data class RoomInfo(
    val roomCode: String,
    val hostGuestId: String,
    val status: String,
    val players: List<RoomPlayer>
)

sealed class MultiplayerEvent {
    data class CardPlayed(val cardId: String, val isAttack: Boolean, val attackCount: Int) : MultiplayerEvent()
    data class CardDrawn(val count: Int) : MultiplayerEvent()
    data class EmoteReceived(val senderId: String, val emoji: String, val text: String) : MultiplayerEvent()
    data class PlayerLeft(val guestId: String, val name: String) : MultiplayerEvent()
    data class PlayerConnectionChanged(val guestId: String, val isConnected: Boolean) : MultiplayerEvent()
}

class MultiplayerRepository(context: Context) {

    private val tag = "MultiplayerRepo"
    private val scope = CoroutineScope(Dispatchers.IO)

    val sessionManager = GuestSessionManager(context)
    val socketManager = SocketManager()

    private val _localGuestId = MutableStateFlow(sessionManager.guestId)
    val localGuestId: StateFlow<String> = _localGuestId.asStateFlow()

    private val _currentRoom = MutableStateFlow<RoomInfo?>(null)
    val currentRoom: StateFlow<RoomInfo?> = _currentRoom.asStateFlow()

    private val _multiplayerGameState = MutableStateFlow<GameState?>(null)
    val multiplayerGameState: StateFlow<GameState?> = _multiplayerGameState.asStateFlow()

    private val _countdownSeconds = MutableStateFlow<Int?>(null)
    val countdownSeconds: StateFlow<Int?> = _countdownSeconds.asStateFlow()

    private var countdownJob: kotlinx.coroutines.Job? = null

    private fun startCountdown(initialSeconds: Int = 5) {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            for (sec in initialSeconds downTo 1) {
                _countdownSeconds.value = sec
                kotlinx.coroutines.delay(1000)
            }
            _countdownSeconds.value = 0
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _countdownSeconds.value = null
    }

    private val _events = MutableSharedFlow<MultiplayerEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<MultiplayerEvent> = _events.asSharedFlow()

    private val _backendUrl = MutableStateFlow(getSavedBackendUrl(context))
    val backendUrl: StateFlow<String> = _backendUrl.asStateFlow()

    private val prefs = context.getSharedPreferences("uno_multiplayer_prefs", Context.MODE_PRIVATE)

    init {
        setupListeners()
        connect()
    }

    private fun getSavedBackendUrl(context: Context): String {
        val p = context.getSharedPreferences("uno_multiplayer_prefs", Context.MODE_PRIVATE)
        return p.getString("backend_url", SocketManager.DEFAULT_SERVER_URL)
            ?: SocketManager.DEFAULT_SERVER_URL
    }

    fun setBackendUrl(url: String) {
        val trimmed = url.trim()
        _backendUrl.value = trimmed
        prefs.edit().putString("backend_url", trimmed).apply()
        socketManager.disconnect()
        socketManager.connect(trimmed)
    }

    fun connect() {
        socketManager.connect(_backendUrl.value)
    }

    suspend fun testConnection(url: String): Pair<Boolean, String> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val target = url.trim().ifEmpty { SocketManager.DEFAULT_SERVER_URL }
            val cleanEndpoint = if (target.endsWith("/")) "${target}health" else "$target/health"
            val connection = java.net.URL(cleanEndpoint).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("Bypass-Tunnel-Reminder", "true")
            connection.setRequestProperty("User-Agent", "UnoRoyaleApp/1.0")
            connection.requestMethod = "GET"
            val code = connection.responseCode
            if (code in 200..299) {
                Pair(true, "Server online (HTTP $code)")
            } else {
                Pair(false, "Server returned HTTP $code")
            }
        } catch (e: Exception) {
            Pair(false, e.message ?: "Failed to reach server")
        }
    }

    private fun setupListeners() {
        socketManager.onRoomUpdated = { json ->
            val roomJson = json.optJSONObject("room") ?: json
            val roomInfo = parseRoomInfo(roomJson)
            _currentRoom.value = roomInfo

            if (roomInfo != null && roomInfo.players.size == 2) {
                if (_countdownSeconds.value == null) {
                    startCountdown(5)
                }
            } else {
                cancelCountdown()
            }
        }

        socketManager.onCountdownStarted = { seconds ->
            startCountdown(seconds)
        }

        socketManager.onCountdownCancelled = {
            cancelCountdown()
        }

        socketManager.onGameStarted = {
            cancelCountdown()
            // Server will immediately follow up with game_state_sync
            Log.d(tag, "Game starting event received from server")
        }

        socketManager.onGameStateSync = { json ->
            val gameJson = json.optJSONObject("gameState")
            if (gameJson != null) {
                val state = parseClientGameState(gameJson)
                _multiplayerGameState.value = state

                val cardPlayedId = json.optString("cardPlayedId")
                val isAttack = json.optBoolean("isAttack", false)
                val attackCount = json.optInt("attackCount", 0)
                val isDraw = json.optBoolean("isDraw", false)

                if (cardPlayedId.isNotEmpty()) {
                    scope.launch {
                        _events.emit(MultiplayerEvent.CardPlayed(cardPlayedId, isAttack, attackCount))
                    }
                } else if (isDraw) {
                    scope.launch {
                        _events.emit(MultiplayerEvent.CardDrawn(1))
                    }
                }
            }
        }

        socketManager.onPlayerConnectionChanged = { guestId, isConnected ->
            scope.launch {
                _events.emit(MultiplayerEvent.PlayerConnectionChanged(guestId, isConnected))
            }
        }

        socketManager.onPlayerLeft = { guestId, name ->
            scope.launch {
                _events.emit(MultiplayerEvent.PlayerLeft(guestId, name))
            }
        }

        socketManager.onEmoteReceived = { senderId, emoji, text ->
            scope.launch {
                _events.emit(MultiplayerEvent.EmoteReceived(senderId, emoji, text))
            }
        }
    }

    fun createRoom(
        nickname: String,
        avatar: String,
        onResult: (Boolean, String?, RoomInfo?) -> Unit
    ) {
        connect()
        val payload = JSONObject().apply {
            put("nickname", nickname)
            put("avatar", avatar)
        }

        socketManager.emitWithAck("create_room", payload) { success, res ->
            if (success && res != null) {
                val roomCode = res.optString("roomCode")
                val guestId = res.optString("guestId")
                val token = res.optString("reconnectToken")
                val roomJson = res.optJSONObject("room")

                if (guestId.isNotEmpty()) {
                    sessionManager.guestId = guestId
                    _localGuestId.value = guestId
                }
                if (token.isNotEmpty()) {
                    sessionManager.reconnectToken = token
                }
                sessionManager.lastRoomCode = roomCode
                sessionManager.nickname = nickname
                sessionManager.avatar = avatar

                val room = roomJson?.let { parseRoomInfo(it) }
                _currentRoom.value = room
                onResult(true, roomCode, room)
            } else {
                val err = res?.optString("error") ?: "Failed to create room"
                onResult(false, err, null)
            }
        }
    }

    fun joinRoom(
        roomCode: String,
        nickname: String,
        avatar: String,
        onResult: (Boolean, String?, RoomInfo?) -> Unit
    ) {
        connect()
        val payload = JSONObject().apply {
            put("roomCode", roomCode.trim())
            put("nickname", nickname)
            put("avatar", avatar)
            put("reconnectToken", sessionManager.reconnectToken)
        }

        socketManager.emitWithAck("join_room", payload) { success, res ->
            if (success && res != null) {
                val guestId = res.optString("guestId")
                val token = res.optString("reconnectToken")
                val roomJson = res.optJSONObject("room")

                if (guestId.isNotEmpty()) {
                    sessionManager.guestId = guestId
                    _localGuestId.value = guestId
                }
                if (token.isNotEmpty()) {
                    sessionManager.reconnectToken = token
                }
                sessionManager.lastRoomCode = roomCode
                sessionManager.nickname = nickname
                sessionManager.avatar = avatar

                val room = roomJson?.let { parseRoomInfo(it) }
                _currentRoom.value = room
                onResult(true, null, room)
            } else {
                val err = res?.optString("error") ?: "Room not found or full"
                onResult(false, err, null)
            }
        }
    }

    fun reconnectSession(onResult: (Boolean, String?) -> Unit) {
        val token = sessionManager.reconnectToken
        if (token.isEmpty()) {
            onResult(false, "No active session to restore")
            return
        }

        connect()
        val payload = JSONObject().apply {
            put("reconnectToken", token)
        }

        socketManager.emitWithAck("reconnect_session", payload) { success, res ->
            if (success && res != null) {
                val roomJson = res.optJSONObject("room")
                val gameJson = res.optJSONObject("gameState")
                if (roomJson != null) {
                    _currentRoom.value = parseRoomInfo(roomJson)
                }
                if (gameJson != null) {
                    _multiplayerGameState.value = parseClientGameState(gameJson)
                }
                onResult(true, null)
            } else {
                val err = res?.optString("error") ?: "Session expired"
                onResult(false, err)
            }
        }
    }

    fun setReady(isReady: Boolean) {
        val roomCode = _currentRoom.value?.roomCode ?: return
        val payload = JSONObject().apply {
            put("roomCode", roomCode)
            put("isReady", isReady)
        }
        socketManager.emit("set_ready", payload)
    }

    fun startGame(onResult: (Boolean, String?) -> Unit) {
        val roomCode = _currentRoom.value?.roomCode ?: return
        val payload = JSONObject().apply {
            put("roomCode", roomCode)
        }
        socketManager.emitWithAck("start_game", payload) { success, res ->
            if (success) {
                onResult(true, null)
            } else {
                val err = res?.optString("error") ?: "Cannot start game"
                onResult(false, err)
            }
        }
    }

    fun playCard(cardId: String, chosenColor: CardColor? = null, sourceXOffset: Float = 0f) {
        val roomCode = _currentRoom.value?.roomCode ?: return
        val payload = JSONObject().apply {
            put("roomCode", roomCode)
            put("cardId", cardId)
            if (chosenColor != null) {
                put("chosenColor", chosenColor.name)
            }
            put("sourceXOffset", sourceXOffset.toDouble())
        }
        socketManager.emit("play_card", payload)
    }

    fun drawCard() {
        val roomCode = _currentRoom.value?.roomCode ?: return
        val payload = JSONObject().apply {
            put("roomCode", roomCode)
        }
        socketManager.emit("draw_card", payload)
    }

    fun skipTurn() {
        val roomCode = _currentRoom.value?.roomCode ?: return
        val payload = JSONObject().apply {
            put("roomCode", roomCode)
        }
        socketManager.emit("skip_turn", payload)
    }

    fun callUno() {
        val roomCode = _currentRoom.value?.roomCode ?: return
        val payload = JSONObject().apply {
            put("roomCode", roomCode)
        }
        socketManager.emit("call_uno", payload)
    }

    fun catchUno() {
        val roomCode = _currentRoom.value?.roomCode ?: return
        val payload = JSONObject().apply {
            put("roomCode", roomCode)
        }
        socketManager.emit("catch_uno", payload)
    }

    fun sendEmote(emoji: String, text: String) {
        val roomCode = _currentRoom.value?.roomCode ?: return
        val payload = JSONObject().apply {
            put("roomCode", roomCode)
            put("emoji", emoji)
            put("text", text)
        }
        socketManager.emit("send_emote", payload)
    }

    fun rematch() {
        val roomCode = _currentRoom.value?.roomCode ?: return
        val payload = JSONObject().apply {
            put("roomCode", roomCode)
        }
        socketManager.emit("rematch", payload)
    }

    fun leaveRoom() {
        cancelCountdown()
        val roomCode = _currentRoom.value?.roomCode
        if (roomCode != null) {
            val payload = JSONObject().apply {
                put("roomCode", roomCode)
            }
            socketManager.emit("leave_room", payload)
        }
        _currentRoom.value = null
        _multiplayerGameState.value = null
        sessionManager.clearSession()
    }

    private fun parseRoomInfo(json: JSONObject): RoomInfo {
        val roomCode = json.optString("roomCode")
        val hostGuestId = json.optString("hostGuestId")
        val status = json.optString("status", "LOBBY")
        val playersArray = json.optJSONArray("players")
        val playerList = mutableListOf<RoomPlayer>()

        if (playersArray != null) {
            for (i in 0 until playersArray.length()) {
                val p = playersArray.getJSONObject(i)
                playerList.add(
                    RoomPlayer(
                        guestId = p.optString("guestId"),
                        name = p.optString("name"),
                        avatar = p.optString("avatar", "🧔"),
                        isReady = p.optBoolean("isReady", false),
                        isConnected = p.optBoolean("isConnected", true),
                        isHost = p.optBoolean("isHost", false)
                    )
                )
            }
        }

        return RoomInfo(
            roomCode = roomCode,
            hostGuestId = hostGuestId,
            status = status,
            players = playerList
        )
    }

    private fun parseClientGameState(json: JSONObject): GameState {
        val id = json.optString("id")
        val discardArray = json.optJSONArray("discardPile")
        val discardPile = mutableListOf<Card>()
        if (discardArray != null) {
            for (i in 0 until discardArray.length()) {
                val c = discardArray.getJSONObject(i)
                discardPile.add(
                    Card(
                        id = c.optString("id"),
                        color = CardColor.valueOf(c.optString("color")),
                        value = CardValue.valueOf(c.optString("value")),
                        score = c.optInt("score", 0)
                    )
                )
            }
        }

        val playersArray = json.optJSONArray("players")
        val players = mutableListOf<Player>()
        if (playersArray != null) {
            for (i in 0 until playersArray.length()) {
                val p = playersArray.getJSONObject(i)
                val handArray = p.optJSONArray("hand")
                val hand = mutableListOf<Card>()
                if (handArray != null) {
                    for (j in 0 until handArray.length()) {
                        val c = handArray.getJSONObject(j)
                        hand.add(
                            Card(
                                id = c.optString("id"),
                                color = CardColor.valueOf(c.optString("color")),
                                value = CardValue.valueOf(c.optString("value")),
                                score = c.optInt("score", 0)
                            )
                        )
                    }
                }

                // If opponent's hand is private (empty array from server), construct placeholder cards using handCount
                val handCount = p.optInt("handCount", hand.size)
                val finalHand = if (hand.isEmpty() && handCount > 0) {
                    List(handCount) { idx ->
                        Card(id = "opp_card_$idx", color = CardColor.WILD, value = CardValue.NUM_0, score = 0)
                    }
                } else {
                    hand
                }

                players.add(
                    Player(
                        id = p.optString("id"),
                        name = p.optString("name"),
                        avatar = p.optString("avatar", "🧔"),
                        isAi = p.optBoolean("isAi", false),
                        hand = finalHand,
                        unoCalled = p.optBoolean("unoCalled", false),
                        score = p.optInt("score", 0)
                    )
                )
            }
        }

        val statusStr = json.optString("status", "PLAYING")
        val status = try {
            GameStatus.valueOf(statusStr)
        } catch (_: Exception) {
            GameStatus.PLAYING
        }

        val activeColorStr = json.optString("activeColor", "RED")
        val activeColor = try {
            CardColor.valueOf(activeColorStr)
        } catch (_: Exception) {
            CardColor.RED
        }

        val winnerId = json.optString("winnerId").ifEmpty { null }
        val roomCode = json.optString("roomCode")
        val isCustomRoom = json.optBoolean("isCustomRoom", true)

        val lastActionJson = json.optJSONObject("lastAction")
        val lastAction = lastActionJson?.let {
            LastAction(
                playerId = it.optString("playerId"),
                text = it.optString("text"),
                timestamp = it.optLong("timestamp", System.currentTimeMillis())
            )
        }

        val unoCatchable = json.optString("unoCatchablePlayerId").ifEmpty { null }
        val seconds = json.optInt("turnSecondsRemaining", 20)
        val hasDrawn = json.optBoolean("hasDrawnThisTurn", false)

        return GameState(
            id = id,
            deck = emptyList(), // Draw pile size managed by cardsDrawnCount and deckCount
            discardPile = discardPile,
            players = players,
            currentTurnIndex = json.optInt("currentTurnIndex", 0),
            activeColor = activeColor,
            direction = json.optInt("direction", 1),
            pendingDrawCount = json.optInt("pendingDrawCount", 0),
            status = status,
            winnerId = winnerId,
            lastAction = lastAction,
            rules = GameRules(),
            unoCatchablePlayerId = unoCatchable,
            turnSecondsRemaining = seconds,
            hasDrawnThisTurn = hasDrawn,
            isCustomRoom = isCustomRoom,
            roomCode = roomCode,
            isBotTakeover = json.optBoolean("isBotTakeover", false),
            roomClosed = json.optBoolean("roomClosed", false),
            isRoomWaiting = false,
            cardsDrawnCount = json.optInt("cardsDrawnCount", 0)
        )
    }
}
