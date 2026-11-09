package com.example.uno.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import androidx.compose.animation.*
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.uno.model.GameRules
import com.example.uno.model.UserProfile
import com.example.uno.network.RoomInfo
import com.example.uno.network.SocketConnectionStatus
import com.example.uno.ui.theme.*

@Composable
fun LobbyScreen(
    profile: UserProfile,
    rules: GameRules,
    currentRoom: RoomInfo?,
    connectionStatus: SocketConnectionStatus,
    localGuestId: String,
    countdownSeconds: Int? = null,
    backendUrl: String = "",
    onUpdateBackendUrl: (String) -> Unit = {},
    onTestConnection: suspend (String) -> Pair<Boolean, String> = { Pair(true, "OK") },
    onCreateRoom: ((Boolean, String?) -> Unit) -> Unit,
    onJoinRoom: (String, String, String, (Boolean, String?) -> Unit) -> Unit,
    onSetReady: (Boolean) -> Unit = {},
    onStartGame: ((Boolean, String?) -> Unit) -> Unit = {},
    onLeaveRoom: () -> Unit,
    onRetryConnection: () -> Unit = {},
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) {
        onRetryConnection()
    }

    BackHandler {
        if (currentRoom != null) {
            onLeaveRoom()
        } else {
            onBack()
        }
    }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var showJoinDialog by remember { mutableStateOf(false) }

    var joinRoomCodeInput by remember { mutableStateOf("") }
    var guestNameInput by remember { mutableStateOf("") }
    var selectedGuestAvatar by remember { mutableStateOf("🦊") }
    var joinError by remember { mutableStateOf<String?>(null) }
    var isJoining by remember { mutableStateOf(false) }
    var isCreatingRoom by remember { mutableStateOf(false) }
    var createRoomError by remember { mutableStateOf<String?>(null) }
    var startGameError by remember { mutableStateOf<String?>(null) }

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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (currentRoom != null) onLeaveRoom() else onBack()
                            },
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
                                text = if (currentRoom == null) "PLAY WITH FRIENDS" else "GAME LOBBY",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp
                            )
                            Text(
                                text = "Real-Time 2-Player Private Room",
                                color = UnoGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Clean connection status dot only if connecting or error
                    if (connectionStatus != SocketConnectionStatus.CONNECTED) {
                        val (statusColor, statusText) = when (connectionStatus) {
                            SocketConnectionStatus.CONNECTING -> Pair(UnoYellow, "Connecting...")
                            SocketConnectionStatus.ERROR -> Pair(UnoRed, "Offline - Tap Retry")
                            SocketConnectionStatus.DISCONNECTED -> Pair(Color.Gray, "Disconnected")
                            else -> Pair(Color.Transparent, "")
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.45f))
                                .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .clickable { onRetryConnection() }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(statusText, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // =============================================================
                // VIEW A: MAIN LOBBY (Create Room & Join Room)
                // =============================================================
                if (currentRoom == null) {
                    // Profile Summary Card
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

                    // 2 PROMINENT BUTTONS: CREATE ROOM & JOIN ROOM
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
                                .clickable(enabled = !isCreatingRoom) {
                                    isCreatingRoom = true
                                    createRoomError = null
                                    onCreateRoom { success, err ->
                                        isCreatingRoom = false
                                        if (!success) {
                                            createRoomError = err ?: "Failed to create room. Please verify your connection."
                                        }
                                    }
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
                                    if (isCreatingRoom) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = Color.Black,
                                            strokeWidth = 2.5.dp
                                        )
                                    } else {
                                        Text("👑", fontSize = 28.sp)
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = if (isCreatingRoom) "CREATING ROOM..." else "CREATE CUSTOM ROOM",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp
                                    )
                                    Text(
                                        text = "Get a unique 4-digit code & invite your friend",
                                        color = Color.Black.copy(alpha = 0.8f),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        if (createRoomError != null) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, UnoRed, RoundedCornerShape(12.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = "Error",
                                        tint = UnoRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = createRoomError ?: "",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    TextButton(
                                        onClick = {
                                            createRoomError = null
                                            onRetryConnection()
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = UnoYellow)
                                    ) {
                                        Text("RECONNECT", fontWeight = FontWeight.Black, fontSize = 11.sp)
                                    }
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
                                    guestNameInput = profile.name
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
                                        text = "Enter 4-digit code to start real-time duel",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // How it works card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.People, "Duel Info", tint = UnoGold, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("HOW 2-PLAYER DUELS WORK", color = UnoGold, fontWeight = FontWeight.Black, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "1. Tap 'CREATE CUSTOM ROOM' to generate your 4-digit code.\n" +
                                       "2. Share the code with your friend.\n" +
                                       "3. When both players mark themselves READY, the host starts the match.\n" +
                                       "4. 100% authoritative real-time play with private hands & zero desync.",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }

                // =============================================================
                // VIEW B: IN-ROOM REAL-TIME LOBBY (Both players synchronized)
                // =============================================================
                else {
                    val isLocalHost = currentRoom.hostGuestId == localGuestId
                    val hostPlayer = currentRoom.players.find { it.isHost } ?: currentRoom.players.firstOrNull()
                    val guestPlayer = currentRoom.players.find { !it.isHost }
                    val localPlayerInRoom = currentRoom.players.find { it.guestId == localGuestId }
                    val isLocalReady = localPlayerInRoom?.isReady ?: false
                    val bothReady = currentRoom.players.size == 2 && currentRoom.players.all { it.isReady && it.isConnected }

                    // Room Code Hero Card
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF121B24)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, UnoGold, RoundedCornerShape(20.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "ROOM CODE",
                                color = UnoGold,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                letterSpacing = 2.sp
                            )
                            Spacer(Modifier.height(8.dp))

                            // 4-Digit Display
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                currentRoom.roomCode.forEach { ch ->
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color.Black.copy(alpha = 0.7f))
                                            .border(2.dp, UnoYellow, RoundedCornerShape(14.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = ch.toString(),
                                            color = UnoYellow,
                                            fontSize = 30.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            // Copy & Share Code Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(currentRoom.roomCode))
                                        Toast.makeText(context, "Room Code ${currentRoom.roomCode} copied!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(
                                        brush = Brush.horizontalGradient(listOf(UnoYellow, UnoGold))
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("COPY CODE", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }

                                Button(
                                    onClick = {
                                        val sendIntent: Intent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "Join my real-time UNO duel! Room Code: ${currentRoom.roomCode}"
                                            )
                                            type = "text/plain"
                                        }
                                        val shareIntent = Intent.createChooser(sendIntent, "Share Room Code")
                                        context.startActivity(shareIntent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = UnoBlue),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Share, "Share", modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("SHARE", fontWeight = FontWeight.Black, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Text(
                        text = "PLAYERS (2 MAX)",
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(Modifier.height(10.dp))

                    // Player 1 Card (Host)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, if (hostPlayer?.isReady == true) UnoGreen else UnoGold, RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(UnoGold.copy(alpha = 0.2f))
                                        .border(1.5.dp, UnoGold, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(hostPlayer?.avatar ?: "👑", fontSize = 26.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = hostPlayer?.name ?: "Host",
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
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
                                    Text(
                                        text = if (hostPlayer?.isConnected == true) "🟢 Online" else "🔴 Disconnected",
                                        color = if (hostPlayer?.isConnected == true) Color(0xFF00E676) else UnoRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Player 2 Card (Guest or Waiting)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.5.dp,
                                if (guestPlayer == null) Color.White.copy(alpha = 0.2f)
                                else UnoGreen,
                                RoundedCornerShape(16.dp)
                            )
                    ) {
                        if (guestPlayer != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(UnoBlue.copy(alpha = 0.2f))
                                            .border(1.5.dp, UnoBlue, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(guestPlayer.avatar, fontSize = 26.sp)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = guestPlayer.name,
                                                color = Color.White,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 15.sp
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(UnoBlue)
                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                            ) {
                                                Text("PLAYER 2", color = Color.White, fontWeight = FontWeight.Black, fontSize = 9.sp)
                                            }
                                        }
                                        Text(
                                            text = if (guestPlayer.isConnected) "🟢 Online" else "🔴 Disconnected",
                                            color = if (guestPlayer.isConnected) Color(0xFF00E676) else UnoRed,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        } else {
                            // Waiting Slot
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = UnoYellow,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "Waiting for Player 2 to join...",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Share 4-digit room code with your friend",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // 5-SECOND AUTOMATIC COUNTDOWN BANNER (Both players present)
                    if (currentRoom.players.size == 2) {
                        val secondsLeft = countdownSeconds ?: 5
                        if (isLocalHost && secondsLeft <= 0) {
                            LaunchedEffect(Unit) {
                                onStartGame { _, _ -> }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF142417)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(2.dp, UnoGreen, RoundedCornerShape(20.dp))
                                .shadow(8.dp, RoundedCornerShape(20.dp))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("⚡", fontSize = 22.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "BOTH PLAYERS IN ROOM!",
                                        color = UnoGreen,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("⚡", fontSize = 22.sp)
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Game starting automatically in",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.height(10.dp))

                                // Glowing Countdown Badge
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.radialGradient(
                                                listOf(UnoGold, UnoYellow, Color(0xFFE65100))
                                            )
                                        )
                                        .border(3.dp, Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (secondsLeft > 0) secondsLeft.toString() else "GO!",
                                        color = Color.Black,
                                        fontSize = if (secondsLeft > 0) 38.sp else 24.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }

                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "Starting without permission... Get ready!",
                                    color = UnoYellow,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Clean Leave Room Button (No ready/start permission buttons)
                    OutlinedButton(
                        onClick = onLeaveRoom,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.75f)),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.25f)))
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("leave_room_button")
                    ) {
                        Icon(Icons.Default.ExitToApp, "Leave", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("LEAVE ROOM", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // =============================================================
        // JOIN ROOM MODAL DIALOG (4-digit code)
        // =============================================================
        if (showJoinDialog) {
            Dialog(onDismissRequest = { if (!isJoining) showJoinDialog = false }) {
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
                            text = "Enter the 4-digit code from the host",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )

                        Spacer(Modifier.height(16.dp))

                        // 4-digit Code Input
                        OutlinedTextField(
                            value = joinRoomCodeInput,
                            onValueChange = {
                                if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                    joinRoomCodeInput = it
                                    joinError = null
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = LocalTextStyle.current.copy(
                                color = UnoGold,
                                fontWeight = FontWeight.Black,
                                fontSize = 28.sp,
                                letterSpacing = 8.sp,
                                textAlign = TextAlign.Center
                            ),
                            placeholder = {
                                Text(
                                    "1234",
                                    color = Color.White.copy(alpha = 0.25f),
                                    fontSize = 26.sp,
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

                        // Player 2 Nickname Input
                        OutlinedTextField(
                            value = guestNameInput,
                            onValueChange = {
                                if (it.length <= 14) guestNameInput = it
                            },
                            label = { Text("Your Nickname", color = Color.White.copy(alpha = 0.7f)) },
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
                                    if (joinRoomCodeInput.length != 4) {
                                        joinError = "Please enter the 4-digit code"
                                        return@Button
                                    }
                                    isJoining = true
                                    joinError = null
                                    onJoinRoom(
                                        joinRoomCodeInput,
                                        guestNameInput.ifBlank { profile.name },
                                        selectedGuestAvatar
                                    ) { success, err ->
                                        isJoining = false
                                        if (success) {
                                            showJoinDialog = false
                                        } else {
                                            joinError = err ?: "Room not found, full, or expired."
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = UnoGreen),
                                enabled = !isJoining,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("submit_join_button")
                            ) {
                                if (isJoining) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                                } else {
                                    Text("JOIN ROOM", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
