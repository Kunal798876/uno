package com.example.uno.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.uno.ads.AdManager
import com.example.uno.logic.UnoEngine
import com.example.uno.model.*
import com.example.uno.network.RoomNetworkManager
import com.example.uno.sound.SoundEffects
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.json.JSONObject

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("uno_device_prefs", Context.MODE_PRIVATE)

    val sounds = SoundEffects(application.applicationContext)

    private val _currentScreen = MutableStateFlow("home")
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    private val hasCompletedOnboarding = prefs.getBoolean("has_completed_onboarding", false)
    private val savedName = prefs.getString("user_name", "Player 1") ?: "Player 1"
    private val savedAvatar = prefs.getString("user_avatar", "🧔") ?: "🧔"

    private val _userProfile = MutableStateFlow(
        UserProfile(
            name = savedName,
            avatar = savedAvatar,
            gamesWon = prefs.getInt("games_won", 0),
            gamesPlayed = prefs.getInt("games_played", 0)
        )
    )
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _showFirstTimeOnboarding = MutableStateFlow(!hasCompletedOnboarding)
    val showFirstTimeOnboarding: StateFlow<Boolean> = _showFirstTimeOnboarding.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadingMessage = MutableStateFlow("Loading...")
    val loadingMessage: StateFlow<String> = _loadingMessage.asStateFlow()

    private val _rules = MutableStateFlow(GameRules())
    val rules: StateFlow<GameRules> = _rules.asStateFlow()

    private val _gameState = MutableStateFlow<GameState?>(null)
    val gameState: StateFlow<GameState?> = _gameState.asStateFlow()

    private val _localPlayerId = MutableStateFlow("player_1")
    val localPlayerId: StateFlow<String> = _localPlayerId.asStateFlow()

    private val _activeEmotes = MutableStateFlow<Map<String, EmoteMessage>>(emptyMap())
    val activeEmotes: StateFlow<Map<String, EmoteMessage>> = _activeEmotes.asStateFlow()

    private val _shakingCardId = MutableStateFlow<String?>(null)
    val shakingCardId: StateFlow<String?> = _shakingCardId.asStateFlow()

    private val _isMuted = MutableStateFlow(sounds.isMuted)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isMusicOn = MutableStateFlow(sounds.isMusicOn)
    val isMusicOn: StateFlow<Boolean> = _isMusicOn.asStateFlow()

    private val _showTestAd = MutableStateFlow(false)
    val showTestAd: StateFlow<Boolean> = _showTestAd.asStateFlow()

    private val _flightAnimation = MutableStateFlow<CardFlightAnimation?>(null)
    val flightAnimation: StateFlow<CardFlightAnimation?> = _flightAnimation.asStateFlow()

    private val _animatingCardId = MutableStateFlow<String?>(null)
    val animatingCardId: StateFlow<String?> = _animatingCardId.asStateFlow()

    private val _isGamePaused = MutableStateFlow(false)
    val isGamePaused: StateFlow<Boolean> = _isGamePaused.asStateFlow()

    val closedRoomCodes = mutableSetOf<String>()

    val notifications = com.example.uno.notification.NotificationHelper(application.applicationContext)

    private val _isNotificationsEnabled = MutableStateFlow(notifications.hasNotificationPermission())
    val isNotificationsEnabled: StateFlow<Boolean> = _isNotificationsEnabled.asStateFlow()

    private var aiJob: Job? = null
    private var timerJob: Job? = null

    init {
        if (_isMusicOn.value && !_isMuted.value) {
            sounds.startBgm()
        }
    }

    fun completeFirstTimeOnboarding(name: String, avatar: String) {
        val trimmed = name.trim().ifEmpty { "Player 1" }
        prefs.edit()
            .putBoolean("has_completed_onboarding", true)
            .putString("user_name", trimmed)
            .putString("user_avatar", avatar)
            .apply()

        _userProfile.value = _userProfile.value.copy(name = trimmed, avatar = avatar)
        _showFirstTimeOnboarding.value = false
    }

    fun navigateTo(screen: String) {
        _currentScreen.value = screen
    }

    fun toggleNotifications(enabled: Boolean) {
        _isNotificationsEnabled.value = enabled
        if (enabled) {
            notifications.sendDailyGiftNotification()
        }
    }

    fun toggleSound() {
        sounds.isMuted = !sounds.isMuted
        _isMuted.value = sounds.isMuted
    }

    fun toggleMusic() {
        sounds.isMusicOn = !sounds.isMusicOn
        _isMusicOn.value = sounds.isMusicOn
    }

    fun updateProfile(newProfile: UserProfile) {
        _userProfile.value = newProfile
        prefs.edit()
            .putString("user_name", newProfile.name)
            .putString("user_avatar", newProfile.avatar)
            .putInt("games_won", newProfile.gamesWon)
            .putInt("games_played", newProfile.gamesPlayed)
            .apply()
    }

    fun updateRules(newRules: GameRules) {
        _rules.value = newRules
    }

    fun resetStats() {
        _userProfile.value = _userProfile.value.copy(gamesWon = 0, gamesPlayed = 0)
        prefs.edit()
            .putInt("games_won", 0)
            .putInt("games_played", 0)
            .apply()
    }

    fun onOpenGiftClicked(activity: Activity) {
        // Direct Rewarded Video Ad on gift click
        AdManager.showRewardedAd(
            activity = activity,
            onRewardEarned = {
                onAdRewardClaimed()
            },
            onFallbackRequired = {
                // If AdMob no-fill / network issue, fallback to full interactive overlay
                _showTestAd.value = true
            }
        )
    }

    fun onOpenGiftDirectAd() {
        _showTestAd.value = true
    }

    fun onAdRewardClaimed() {
        _showTestAd.value = false
        sounds.playWinFanfare()
        updateProfile(_userProfile.value.copy(gamesWon = _userProfile.value.gamesWon + 1))
    }

    fun dismissTestAd() {
        _showTestAd.value = false
    }

    fun onAppBackgrounded() {
        timerJob?.cancel()
        aiJob?.cancel()
        sounds.pauseBgm()
        if (_currentScreen.value == "playing" && _gameState.value?.status == GameStatus.PLAYING) {
            _isGamePaused.value = true
        }
    }

    fun onAppForegrounded() {
        if (!_isMuted.value && _isMusicOn.value) {
            sounds.resumeBgm()
        }
    }

    fun resumeFromPause() {
        _isGamePaused.value = false
        if (_currentScreen.value == "playing" && _gameState.value?.status == GameStatus.PLAYING) {
            startTurnTimer()
            checkAiTurn()
        }
    }

    fun clearFlightAnimation() {
        _flightAnimation.value = null
        _animatingCardId.value = null
    }

    fun startAiMatch() {
        viewModelScope.launch {
            _isLoading.value = true
            _loadingMessage.value = "Shuffling cards & setting table..."
            delay(500)

            val p1 = Player(
                id = "player_local",
                name = _userProfile.value.name,
                avatar = _userProfile.value.avatar,
                isAi = false
            )
            val p2 = Player(
                id = "player_ai",
                name = "Doris",
                avatar = "👩",
                isAi = true
            )
            _localPlayerId.value = "player_local"
            val state = UnoEngine.initializeMatch(p1, p2, _rules.value)
            _gameState.value = state.copy(isCustomRoom = false)
            _currentScreen.value = "playing"
            updateProfile(_userProfile.value.copy(gamesPlayed = _userProfile.value.gamesPlayed + 1))

            _isLoading.value = false
            startTurnTimer()
            checkAiTurn()
        }
    }

    val activeRooms = mutableMapOf<String, UserProfile>()
    private var networkRoomJob: Job? = null

    private val jsonFormat = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun broadcastStateSync(
        state: GameState,
        lastCardPlayed: Card? = null,
        sourceXOffset: Float = 0f,
        isAttack: Boolean = false,
        attackCount: Int = 0,
        isDraw: Boolean = false
    ) {
        val code = state.roomCode ?: return
        viewModelScope.launch {
            try {
                val encodedState = jsonFormat.encodeToString(GameState.serializer(), state)
                val syncMsg = JSONObject().apply {
                    put("type", "STATE_SYNC")
                    put("sender", _localPlayerId.value)
                    put("stateJson", encodedState)
                    if (lastCardPlayed != null) {
                        put("cardId", lastCardPlayed.id)
                        put("cardColor", lastCardPlayed.color.name)
                        put("cardValue", lastCardPlayed.value.name)
                        put("cardScore", lastCardPlayed.score)
                    }
                    put("sourceXOffset", sourceXOffset.toDouble())
                    put("isAttack", isAttack)
                    put("attackCount", attackCount)
                    put("isDraw", isDraw)
                }
                RoomNetworkManager.postMessage(code, syncMsg.toString())
            } catch (_: Exception) {
            }
        }
    }

    fun createCustomRoom(code: String) {
        networkRoomJob?.cancel()
        val p1 = Player(
            id = "player_host",
            name = _userProfile.value.name,
            avatar = _userProfile.value.avatar,
            isAi = false
        )
        val p2 = Player(
            id = "player_guest",
            name = "Waiting for Player...",
            avatar = "👤",
            isAi = false
        )
        _localPlayerId.value = "player_host"
        val state = UnoEngine.initializeMatch(p1, p2, _rules.value).copy(
            isCustomRoom = true,
            roomCode = code,
            isRoomWaiting = true,
            isBotTakeover = false,
            roomClosed = false
        )
        activeRooms[code] = _userProfile.value
        _gameState.value = state
        _currentScreen.value = "playing"
        updateProfile(_userProfile.value.copy(gamesPlayed = _userProfile.value.gamesPlayed + 1))

        // Start zero-delay persistent HTTP streaming connection
        networkRoomJob = RoomNetworkManager.startStreaming(viewModelScope, code) { msgJson ->
            handleNetworkMessage(msgJson)
        }

        // Announce room on network channel
        viewModelScope.launch {
            val announce = JSONObject().apply {
                put("type", "ROOM_ANNOUNCED")
                put("code", code)
                put("sender", "player_host")
                put("name", _userProfile.value.name)
                put("avatar", _userProfile.value.avatar)
            }
            RoomNetworkManager.postMessage(code, announce.toString())
        }
    }

    fun joinCustomRoom(code: String, guestName: String = "", guestAvatar: String = "🦊"): Boolean {
        networkRoomJob?.cancel()
        val trimmed = code.trim()
        if (closedRoomCodes.contains(trimmed)) {
            return false
        }
        val currentMatch = _gameState.value
        val resolvedName = guestName.trim().ifEmpty { _userProfile.value.name }
        val resolvedAvatar = if (guestAvatar.isNotEmpty()) guestAvatar else _userProfile.value.avatar

        if (currentMatch != null && currentMatch.roomCode == trimmed && currentMatch.isRoomWaiting) {
            // Real-time join: update guest profile in existing waiting room (in-memory)
            sounds.playWinFanfare()
            val p1 = currentMatch.players[0]
            val p2 = currentMatch.players[1].copy(
                name = resolvedName,
                avatar = resolvedAvatar,
                isAi = false
            )
            _gameState.value = currentMatch.copy(
                players = listOf(p1, p2),
                isRoomWaiting = false
            )
            startTurnTimer()
            return true
        } else {
            // Join as Guest into room match across devices/sessions
            val hostProfile = activeRooms[trimmed] ?: UserProfile(name = "Room Host", avatar = "👑")
            val p1 = Player(
                id = "player_host",
                name = hostProfile.name,
                avatar = hostProfile.avatar,
                isAi = false
            )
            val p2 = Player(
                id = "player_guest",
                name = resolvedName,
                avatar = resolvedAvatar,
                isAi = false
            )
            _localPlayerId.value = "player_guest"
            val state = UnoEngine.initializeMatch(p1, p2, _rules.value).copy(
                isCustomRoom = true,
                roomCode = trimmed,
                isRoomWaiting = false,
                isBotTakeover = false,
                roomClosed = false
            )
            sounds.playWinFanfare()
            _gameState.value = state
            _currentScreen.value = "playing"
            updateProfile(_userProfile.value.copy(gamesPlayed = _userProfile.value.gamesPlayed + 1))
            startTurnTimer()

            // Start zero-delay persistent HTTP streaming connection
            networkRoomJob = RoomNetworkManager.startStreaming(viewModelScope, trimmed) { msgJson ->
                handleNetworkMessage(msgJson)
            }

            // Immediately broadcast join event to Host
            viewModelScope.launch {
                val joinMsg = JSONObject().apply {
                    put("type", "PLAYER_JOINED")
                    put("code", trimmed)
                    put("sender", "player_guest")
                    put("name", resolvedName)
                    put("avatar", resolvedAvatar)
                }
                RoomNetworkManager.postMessage(trimmed, joinMsg.toString())
            }
            return true
        }
    }

    private fun handleNetworkMessage(msgJson: JSONObject) {
        val type = msgJson.optString("type")
        val sender = msgJson.optString("sender")
        if (sender == _localPlayerId.value) return // Ignore own broadcast

        when (type) {
            "ROOM_ANNOUNCED" -> {
                val hostName = msgJson.optString("name")
                val hostAvatar = msgJson.optString("avatar")
                val state = _gameState.value ?: return
                if (state.isCustomRoom && _localPlayerId.value == "player_guest") {
                    val p1 = state.players[0].copy(name = hostName, avatar = hostAvatar)
                    _gameState.value = state.copy(players = listOf(p1, state.players[1]))
                }
            }
            "PLAYER_JOINED" -> {
                // Host screen: friend joined in real time!
                val guestName = msgJson.optString("name", "Friend")
                val guestAvatar = msgJson.optString("avatar", "🦊")
                val state = _gameState.value ?: return
                if (state.isCustomRoom && _localPlayerId.value == "player_host") {
                    sounds.playWinFanfare()
                    val p1 = state.players[0]
                    val p2 = state.players[1].copy(
                        name = guestName,
                        avatar = guestAvatar,
                        isAi = false
                    )
                    val runningState = state.copy(
                        players = listOf(p1, p2),
                        isRoomWaiting = false
                    )
                    _gameState.value = runningState
                    startTurnTimer()

                    // Broadcast authoritative full state so Guest screen is immediately synchronized!
                    broadcastStateSync(runningState)
                }
            }
            "STATE_SYNC" -> {
                // Real-time authoritative state synchronization from Host
                val stateJson = msgJson.optString("stateJson")
                if (stateJson.isNotEmpty()) {
                    try {
                        val syncedState = jsonFormat.decodeFromString(GameState.serializer(), stateJson)
                        val cardId = msgJson.optString("cardId")
                        val isAttack = msgJson.optBoolean("isAttack", false)
                        val attackCount = msgJson.optInt("attackCount", 0)
                        val isDraw = msgJson.optBoolean("isDraw", false)

                        // Visual flight animation for cards played by remote player
                        if (cardId.isNotEmpty()) {
                            val colorStr = msgJson.optString("cardColor")
                            val valueStr = msgJson.optString("cardValue")
                            val score = msgJson.optInt("cardScore", 0)
                            val card = if (colorStr.isNotEmpty() && valueStr.isNotEmpty()) {
                                Card(cardId, CardColor.valueOf(colorStr), CardValue.valueOf(valueStr), score)
                            } else null

                            sounds.playCardPlay()
                            _flightAnimation.value = CardFlightAnimation(
                                id = UUID.randomUUID().toString(),
                                card = card,
                                type = "play",
                                startOffsetX = 0f,
                                startOffsetY = -240f,
                                targetOffsetX = 0f,
                                targetOffsetY = -10f,
                                durationMs = 500
                            )
                            viewModelScope.launch {
                                delay(500)
                                _flightAnimation.value = null
                            }
                        } else if (isDraw) {
                            sounds.playCardDraw()
                        }

                        // Animate +2 or +4 attack cards into hand and play penalty sound
                        if (isAttack && attackCount > 0) {
                            sounds.playPenalty()
                            _flightAnimation.value = CardFlightAnimation(
                                id = UUID.randomUUID().toString(),
                                card = null,
                                isBack = true,
                                type = "attack",
                                count = attackCount,
                                startOffsetX = -130f,
                                startOffsetY = 65f,
                                targetOffsetX = 0f,
                                targetOffsetY = 280f,
                                durationMs = 800
                            )
                            viewModelScope.launch {
                                delay(800)
                                _flightAnimation.value = null
                            }
                        }

                        _gameState.value = syncedState.copy(isRoomWaiting = false)
                        startTurnTimer()

                        if (syncedState.status == GameStatus.GAMEOVER) {
                            onGameOver(syncedState)
                        }
                    } catch (_: Exception) {
                    }
                }
            }
            "REQUEST_PLAY" -> {
                // Host receives play action from Guest: execute authoritatively and broadcast state
                if (_localPlayerId.value == "player_host") {
                    val cardId = msgJson.optString("cardId")
                    val colorStr = msgJson.optString("color")
                    val chosenColor = if (colorStr.isNotEmpty()) CardColor.valueOf(colorStr) else null
                    val sourceX = msgJson.optDouble("sourceXOffset", 0.0).toFloat()
                    val state = _gameState.value ?: return

                    val (nextState, success) = UnoEngine.executePlayCard(state, "player_guest", cardId, chosenColor)
                    if (success) {
                        sounds.playCardPlay()
                        val card = state.players.getOrNull(1)?.hand?.find { it.id == cardId }
                        val isAttack = card != null && (card.value == CardValue.DRAW2 || card.value == CardValue.WILD4)
                        val count = if (card?.value == CardValue.WILD4) 4 else if (card?.value == CardValue.DRAW2) 2 else 0

                        _gameState.value = nextState
                        startTurnTimer()
                        broadcastStateSync(nextState, card, sourceX, isAttack, count)

                        if (nextState.status == GameStatus.GAMEOVER) {
                            onGameOver(nextState)
                        }
                    }
                }
            }
            "REQUEST_DRAW" -> {
                // Host receives draw action from Guest: execute authoritatively and broadcast state
                if (_localPlayerId.value == "player_host") {
                    val state = _gameState.value ?: return
                    val (nextState, drawn) = UnoEngine.executeDrawCard(state, "player_guest")
                    if (drawn.isNotEmpty()) {
                        sounds.playCardDraw()
                        _gameState.value = nextState
                        startTurnTimer()
                        broadcastStateSync(nextState, isDraw = true)
                    }
                }
            }
            "REQUEST_SKIP" -> {
                if (_localPlayerId.value == "player_host") {
                    val state = _gameState.value ?: return
                    sounds.playCardSwoop()
                    val nextState = UnoEngine.executeSkipTurn(state, "player_guest")
                    _gameState.value = nextState
                    startTurnTimer()
                    broadcastStateSync(nextState)
                }
            }
            "REQUEST_CALL_UNO" -> {
                if (_localPlayerId.value == "player_host") {
                    val state = _gameState.value ?: return
                    val (nextState, success) = UnoEngine.executeCallUno(state, "player_guest")
                    if (success) {
                        sounds.playUnoCall()
                        _gameState.value = nextState
                        broadcastStateSync(nextState)
                    }
                }
            }
            "REQUEST_CATCH_UNO" -> {
                if (_localPlayerId.value == "player_host") {
                    val state = _gameState.value ?: return
                    val (nextState, success) = UnoEngine.executeCatchUno(state, "player_guest")
                    if (success) {
                        sounds.playUnoCall()
                        _gameState.value = nextState
                        broadcastStateSync(nextState)
                    }
                }
            }
            "REQUEST_REMATCH" -> {
                if (_localPlayerId.value == "player_host") {
                    rematch()
                }
            }
            "EMOTE" -> {
                val emoji = msgJson.optString("emoji")
                val text = msgJson.optString("text")
                val map = _activeEmotes.value.toMutableMap()
                map[sender] = EmoteMessage(emoji, text)
                _activeEmotes.value = map

                viewModelScope.launch {
                    delay(3500)
                    val cur = _activeEmotes.value.toMutableMap()
                    if (cur[sender]?.emoji == emoji) {
                        cur.remove(sender)
                        _activeEmotes.value = cur
                    }
                }
            }
        }
    }

    fun onFriendJoined(guestName: String = "Friend", guestAvatar: String = "🦊") {
        val state = _gameState.value ?: return
        if (!state.isRoomWaiting) return
        sounds.playWinFanfare()
        val p1 = state.players[0]
        val p2 = state.players[1].copy(
            name = guestName,
            avatar = guestAvatar,
            isAi = false
        )
        val next = state.copy(
            players = listOf(p1, p2),
            isRoomWaiting = false
        )
        _gameState.value = next
        startTurnTimer()
        if (state.isCustomRoom) {
            broadcastStateSync(next)
        }
    }

    fun onPlayCard(cardId: String, chosenColor: CardColor? = null, sourceXOffset: Float = 0f) {
        val state = _gameState.value ?: return
        val currentP = state.players[state.currentTurnIndex]
        val card = currentP.hand.find { it.id == cardId } ?: return

        // CHECK IF LEGAL FIRST!
        val topCard = state.discardPile.lastOrNull()
        val isLegal = topCard != null && UnoEngine.isCardPlayable(
            card = card,
            topCard = topCard,
            activeColor = state.activeColor,
            pendingDrawCount = state.pendingDrawCount,
            rules = state.rules
        )

        if (!isLegal) {
            sounds.playBuzzer()
            _shakingCardId.value = cardId
            viewModelScope.launch {
                delay(400)
                if (_shakingCardId.value == cardId) {
                    _shakingCardId.value = null
                }
            }
            return
        }

        val isLocal = currentP.id == _localPlayerId.value

        // In a custom room, if Guest plays, send request to authoritative Host
        if (state.isCustomRoom && _localPlayerId.value == "player_guest") {
            _animatingCardId.value = cardId
            val flightDuration = 550
            _flightAnimation.value = CardFlightAnimation(
                id = UUID.randomUUID().toString(),
                card = card,
                type = "play",
                startOffsetX = sourceXOffset,
                startOffsetY = 280f,
                targetOffsetX = 0f,
                targetOffsetY = -10f,
                durationMs = flightDuration
            )
            viewModelScope.launch {
                delay(flightDuration.toLong())
                _animatingCardId.value = null
                _flightAnimation.value = null

                val req = JSONObject().apply {
                    put("type", "REQUEST_PLAY")
                    put("sender", "player_guest")
                    put("cardId", cardId)
                    put("color", chosenColor?.name ?: "")
                    put("sourceXOffset", sourceXOffset.toDouble())
                }
                RoomNetworkManager.postMessage(state.roomCode ?: "", req.toString())
            }
            return
        }

        // Host turn or single-player AI match
        _animatingCardId.value = cardId
        val flightDuration = 600
        _flightAnimation.value = CardFlightAnimation(
            id = UUID.randomUUID().toString(),
            card = card,
            type = "play",
            startOffsetX = if (isLocal) sourceXOffset else -130f,
            startOffsetY = if (isLocal) 280f else -240f,
            targetOffsetX = 0f,
            targetOffsetY = -10f,
            durationMs = flightDuration
        )

        viewModelScope.launch {
            delay(flightDuration.toLong())

            val currentState = _gameState.value ?: return@launch
            val (nextState, success) = UnoEngine.executePlayCard(currentState, currentP.id, cardId, chosenColor)
            if (success) {
                sounds.playCardPlay()
                _gameState.value = nextState
                _animatingCardId.value = null
                _flightAnimation.value = null

                val isAttack = card.value == CardValue.DRAW2 || card.value == CardValue.WILD4
                val attackCount = if (card.value == CardValue.WILD4) 4 else if (card.value == CardValue.DRAW2) 2 else 0

                if (isAttack) {
                    sounds.playPenalty()
                    _flightAnimation.value = CardFlightAnimation(
                        id = UUID.randomUUID().toString(),
                        card = null,
                        isBack = true,
                        type = "attack",
                        count = attackCount,
                        startOffsetX = -130f,
                        startOffsetY = 65f,
                        targetOffsetX = if (isLocal) -130f else 0f,
                        targetOffsetY = if (isLocal) -240f else 280f,
                        durationMs = 900
                    )
                    delay(900)
                    _flightAnimation.value = null
                }

                // Authoritative Host broadcasts full state immediately to network
                if (currentState.isCustomRoom) {
                    broadcastStateSync(nextState, card, sourceXOffset, isAttack, attackCount)
                }

                if (nextState.status == GameStatus.GAMEOVER) {
                    onGameOver(nextState)
                } else {
                    if (!_isGamePaused.value) {
                        startTurnTimer()
                        checkAiTurn()
                    }
                }
            } else {
                _animatingCardId.value = null
                _flightAnimation.value = null
            }
        }
    }

    fun onDrawCard() {
        val state = _gameState.value ?: return
        val currentP = state.players[state.currentTurnIndex]
        val isLocal = currentP.id == _localPlayerId.value

        _flightAnimation.value = CardFlightAnimation(
            id = UUID.randomUUID().toString(),
            card = null,
            isBack = true,
            type = "draw",
            startOffsetX = -130f,
            startOffsetY = 65f,
            targetOffsetX = if (isLocal) 0f else -130f,
            targetOffsetY = if (isLocal) 290f else -260f,
            durationMs = 700
        )

        // Guest requests draw from Host
        if (state.isCustomRoom && _localPlayerId.value == "player_guest") {
            viewModelScope.launch {
                delay(700)
                _flightAnimation.value = null
                val req = JSONObject().apply {
                    put("type", "REQUEST_DRAW")
                    put("sender", "player_guest")
                }
                RoomNetworkManager.postMessage(state.roomCode ?: "", req.toString())
            }
            return
        }

        viewModelScope.launch {
            delay(700)
            val currentState = _gameState.value ?: return@launch
            val (nextState, drawn) = UnoEngine.executeDrawCard(currentState, currentP.id)
            if (drawn.isNotEmpty()) {
                sounds.playCardDraw()
                _gameState.value = nextState
                startTurnTimer()
                checkAiTurn()

                if (currentState.isCustomRoom) {
                    broadcastStateSync(nextState, isDraw = true)
                }
            }
        }
    }

    fun onSkipTurn() {
        val state = _gameState.value ?: return
        val currentP = state.players[state.currentTurnIndex]
        if (!state.hasDrawnThisTurn) return

        if (state.isCustomRoom && _localPlayerId.value == "player_guest") {
            viewModelScope.launch {
                val req = JSONObject().apply {
                    put("type", "REQUEST_SKIP")
                    put("sender", "player_guest")
                }
                RoomNetworkManager.postMessage(state.roomCode ?: "", req.toString())
            }
            return
        }

        sounds.playCardSwoop()
        val nextState = UnoEngine.executeSkipTurn(state, currentP.id)
        _gameState.value = nextState
        startTurnTimer()
        checkAiTurn()

        if (state.isCustomRoom) {
            broadcastStateSync(nextState)
        }
    }

    fun onCallUno() {
        val state = _gameState.value ?: return
        val currentP = state.players[state.currentTurnIndex]

        if (state.isCustomRoom && _localPlayerId.value == "player_guest") {
            viewModelScope.launch {
                val req = JSONObject().apply {
                    put("type", "REQUEST_CALL_UNO")
                    put("sender", "player_guest")
                }
                RoomNetworkManager.postMessage(state.roomCode ?: "", req.toString())
            }
            return
        }

        val (nextState, success) = UnoEngine.executeCallUno(state, currentP.id)
        if (success) {
            sounds.playUnoCall()
            _gameState.value = nextState
            if (state.isCustomRoom) {
                broadcastStateSync(nextState)
            }
        }
    }

    fun onCatchUno() {
        val state = _gameState.value ?: return
        val catcher = state.players.find { it.id == _localPlayerId.value } ?: return

        if (state.isCustomRoom && _localPlayerId.value == "player_guest") {
            viewModelScope.launch {
                val req = JSONObject().apply {
                    put("type", "REQUEST_CATCH_UNO")
                    put("sender", "player_guest")
                }
                RoomNetworkManager.postMessage(state.roomCode ?: "", req.toString())
            }
            return
        }

        val (nextState, success) = UnoEngine.executeCatchUno(state, catcher.id)
        if (success) {
            sounds.playUnoCall()
            _gameState.value = nextState
            if (state.isCustomRoom) {
                broadcastStateSync(nextState)
            }
        }
    }

    fun sendEmote(emoji: String, text: String) {
        val pid = _localPlayerId.value
        val map = _activeEmotes.value.toMutableMap()
        map[pid] = EmoteMessage(emoji, text)
        _activeEmotes.value = map

        val state = _gameState.value
        if (state != null && state.isCustomRoom) {
            viewModelScope.launch {
                val emoteMsg = JSONObject().apply {
                    put("type", "EMOTE")
                    put("sender", pid)
                    put("emoji", emoji)
                    put("text", text)
                }
                RoomNetworkManager.postMessage(state.roomCode ?: "", emoteMsg.toString())
            }
        }

        viewModelScope.launch {
            delay(3500)
            val currentMap = _activeEmotes.value.toMutableMap()
            if (currentMap[pid]?.emoji == emoji) {
                currentMap.remove(pid)
                _activeEmotes.value = currentMap
            }
        }
    }

    private fun startTurnTimer() {
        timerJob?.cancel()
        val state = _gameState.value ?: return
        if (state.status != GameStatus.PLAYING || _isGamePaused.value) return

        timerJob = viewModelScope.launch {
            var seconds = 20
            while (seconds > 0) {
                if (_isGamePaused.value) return@launch
                _gameState.value = _gameState.value?.copy(turnSecondsRemaining = seconds)
                delay(1000L)
                seconds--

                val currentState = _gameState.value ?: return@launch
                if (currentState.status != GameStatus.PLAYING || _isGamePaused.value) return@launch

                if (seconds in 1..5) {
                    sounds.playTimerTick()
                }
            }

            // Timer expired! 20-second penalty imposed: 1 penalty card drawn and turn passes
            val currentState = _gameState.value ?: return@launch
            if (currentState.status != GameStatus.PLAYING || _isGamePaused.value) return@launch

            sounds.playPenalty()
            val (penalizedState, drawnCard) = UnoEngine.executeTimerPenalty(currentState)

            val penalizedPlayer = currentState.players.getOrNull(currentState.currentTurnIndex)
            val isLocal = penalizedPlayer?.id == _localPlayerId.value

            _flightAnimation.value = CardFlightAnimation(
                id = UUID.randomUUID().toString(),
                card = drawnCard,
                isBack = !isLocal,
                type = "draw",
                startOffsetX = -130f,
                startOffsetY = 65f, // Directly from the deck of cards!
                targetOffsetX = if (isLocal) 0f else -130f,
                targetOffsetY = if (isLocal) 290f else -260f,
                durationMs = 850
            )
            delay(850)

            _gameState.value = penalizedState

            if (penalizedState.status == GameStatus.GAMEOVER) {
                onGameOver(penalizedState)
            } else {
                startTurnTimer()
                checkAiTurn()
            }
        }
    }

    private fun checkAiTurn() {
        aiJob?.cancel()
        val state = _gameState.value ?: return
        if (state.status != GameStatus.PLAYING || _isGamePaused.value) return

        val currentTurnPlayer = state.players.getOrNull(state.currentTurnIndex) ?: return
        val isBotPlaying = currentTurnPlayer.isAi || (state.isBotTakeover && currentTurnPlayer.id != _localPlayerId.value)
        if (!isBotPlaying) return

        aiJob = viewModelScope.launch {
            delay(1300) // Realistic bot deliberation
            val latestState = _gameState.value ?: return@launch
            if (latestState.status != GameStatus.PLAYING || _isGamePaused.value) return@launch
            if (latestState.players[latestState.currentTurnIndex].id != currentTurnPlayer.id) return@launch

            val decision = UnoEngine.getAiDecision(latestState, currentTurnPlayer.id)

            if (decision.callUnoFirst) {
                val (unoState, _) = UnoEngine.executeCallUno(latestState, currentTurnPlayer.id)
                sounds.playUnoCall()
                _gameState.value = unoState
                delay(500)
            }

            val stateBeforeAction = _gameState.value ?: return@launch
            if (_isGamePaused.value) return@launch
            when (decision.action) {
                "play" -> {
                    decision.cardId?.let { cid ->
                        onPlayCard(cid, decision.chosenColor, sourceXOffset = -130f)
                    }
                }
                "skip" -> {
                    onSkipTurn()
                }
                "callUno" -> {
                    val (afterCatch, _) = UnoEngine.executeCatchUno(stateBeforeAction, currentTurnPlayer.id)
                    sounds.playUnoCall()
                    _gameState.value = afterCatch
                    startTurnTimer()
                    checkAiTurn()
                }
                else -> {
                    onDrawCard()
                }
            }
        }
    }

    private fun onGameOver(state: GameState) {
        timerJob?.cancel()
        aiJob?.cancel()

        // If custom room, permanently close it
        state.roomCode?.let { code ->
            closedRoomCodes.add(code)
            _gameState.value = state.copy(roomClosed = true)
        }

        val winner = state.players.find { it.id == state.winnerId }
        val isLocalWinner = winner?.id == _localPlayerId.value
        if (isLocalWinner) {
            sounds.playWinFanfare()
            updateProfile(_userProfile.value.copy(gamesWon = _userProfile.value.gamesWon + 1))
        } else {
            sounds.playDefeatSound()
        }
    }

    fun rematch() {
        val state = _gameState.value ?: return

        if (state.isCustomRoom && _localPlayerId.value == "player_guest") {
            viewModelScope.launch {
                val req = JSONObject().apply {
                    put("type", "REQUEST_REMATCH")
                    put("sender", "player_guest")
                }
                RoomNetworkManager.postMessage(state.roomCode ?: "", req.toString())
            }
            return
        }

        val p1 = state.players[0].copy(hand = emptyList(), unoCalled = false)
        val p2 = state.players[1].copy(hand = emptyList(), unoCalled = false)
        val freshState = UnoEngine.initializeMatch(p1, p2, _rules.value).copy(
            isCustomRoom = state.isCustomRoom,
            roomCode = state.roomCode,
            isRoomWaiting = false
        )
        _gameState.value = freshState
        updateProfile(_userProfile.value.copy(gamesPlayed = _userProfile.value.gamesPlayed + 1))
        startTurnTimer()
        checkAiTurn()

        if (state.isCustomRoom) {
            broadcastStateSync(freshState)
        }
    }

    fun leaveGame() {
        val state = _gameState.value
        if (state != null && state.isCustomRoom) {
            if (state.isBotTakeover) {
                state.roomCode?.let { closedRoomCodes.add(it) }
                timerJob?.cancel()
                aiJob?.cancel()
                _gameState.value = null
                _currentScreen.value = "home"
                return
            } else {
                val botTakeoverState = state.copy(
                    isBotTakeover = true,
                    lastAction = LastAction(
                        playerId = "system",
                        text = "Player offline - Bot playing"
                    )
                )
                _gameState.value = botTakeoverState
                state.roomCode?.let { closedRoomCodes.add(it) }
                timerJob?.cancel()
                aiJob?.cancel()
                _gameState.value = null
                _currentScreen.value = "home"
                return
            }
        }

        networkRoomJob?.cancel()
        timerJob?.cancel()
        aiJob?.cancel()
        _gameState.value = null
        _currentScreen.value = "home"
    }

    override fun onCleared() {
        super.onCleared()
        networkRoomJob?.cancel()
        timerJob?.cancel()
        aiJob?.cancel()
        sounds.release()
    }
}
