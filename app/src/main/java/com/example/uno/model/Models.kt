package com.example.uno.model

import kotlinx.serialization.Serializable

@Serializable
enum class CardColor {
    RED,
    BLUE,
    GREEN,
    YELLOW,
    WILD;

    val hex: Long
        get() = when (this) {
            RED -> 0xFFEE2737
            BLUE -> 0xFF0072BC
            GREEN -> 0xFF00A651
            YELLOW -> 0xFFFFCC00
            WILD -> 0xFF1A1A1A
        }

    val displayName: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

@Serializable
enum class CardValue {
    NUM_0, NUM_1, NUM_2, NUM_3, NUM_4, NUM_5, NUM_6, NUM_7, NUM_8, NUM_9,
    SKIP, REVERSE, DRAW2, WILD, WILD4;

    val displaySymbol: String
        get() = when (this) {
            NUM_0 -> "0"
            NUM_1 -> "1"
            NUM_2 -> "2"
            NUM_3 -> "3"
            NUM_4 -> "4"
            NUM_5 -> "5"
            NUM_6 -> "6"
            NUM_7 -> "7"
            NUM_8 -> "8"
            NUM_9 -> "9"
            SKIP -> "⊘"
            REVERSE -> "⇄"
            DRAW2 -> "+2"
            WILD -> "W"
            WILD4 -> "+4"
        }

    val isWild: Boolean
        get() = this == WILD || this == WILD4
}

@Serializable
data class Card(
    val id: String,
    val color: CardColor,
    val value: CardValue,
    val score: Int
)

@Serializable
enum class AiDifficulty {
    EASY,
    MEDIUM,
    HARD;

    val displayName: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

@Serializable
data class GameRules(
    val stackDrawCards: Boolean = true,
    val drawToMatch: Boolean = false,
    val reverseAsSkipInTwoPlayer: Boolean = true,
    val sevenZeroRule: Boolean = false,
    val jumpIn: Boolean = false,
    val unoPenaltyCards: Int = 2,
    val challengeWild4: Boolean = false,
    val aiDifficulty: AiDifficulty = AiDifficulty.MEDIUM
)

@Serializable
data class Player(
    val id: String,
    val name: String,
    val avatar: String = "🧔",
    val isAi: Boolean = false,
    val hand: List<Card> = emptyList(),
    val unoCalled: Boolean = false,
    val score: Int = 0
)

@Serializable
enum class GameStatus {
    WAITING,
    PLAYING,
    GAMEOVER
}

@Serializable
data class LastAction(
    val playerId: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val card: Card? = null
)

@Serializable
data class GameState(
    val id: String,
    val deck: List<Card>,
    val discardPile: List<Card>,
    val players: List<Player>,
    val currentTurnIndex: Int = 0,
    val activeColor: CardColor = CardColor.RED,
    val direction: Int = 1,
    val pendingDrawCount: Int = 0,
    val status: GameStatus = GameStatus.WAITING,
    val winnerId: String? = null,
    val lastAction: LastAction? = null,
    val rules: GameRules = GameRules(),
    val unoCatchablePlayerId: String? = null,
    val turnSecondsRemaining: Int = 20,
    val hasDrawnThisTurn: Boolean = false,
    val isCustomRoom: Boolean = false,
    val roomCode: String? = null,
    val isBotTakeover: Boolean = false,
    val roomClosed: Boolean = false,
    val isRoomWaiting: Boolean = false,
    val cardsDrawnCount: Int = 0
)

@Serializable
data class UserProfile(
    val name: String = "Kannu",
    val avatar: String = "🧔",
    val gamesWon: Int = 0,
    val gamesPlayed: Int = 0
)

data class EmoteMessage(
    val emoji: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class CardFlightAnimation(
    val id: String,
    val card: Card? = null,
    val isBack: Boolean = false,
    val type: String = "play", // "play" (hand to discard), "draw" (deck to hand), "attack" (deck to opponent)
    val startOffsetY: Float = 0f,
    val targetOffsetY: Float = 0f,
    val startOffsetX: Float = 0f,
    val targetOffsetX: Float = 0f,
    val count: Int = 1,
    val durationMs: Int = 800
)
