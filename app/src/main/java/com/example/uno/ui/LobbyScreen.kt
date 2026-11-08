package com.example.uno.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.uno.model.GameRules
import com.example.uno.model.UserProfile
import com.example.uno.ui.theme.*

@Composable
fun LobbyScreen(
    profile: UserProfile,
    rules: GameRules,
    onCreateRoom: (String) -> Unit,
    onJoinRoom: (String, String, String) -> Boolean,
    onBack: () -> Unit
) {
    BackHandler {
        onBack()
    }

    var showJoinDialog by remember { mutableStateOf(false) }
    var joinRoomCodeInput by remember { mutableStateOf("") }
    var guestNameInput by remember { mutableStateOf("") }
    var selectedGuestAvatar by remember { mutableStateOf("🦊") }
    var joinError by remember { mutableStateOf<String?>(null) }

    val avatars = listOf("🦊", "🦁", "🐼", "👩", "🧔", "👑", "🦄", "⚡")

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
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
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
                            text = "PLAY WITH FRIENDS",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "Real-Time 2-Player Private Room Duel",
                            color = UnoGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Current Profile Summary Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, UnoGold.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(UnoGold.copy(alpha = 0.2f))
                                .border(1.5.dp, UnoGold, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(profile.avatar, fontSize = 28.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = profile.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp
                                )
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(UnoGold)
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text("YOU", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 9.sp)
                                }
                            }
                            Text(
                                text = "Ready to host or join room",
                                color = UnoGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // 2 PROMINENT BUTTONS: CREATE ROOM & JOIN ROOM (As requested)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // BUTTON 1: CREATE ROOM
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .shadow(12.dp, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(UnoYellow, Color(0xFFFFB300), UnoGold)
                                )
                            )
                            .border(2.dp, Color.White, RoundedCornerShape(20.dp))
                            .clickable {
                                // Generate 5-digit room code and immediately navigate host to game window
                                val newRoomCode = (10000..99999).random().toString()
                                onCreateRoom(newRoomCode)
                            }
                            .padding(horizontal = 18.dp)
                            .testTag("create_room_primary_button"),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("👑", fontSize = 28.sp)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "CREATE ROOM",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "Go to game table & wait for friend with code",
                                    color = Color.Black.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // BUTTON 2: JOIN ROOM
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .shadow(10.dp, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(UnoGreen, Color(0xFF007A38))
                                )
                            )
                            .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .clickable {
                                joinRoomCodeInput = ""
                                guestNameInput = ""
                                joinError = null
                                showJoinDialog = true
                            }
                            .padding(horizontal = 18.dp)
                            .testTag("join_room_primary_button"),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🔑", fontSize = 26.sp)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "JOIN ROOM",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "Enter 5-digit code to start duel immediately",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Custom Room Duel Info Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.People, "Duel Info", tint = UnoGold, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("HOW PRIVATE DUELS WORK", color = UnoGold, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "1. Tap 'CREATE ROOM' to enter the arena and receive your 5-digit code.\n" +
                                   "2. Share the code with your friend.\n" +
                                   "3. When your friend taps 'JOIN ROOM' and enters the code, the game starts instantly in real-time.\n" +
                                   "4. Both player names and avatars update dynamically on the board.",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        // JOIN ROOM MODAL DIALOG
        if (showJoinDialog) {
            Dialog(onDismissRequest = { showJoinDialog = false }) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .border(2.dp, UnoGold, RoundedCornerShape(24.dp))
                        .testTag("join_room_dialog")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "JOIN FRIEND'S ROOM",
                            color = UnoGold,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Enter the 5-digit code from the host",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )

                        Spacer(Modifier.height(16.dp))

                        // 5-digit Code Input
                        OutlinedTextField(
                            value = joinRoomCodeInput,
                            onValueChange = {
                                if (it.length <= 5 && it.all { char -> char.isDigit() }) {
                                    joinRoomCodeInput = it
                                    joinError = null
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = LocalTextStyle.current.copy(
                                color = UnoGold,
                                fontWeight = FontWeight.Black,
                                fontSize = 26.sp,
                                letterSpacing = 6.sp,
                                textAlign = TextAlign.Center
                            ),
                            placeholder = {
                                Text(
                                    "12345",
                                    color = Color.White.copy(alpha = 0.25f),
                                    fontSize = 24.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = UnoGold,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("room_code_input")
                        )

                        Spacer(Modifier.height(14.dp))

                        // Player 2 Name Input
                        OutlinedTextField(
                            value = guestNameInput,
                            onValueChange = {
                                if (it.length <= 14) guestNameInput = it
                            },
                            label = { Text("Your Player Name", color = Color.White.copy(alpha = 0.7f)) },
                            placeholder = { Text(profile.name, color = Color.White.copy(alpha = 0.3f)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = UnoGreen,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.35f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = "CHOOSE AVATAR",
                            color = Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            avatars.take(6).forEach { av ->
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (selectedGuestAvatar == av) UnoGold else Color.Black.copy(alpha = 0.5f))
                                        .border(
                                            1.5.dp,
                                            if (selectedGuestAvatar == av) Color.White else Color.White.copy(alpha = 0.25f),
                                            CircleShape
                                        )
                                        .clickable { selectedGuestAvatar = av },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(av, fontSize = 20.sp)
                                }
                            }
                        }

                        joinError?.let { err ->
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = err,
                                color = UnoRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showJoinDialog = false },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("CANCEL", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    if (joinRoomCodeInput.length != 5) {
                                        joinError = "Please enter all 5 digits of the room code"
                                        return@Button
                                    }
                                    val success = onJoinRoom(
                                        joinRoomCodeInput,
                                        guestNameInput.ifBlank { profile.name },
                                        selectedGuestAvatar
                                    )
                                    if (success) {
                                        showJoinDialog = false
                                    } else {
                                        joinError = "Room not found or closed. Check code with host."
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = UnoGreen),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("submit_join_button")
                            ) {
                                Text("START DUEL", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
