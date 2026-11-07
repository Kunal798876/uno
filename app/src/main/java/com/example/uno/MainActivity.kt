package com.example.uno

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
import com.example.uno.ui.*
import com.example.uno.ui.components.*
import com.example.uno.ui.theme.UnoRoyaleTheme
import com.example.uno.viewmodel.GameViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            UnoRoyaleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    UnoRoyaleApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun UnoRoyaleApp(viewModel: GameViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val gameState by viewModel.gameState.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val rules by viewModel.rules.collectAsState()
    val localPlayerId by viewModel.localPlayerId.collectAsState()
    val shakingCardId by viewModel.shakingCardId.collectAsState()
    val activeEmotes by viewModel.activeEmotes.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isMusicOn by viewModel.isMusicOn.collectAsState()
    val showTestAd by viewModel.showTestAd.collectAsState()
    val flightAnimation by viewModel.flightAnimation.collectAsState()
    val showFirstTimeOnboarding by viewModel.showFirstTimeOnboarding.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadingMessage by viewModel.loadingMessage.collectAsState()

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
                        flightAnimation = flightAnimation,
                        activeEmotes = activeEmotes,
                        isMuted = isMuted,
                        isMusicOn = isMusicOn,
                        onPlayCard = { cid, color -> viewModel.onPlayCard(cid, color) },
                        onDrawCard = { viewModel.onDrawCard() },
                        onSkipTurn = { viewModel.onSkipTurn() },
                        onClearFlightAnimation = { viewModel.clearFlightAnimation() },
                        onCallUno = { viewModel.onCallUno() },
                        onCatchUno = { viewModel.onCatchUno() },
                        onSendEmote = { emoji, text -> viewModel.sendEmote(emoji, text) },
                        onToggleSound = { viewModel.toggleSound() },
                        onToggleMusic = { viewModel.toggleMusic() },
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
                    onStartGame = { code, isHost -> viewModel.startCustomRoomMatch(code, isHost) },
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
                    onOpenGift = { viewModel.onOpenGiftDirectAd() },
                    onOpenProfile = { showProfileModal = true },
                    onOpenSettings = { viewModel.navigateTo("settings") }
                )
            }
        }
    }

    // Direct Fullscreen Test Ad (on Gift click, no confirmation, no waiting)
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
