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
// HOME SCREEN BOTTOM BANNER AD
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

