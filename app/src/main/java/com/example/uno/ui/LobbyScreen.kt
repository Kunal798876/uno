package com.example.uno.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uno.model.GameRules
import com.example.uno.model.UserProfile
import com.example.uno.ui.theme.*
import kotlin.random.Random

@Composable
fun LobbyScreen(
    profile: UserProfile,
    rules: GameRules,
    onStartGame: (String, Boolean) -> Boolean,
    onBack: () -> Unit
) {
    BackHandler {
        onBack()
    }

    val context = LocalContext.current
    var isHostTab by remember { mutableStateOf(true) }

    // Exactly 5 digits room code
    val generatedRoomCode = remember { (10000..99999).random().toString() }
    var joinRoomCodeInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var copiedToast by remember { mutableStateOf(false) }

    // Pulsing rotation for waiting animation
    val infiniteTransition = rememberInfiniteTransition(label = "waiting_hourglass")
    val hourglassRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rot"
    )

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
            .testTag("lobby_screen")
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
                // Header Bar with Back Button
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
                            .testTag("lobby_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "CUSTOM ROOM",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "Play with friends (2 Players Only)",
                            color = UnoGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Host Room / Join Room Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isHostTab) UnoYellow else Color.Transparent)
                            .clickable {
                                isHostTab = true
                                errorMessage = null
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "CREATE ROOM",
                            color = if (isHostTab) Color.Black else Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (!isHostTab) UnoYellow else Color.Transparent)
                            .clickable {
                                isHostTab = false
                                errorMessage = null
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "JOIN ROOM",
                            color = if (!isHostTab) Color.Black else Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (isHostTab) {
                    // HOST ROOM VIEW: 5-digit code & waiting for player to join
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.65f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, UnoGold, RoundedCornerShape(18.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "5-DIGIT ROOM CODE",
                                color = Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                letterSpacing = 1.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = generatedRoomCode,
                                    color = UnoGold,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 36.sp,
                                    letterSpacing = 4.sp
                                )
                                Spacer(Modifier.width(12.dp))
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val clip = ClipData.newPlainText("UNO Room Code", generatedRoomCode)
                                        clipboard?.setPrimaryClip(clip)
                                        copiedToast = true
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(UnoGold.copy(alpha = 0.2f))
                                        .border(1.dp, UnoGold, CircleShape)
                                ) {
                                    Icon(Icons.Default.ContentCopy, "Copy Code", tint = UnoGold, modifier = Modifier.size(18.dp))
                                }
                            }

                            if (copiedToast) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Copied 5-digit code to clipboard!",
                                    color = UnoGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Share this 5-digit code with your friend to connect.",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 11.sp
                            )
                        }
                    }
                } else {
                    // JOIN ROOM VIEW: Enter 5-digit room code
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.65f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, UnoBlue, RoundedCornerShape(18.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "ENTER 5-DIGIT CODE",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = joinRoomCodeInput,
                                onValueChange = {
                                    if (it.length <= 5 && it.all { char -> char.isDigit() }) {
                                        joinRoomCodeInput = it
                                        errorMessage = null
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = LocalTextStyle.current.copy(
                                    color = UnoGold,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 24.sp,
                                    letterSpacing = 4.sp
                                ),
                                placeholder = {
                                    Text("XXXXX", color = Color.White.copy(alpha = 0.3f), fontSize = 24.sp)
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = UnoGold,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("join_room_input")
                            )

                            errorMessage?.let { err ->
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = err,
                                    color = UnoRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ONLY HOST AND PLAYER 2 VISIBLE (BOT STRICTLY HIDDEN)
                Text(
                    text = "PLAYERS IN ROOM (2 MAX)",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(8.dp))

                // Host Card (Player 1)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, UnoGold, RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isHostTab) profile.avatar else "👑", fontSize = 28.sp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (isHostTab) profile.name else "Room Host",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(UnoGold)
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text("HOST", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 9.sp)
                                    }
                                }
                                Text("Ready to duel", color = UnoGreen, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Icon(Icons.Default.CheckCircle, "Ready", tint = UnoGreen)
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Player 2 Card (Friend / Guest - Bot is hidden!)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (!isHostTab) profile.avatar else "👤", fontSize = 28.sp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (!isHostTab) profile.name else "Waiting for Player 2...",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = if (!isHostTab) "Connected" else "Waiting for player to join with code",
                                    color = if (!isHostTab) UnoGreen else UnoGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        if (isHostTab) {
                            Icon(
                                Icons.Default.HourglassTop,
                                "Waiting",
                                tint = UnoGold,
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(hourglassRotation)
                            )
                        } else {
                            Icon(Icons.Default.CheckCircle, "Ready", tint = UnoGreen)
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Rules Notice
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("CUSTOM ROOM RULES", color = UnoGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "• Room closes permanently after 1 game (cannot be rejoined with old code)\n" +
                            "• If a player disconnects, AI bot takes over with notification\n" +
                            "• If both players leave, room terminates completely\n" +
                            "• 20s turn timer with 1 card penalty if time runs out",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Start Game / Connect Button
            Button(
                onClick = {
                    val codeToUse = if (isHostTab) generatedRoomCode else joinRoomCodeInput
                    if (!isHostTab && codeToUse.length != 5) {
                        errorMessage = "Please enter a valid 5-digit code"
                        return@Button
                    }
                    val success = onStartGame(codeToUse, isHostTab)
                    if (!success) {
                        errorMessage = "This room has ended and is permanently closed. Please create a new room."
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = UnoYellow),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("start_custom_room_button")
            ) {
                Text(
                    text = if (isHostTab) "START MATCH NOW" else "JOIN & START DUEL",
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
            }
        }
    }
}
