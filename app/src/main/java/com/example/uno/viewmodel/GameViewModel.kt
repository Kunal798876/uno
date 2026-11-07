package com.example.uno.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.uno.logic.UnoEngine
import com.example.uno.model.*
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

    fun onOpenGiftDirectAd() {
        // Direct test ad: no confirmation, no waiting!
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

    fun clearFlightAnimation() {
        _flightAnimation.value = null
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

    fun startCustomRoomMatch(code: String, isHost: Boolean): Boolean {
        if (closedRoomCodes.contains(code)) {
            // Cannot join closed room
            return false
        }

        viewModelScope.launch {
            _isLoading.value = true
            _loadingMessage.value = "Connecting to 5-digit room $code..."
            delay(500)

            val p1 = Player(
                id = "player_host",
                name = if (isHost) _userProfile.value.name else "Friend Host",
                avatar = if (isHost) _userProfile.value.avatar else "👑",
                isAi = false
            )
            val p2 = Player(
                id = "player_guest",
                name = if (isHost) "Player 2" else _userProfile.value.name,
                avatar = if (isHost) "👤" else _userProfile.value.avatar,
                isAi = false
            )
            _localPlayerId.value = if (isHost) "player_host" else "player_guest"

            val state = UnoEngine.initializeMatch(p1, p2, _rules.value).copy(
                isCustomRoom = true,
                roomCode = code,
                isBotTakeover = false,
                roomClosed = false
            )
            _gameState.value = state
            _currentScreen.value = "playing"
            updateProfile(_userProfile.value.copy(gamesPlayed = _userProfile.value.gamesPlayed + 1))

            _isLoading.value = false
            startTurnTimer()
        }
        return true
    }

    fun onPlayCard(cardId: String, chosenColor: CardColor? = null) {
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
            // "if user doesnt have required cards and if plays clicks on wrong card then no animation only shake red if not correct card"
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

        // Emit flight animation for the card moving to discard pile (from hand or opponent to center)
        _flightAnimation.value = CardFlightAnimation(
            id = UUID.randomUUID().toString(),
            card = card,
            type = "play",
            startOffsetX = if (isLocal) 0f else -130f,
            startOffsetY = if (isLocal) 290f else -260f,
            targetOffsetX = 0f,
            targetOffsetY = -10f,
            durationMs = 750
        )

        viewModelScope.launch {
            delay(750) // Slow, visible and realistic flight delay

            val currentState = _gameState.value ?: return@launch
            val (nextState, success) = UnoEngine.executePlayCard(currentState, currentP.id, cardId, chosenColor)
            if (success) {
                sounds.playCardPlay()

                // Check if card had +2 or +4: animate cards from deck to opponent/player in slow noticeable animation!
                if (card.value == CardValue.DRAW2 || card.value == CardValue.WILD4) {
                    val count = if (card.value == CardValue.WILD4) 4 else 2
                    sounds.playPenalty()
                    _flightAnimation.value = CardFlightAnimation(
                        id = UUID.randomUUID().toString(),
                        card = null,
                        isBack = true,
                        type = "attack",
                        count = count,
                        startOffsetX = -130f,
                        startOffsetY = 65f, // Deck of cards!
                        targetOffsetX = if (isLocal) -130f else 0f, // To opponent or local hand
                        targetOffsetY = if (isLocal) -260f else 290f,
                        durationMs = 1200 // Slow, dramatic and visible
                    )
                    delay(1200)
                }

                _gameState.value = nextState
                if (nextState.status == GameStatus.GAMEOVER) {
                    onGameOver(nextState)
                } else {
                    startTurnTimer()
                    checkAiTurn()
                }
            }
        }
    }

    fun onDrawCard() {
        val state = _gameState.value ?: return
        val currentP = state.players[state.currentTurnIndex]
        val isLocal = currentP.id == _localPlayerId.value

        // Card animation COMING FROM DECK OF CARDS (-130f, 65f)
        _flightAnimation.value = CardFlightAnimation(
            id = UUID.randomUUID().toString(),
            card = null,
            isBack = true,
            type = "draw",
            startOffsetX = -130f,
            startOffsetY = 65f, // Directly from the deck of cards!
            targetOffsetX = if (isLocal) 0f else -130f,
            targetOffsetY = if (isLocal) 290f else -260f,
            durationMs = 800 // Slow, real and visible
        )

        viewModelScope.launch {
            delay(800)
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

        sounds.playCardSwoop()
        val nextState = UnoEngine.executeSkipTurn(state, currentP.id)
        _gameState.value = nextState
        startTurnTimer()
        checkAiTurn()
    }

    fun onCallUno() {
        val state = _gameState.value ?: return
        val currentP = state.players[state.currentTurnIndex]

        val (nextState, success) = UnoEngine.executeCallUno(state, currentP.id)
        if (success) {
            sounds.playUnoCall()
            _gameState.value = nextState
        }
    }

    fun onCatchUno() {
        val state = _gameState.value ?: return
        val catcher = state.players.find { it.id == _localPlayerId.value } ?: return

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
        if (state.status != GameStatus.PLAYING) return

        timerJob = viewModelScope.launch {
            var seconds = 20
            while (seconds > 0) {
                _gameState.value = _gameState.value?.copy(turnSecondsRemaining = seconds)
                delay(1000L)
                seconds--

                val currentState = _gameState.value ?: return@launch
                if (currentState.status != GameStatus.PLAYING) return@launch

                if (seconds in 1..5) {
                    sounds.playTimerTick()
                }
            }

            // Timer expired! 20-second penalty imposed: 1 penalty card drawn and turn passes
            val currentState = _gameState.value ?: return@launch
            if (currentState.status != GameStatus.PLAYING) return@launch

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
        if (state.status != GameStatus.PLAYING) return

        val currentTurnPlayer = state.players.getOrNull(state.currentTurnIndex) ?: return
        val isBotPlaying = currentTurnPlayer.isAi || (state.isBotTakeover && currentTurnPlayer.id != _localPlayerId.value)
        if (!isBotPlaying) return

        aiJob = viewModelScope.launch {
            delay(1300) // Realistic bot deliberation
            val latestState = _gameState.value ?: return@launch
            if (latestState.status != GameStatus.PLAYING) return@launch
            if (latestState.players[latestState.currentTurnIndex].id != currentTurnPlayer.id) return@launch

            val decision = UnoEngine.getAiDecision(latestState, currentTurnPlayer.id)

            if (decision.callUnoFirst) {
                val (unoState, _) = UnoEngine.executeCallUno(latestState, currentTurnPlayer.id)
                sounds.playUnoCall()
                _gameState.value = unoState
                delay(500)
            }

            val stateBeforeAction = _gameState.value ?: return@launch
            when (decision.action) {
                "play" -> {
                    decision.cardId?.let { cid ->
                        onPlayCard(cid, decision.chosenColor)
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
        if (state.isCustomRoom) {
            leaveGame()
            return
        }
        val p1 = state.players[0]
        val p2 = state.players[1]
        val freshState = UnoEngine.initializeMatch(p1, p2, _rules.value)
        _gameState.value = freshState
        updateProfile(_userProfile.value.copy(gamesPlayed = _userProfile.value.gamesPlayed + 1))
        startTurnTimer()
        checkAiTurn()
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

        timerJob?.cancel()
        aiJob?.cancel()
        _gameState.value = null
        _currentScreen.value = "home"
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        aiJob?.cancel()
        sounds.release()
    }
}
