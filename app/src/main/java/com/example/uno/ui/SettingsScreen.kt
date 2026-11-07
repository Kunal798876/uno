package com.example.uno.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uno.model.AiDifficulty
import com.example.uno.model.GameRules
import com.example.uno.ui.theme.*

@Composable
fun SettingsScreen(
    rules: GameRules,
    isMuted: Boolean,
    isMusicOn: Boolean,
    isNotificationsEnabled: Boolean = true,
    onToggleSound: () -> Unit,
    onToggleMusic: () -> Unit,
    onToggleNotifications: (Boolean) -> Unit = {},
    onUpdateRules: (GameRules) -> Unit,
    onResetStats: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler {
        onBack()
    }

    var showResetConfirm by remember { mutableStateOf(false) }

    // Launcher for runtime notification permission on Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onToggleNotifications(isGranted)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(UnoBgRed, UnoBgDark, Color(0xFF4A0000)),
                    radius = 1200f
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("settings_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            .testTag("settings_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "SETTINGS",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    )
                }

                Spacer(Modifier.height(24.dp))

                // NOTIFICATIONS & NETWORK SECTION
                Text(
                    text = "NOTIFICATIONS & NETWORK",
                    color = UnoGold,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(8.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Game & Daily Gift Alerts", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Daily chest rewards & rematch alerts", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                            }
                            Switch(
                                checked = isNotificationsEnabled,
                                onCheckedChange = { checked ->
                                    if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        onToggleNotifications(checked)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = UnoGold,
                                    checkedTrackColor = UnoGold.copy(alpha = 0.4f)
                                )
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.1f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Internet & Multiplayer Sync", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Real-time online match matchmaking ready", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                            }
                            Icon(Icons.Default.Wifi, "Internet Connected", tint = UnoGreen)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // AUDIO & HAPTICS SECTION
                Text(
                    text = "AUDIO & HAPTICS",
                    color = UnoGold,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(8.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Sound Effects & Haptics", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Card play, draw, buzzer, UNO fanfare", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                            }
                            Switch(
                                checked = !isMuted,
                                onCheckedChange = { onToggleSound() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = UnoGold,
                                    checkedTrackColor = UnoGold.copy(alpha = 0.4f)
                                )
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.1f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Background Music (BGM)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Arcade duel soundtrack", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                            }
                            Switch(
                                checked = isMusicOn,
                                onCheckedChange = { onToggleMusic() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = UnoGold,
                                    checkedTrackColor = UnoGold.copy(alpha = 0.4f)
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // AI DIFFICULTY SECTION
                Text(
                    text = "AI OPPONENT DIFFICULTY",
                    color = UnoGold,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(8.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AiDifficulty.values().forEach { diff ->
                                val isSelected = rules.aiDifficulty == diff
                                Button(
                                    onClick = {
                                        onUpdateRules(rules.copy(aiDifficulty = diff))
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) UnoYellow else Color.DarkGray
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = diff.displayName,
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // DATA MANAGEMENT
                Text(
                    text = "DATA MANAGEMENT",
                    color = UnoGold,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showResetConfirm = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = UnoRed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, "Reset", tint = UnoRed)
                    Spacer(Modifier.width(8.dp))
                    Text("RESET MATCH WIN/PLAYED STATS", color = UnoRed, fontWeight = FontWeight.Black)
                }
            }

            // Version info footer
            Column(
                modifier = Modifier.padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "UNO Royale: 2-Player Duels v1.0",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
                Text(
                    "Google Play Store Ready • Kotlin & Jetpack Compose",
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 10.sp
                )
            }
        }

        if (showResetConfirm) {
            AlertDialog(
                onDismissRequest = { showResetConfirm = false },
                title = { Text("Reset Stats?", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to reset your won and played games back to 0?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onResetStats()
                            showResetConfirm = false
                        }
                    ) {
                        Text("Reset", color = UnoRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
