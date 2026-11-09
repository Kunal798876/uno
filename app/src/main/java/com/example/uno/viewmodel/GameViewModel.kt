package com.example.uno.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.uno.ads.UnityAdsManager
import com.example.uno.logic.UnoEngine
import com.example.uno.model.*
import com.example.uno.network.NetworkConnectivityManager
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

    private val networkManager = NetworkConnectivityManager.getInstance(application)

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

    private val _localPlayerId = MutableStateFlow("player_local")
    val localPlayerId: StateFlow<String> = _localPlayerId.asStateFlow()

    private val _activeEmotes = MutableStateFlow<Map<String, EmoteMessage>>(emptyMap())
    val activeEmotes: StateFlow<Map<String, EmoteMessage>> = _activeEmotes.asStateFlow()

    private val _shakingCardId = MutableStateFlow<String?>(null)
    val shakingCardId: StateFlow<String?> = _shakingCardId.asStateFlow()

    private val _isMuted = MutableStateFlow(sounds.isMuted)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isMusicOn = MutableStateFlow(sounds.isMusicOn)
    val isMusicOn: StateFlow<Boolean> = _isMusicOn.asStateFlow()

    private val _flightAnimation = MutableStateFlow<CardFlightAnimation?>(null)
    val flightAnimation: StateFlow<CardFlightAnimation?> = _flightAnimation.asStateFlow()

    private val _animatingCardId = MutableStateFlow<String?>(null)
    val animatingCardId: StateFlow<String?> = _animatingCardId.asStateFlow()

    private val _isGamePaused = MutableStateFlow(false)
    val isGamePaused: StateFlow<Boolean> = _isGamePaused.asStateFlow()

    val notifications = com.example.uno.notification.NotificationHelper(application.applicationContext)

    private val _isNotificationsEnabled = MutableStateFlow(notifications.hasNotificationPermission())
    val isNotificationsEnabled: StateFlow<Boolean> = _isNotificationsEnabled.asStateFlow()

    private var aiJob: Job? = null
    private var timerJob: Job? = null

    init {
        if (_isMusicOn.value && !_isMuted.value) {
            sounds.startBgm()
        }

        // Global network connectivity observer
        viewModelScope.launch {
            networkManager.isOnline.collect { online ->
                if (!online) {
                    // Internet connection lost: pause active gameplay immediately
                    if (_currentScreen.value == "playing" && _gameState.value?.status == GameStatus.PLAYING) {
                        _isGamePaused.value = true
                        timerJob?.cancel()
                        aiJob?.cancel()
                    }
                    UnityAdsManager.onNetworkLost()
                } else {
                    // Internet connection restored: resume active gameplay safely without restarting
                    if (_currentScreen.value == "playing" && _gameState.value?.status == GameStatus.PLAYING) {
                        _isGamePaused.value = false
                        startTurnTimer()
                        checkAiTurn()
                    }
                    UnityAdsManager.onNetworkRestored(application.applicationContext)
                }
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

    fun setAiDifficulty(difficulty: AiDifficulty) {
        _rules.value = _rules.value.copy(aiDifficulty = difficulty)
    }

    fun resetStats() {
        _userProfile.value = _userProfile.value.copy(gamesWon = 0, gamesPlayed = 0)
        prefs.edit()
            .putInt("games_won", 0)
            .putInt("games_played", 0)
            .apply()
    }

    fun onOpenGiftClicked(activity: Activity) {
        if (!networkManager.isOnline.value) {
            Toast.makeText(
                activity,
                "Internet connection required to load advertisements",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Native Unity Ads Rewarded Video (BP_Rewarded_Android)
        // Triggers video advertisement experience. User returns to Home screen after ad finishes or closes.
        // No rewards, stars, coins, points, or balance modifications are granted.
        UnityAdsManager.showRewardedVideo(
            activity = activity,
            onAdClosed = {
                // User returned to Home screen - no rewards or balances granted
            }
        )
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
        if (!networkManager.isOnline.value) return
        _isGamePaused.value = false
        if (_currentScreen.value == "playing" && _gameState.value?.status == GameStatus.PLAYING) {
            val state = _gameState.value
            if (state != null) {
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
    // AI MATCH (Online-Only: strictly blocks offline play)
    // =========================================================================
    fun startAiMatch(chosenDifficulty: AiDifficulty? = null) {
        // Enforce online-only requirement
        if (!networkManager.isOnline.value) {
            return
        }

        if (chosenDifficulty != null) {
            _rules.value = _rules.value.copy(aiDifficulty = chosenDifficulty)
        }

        viewModelScope.launch {
            _isLoading.value = true
            _loadingMessage.value = "Connecting to arena & starting duel vs AI..."
            delay(400)

            val p1 = Player(
                id = "player_local",
                name = _userProfile.value.name,
                avatar = _userProfile.value.avatar,
                isAi = false
            )
            val aiName = when (_rules.value.aiDifficulty) {
                AiDifficulty.EASY -> "Novice Bot"
                AiDifficulty.MEDIUM -> "Pro Doris"
                AiDifficulty.HARD -> "Grandmaster AI"
            }
            val aiAvatar = when (_rules.value.aiDifficulty) {
                AiDifficulty.EASY -> "🤖"
                AiDifficulty.MEDIUM -> "👩"
                AiDifficulty.HARD -> "🧠"
            }
            val p2 = Player(
                id = "player_ai",
                name = aiName,
                avatar = aiAvatar,
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
    // GAMEPLAY ACTIONS (Guarded by internet connectivity)
    // =========================================================================

    fun onPlayCard(cardId: String, chosenColor: CardColor? = null, sourceXOffset: Float = 0f) {
        if (!networkManager.isOnline.value) return

        val state = _gameState.value ?: return
        if (state.status != GameStatus.PLAYING) return
        if (_animatingCardId.value != null || _flightAnimation.value != null) return

        val currentP = state.players[state.currentTurnIndex]
        val card = currentP.hand.find { it.id == cardId } ?: return

        // Validate legality
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

        _animatingCardId.value = cardId
        val flightDuration = 550
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
        if (!networkManager.isOnline.value) return

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
        if (!networkManager.isOnline.value) return

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
        if (!networkManager.isOnline.value) return

        val state = _gameState.value ?: return
        val currentP = state.players[state.currentTurnIndex]

        val (nextState, success) = UnoEngine.executeCallUno(state, currentP.id)
        if (success) {
            sounds.playUnoCall()
            _gameState.value = nextState
        }
    }

    fun onCatchUno() {
        if (!networkManager.isOnline.value) return

        val state = _gameState.value ?: return
        val catcher = state.players.find { it.id == _localPlayerId.value } ?: return

        val (nextState, success) = UnoEngine.executeCatchUno(state, catcher.id)
        if (success) {
            sounds.playUnoCall()
            _gameState.value = nextState
        }
    }

    fun sendEmote(emoji: String, text: String) {
        if (!networkManager.isOnline.value) return

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

            // Timer expired! 20-second penalty imposed
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
        if (state.status != GameStatus.PLAYING || _isGamePaused.value) return

        val currentTurnPlayer = state.players.getOrNull(state.currentTurnIndex) ?: return
        if (!currentTurnPlayer.isAi) return

        aiJob = viewModelScope.launch {
            delay(1200) // Realistic bot deliberation
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
        if (!networkManager.isOnline.value) return

        val state = _gameState.value ?: return

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
