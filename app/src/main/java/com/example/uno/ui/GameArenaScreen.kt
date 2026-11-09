package com.example.uno.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uno.logic.UnoEngine
import com.example.uno.model.*
import com.example.uno.ui.components.*
import com.example.uno.ui.theme.*

@Composable
fun GameArenaScreen(
    gameState: GameState,
    localPlayerId: String,
    shakingCardId: String?,
    animatingCardId: String? = null,
    isGamePaused: Boolean = false,
    flightAnimation: CardFlightAnimation?,
    activeEmotes: Map<String, EmoteMessage>,
    isMuted: Boolean,
    isMusicOn: Boolean,
    onPlayCard: (String, CardColor?, Float) -> Unit,
    onDrawCard: () -> Unit,
    onSkipTurn: () -> Unit,
    onClearFlightAnimation: () -> Unit,
    onCallUno: () -> Unit,
    onCatchUno: () -> Unit,
    onSendEmote: (String, String) -> Unit,
    onToggleSound: () -> Unit,
    onToggleMusic: () -> Unit,
    onResumeFromPause: () -> Unit = {},
    onFriendJoined: (String, String) -> Unit = { _, _ -> },
    onRematch: () -> Unit,
    onLeaveGame: () -> Unit
) {
    var showPauseMenu by remember { mutableStateOf(false) }
    var pendingWildCardId by remember { mutableStateOf<String?>(null) }
    var pendingWildCardXOffset by remember { mutableStateOf(0f) }
    var showEmotePicker by remember { mutableStateOf(false) }

    BackHandler {
        showPauseMenu = true
    }

    val localPlayerIndex = gameState.players.indexOfFirst { it.id == localPlayerId }.let { if (it == -1) 0 else it }
    val opponentIndex = 1 - localPlayerIndex

    val localPlayer = gameState.players[localPlayerIndex]
    val opponent = gameState.players[opponentIndex]
    val isLocalTurn = gameState.currentTurnIndex == localPlayerIndex
    val topCard = gameState.discardPile.lastOrNull()

    val canCallUno = UnoEngine.canPlayerCallUno(gameState, localPlayer.id)
    val canCatchOpponent = gameState.unoCatchablePlayerId == opponent.id

    val localEmote = activeEmotes[localPlayer.id]
    val opponentEmote = activeEmotes[opponent.id]

    // Turn ring rotation animation (indicates play direction as seen in 3.jpg)
    val infiniteTransition = rememberInfiniteTransition(label = "turn_direction_spin")
    val directionRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (gameState.direction > 0) 360f else -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "turn_spin"
    )

    // Breathing pulse for active turn countdown
    val countdownPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (gameState.turnSecondsRemaining <= 5) 1.12f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (gameState.turnSecondsRemaining <= 5) 400 else 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "countdown_pulse"
    )

    // Countdown color transition from Green (20s..12s) to Yellow (11s..6s) to Red (5s..0s)
    val secondsRemaining = gameState.turnSecondsRemaining
    val countdownColor = when {
        secondsRemaining <= 5 -> Color(0xFFFF1744) // Intense Red
        secondsRemaining <= 11 -> Color(0xFFFFD600) // Warning Yellow
        else -> Color(0xFF00E676) // Vibrant Green
    }

    val activeColorHex = when (gameState.activeColor) {
        CardColor.RED -> UnoRed
        CardColor.BLUE -> UnoBlue
        CardColor.GREEN -> UnoGreen
        CardColor.YELLOW -> UnoYellow
        CardColor.WILD -> UnoWildBlack
    }

    val currentCardColor = topCard?.let { card ->
        if (card.color == CardColor.WILD || card.value.isWild) {
            gameState.activeColor
        } else {
            card.color
        }
    } ?: gameState.activeColor

    val targetCircleColor = when (currentCardColor) {
        CardColor.RED -> Color(0xFFFF1B04)
        CardColor.BLUE -> Color(0xFF0072BC)
        CardColor.GREEN -> Color(0xFF009E38)
        CardColor.YELLOW -> Color(0xFFFFD600)
        CardColor.WILD -> Color(0xFFFF9800)
    }

    val animatedCircleColor by animateColorAsState(
        targetValue = targetCircleColor,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "circle_color_anim"
    )

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF009EB3), Color(0xFF00758A), Color(0xFF004D5C)),
                    radius = 1400f
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("game_arena_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ========================================================
            // 1. TOP BAR (Back, Sound, Music, Active Color Pill) - Fixed Height
            // ========================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left controls: Back button, Sound, Music
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Yellow Squircle Back Button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .shadow(4.dp, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .background(UnoYellow)
                            .border(1.5.dp, Color(0xFFD49E00), RoundedCornerShape(12.dp))
                            .clickable { showPauseMenu = true }
                            .testTag("arena_back_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Exit / Pause",
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Sound Toggle Button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF004D5C).copy(alpha = 0.85f))
                            .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                            .clickable(onClick = onToggleSound),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Sound",
                            tint = if (isMuted) Color.White.copy(alpha = 0.5f) else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Music Toggle Button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF004D5C).copy(alpha = 0.85f))
                            .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                            .clickable(onClick = onToggleMusic),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isMusicOn) Icons.Default.MusicNote else Icons.Default.MusicOff,
                            contentDescription = "Music",
                            tint = if (isMusicOn) UnoYellow else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Center-Right: Active Color Pill Indicator (NO COINS)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .border(2.dp, activeColorHex, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(activeColorHex)
                            .border(1.dp, Color.White, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = gameState.activeColor.displayName.uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp
                    )
                }
            }

            // Bot takeover notification banner if active
            if (gameState.isBotTakeover) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF8B2500))
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🤖 Player offline - Bot playing",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp
                    )
                }
            }

            // ========================================================
            // 2. OPPONENT SECTION (Top-Left) - Fixed Height to prevent shifting
            // ========================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    // Reserved fixed 24dp slot so avatar position NEVER jumps when turn alternates
                    Box(
                        modifier = Modifier.height(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!isLocalTurn) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(countdownColor)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "⏱️ ${secondsRemaining}s",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(2.dp))

                    Box(contentAlignment = Alignment.BottomEnd) {
                        // Square Avatar Box with countdown highlight border
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(UnoRed)
                                .border(
                                    width = if (!isLocalTurn) 3.dp else 1.5.dp,
                                    color = if (!isLocalTurn) countdownColor else Color.White.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .then(
                                    if (!isLocalTurn) {
                                        Modifier.shadow(8.dp, RoundedCornerShape(18.dp), spotColor = countdownColor)
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(opponent.avatar, fontSize = 32.sp)
                        }

                        // Remaining cards count badge in bottom-right corner
                        Box(
                            modifier = Modifier
                                .offset(x = 6.dp, y = 6.dp)
                                .shadow(4.dp, RoundedCornerShape(6.dp))
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0072BC))
                                .border(1.dp, Color.White, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${opponent.hand.size}",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = opponent.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    if (opponent.unoCalled) {
                        Text(
                            text = "UNO!",
                            color = UnoGold,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp
                        )
                    }
                }

                // Opponent Emote if active (floating banner)
                opponentEmote?.let { em ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("${em.emoji} ${em.text}", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // ========================================================
            // 3. CENTER PLAY ARENA (Permanently anchored via weight(1f))
            // ========================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                // Glowing radial backdrop aura reflecting current card color
                Box(
                    modifier = Modifier
                        .size(195.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    animatedCircleColor.copy(alpha = 0.45f),
                                    animatedCircleColor.copy(alpha = 0.12f),
                                    Color.Transparent
                                ),
                                radius = 180f
                            ),
                            CircleShape
                        )
                )

                // Glowing Turn Direction Ring with Rotation Arrows tinted to current card color
                Box(
                    modifier = Modifier
                        .size(175.dp)
                        .rotate(directionRotation),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(170.dp)
                            .background(Color(0xFF071820).copy(alpha = 0.65f), CircleShape)
                            .border(4.dp, animatedCircleColor, CircleShape)
                            .shadow(10.dp, CircleShape, spotColor = animatedCircleColor)
                    )

                    Text(
                        text = "➔",
                        color = animatedCircleColor,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-2).dp)
                            .rotate(45f)
                    )
                    Text(
                        text = "➔",
                        color = animatedCircleColor,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = (-4).dp, y = 2.dp)
                            .rotate(225f)
                    )
                }

                // Currently Played Top Card
                topCard?.let { card ->
                    UnoCardView(
                        card = card,
                        width = 76.dp,
                        height = 112.dp,
                        elevation = 8.dp,
                        rotation = 2f
                    )
                }
            }

            // ========================================================
            // 4. MID ROW: Draw Deck (Left) & Local Avatar (Right) - Fixed Height
            // ========================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Left: Draw Deck Card Bundle
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UnoCardView(
                        isBack = true,
                        width = 68.dp,
                        height = 100.dp,
                        elevation = 8.dp,
                        onClick = if (isLocalTurn) onDrawCard else null,
                        modifier = Modifier.testTag("draw_pile_deck")
                    )

                    // Skip Turn button (appears next to draw deck if 1 card drawn and player passes)
                    if (isLocalTurn && gameState.hasDrawnThisTurn) {
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = onSkipTurn,
                            colors = ButtonDefaults.buttonColors(containerColor = UnoRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(44.dp)
                                .shadow(6.dp, RoundedCornerShape(12.dp))
                                .border(1.5.dp, Color.White, RoundedCornerShape(12.dp))
                                .testTag("skip_turn_button")
                        ) {
                            Text("SKIP ⏭️", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                    }
                }

                // Center Action: Call UNO / Catch UNO
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (canCallUno) {
                        Button(
                            onClick = onCallUno,
                            colors = ButtonDefaults.buttonColors(containerColor = UnoYellow),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(44.dp)
                                .shadow(8.dp, RoundedCornerShape(14.dp))
                                .border(2.dp, Color.White, RoundedCornerShape(14.dp))
                                .testTag("call_uno_button")
                        ) {
                            Text("📢 CALL UNO!", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 13.sp)
                        }
                    }

                    if (canCatchOpponent) {
                        Button(
                            onClick = onCatchUno,
                            colors = ButtonDefaults.buttonColors(containerColor = UnoRed),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(44.dp)
                                .shadow(8.dp, RoundedCornerShape(14.dp))
                                .border(2.dp, UnoGold, RoundedCornerShape(14.dp))
                                .testTag("catch_uno_button")
                        ) {
                            Text("🚨 CATCH!", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                        }
                    }
                }

                // Right: Local Player Avatar with dedicated fixed 24dp timer slot
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Floating Emote Toggle Button
                    IconButton(
                        onClick = { showEmotePicker = !showEmotePicker },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                            .testTag("emote_button")
                    ) {
                        Text("💬", fontSize = 16.sp)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Dedicated fixed 24dp countdown container
                        Box(
                            modifier = Modifier.height(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLocalTurn) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(countdownColor)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "⏱️ ${secondsRemaining}s",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(2.dp))

                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(UnoRed)
                                .border(
                                    width = if (isLocalTurn) 3.dp else 1.5.dp,
                                    color = if (isLocalTurn) countdownColor else Color.White.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .then(
                                    if (isLocalTurn) {
                                        Modifier.shadow(8.dp, RoundedCornerShape(18.dp), spotColor = countdownColor)
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(localPlayer.avatar, fontSize = 32.sp)
                        }

                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = localPlayer.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Active Emote Bubble for Local Player (Fixed 24dp slot)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                localEmote?.let { em ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text("${em.emoji} ${em.text}", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }

            // ========================================================
            // 5. LOCAL PLAYER HAND CARDS (Bottom row) - Fixed Height 122dp
            // ========================================================
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(122.dp)
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                val hand = localPlayer.hand
                val cardCount = hand.size

                if (cardCount > 0) {
                    val cardWidth = 66.dp
                    val cardHeight = 104.dp
                    val containerWidth = maxWidth - 16.dp

                    // Dynamic step calculation to prevent overflow
                    val step = if (cardCount == 1) {
                        0.dp
                    } else {
                        val maxStep = 56.dp
                        val calculatedStep = (containerWidth - cardWidth) / (cardCount - 1)
                        if (calculatedStep < maxStep) calculatedStep else maxStep
                    }

                    val totalSpan = cardWidth + (step * (cardCount - 1))
                    val startOffset = (cardWidth / 2) - (totalSpan / 2)

                    hand.forEachIndexed { index, card ->
                        val isShaking = card.id == shakingCardId
                        val isAnimatingOut = card.id == animatingCardId
                        val xOffset = startOffset + (step * index)

                        Box(
                            modifier = Modifier
                                .offset(x = xOffset)
                                .align(Alignment.BottomCenter)
                        ) {
                            UnoCardView(
                                card = card,
                                width = cardWidth,
                                height = cardHeight,
                                isShaking = isShaking,
                                elevation = 8.dp,
                                modifier = Modifier.alpha(if (isAnimatingOut) 0f else 1f),
                                onClick = {
                                    if (card.color == CardColor.WILD || card.value.isWild) {
                                        pendingWildCardId = card.id
                                        pendingWildCardXOffset = xOffset.value
                                    } else {
                                        onPlayCard(card.id, null, xOffset.value)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // ========================================================
        // FLOATING EMOTE PICKER (Overlays above cards without shifting layout)
        // ========================================================
        if (showEmotePicker) {
            val emotes = listOf(
                Pair("👋", "Hello!"),
                Pair("🔥", "UNO coming!"),
                Pair("😱", "No way!"),
                Pair("😎", "Too easy!"),
                Pair("🤔", "Thinking..."),
                Pair("💥", "Boom!")
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { showEmotePicker = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 126.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.92f))
                        .border(1.5.dp, UnoGold, RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        emotes.forEach { (emoji, label) ->
                            Text(
                                text = emoji,
                                fontSize = 24.sp,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        onSendEmote(emoji, label)
                                        showEmotePicker = false
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }
        }

        // ========================================================
        // ROOM WAITING OVERLAY (Shows 5-digit code, waiting radar, and instant test join)
        // ========================================================
        if (gameState.isRoomWaiting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2630)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(UnoGold, UnoYellow)))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Title
                        Text(
                            text = "ROOM CREATED",
                            color = UnoGold,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp
                        )

                        Text(
                            text = "Share this code with your friend to play!",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )

                        // 4-Digit Code display
                        val code = gameState.roomCode ?: "1234"
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            code.forEach { ch ->
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .border(2.dp, UnoGold, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = ch.toString(),
                                        color = UnoYellow,
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }

                        // Copy Code Button
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(code))
                                Toast.makeText(context, "Room Code $code copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(UnoYellow, UnoGold))),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("COPY CODE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Divider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 4.dp))

                        // Animated Waiting Radar Indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = UnoYellow,
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                text = "Waiting for player to join with code...",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        // Cancel / Leave Button
                        TextButton(
                            onClick = onLeaveGame,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.6f))
                        ) {
                            Text("Cancel & Leave Room", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // ========================================================
        // FLYING CARD OVERLAY (Animated card flight on legal play)
        // ========================================================
        FlyingCardOverlay(
            animation = flightAnimation,
            onAnimationEnd = onClearFlightAnimation
        )

        // ========================================================
        // MODALS
        // ========================================================
        // Color Picker Modal for Wilds
        if (pendingWildCardId != null) {
            ColorPickerModal(
                onSelectColor = { chosen ->
                    pendingWildCardId?.let { cid ->
                        onPlayCard(cid, chosen, pendingWildCardXOffset)
                    }
                    pendingWildCardId = null
                },
                onDismiss = { pendingWildCardId = null }
            )
        }

        // Pause Menu Modal (Triggered by button or automatically when screen turns off / app backgrounded)
        if (showPauseMenu || isGamePaused) {
            PauseMenuModal(
                onSoundToggle = onToggleSound,
                isMuted = isMuted,
                onResume = {
                    showPauseMenu = false
                    onResumeFromPause()
                },
                onRestart = {
                    showPauseMenu = false
                    onResumeFromPause()
                    onRematch()
                },
                onQuit = {
                    showPauseMenu = false
                    onResumeFromPause()
                    onLeaveGame()
                }
            )
        }

        // Victory Modal
        if (gameState.status == GameStatus.GAMEOVER) {
            val winner = gameState.players.find { it.id == gameState.winnerId }
            val isLocalWinner = winner?.id == localPlayer.id
            VictoryModal(
                winner = winner,
                isWinnerLocal = isLocalWinner,
                opponentCardsLeft = opponent.hand.size,
                onRematch = onRematch,
                onHome = onLeaveGame
            )
        }
    }
}
