package com.example.uno.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.uno.ads.AdManager
import com.example.uno.logic.UnoEngine
import com.example.uno.model.*
import com.example.uno.network.MultiplayerEvent
import com.example.uno.network.MultiplayerRepository
import com.example.uno.network.RoomInfo
import com.example.uno.network.SocketConnectionStatus
import com.example.uno.sound.SoundEffects
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("uno_device_prefs", Context.MODE_PRIVATE)

    val sounds = SoundEffects(application.applicationContext)
    val multiplayerRepo = MultiplayerRepository(application.applicationContext)

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

    // Multiplayer room & connection
    val currentRoom: StateFlow<RoomInfo?> = multiplayerRepo.currentRoom
    val countdownSeconds: StateFlow<Int?> = multiplayerRepo.countdownSeconds
    val socketConnectionStatus: StateFlow<SocketConnectionStatus> = multiplayerRepo.socketManager.connectionStatus

    val notifications = com.example.uno.notification.NotificationHelper(application.applicationContext)

    private val _isNotificationsEnabled = MutableStateFlow(notifications.hasNotificationPermission())
    val isNotificationsEnabled: StateFlow<Boolean> = _isNotificationsEnabled.asStateFlow()

    private var aiJob: Job? = null
    private var timerJob: Job? = null

    init {
        if (_isMusicOn.value && !_isMuted.value) {
            sounds.startBgm()
        }

        // Listen for authoritative multiplayer state updates from server
        viewModelScope.launch {
            multiplayerRepo.multiplayerGameState.collect { remoteState ->
                if (remoteState != null) {
                    _gameState.value = remoteState
                    if (_currentScreen.value != "playing" && remoteState.status == GameStatus.PLAYING) {
                        _currentScreen.value = "playing"
                    }
                    if (remoteState.status == GameStatus.GAMEOVER) {
                        onGameOver(remoteState)
                    }
                }
            }
        }

        // Listen for multiplayer remote events
        viewModelScope.launch {
            multiplayerRepo.events.collect { event ->
                handleMultiplayerEvent(event)
            }
        }
    }

    private fun handleMultiplayerEvent(event: MultiplayerEvent) {
        when (event) {
            is MultiplayerEvent.CardPlayed -> {
                sounds.playCardPlay()
                // Remote player played card
                _flightAnimation.value = CardFlightAnimation(
                    id = UUID.randomUUID().toString(),
                    card = null,
                    isBack = true,
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

                if (event.isAttack && event.attackCount > 0) {
                    sounds.playPenalty()
                    _flightAnimation.value = CardFlightAnimation(
                        id = UUID.randomUUID().toString(),
                        card = null,
                        isBack = true,
                        type = "attack",
                        count = event.attackCount,
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
            }
            is MultiplayerEvent.CardDrawn -> {
                sounds.playCardDraw()
            }
            is MultiplayerEvent.EmoteReceived -> {
                val map = _activeEmotes.value.toMutableMap()
                map[event.senderId] = EmoteMessage(event.emoji, event.text)
                _activeEmotes.value = map

                viewModelScope.launch {
                    delay(3500)
                    val cur = _activeEmotes.value.toMutableMap()
                    if (cur[event.senderId]?.emoji == event.emoji) {
                        cur.remove(event.senderId)
                        _activeEmotes.value = cur
                    }
                }
            }
            is MultiplayerEvent.PlayerConnectionChanged -> {
                // Connection indicator updated in RoomInfo
            }
            is MultiplayerEvent.PlayerLeft -> {
                // Handled in room state
            }
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
        // Auto-reconnect multiplayer session if disconnected
        val room = currentRoom.value
        if (room != null && socketConnectionStatus.value == SocketConnectionStatus.DISCONNECTED) {
            multiplayerRepo.reconnectSession { _, _ -> }
        }
    }

    fun resumeFromPause() {
        _isGamePaused.value = false
        if (_currentScreen.value == "playing" && _gameState.value?.status == GameStatus.PLAYING) {
            val state = _gameState.value
            if (state != null && !state.isCustomRoom) {
                startTurnTimer()
                checkAiTurn()
            }
        }
    }

    fun clearFlightAnimation() {
        _flightAnimation.value = null
        _animatingCardId.value = null
    }

    // =========================================================================
    // OFFLINE AI MATCH (Completely preserved and intact)
    // =========================================================================
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

    // =========================================================================
    // ONLINE MULTIPLAYER (Socket.IO + Redis Authoritative Backend)
    // =========================================================================

    val backendUrl: StateFlow<String> = multiplayerRepo.backendUrl

    fun setBackendUrl(url: String) {
        multiplayerRepo.setBackendUrl(url)
    }

    suspend fun testServerConnection(url: String): Pair<Boolean, String> {
        return multiplayerRepo.testConnection(url)
    }

    fun createCustomRoom(onResult: (Boolean, String?) -> Unit) {
        _isLoading.value = true
        _loadingMessage.value = "Creating room on server..."
        multiplayerRepo.createRoom(
            nickname = _userProfile.value.name,
            avatar = _userProfile.value.avatar
        ) { success, roomCodeOrError, room ->
            _isLoading.value = false
            if (success && roomCodeOrError != null) {
                _localPlayerId.value = multiplayerRepo.sessionManager.guestId
                onResult(true, roomCodeOrError)
            } else {
                onResult(false, roomCodeOrError ?: "Network connection failed. Please check internet.")
            }
        }
    }

    fun joinCustomRoom(
        roomCode: String,
        guestName: String = "",
        guestAvatar: String = "🦊",
        onJoined: (Boolean, String?) -> Unit
    ) {
        _isLoading.value = true
        _loadingMessage.value = "Connecting to room $roomCode..."
        val resolvedName = guestName.trim().ifEmpty { _userProfile.value.name }
        val resolvedAvatar = if (guestAvatar.isNotEmpty()) guestAvatar else _userProfile.value.avatar

        multiplayerRepo.joinRoom(
            roomCode = roomCode,
            nickname = resolvedName,
            avatar = resolvedAvatar
        ) { success, error, _ ->
            _isLoading.value = false
            if (success) {
                _localPlayerId.value = multiplayerRepo.sessionManager.guestId
                onJoined(true, null)
            } else {
                onJoined(false, error ?: "Room not found or full")
            }
        }
    }

    fun setReady(isReady: Boolean) {
        multiplayerRepo.setReady(isReady)
    }

    fun startMultiplayerGame(onResult: (Boolean, String?) -> Unit) {
        _isLoading.value = true
        _loadingMessage.value = "Dealing authoritative deck..."
        multiplayerRepo.startGame { success, error ->
            _isLoading.value = false
            onResult(success, error)
        }
    }

    fun onFriendJoined(guestName: String = "Friend", guestAvatar: String = "🦊") {
        // Handled via Socket.IO room_updated event
    }

    // =========================================================================
    // GAMEPLAY ACTIONS (Works for both Offline AI and Authoritative Server)
    // =========================================================================

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

        // MULTIPLAYER MODE: Send to authoritative server
        if (state.isCustomRoom) {
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
                multiplayerRepo.playCard(cardId, chosenColor, sourceXOffset)
            }
            return
        }

        // OFFLINE AI MODE
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

        if (state.isCustomRoom) {
            viewModelScope.launch {
                delay(700)
                _flightAnimation.value = null
                multiplayerRepo.drawCard()
            }
            return
        }

        // OFFLINE AI MODE
        viewModelScope.launch {
            delay(700)
            val currentState = _gameState.value ?: return@launch
            val (nextState, drawn) = UnoEngine.executeDrawCard(currentState, currentP.id)
            if (drawn.isNotEmpty()) {
                sounds.playCardDraw()
                _gameState.value = nextState
                startTurnTimer()
                checkAiTurn()
            }
        }
    }

    fun onSkipTurn() {
        val state = _gameState.value ?: return
        val currentP = state.players[state.currentTurnIndex]
        if (!state.hasDrawnThisTurn) return

        if (state.isCustomRoom) {
            multiplayerRepo.skipTurn()
            return
        }

        sounds.playCardSwoop()
        val nextState = UnoEngine.executeSkipTurn(state, currentP.id)
        _gameState.value = nextState
        startTurnTimer()
        checkAiTurn()
    }

    fun onCallUno() {
        val state = _gameState.value ?: return
        val currentP = state.players[state.currentTurnIndex]

        if (state.isCustomRoom) {
            sounds.playUnoCall()
            multiplayerRepo.callUno()
            return
        }

        val (nextState, success) = UnoEngine.executeCallUno(state, currentP.id)
        if (success) {
            sounds.playUnoCall()
            _gameState.value = nextState
        }
    }

    fun onCatchUno() {
        val state = _gameState.value ?: return
        val catcher = state.players.find { it.id == _localPlayerId.value } ?: return

        if (state.isCustomRoom) {
            sounds.playUnoCall()
            multiplayerRepo.catchUno()
            return
        }

        val (nextState, success) = UnoEngine.executeCatchUno(state, catcher.id)
        if (success) {
            sounds.playUnoCall()
            _gameState.value = nextState
        }
    }

    fun sendEmote(emoji: String, text: String) {
        val pid = _localPlayerId.value
        val map = _activeEmotes.value.toMutableMap()
        map[pid] = EmoteMessage(emoji, text)
        _activeEmotes.value = map

        val state = _gameState.value
        if (state != null && state.isCustomRoom) {
            multiplayerRepo.sendEmote(emoji, text)
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

            // Timer expired! 20-second penalty imposed in single-player
            val currentState = _gameState.value ?: return@launch
            if (currentState.status != GameStatus.PLAYING || _isGamePaused.value || currentState.isCustomRoom) return@launch

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
                startOffsetY = 65f,
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
        if (state.status != GameStatus.PLAYING || _isGamePaused.value || state.isCustomRoom) return

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

        if (state.isCustomRoom) {
            multiplayerRepo.rematch()
            return
        }

        val p1 = state.players[0].copy(hand = emptyList(), unoCalled = false)
        val p2 = state.players[1].copy(hand = emptyList(), unoCalled = false)
        val freshState = UnoEngine.initializeMatch(p1, p2, _rules.value).copy(
            isCustomRoom = false
        )
        _gameState.value = freshState
        updateProfile(_userProfile.value.copy(gamesPlayed = _userProfile.value.gamesPlayed + 1))
        startTurnTimer()
        checkAiTurn()
    }

    fun leaveGame() {
        val state = _gameState.value
        if (state != null && state.isCustomRoom) {
            multiplayerRepo.leaveRoom()
        }

        timerJob?.cancel()
        aiJob?.cancel()
        _gameState.value = null
        _currentScreen.value = "home"
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        aiJob?.cancel()
        multiplayerRepo.leaveRoom()
        sounds.release()
    }
}
