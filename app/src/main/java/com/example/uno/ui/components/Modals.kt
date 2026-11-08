package com.example.uno.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.uno.model.*
import com.example.uno.ui.theme.*

// ---------------------------------------------------------------------------
// COLOR PICKER MODAL (Wild / Wild+4)
// ---------------------------------------------------------------------------
@Composable
fun ColorPickerModal(
    onSelectColor: (CardColor) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(2.dp, UnoGold, RoundedCornerShape(24.dp))
                .testTag("color_picker_modal")
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "CHOOSE COLOR",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Select the active color for play",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(20.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ColorChoiceButton(
                            colorName = "RED",
                            color = UnoRed,
                            modifier = Modifier.weight(1f).testTag("select_color_red"),
                            onClick = { onSelectColor(CardColor.RED) }
                        )
                        ColorChoiceButton(
                            colorName = "BLUE",
                            color = UnoBlue,
                            modifier = Modifier.weight(1f).testTag("select_color_blue"),
                            onClick = { onSelectColor(CardColor.BLUE) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ColorChoiceButton(
                            colorName = "YELLOW",
                            color = UnoYellow,
                            modifier = Modifier.weight(1f).testTag("select_color_yellow"),
                            textColor = Color.Black,
                            onClick = { onSelectColor(CardColor.YELLOW) }
                        )
                        ColorChoiceButton(
                            colorName = "GREEN",
                            color = UnoGreen,
                            modifier = Modifier.weight(1f).testTag("select_color_green"),
                            onClick = { onSelectColor(CardColor.GREEN) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorChoiceButton(
    colorName: String,
    color: Color,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color)
            .border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = colorName,
            color = textColor,
            fontWeight = FontWeight.Black,
            fontSize = 16.sp
        )
    }
}

// ---------------------------------------------------------------------------
// CUSTOM HOUSE RULES MODAL
// ---------------------------------------------------------------------------
@Composable
fun CustomRulesModal(
    rules: GameRules,
    onSaveRules: (GameRules) -> Unit,
    onDismiss: () -> Unit
) {
    var stackDraw by remember { mutableStateOf(rules.stackDrawCards) }
    var drawToMatch by remember { mutableStateOf(rules.drawToMatch) }
    var reverseAsSkip by remember { mutableStateOf(rules.reverseAsSkipInTwoPlayer) }
    var sevenZero by remember { mutableStateOf(rules.sevenZeroRule) }
    var jumpIn by remember { mutableStateOf(rules.jumpIn) }
    var penaltyCards by remember { mutableStateOf(rules.unoPenaltyCards) }
    var difficulty by remember { mutableStateOf(rules.aiDifficulty) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
                .border(2.dp, UnoGold, RoundedCornerShape(24.dp))
                .testTag("custom_rules_modal")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "CUSTOM HOUSE RULES",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp
                )
                Spacer(Modifier.height(16.dp))

                RuleToggleItem(
                    title = "Stack Draw Cards (+2 on +2)",
                    subtitle = "Stack +2 or +4 to pass drawing penalty to opponent",
                    checked = stackDraw,
                    onCheckedChange = { stackDraw = it }
                )
                RuleToggleItem(
                    title = "2-Player Reverse Skips Turn",
                    subtitle = "In 2-player UNO, Reverse skips opponent giving another turn",
                    checked = reverseAsSkip,
                    onCheckedChange = { reverseAsSkip = it }
                )
                RuleToggleItem(
                    title = "7-0 Hand Swap Rule",
                    subtitle = "Playing 7 swaps entire hands with your opponent",
                    checked = sevenZero,
                    onCheckedChange = { sevenZero = it }
                )
                RuleToggleItem(
                    title = "Draw-to-Match Mode",
                    subtitle = "Keep drawing from deck until a playable card is found",
                    checked = drawToMatch,
                    onCheckedChange = { drawToMatch = it }
                )
                RuleToggleItem(
                    title = "Jump-In Rule",
                    subtitle = "Play identical color & value anytime out of turn",
                    checked = jumpIn,
                    onCheckedChange = { jumpIn = it }
                )

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("UNO Miss Penalty Cards", color = Color.White, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (penaltyCards > 1) penaltyCards-- },
                            enabled = penaltyCards > 1
                        ) {
                            Icon(Icons.Default.RemoveCircle, "Minus", tint = UnoYellow)
                        }
                        Text(
                            "$penaltyCards",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        IconButton(
                            onClick = { if (penaltyCards < 4) penaltyCards++ },
                            enabled = penaltyCards < 4
                        ) {
                            Icon(Icons.Default.AddCircle, "Plus", tint = UnoYellow)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        onSaveRules(
                            rules.copy(
                                stackDrawCards = stackDraw,
                                drawToMatch = drawToMatch,
                                reverseAsSkipInTwoPlayer = reverseAsSkip,
                                sevenZeroRule = sevenZero,
                                jumpIn = jumpIn,
                                unoPenaltyCards = penaltyCards,
                                aiDifficulty = difficulty
                            )
                        )
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = UnoYellow),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_rules_button")
                ) {
                    Text("SAVE HOUSE RULES", color = Color.Black, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun RuleToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = UnoGold,
                checkedTrackColor = UnoGold.copy(alpha = 0.4f),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.DarkGray
            )
        )
    }
}

// ---------------------------------------------------------------------------
// HOW TO PLAY MODAL
// ---------------------------------------------------------------------------
@Composable
fun HowToPlayModal(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
                .border(2.dp, UnoGold, RoundedCornerShape(24.dp))
                .testTag("how_to_play_modal")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "HOW TO PLAY UNO ROYALE",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )
                Spacer(Modifier.height(14.dp))

                HowToPlaySection("🎯 OBJECTIVE", "Be the first duelist to get rid of all 7 cards in your hand.")
                HowToPlaySection(
                    "🎴 BASIC PLAY",
                    "Match the top card by color or number/action symbol. Play Wild cards anytime to change the color."
                )
                HowToPlaySection(
                    "⚡ ACTION CARDS",
                    "• Skip (⊘): Skips opponent, gives you another turn!\n" +
                    "• Reverse (⇄): Reverses order (gives you another turn in 2-player!)\n" +
                    "• Draw Two (+2): Forces opponent to draw 2 cards and skips them\n" +
                    "• Wild (+4): Choose new color and force opponent to draw 4 cards!"
                )
                HowToPlaySection(
                    "📢 CALLING UNO",
                    "When down to 1 card, press CALL UNO immediately! If your opponent catches you before you call it, you suffer penalty cards!"
                )

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = UnoYellow),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("GOT IT, LET'S PLAY!", color = Color.Black, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun HowToPlaySection(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(title, color = UnoGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(body, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 16.sp)
    }
}

// ---------------------------------------------------------------------------
// PROFILE SETUP MODAL
// ---------------------------------------------------------------------------
@Composable
fun ProfileSetupModal(
    currentProfile: UserProfile,
    onSaveProfile: (UserProfile) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentProfile.name) }
    var avatar by remember { mutableStateOf(currentProfile.avatar) }
    val avatarOptions = listOf("🧔", "👑", "🦊", "🐯", "🐼", "🚀", "🔥", "🦄", "🤠", "🤖")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(2.dp, UnoGold, RoundedCornerShape(24.dp))
                .testTag("profile_modal")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("YOUR DUELIST PROFILE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(UnoGold)
                        .border(3.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(avatar, fontSize = 42.sp)
                }
                Spacer(Modifier.height(12.dp))

                Text("Pick an Avatar", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    avatarOptions.take(5).forEach { em ->
                        Text(
                            text = em,
                            fontSize = 24.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { avatar = em }
                                .padding(4.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    avatarOptions.drop(5).forEach { em ->
                        Text(
                            text = em,
                            fontSize = 24.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { avatar = em }
                                .padding(4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Duelist Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = UnoGold,
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = UnoGold
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("profile_name_input")
                )

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        onSaveProfile(currentProfile.copy(name = name.ifBlank { "Kannu" }, avatar = avatar))
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = UnoYellow),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_profile_button")
                ) {
                    Text("SAVE PROFILE", color = Color.Black, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// SURPRISE DAILY GIFT MODAL
// ---------------------------------------------------------------------------
@Composable
fun SurpriseGiftModal(
    onDismiss: () -> Unit
) {
    var isOpened by remember { mutableStateOf(false) }
    val chestScale by animateFloatAsState(
        targetValue = if (isOpened) 1.25f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "chest_scale"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(2.dp, UnoGold, RoundedCornerShape(24.dp))
                .testTag("surprise_gift_modal")
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isOpened) "🎁 REWARD CLAIMED!" else "🎁 DAILY SURPRISE GIFT",
                    color = UnoGold,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp
                )
                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(chestScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(listOf(UnoGold.copy(alpha = 0.4f), Color.Transparent))
                        )
                        .clickable(enabled = !isOpened) { isOpened = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isOpened) "🎉" else "🎁", fontSize = 64.sp)
                }

                Spacer(Modifier.height(16.dp))
                if (!isOpened) {
                    Text(
                        "Tap the gift chest to reveal your daily reward!",
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp
                    )
                } else {
                    Text(
                        "+500 Royale Stars & Lucky Crown Unlocked!",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontSize = 15.sp
                    )
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        if (!isOpened) isOpened = true else onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = UnoYellow),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(if (isOpened) "AWESOME!" else "OPEN GIFT", color = Color.Black, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// VICTORY / GAMEOVER MODAL (High-Energy Arcade Victory & Defeat)
// ---------------------------------------------------------------------------
@Composable
fun VictoryModal(
    winner: Player?,
    isWinnerLocal: Boolean,
    opponentCardsLeft: Int = 3,
    onRematch: () -> Unit,
    onHome: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "victory_anim")
    val rayRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ray_rot"
    )

    val trophyPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "trophy_pulse"
    )

    Dialog(onDismissRequest = onHome) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101010)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .border(
                    3.dp,
                    Brush.linearGradient(
                        listOf(if (isWinnerLocal) UnoGold else UnoRed, Color.White, if (isWinnerLocal) UnoYellow else Color(0xFF9E0E00))
                    ),
                    RoundedCornerShape(28.dp)
                )
                .testTag("victory_modal")
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Background subtle glow
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .align(Alignment.TopCenter)
                        .rotate(rayRotation)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    if (isWinnerLocal) UnoGold.copy(alpha = 0.25f) else UnoRed.copy(alpha = 0.25f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Title Banner
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isWinnerLocal) UnoGold else UnoRed)
                            .padding(horizontal = 18.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isWinnerLocal) "👑 VICTORY ROYALE!" else "💀 MATCH DEFEAT",
                            color = if (isWinnerLocal) Color.Black else Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    // Animated Icon
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .scale(trophyPulse)
                            .clip(CircleShape)
                            .background(
                                if (isWinnerLocal)
                                    Brush.radialGradient(listOf(UnoGold.copy(alpha = 0.4f), Color(0xFF222222)))
                                else
                                    Brush.radialGradient(listOf(UnoRed.copy(alpha = 0.4f), Color(0xFF222222)))
                            )
                            .border(2.dp, if (isWinnerLocal) UnoGold else UnoRed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (isWinnerLocal) "🏆" else "💔", fontSize = 58.sp)
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = if (isWinnerLocal) "YOU WON THE DUEL!" else "${winner?.name ?: "Opponent"} Won!",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isWinnerLocal) "All cards cleared first! Brilliant strategy." else "All cards cleared by opponent. Rematch to reclaim honor!",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(18.dp))

                    // Stats Tally Cards (NO XP - clean stars and cards tally)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, if (isWinnerLocal) UnoGold.copy(alpha = 0.4f) else UnoRed.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("DUEL RESULT", color = if (isWinnerLocal) UnoGold else UnoRed, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                Spacer(Modifier.height(3.dp))
                                Text(if (isWinnerLocal) "+500 ⭐" else "0 ⭐", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("HAND REMAINING", color = UnoYellow, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                Spacer(Modifier.height(3.dp))
                                Text(if (isWinnerLocal) "0 Cards" else "$opponentCardsLeft Cards", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Buttons: Prominent REMATCH / PLAY AGAIN & MAIN MENU
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onRematch,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isWinnerLocal) UnoYellow else UnoGreen
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("rematch_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⚔️", fontSize = 18.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "PLAY AGAIN / REMATCH",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onHome,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("return_home_button")
                        ) {
                            Text("🏠 MAIN MENU", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// PAUSE MENU MODAL
// ---------------------------------------------------------------------------
@Composable
fun PauseMenuModal(
    onSoundToggle: () -> Unit,
    isMuted: Boolean,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onQuit: () -> Unit
) {
    Dialog(onDismissRequest = onResume) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(2.dp, UnoGold, RoundedCornerShape(24.dp))
                .testTag("pause_menu_modal")
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("GAME PAUSED", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.buttonColors(containerColor = UnoYellow),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("resume_button")
                ) {
                    Text("RESUME GAME", color = Color.Black, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onSoundToggle,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, "Sound")
                    Spacer(Modifier.width(8.dp))
                    Text(if (isMuted) "SOUND: MUTED" else "SOUND: ON", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onRestart,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("RESTART DUEL", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onQuit,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = UnoRed),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("quit_game_button")
                ) {
                    Text("QUIT TO MENU", color = UnoRed, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// FIRST TIME ONBOARDING MODAL (User creates name and avatar once per device)
// ---------------------------------------------------------------------------
@Composable
fun FirstTimeOnboardingModal(
    onComplete: (String, String) -> Unit
) {
    var nameInput by remember { mutableStateOf("") }
    var selectedAvatar by remember { mutableStateOf("🧔") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val avatars = listOf("🧔", "👩", "🦊", "🦁", "🤖", "👑", "🧙‍♂️", "🥷", "👸", "🐱", "🐶", "🐼")

    Dialog(
        onDismissRequest = { /* Cannot dismiss without onboarding */ },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .border(2.5.dp, UnoGold, RoundedCornerShape(26.dp))
                .testTag("first_time_onboarding_modal")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "WELCOME TO UNO ROYALE!",
                    color = UnoGold,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Create your duelist profile to get started",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(18.dp))

                // Avatar Large Preview
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF242424))
                        .border(3.dp, UnoGold, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(selectedAvatar, fontSize = 42.sp)
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "CHOOSE YOUR AVATAR",
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))

                // Avatar Grid (2 rows of 6)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        avatars.take(6).forEach { av ->
                            val isSelected = av == selectedAvatar
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) UnoGold else Color.Black.copy(alpha = 0.5f))
                                    .border(
                                        if (isSelected) 2.dp else 1.dp,
                                        if (isSelected) Color.White else Color.White.copy(alpha = 0.2f),
                                        CircleShape
                                    )
                                    .clickable { selectedAvatar = av },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(av, fontSize = 22.sp)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        avatars.drop(6).forEach { av ->
                            val isSelected = av == selectedAvatar
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) UnoGold else Color.Black.copy(alpha = 0.5f))
                                    .border(
                                        if (isSelected) 2.dp else 1.dp,
                                        if (isSelected) Color.White else Color.White.copy(alpha = 0.2f),
                                        CircleShape
                                    )
                                    .clickable { selectedAvatar = av },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(av, fontSize = 22.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Name Input
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = {
                        if (it.length <= 15) {
                            nameInput = it
                            errorText = null
                        }
                    },
                    label = { Text("Your Duelist Name") },
                    placeholder = { Text("e.g. Kannu") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = UnoGold,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_name_input")
                )

                errorText?.let { err ->
                    Spacer(Modifier.height(4.dp))
                    Text(err, color = UnoRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        val trimmed = nameInput.trim()
                        if (trimmed.isEmpty()) {
                            errorText = "Please enter your name"
                        } else {
                            onComplete(trimmed, selectedAvatar)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = UnoYellow),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("onboarding_submit_button")
                ) {
                    Text("SAVE & START DUELING", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 15.sp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// LOADING OVERLAY (Shown when setting table or connecting)
// ---------------------------------------------------------------------------
@Composable
fun LoadingOverlay(
    message: String = "Loading..."
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading_anim")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .rotate(rotation)
                    .clip(CircleShape)
                    .border(3.5.dp, Brush.sweepGradient(listOf(UnoYellow, UnoRed, UnoBlue, UnoGreen, UnoYellow)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("🃏", fontSize = 28.sp)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = message,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
