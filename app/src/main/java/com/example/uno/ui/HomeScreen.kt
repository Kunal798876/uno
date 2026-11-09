package com.example.uno.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uno.R
import com.example.uno.ads.UnityBannerAd
import com.example.uno.model.AiDifficulty
import com.example.uno.model.UserProfile
import com.example.uno.ui.theme.*

@Composable
fun HomeScreen(
    profile: UserProfile,
    currentDifficulty: AiDifficulty,
    onSelectDifficulty: (AiDifficulty) -> Unit,
    onStartAiMatch: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenHowToPlay: () -> Unit,
    onOpenGift: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val scrollState = rememberScrollState()

    // Breathing glow animation for primary battle button
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_screen")
    ) {
        Image(
            painter = painterResource(id = R.drawable.bg_app_red),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 68.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // =========================================================================
            // TOP BAR: Player Profile Badge & Header Quick Actions
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Interactive User Profile Card
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.5.dp, UnoGold.copy(alpha = 0.8f), RoundedCornerShape(22.dp))
                        .clickable(onClick = onOpenProfile)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("profile_pill"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(UnoGold.copy(alpha = 0.2f))
                            .border(1.dp, UnoGold, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(profile.avatar, fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile.name,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Profile",
                            tint = UnoYellow,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                // Daily Chest & Settings Icons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onOpenGift,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0xFF8B6508), Color(0xFF261A00))
                                )
                            )
                            .border(1.5.dp, UnoGold, CircleShape)
                            .shadow(6.dp, CircleShape)
                            .testTag("daily_gift_button")
                    ) {
                        Text("🎁", fontSize = 20.sp)
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                            .testTag("settings_button")
                    ) {
                        Icon(Icons.Default.Settings, "Settings", tint = Color.White)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // =========================================================================
            // HERO BANNER: Uno Royale Arena Showcase
            // =========================================================================
            Card(
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .shadow(14.dp, RoundedCornerShape(22.dp))
                    .border(2.dp, UnoGold, RoundedCornerShape(22.dp))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = R.drawable.banner_uno_royale),
                        contentDescription = "UNO Royale Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // High-contrast gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.35f),
                                        Color.Black.copy(alpha = 0.88f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(UnoRed)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "OFFLINE 1v1",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(UnoYellow)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "108 CARDS",
                                    color = Color.Black,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "UNO ROYALE",
                            color = UnoYellow,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic,
                            fontSize = 24.sp,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "Lightning-Fast Card Duels vs Smart AI",
                            color = Color.White.copy(alpha = 0.95f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // =========================================================================
            // AI DIFFICULTY SELECTION
            // =========================================================================
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "SELECT AI OPPONENT",
                    color = UnoGold,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiDifficulty.values().forEach { diff ->
                        val isSelected = currentDifficulty == diff
                        val (label, avatar, sub) = when (diff) {
                            AiDifficulty.EASY -> Triple("CASUAL", "🤖", "Relaxed")
                            AiDifficulty.MEDIUM -> Triple("PRO", "👩", "Tactical")
                            AiDifficulty.HARD -> Triple("MASTER", "🧠", "Ruthless")
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) {
                                        Brush.verticalGradient(
                                            listOf(UnoYellow, Color(0xFFFF8F00))
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF262626), Color(0xFF141414))
                                        )
                                    }
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { onSelectDifficulty(diff) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(avatar, fontSize = 20.sp)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = sub,
                                    color = if (isSelected) Color.Black.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.5f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // =========================================================================
            // PRIMARY ACTION: QUICK PLAY VS AI DUEL BUTTON
            // =========================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .scale(pulseScale)
                    .shadow(16.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                UnoYellow,
                                Color(0xFFFFB300),
                                UnoGold
                            )
                        )
                    )
                    .border(2.5.dp, Color.White, RoundedCornerShape(20.dp))
                    .clickable(onClick = onStartAiMatch)
                    .testTag("quick_play_ai_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⚔️", fontSize = 24.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "PLAY VS AI DUEL",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Instant 1v1 Card Battle • Tap to Launch",
                            color = Color.Black.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // =========================================================================
            // SECONDARY ACTIONS: Custom House Rules & How To Play
            // =========================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenRules,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.45f),
                        contentColor = Color.White
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(
                            listOf(UnoGold.copy(alpha = 0.6f), UnoYellow.copy(alpha = 0.6f))
                        )
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("house_rules_button")
                ) {
                    Icon(
                        Icons.Default.Gavel,
                        contentDescription = "Rules",
                        tint = UnoGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "HOUSE RULES",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                OutlinedButton(
                    onClick = onOpenHowToPlay,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.45f),
                        contentColor = Color.White
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(
                            listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.35f))
                        )
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("how_to_play_button")
                ) {
                    Icon(
                        Icons.Default.HelpOutline,
                        contentDescription = "Help",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "HOW TO PLAY",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }

        // =========================================================================
        // BOTTOM DOCKED: Unity Ads Banner Container (BP_Banner_Android)
        // Pinned to the very bottom screen edge with navigation bar insets
        // =========================================================================
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            UnityBannerAd()
        }
    }
}
}

