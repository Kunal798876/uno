package com.example.uno.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uno.model.Card
import com.example.uno.model.CardColor
import com.example.uno.model.CardValue
import com.example.uno.ui.theme.*

@Composable
fun UnoCardView(
    modifier: Modifier = Modifier,
    card: Card? = null,
    isBack: Boolean = false,
    onClick: (() -> Unit)? = null,
    width: Dp = 68.dp,
    height: Dp = 100.dp,
    rotation: Float = 0f,
    isShaking: Boolean = false,
    elevation: Dp = 6.dp
) {
    val shakeOffset by animateFloatAsState(
        targetValue = if (isShaking) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessHigh),
        label = "shake"
    )

    val shakeModifier = if (isShaking) {
        Modifier.offset(x = (shakeOffset * 8).dp)
    } else Modifier

    val cardShape = RoundedCornerShape(12.dp)
    val innerShape = RoundedCornerShape(8.dp)

    // Red highlight on invalid card play
    val cardBorderColor = if (isShaking) Color(0xFFFF1744) else CardBorder
    val cardBorderWidth = if (isShaking) 3.dp else 2.dp

    Box(
        modifier = modifier
            .size(width, height)
            .rotate(rotation)
            .then(shakeModifier)
            .shadow(elevation, cardShape)
            .clip(cardShape)
            .background(Color.White)
            .border(cardBorderWidth, cardBorderColor, cardShape)
            .clickable(
                enabled = onClick != null,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onClick?.invoke()
            }
            .padding(3.dp)
            .testTag(if (isBack) "card_back" else "card_${card?.id ?: "unknown"}")
    ) {
        if (isBack || card == null) {
            // CARD BACK (DUO / UNO with 4 color accents matching image 3.jpg)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(innerShape)
                    .background(Color(0xFF181818))
                    .border(1.dp, Color(0xFF383838), innerShape),
                contentAlignment = Alignment.Center
            ) {
                // 4 Color Corner Quadrant Accents
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.TopStart)
                        .background(UnoGreen, RoundedCornerShape(bottomEnd = 7.dp))
                )
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.TopEnd)
                        .background(UnoRed, RoundedCornerShape(bottomStart = 7.dp))
                )
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomStart)
                        .background(UnoYellow, RoundedCornerShape(topEnd = 7.dp))
                )
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                        .background(UnoBlue, RoundedCornerShape(topStart = 7.dp))
                )

                // Center DUO / UNO Red Pill Emblem
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(listOf(Color(0xFFCC0000), Color(0xFF880000)))
                        )
                        .border(1.5.dp, UnoGold, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "DUO",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic,
                        fontSize = 13.sp,
                        letterSpacing = (-0.5).sp
                    )
                }
            }
        } else {
            // CARD FACE (Modern UNO style as shown in 3.jpg)
            val cardColor = when (card.color) {
                CardColor.RED -> UnoRed
                CardColor.BLUE -> UnoBlue
                CardColor.GREEN -> UnoGreen
                CardColor.YELLOW -> UnoYellow
                CardColor.WILD -> UnoWildBlack
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Header Row with Corner Pip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 1.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.White)
                            .border(0.5.dp, Color(0xFFCCCCCC), RoundedCornerShape(5.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        IndexPip(card = card, cardColor = cardColor, fontSize = 11.sp)
                    }

                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(cardColor)
                            .border(0.5.dp, Color.Black.copy(alpha = 0.3f), CircleShape)
                    )
                }

                // Center Colored Body with Swoosh & Center Graphic
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 1.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(cardColor, cardColor.copy(alpha = 0.90f))
                            )
                        )
                        .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    CenterGraphic(card = card)
                }

                // Bottom Inverted Pip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 1.dp)
                        .rotate(180f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.White)
                            .border(0.5.dp, Color(0xFFCCCCCC), RoundedCornerShape(5.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        IndexPip(card = card, cardColor = cardColor, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun IndexPip(card: Card, cardColor: Color, fontSize: androidx.compose.ui.unit.TextUnit) {
    val pipColor = if (card.color == CardColor.WILD) Color.Black else cardColor
    when (card.value) {
        CardValue.SKIP -> Text("⊘", color = pipColor, fontWeight = FontWeight.Black, fontSize = fontSize)
        CardValue.REVERSE -> Text("⇄", color = pipColor, fontWeight = FontWeight.Black, fontSize = fontSize)
        CardValue.DRAW2 -> Text("+2", color = pipColor, fontWeight = FontWeight.Black, fontSize = fontSize)
        CardValue.WILD -> {
            Row(modifier = Modifier.size(9.dp)) {
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth().weight(1f).background(UnoRed))
                    Box(Modifier.fillMaxWidth().weight(1f).background(UnoBlue))
                }
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth().weight(1f).background(UnoYellow))
                    Box(Modifier.fillMaxWidth().weight(1f).background(UnoGreen))
                }
            }
        }
        CardValue.WILD4 -> Text("+4", color = Color.Black, fontWeight = FontWeight.Black, fontSize = fontSize)
        CardValue.NUM_6, CardValue.NUM_9 -> {
            Text(
                text = card.value.displaySymbol,
                color = pipColor,
                fontWeight = FontWeight.Black,
                fontSize = fontSize,
                textDecoration = TextDecoration.Underline
            )
        }
        else -> {
            Text(card.value.displaySymbol, color = pipColor, fontWeight = FontWeight.Black, fontSize = fontSize)
        }
    }
}

@Composable
private fun CenterGraphic(card: Card) {
    when (card.value) {
        CardValue.SKIP -> {
            Text("⊘", color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp)
        }
        CardValue.REVERSE -> {
            Text("⇄", color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp)
        }
        CardValue.DRAW2 -> {
            Text("+2", color = Color.White, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 24.sp)
        }
        CardValue.WILD -> {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(2.dp)
            ) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(topStart = 4.dp)).background(UnoRed))
                        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(bottomStart = 4.dp)).background(UnoBlue))
                    }
                    Spacer(Modifier.width(2.dp))
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(topEnd = 4.dp)).background(UnoYellow))
                        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(bottomEnd = 4.dp)).background(UnoGreen))
                    }
                }
            }
        }
        CardValue.WILD4 -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(1.dp)
                ) {
                    Row(Modifier.fillMaxSize()) {
                        Column(Modifier.weight(1f)) {
                            Box(Modifier.fillMaxWidth().weight(1f).background(UnoRed))
                            Box(Modifier.fillMaxWidth().weight(1f).background(UnoBlue))
                        }
                        Column(Modifier.weight(1f)) {
                            Box(Modifier.fillMaxWidth().weight(1f).background(UnoYellow))
                            Box(Modifier.fillMaxWidth().weight(1f).background(UnoGreen))
                        }
                    }
                }
                Text("+4", color = Color.White, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 16.sp)
            }
        }
        CardValue.NUM_6, CardValue.NUM_9 -> {
            Text(
                text = card.value.displaySymbol,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                fontSize = 32.sp,
                textDecoration = TextDecoration.Underline
            )
        }
        else -> {
            Text(
                text = card.value.displaySymbol,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                fontSize = 32.sp
            )
        }
    }
}
