package com.example.uno.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.uno.model.Card
import com.example.uno.model.CardFlightAnimation
import com.example.uno.ui.theme.*
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------
// FLYING CARD ANIMATION OVERLAY
// ---------------------------------------------------------------------------
@Composable
fun FlyingCardOverlay(
    animation: CardFlightAnimation?,
    onAnimationEnd: () -> Unit
) {
    if (animation == null) return

    val progress = remember(animation.id) { Animatable(0f) }

    LaunchedEffect(animation.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = animation.durationMs,
                easing = FastOutSlowInEasing
            )
        )
        onAnimationEnd()
    }

    val currentProgress = progress.value
    val currentX = animation.startOffsetX + (animation.targetOffsetX - animation.startOffsetX) * currentProgress
    // Subtle upward aerodynamic curve for realistic flight
    val arcPeak = if (animation.type == "attack") -40f else -25f
    val arc = kotlin.math.sin(currentProgress * Math.PI.toFloat()) * arcPeak
    val currentY = (animation.startOffsetY + (animation.targetOffsetY - animation.startOffsetY) * currentProgress) + arc

    val scale = 0.95f + (kotlin.math.sin(currentProgress * Math.PI.toFloat()) * 0.12f)
    val rotation = (currentProgress * 24f) - 12f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center)
            .offset(x = currentX.dp, y = currentY.dp)
            .scale(scale)
            .rotate(if (animation.type == "play") rotation else (rotation * 0.5f))
            .testTag("flying_card_anim")
    ) {
        if (animation.count > 1) {
            // Visible stack of 2 or 4 cards flying for +2 / +4 attacks
            repeat(animation.count) { idx ->
                Box(modifier = Modifier.offset(x = (idx * 10 - (animation.count * 5)).dp, y = (idx * 4).dp)) {
                    UnoCardView(
                        isBack = true,
                        width = 68.dp,
                        height = 100.dp,
                        elevation = (8 + idx * 2).dp
                    )
                }
            }
        } else {
            if (animation.isBack || animation.card == null) {
                UnoCardView(isBack = true, width = 68.dp, height = 100.dp, elevation = 10.dp)
            } else {
                UnoCardView(card = animation.card, width = 68.dp, height = 100.dp, elevation = 10.dp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// HOME SCREEN BOTTOM BANNER AD (Standard Start.io Banner)
// ---------------------------------------------------------------------------
@Composable
fun HomeBottomTestBannerAd(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF202124))
            .border(1.dp, Color(0xFF5F6368), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("home_bottom_test_banner_ad"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Ad Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(UnoGold)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Ad",
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Test Ad: Google Mobile Ads",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "320x50 Standard Banner • Test AdUnit",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 10.sp
                    )
                }
            }
            Icon(
                Icons.Default.Info,
                contentDescription = "Ad Info",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// DIRECT REWARDED VIDEO TEST AD OVERLAY (Direct on Gift click, no waiting)
// ---------------------------------------------------------------------------
@Composable
fun TestAdFullscreenOverlay(
    onRewardEarned: () -> Unit,
    onDismiss: () -> Unit
) {
    var secondsLeft by remember { mutableStateOf(5) }
    val progress = (5 - secondsLeft) / 5f

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        }
        onRewardEarned()
    }

    Dialog(
        onDismissRequest = {
            if (secondsLeft == 0) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("test_ad_fullscreen_overlay")
        ) {
            // Ad Content Simulation
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Ad Bar with Badge & Timer / Close Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(UnoGold)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Test Ad", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Google Mobile Ads • Rewarded Video",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }

                    if (secondsLeft > 0) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "Reward in $secondsLeft",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.3f))
                                .testTag("close_test_ad_button")
                        ) {
                            Icon(Icons.Default.Close, "Close Ad", tint = Color.White)
                        }
                    }
                }

                // Middle: Visual Promo Content
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(UnoGold, UnoRed)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("👑", fontSize = 54.sp)
                    }

                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "UNO ROYALE DUELS",
                        color = UnoYellow,
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "The ultimate 2-player arcade card game!\nClaim your Daily Gift stars upon ad completion.",
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                }

                // Bottom: Progress & CTA
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = UnoGold,
                        trackColor = Color.DarkGray
                    )

                    Spacer(Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (secondsLeft == 0) onDismiss()
                        },
                        enabled = secondsLeft == 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UnoYellow,
                            disabledContainerColor = Color.DarkGray
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("claim_ad_reward_button")
                    ) {
                        Text(
                            text = if (secondsLeft == 0) "CLAIM DAILY REWARD (+500 STARS)" else "WATCHING TEST AD...",
                            color = if (secondsLeft == 0) Color.Black else Color.White.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
