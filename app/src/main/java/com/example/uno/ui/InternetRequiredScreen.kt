package com.example.uno.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uno.R
import com.example.uno.ui.theme.*

/**
 * Global Internet Connection Required Gate Screen for UNO Royale.
 * Matches UNO Royale's iconic red, black, and gold theme.
 *
 * Blocks gameplay and all UI interactions when offline.
 * Provides a real "Retry Connection" check and automatically resumes access
 * as soon as validated internet connectivity is restored.
 */
@Composable
fun InternetRequiredScreen(
    onRetry: () -> Boolean,
    modifier: Modifier = Modifier
) {
    // Pulse animation for the disconnected icon
    val infiniteTransition = rememberInfiniteTransition(label = "wifi_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wifi_scale"
    )

    var isChecking by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("internet_required_screen")
    ) {
        // Red Arena Background
        Image(
            painter = painterResource(id = R.drawable.bg_app_red),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Dark Vignette Overlay to maintain contrast and emphasize the offline gate
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.82f),
                            Color(0xFF2B0500).copy(alpha = 0.92f),
                            Color.Black.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        // Centered Modal Card
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF181818).copy(alpha = 0.95f)
            ),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                width = 2.dp,
                brush = Brush.horizontalGradient(
                    listOf(UnoGold, UnoYellow, UnoGold)
                )
            ),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.92f)
                .padding(horizontal = 16.dp)
                .shadow(24.dp, RoundedCornerShape(26.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Pulsing Wi-Fi Disconnected Icon Badge
                Box(
                    modifier = Modifier
                        .scale(pulseScale)
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF8B0000), Color(0xFF350000))
                            )
                        )
                        .border(2.5.dp, UnoGold, CircleShape)
                        .shadow(12.dp, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = "Wi-Fi Disconnected",
                        tint = UnoYellow,
                        modifier = Modifier.size(50.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Title
                Text(
                    text = "INTERNET CONNECTION REQUIRED",
                    color = UnoGold,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(14.dp))

                // Description
                Text(
                    text = "UNO Royale requires an active internet connection to play. Please connect to Wi-Fi or mobile data to continue.",
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(18.dp))

                // Connection status indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(UnoRed)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Status: Offline • Reconnecting automatically...",
                        color = UnoYellow.copy(alpha = 0.9f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(26.dp))

                // Real "Retry Connection" Button
                Button(
                    onClick = {
                        isChecking = true
                        onRetry()
                        isChecking = false
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UnoYellow,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .shadow(8.dp, RoundedCornerShape(16.dp))
                        .testTag("retry_connection_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry Icon",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "RETRY CONNECTION",
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp
                    )
                }
            }
        }
    }
}
