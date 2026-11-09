package com.example.uno

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.uno.ads.AdManager
import com.example.uno.network.NetworkMonitor
import com.example.uno.network.NoInternetDialog
import com.example.uno.ui.*
import com.example.uno.ui.components.*
import com.example.uno.ui.theme.UnoRoyaleTheme
import com.example.uno.viewmodel.GameViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()
    private lateinit var networkMonitor: NetworkMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Google Mobile Ads SDK with App ID
        AdManager.initialize(this)

        // Initialize active network monitor
        networkMonitor = NetworkMonitor(this)

        setContent {
            UnoRoyaleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    UnoRoyaleApp(activity = this, viewModel = viewModel, networkMonitor = networkMonitor)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Immediately pause game turns, timer and music when screen turns off or user leaves app
        viewModel.onAppBackgrounded()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onAppBackgrounded()
    }

    override fun onResume() {
        super.onResume()
        // Resume activity state and check network connection
        viewModel.onAppForegrounded()
        networkMonitor.checkConnection()
    }
}

@Composable
fun UnoRoyaleApp(
    activity: Activity,
    viewModel: GameViewModel,
    networkMonitor: NetworkMonitor
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val gameState by viewModel.gameState.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val rules by viewModel.rules.collectAsState()
    val localPlayerId by viewModel.localPlayerId.collectAsState()
    val shakingCardId by viewModel.shakingCardId.collectAsState()
    val animatingCardId by viewModel.animatingCardId.collectAsState()
    val isGamePaused by viewModel.isGamePaused.collectAsState()
    val activeEmotes by viewModel.activeEmotes.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isMusicOn by viewModel.isMusicOn.collectAsState()
    val showTestAd by viewModel.showTestAd.collectAsState()
    val flightAnimation by viewModel.flightAnimation.collectAsState()
    val showFirstTimeOnboarding by viewModel.showFirstTimeOnboarding.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadingMessage by viewModel.loadingMessage.collectAsState()
    val isNetworkConnected by networkMonitor.isConnected.collectAsState()
    val currentRoom by viewModel.currentRoom.collectAsState()
    val countdownSeconds by viewModel.countdownSeconds.collectAsState()
    val socketConnectionStatus by viewModel.socketConnectionStatus.collectAsState()
    val backendUrl by viewModel.backendUrl.collectAsState()

    var showRulesModal by remember { mutableStateOf(false) }
    var showHowToPlayModal by remember { mutableStateOf(false) }
    var showProfileModal by remember { mutableStateOf(false) }

    Crossfade(targetState = currentScreen, label = "screen_crossfade") { screen ->
        when (screen) {
            "playing" -> {
                gameState?.let { state ->
                    GameArenaScreen(
                        gameState = state,
                        localPlayerId = localPlayerId,
                        shakingCardId = shakingCardId,
                        animatingCardId = animatingCardId,
                        isGamePaused = isGamePaused,
                        flightAnimation = flightAnimation,
                        activeEmotes = activeEmotes,
                        isMuted = isMuted,
                        isMusicOn = isMusicOn,
                        onPlayCard = { cid, color, xOffset -> viewModel.onPlayCard(cid, color, xOffset) },
                        onDrawCard = { viewModel.onDrawCard() },
                        onSkipTurn = { viewModel.onSkipTurn() },
                        onClearFlightAnimation = { viewModel.clearFlightAnimation() },
                        onCallUno = { viewModel.onCallUno() },
                        onCatchUno = { viewModel.onCatchUno() },
                        onSendEmote = { emoji, text -> viewModel.sendEmote(emoji, text) },
                        onToggleSound = { viewModel.toggleSound() },
                        onToggleMusic = { viewModel.toggleMusic() },
                        onResumeFromPause = { viewModel.resumeFromPause() },
                        onFriendJoined = { name, avatar -> viewModel.onFriendJoined(name, avatar) },
                        onRematch = { viewModel.rematch() },
                        onLeaveGame = { viewModel.leaveGame() }
                    )
                } ?: run {
                    viewModel.navigateTo("home")
                }
            }
            "lobby" -> {
                LobbyScreen(
                    profile = userProfile,
                    rules = rules,
                    currentRoom = currentRoom,
                    connectionStatus = socketConnectionStatus,
                    localGuestId = localPlayerId,
                    countdownSeconds = countdownSeconds,
                    onCreateRoom = { onResult ->
                        viewModel.createCustomRoom(onResult)
                    },
                    onJoinRoom = { code, name, avatar, onJoined ->
                        viewModel.joinCustomRoom(code, name, avatar, onJoined)
                    },
                    onSetReady = { isReady ->
                        viewModel.setReady(isReady)
                    },
                    onStartGame = { callback ->
                        viewModel.startMultiplayerGame(callback)
                    },
                    onLeaveRoom = {
                        viewModel.multiplayerRepo.leaveRoom()
                    },
                    onRetryConnection = {
                        viewModel.multiplayerRepo.connect()
                    },
                    onBack = { viewModel.navigateTo("home") }
                )
            }
            "settings" -> {
                val isNotificationsEnabled by viewModel.isNotificationsEnabled.collectAsState()
                SettingsScreen(
                    rules = rules,
                    isMuted = isMuted,
                    isMusicOn = isMusicOn,
                    isNotificationsEnabled = isNotificationsEnabled,
                    onToggleSound = { viewModel.toggleSound() },
                    onToggleMusic = { viewModel.toggleMusic() },
                    onToggleNotifications = { viewModel.toggleNotifications(it) },
                    onUpdateRules = { viewModel.updateRules(it) },
                    onResetStats = { viewModel.resetStats() },
                    onBack = { viewModel.navigateTo("home") }
                )
            }
            else -> {
                HomeScreen(
                    profile = userProfile,
                    onStartAiMatch = { viewModel.startAiMatch() },
                    onOpenLobby = { viewModel.navigateTo("lobby") },
                    onOpenRules = { showRulesModal = true },
                    onOpenHowToPlay = { showHowToPlayModal = true },
                    onOpenGift = { viewModel.onOpenGiftClicked(activity) },
                    onOpenProfile = { showProfileModal = true },
                    onOpenSettings = { viewModel.navigateTo("settings") }
                )
            }
        }
    }

    // Interactive No Internet Connection Modal (prompts for Wi-Fi or Mobile Data)
    if (!isNetworkConnected) {
        NoInternetDialog(
            onRetry = {
                networkMonitor.checkConnection()
            }
        )
    }

    // Fullscreen Rewarded Video Ad Overlay (on Gift click fallback)
    if (showTestAd) {
        TestAdFullscreenOverlay(
            onRewardEarned = { viewModel.onAdRewardClaimed() },
            onDismiss = { viewModel.dismissTestAd() }
        )
    }

    // First-Time Device Onboarding (Shown only once per device)
    if (showFirstTimeOnboarding) {
        FirstTimeOnboardingModal(
            onComplete = { name, avatar ->
                viewModel.completeFirstTimeOnboarding(name, avatar)
            }
        )
    }

    // Loading Overlay (Shown when setting table or connecting)
    if (isLoading) {
        LoadingOverlay(message = loadingMessage)
    }

    if (showRulesModal) {
        CustomRulesModal(
            rules = rules,
            onSaveRules = { viewModel.updateRules(it) },
            onDismiss = { showRulesModal = false }
        )
    }

    if (showHowToPlayModal) {
        HowToPlayModal(
            onDismiss = { showHowToPlayModal = false }
        )
    }

    if (showProfileModal) {
        ProfileSetupModal(
            currentProfile = userProfile,
            onSaveProfile = { viewModel.updateProfile(it) },
            onDismiss = { showProfileModal = false }
        )
    }
}
