package com.example.uno.logic

import com.example.uno.model.*
import kotlin.random.Random

object UnoEngine {

    val BASE_COLORS = listOf(CardColor.RED, CardColor.BLUE, CardColor.GREEN, CardColor.YELLOW)

    /**
     * Creates a standard 108-card UNO deck:
     * - 19 Red, 19 Blue, 19 Green, 19 Yellow (one 0, two of 1-9)
     * - 8 Skip (2 per color)
     * - 8 Reverse (2 per color)
     * - 8 Draw Two (2 per color)
     * - 4 Wild
     * - 4 Wild Draw Four
     */
    fun createStandardDeck(): List<Card> {
        val cards = mutableListOf<Card>()
        var cardIndex = 0

        for (color in BASE_COLORS) {
            // One 0
            cards.add(
                Card(
                    id = "${color.name.lowercase()}-0-${cardIndex++}",
                    color = color,
                    value = CardValue.NUM_0,
                    score = 0
                )
            )

            // Two of 1-9
            val numberValues = listOf(
                CardValue.NUM_1, CardValue.NUM_2, CardValue.NUM_3,
                CardValue.NUM_4, CardValue.NUM_5, CardValue.NUM_6,
                CardValue.NUM_7, CardValue.NUM_8, CardValue.NUM_9
            )
            for ((idx, valEnum) in numberValues.withIndex()) {
                val score = idx + 1
                cards.add(
                    Card(
                        id = "${color.name.lowercase()}-${score}-a-${cardIndex++}",
                        color = color,
                        value = valEnum,
                        score = score
                    )
                )
                cards.add(
                    Card(
                        id = "${color.name.lowercase()}-${score}-b-${cardIndex++}",
                        color = color,
                        value = valEnum,
                        score = score
                    )
                )
            }

            // Two Skip, Two Reverse, Two Draw Two
            val actionValues = listOf(CardValue.SKIP, CardValue.REVERSE, CardValue.DRAW2)
            for (action in actionValues) {
                cards.add(
                    Card(
                        id = "${color.name.lowercase()}-${action.name.lowercase()}-a-${cardIndex++}",
                        color = color,
                        value = action,
                        score = 20
                    )
                )
                cards.add(
                    Card(
                        id = "${color.name.lowercase()}-${action.name.lowercase()}-b-${cardIndex++}",
                        color = color,
                        value = action,
                        score = 20
                    )
                )
            }
        }

        // 4 Wild, 4 Wild Draw Four
        for (i in 0 until 4) {
            cards.add(
                Card(
                    id = "wild-$i-${cardIndex++}",
                    color = CardColor.WILD,
                    value = CardValue.WILD,
                    score = 50
                )
            )
            cards.add(
                Card(
                    id = "wild4-$i-${cardIndex++}",
                    color = CardColor.WILD,
                    value = CardValue.WILD4,
                    score = 50
                )
            )
        }

        return cards
    }

    fun shuffleDeck(deck: List<Card>): MutableList<Card> {
        val list = deck.toMutableList()
        list.shuffle()
        return list
    }

    /**
     * Check if a card can legally be played on top of the discard pile
     */
    fun isCardPlayable(
        card: Card,
        topCard: Card,
        activeColor: CardColor,
        pendingDrawCount: Int,
        rules: GameRules
    ): Boolean {
        // If there's an active draw penalty stacked (+2 or +4)
        if (pendingDrawCount > 0) {
            if (rules.stackDrawCards) {
                if (topCard.value == CardValue.DRAW2 && card.value == CardValue.DRAW2) return true
                if (topCard.value == CardValue.WILD4 && card.value == CardValue.WILD4) return true
            }
            return false
        }

        // Wild cards can always be played
        if (card.color == CardColor.WILD || card.value.isWild) {
            return true
        }

        // Matches active color
        if (card.color == activeColor) {
            return true
        }

        // Matches face value/action of top card
        if (card.value == topCard.value) {
            return true
        }

        return false
    }

    /**
     * Initialize a new 2-player match
     */
    fun initializeMatch(
        player1: Player,
        player2: Player,
        rules: GameRules = GameRules()
    ): GameState {
        val deck = shuffleDeck(createStandardDeck())

        val hand1 = deck.take(7).toMutableList()
        repeat(7) { deck.removeAt(0) }

        val hand2 = deck.take(7).toMutableList()
        repeat(7) { deck.removeAt(0) }

        // Top card cannot be a Wild4 initially
        var topCardIndex = deck.indexOfFirst { it.value != CardValue.WILD4 }
        if (topCardIndex == -1) topCardIndex = 0
        val topCard = deck.removeAt(topCardIndex)

        val discardPile = mutableListOf(topCard)
        val activeColor = if (topCard.color == CardColor.WILD) CardColor.RED else topCard.color
        var currentTurnIndex = 0
        var pendingDrawCount = 0

        var initialActionText = "Game started! Top card is ${topCard.color.displayName} ${topCard.value.displaySymbol}."

        if (topCard.value == CardValue.DRAW2) {
            val extra = deck.take(2)
            repeat(extra.size) { deck.removeAt(0) }
            hand1.addAll(extra)
            currentTurnIndex = 1
            initialActionText = "Top card is Draw Two! 2 cards added to ${player1.name}, turn to ${player2.name}."
        } else if (topCard.value == CardValue.SKIP || (topCard.value == CardValue.REVERSE && rules.reverseAsSkipInTwoPlayer)) {
            currentTurnIndex = 1
            initialActionText = "Top card is ${topCard.value.name}! Turn skipped to ${player2.name}."
        }

        val players = listOf(
            player1.copy(hand = hand1, unoCalled = false, score = 0),
            player2.copy(hand = hand2, unoCalled = false, score = 0)
        )

        return GameState(
            id = "game-${System.currentTimeMillis()}-${Random.nextInt(1000)}",
            deck = deck,
            discardPile = discardPile,
            players = players,
            currentTurnIndex = currentTurnIndex,
            activeColor = activeColor,
            direction = 1,
            pendingDrawCount = pendingDrawCount,
            status = GameStatus.PLAYING,
            winnerId = null,
            lastAction = LastAction(
                playerId = "system",
                text = initialActionText,
                card = topCard
            ),
            rules = rules,
            unoCatchablePlayerId = null
        )
    }

    /**
     * Handle drawing cards from deck (with reshuffle if deck is low)
     */
    fun drawFromDeck(
        deck: List<Card>,
        discardPile: List<Card>,
        count: Int
    ): Triple<List<Card>, List<Card>, List<Card>> {
        val currentDeck = deck.toMutableList()
        val currentDiscard = discardPile.toMutableList()
        val drawn = mutableListOf<Card>()

        for (i in 0 until count) {
            if (currentDeck.isEmpty()) {
                if (currentDiscard.size <= 1) break
                val topCard = currentDiscard.removeAt(currentDiscard.size - 1)
                currentDeck.addAll(shuffleDeck(currentDiscard))
                currentDiscard.clear()
                currentDiscard.add(topCard)
            }
            if (currentDeck.isNotEmpty()) {
                drawn.add(currentDeck.removeAt(0))
            }
        }

        return Triple(drawn, currentDeck, currentDiscard)
    }

    /**
     * Reshuffle discard pile into deck after 25 cards drawn from deck
     */
    fun checkAndReshuffleAfter25Drawn(
        deck: List<Card>,
        discardPile: List<Card>,
        currentDrawnCount: Int,
        newlyDrawn: Int
    ): Triple<List<Card>, List<Card>, Int> {
        val totalDrawn = currentDrawnCount + newlyDrawn
        if (totalDrawn >= 25 && discardPile.size > 1) {
            val topCard = discardPile.last()
            val toReshuffle = discardPile.dropLast(1)
            val mergedDeck = shuffleDeck(deck + toReshuffle)
            return Triple(mergedDeck, listOf(topCard), 0)
        }
        return Triple(deck, discardPile, totalDrawn)
    }

    /**
     * Play a card from player's hand
     */
    fun executePlayCard(
        state: GameState,
        playerId: String,
        cardId: String,
        chosenColor: CardColor? = null
    ): Pair<GameState, Boolean> {
        if (state.status != GameStatus.PLAYING) return Pair(state, false)

        val playerIndex = state.players.indexOfFirst { it.id == playerId }
        if (playerIndex == -1) return Pair(state, false)

        val isCurrentTurn = state.currentTurnIndex == playerIndex
        val player = state.players[playerIndex]
        val cardIndex = player.hand.indexOfFirst { it.id == cardId }
        if (cardIndex == -1) return Pair(state, false)

        val card = player.hand[cardIndex]
        val topCard = state.discardPile.last()

        val isJumpIn = !isCurrentTurn && state.rules.jumpIn &&
                card.color == topCard.color && card.value == topCard.value

        if (!isCurrentTurn && !isJumpIn) return Pair(state, false)

        val legal = isCardPlayable(card, topCard, state.activeColor, state.pendingDrawCount, state.rules)
        if (!legal) return Pair(state, false)

        val newHand = player.hand.toMutableList()
        newHand.removeAt(cardIndex)

        val newDiscard = state.discardPile.toMutableList()
        newDiscard.add(card)

        var nextColor = if (card.color == CardColor.WILD) (chosenColor ?: CardColor.RED) else card.color
        var nextDirection = state.direction
        val opponentIndex = 1 - playerIndex
        var nextTurnIndex = opponentIndex
        var actionText = "${player.name} played ${if (card.color == CardColor.WILD) "WILD" else card.color.displayName} ${card.value.displaySymbol}"

        var currentDeck = state.deck
        var currentDiscardList = newDiscard.toList()

        val playersList = state.players.toMutableList()
        playersList[playerIndex] = player.copy(
            hand = newHand,
            unoCalled = if (newHand.size == 1) player.unoCalled else false
        )

        // Action mechanics
        // Action mechanics: Only SKIP and REVERSE grant an extra card turn. DRAW2 and WILD4 pass turn to opponent!
        if (card.value == CardValue.DRAW2) {
            val (drawn, nDeck, nDisc) = drawFromDeck(currentDeck, currentDiscardList, 2)
            currentDeck = nDeck
            currentDiscardList = nDisc

            val opp = playersList[opponentIndex]
            playersList[opponentIndex] = opp.copy(
                hand = opp.hand + drawn,
                unoCalled = false
            )
            nextTurnIndex = opponentIndex
            actionText += "! 2 cards added to ${opp.name}"
        } else if (card.value == CardValue.WILD4) {
            val (drawn, nDeck, nDisc) = drawFromDeck(currentDeck, currentDiscardList, 4)
            currentDeck = nDeck
            currentDiscardList = nDisc

            val opp = playersList[opponentIndex]
            playersList[opponentIndex] = opp.copy(
                hand = opp.hand + drawn,
                unoCalled = false
            )
            nextTurnIndex = opponentIndex
            actionText += "! 4 cards added to ${opp.name} & chose ${nextColor.displayName}"
        } else if (card.value == CardValue.SKIP) {
            nextTurnIndex = playerIndex
            actionText += " (Skipped! ${player.name} goes again)"
        } else if (card.value == CardValue.REVERSE) {
            nextDirection *= -1
            if (state.rules.reverseAsSkipInTwoPlayer) {
                nextTurnIndex = playerIndex
                actionText += " (Reversed! ${player.name} goes again)"
            }
        } else if (card.color == CardColor.WILD) {
            actionText += " and chose ${nextColor.displayName}"
        }

        if (state.rules.sevenZeroRule) {
            if (card.value == CardValue.NUM_7) {
                val p1Hand = playersList[0].hand
                val p2Hand = playersList[1].hand
                playersList[0] = playersList[0].copy(hand = p2Hand, unoCalled = false)
                playersList[1] = playersList[1].copy(hand = p1Hand, unoCalled = false)
                actionText += " (7 Rule: Swapped hands!)"
            }
        }

        // UNO Check: if player had 2 cards and plays down to 1 card
        val wasTwoCards = player.hand.size == 2
        var catchablePlayerId: String? = null
        if (wasTwoCards) {
            if (!player.unoCalled) {
                // Suffer penalty of 2 cards for failing to call UNO in the remaining 2 cards!
                val (penaltyCards, pDeck, pDisc) = drawFromDeck(currentDeck, currentDiscardList, 2)
                currentDeck = pDeck
                currentDiscardList = pDisc
                newHand.addAll(penaltyCards)
                playersList[playerIndex] = playersList[playerIndex].copy(hand = newHand, unoCalled = false)
                actionText += " (🚨 Missed UNO! +2 penalty cards added)"
            }
        }

        val updatedPlayer = playersList[playerIndex]
        if (updatedPlayer.hand.size == 1) {
            if (!updatedPlayer.unoCalled) {
                catchablePlayerId = updatedPlayer.id
            }
        } else {
            playersList[playerIndex] = updatedPlayer.copy(unoCalled = false)
        }

        var winnerId: String? = null
        var gameStatus = GameStatus.PLAYING

        if (updatedPlayer.hand.isEmpty()) {
            winnerId = updatedPlayer.id
            gameStatus = GameStatus.GAMEOVER
            actionText = "🎉 ${updatedPlayer.name} has played their last card and WON THE GAME!"
        }

        // Reshuffle after 25 cards drawn from deck
        val extraDrawn = when (card.value) {
            CardValue.DRAW2 -> 2
            CardValue.WILD4 -> 4
            else -> if (wasTwoCards && !player.unoCalled) 2 else 0
        }
        val (finalDeck, finalDiscard, updatedCardsDrawnCount) = checkAndReshuffleAfter25Drawn(
            currentDeck,
            currentDiscardList,
            state.cardsDrawnCount,
            extraDrawn
        )

        val nextState = state.copy(
            deck = finalDeck,
            discardPile = finalDiscard,
            cardsDrawnCount = updatedCardsDrawnCount,
            players = playersList,
            currentTurnIndex = nextTurnIndex,
            activeColor = nextColor,
            direction = nextDirection,
            pendingDrawCount = 0,
            status = gameStatus,
            winnerId = winnerId,
            turnSecondsRemaining = 20,
            hasDrawnThisTurn = false,
            lastAction = LastAction(
                playerId = playerId,
                text = actionText,
                card = card
            ),
            unoCatchablePlayerId = catchablePlayerId
        )

        return Pair(nextState, true)
    }

    /**
     * Handle player drawing a card
     */
    fun executeDrawCard(
        state: GameState,
        playerId: String
    ): Pair<GameState, List<Card>> {
        if (state.status != GameStatus.PLAYING) return Pair(state, emptyList())

        val playerIndex = state.players.indexOfFirst { it.id == playerId }
        if (playerIndex == -1 || state.currentTurnIndex != playerIndex) return Pair(state, emptyList())

        val player = state.players[playerIndex]
        val countToDraw = if (state.pendingDrawCount > 0) state.pendingDrawCount else 1
        val wasPenalty = state.pendingDrawCount > 0

        val (drawn, newDeck, newDiscard) = drawFromDeck(state.deck, state.discardPile, countToDraw)

        val updatedHand = player.hand + drawn
        val playersList = state.players.toMutableList()
        playersList[playerIndex] = player.copy(
            hand = updatedHand,
            unoCalled = false
        )

        val opponentIndex = 1 - playerIndex
        // If it was a forced draw penalty (+2/+4), turn passes immediately.
        // If normal draw (player pulled 1 card), keep turn so player can choose to play or click skip!
        val nextTurnIndex = if (wasPenalty) opponentIndex else playerIndex
        val actionText = if (wasPenalty) {
            "${player.name} drew $countToDraw penalty cards!"
        } else {
            "${player.name} drew a card. Play or click Skip."
        }

        // Reshuffle discard pile into deck after 25 cards drawn
        val (finalDeck, finalDiscard, updatedCardsDrawnCount) = checkAndReshuffleAfter25Drawn(
            newDeck,
            newDiscard,
            state.cardsDrawnCount,
            drawn.size
        )

        val nextState = state.copy(
            deck = finalDeck,
            discardPile = finalDiscard,
            cardsDrawnCount = updatedCardsDrawnCount,
            players = playersList,
            currentTurnIndex = nextTurnIndex,
            pendingDrawCount = 0,
            hasDrawnThisTurn = !wasPenalty,
            turnSecondsRemaining = 20,
            lastAction = LastAction(
                playerId = playerId,
                text = actionText
            ),
            unoCatchablePlayerId = null
        )

        return Pair(nextState, drawn)
    }

    /**
     * Skip/Pass turn after pulling a card from the deck
     */
    fun executeSkipTurn(
        state: GameState,
        playerId: String
    ): GameState {
        val playerIndex = state.players.indexOfFirst { it.id == playerId }
        if (playerIndex == -1 || state.currentTurnIndex != playerIndex) return state

        val player = state.players[playerIndex]
        val opponentIndex = 1 - playerIndex

        return state.copy(
            currentTurnIndex = opponentIndex,
            hasDrawnThisTurn = false,
            turnSecondsRemaining = 20,
            lastAction = LastAction(
                playerId = playerId,
                text = "${player.name} skipped turn."
            )
        )
    }

    /**
     * Penalty of 1 card when 20s turn timer expires
     */
    fun executeTimerPenalty(
        state: GameState
    ): Pair<GameState, Card?> {
        val playerIndex = state.currentTurnIndex
        val player = state.players.getOrNull(playerIndex) ?: return Pair(state, null)
        val opponentIndex = 1 - playerIndex

        val (drawn, newDeck, newDiscard) = drawFromDeck(state.deck, state.discardPile, 1)
        val drawnCard = drawn.firstOrNull()

        val playersList = state.players.toMutableList()
        playersList[playerIndex] = player.copy(
            hand = player.hand + drawn,
            unoCalled = false
        )

        val (finalDeck, finalDiscard, updatedCardsDrawnCount) = checkAndReshuffleAfter25Drawn(
            newDeck,
            newDiscard,
            state.cardsDrawnCount,
            drawn.size
        )

        val nextState = state.copy(
            deck = finalDeck,
            discardPile = finalDiscard,
            cardsDrawnCount = updatedCardsDrawnCount,
            players = playersList,
            currentTurnIndex = opponentIndex,
            hasDrawnThisTurn = false,
            turnSecondsRemaining = 20,
            lastAction = LastAction(
                playerId = player.id,
                text = "⏰ Time's up! ${player.name} received 1 penalty card."
            ),
            unoCatchablePlayerId = null
        )

        return Pair(nextState, drawnCard)
    }

    /**
     * Check if a player can legally call UNO:
     * - Hand size is <= 2 cards
     * - Has not yet called UNO
     * - Must have at least ONE valid playable card in hand against the current discard top card
     *   (If both cards are invalid, CALL UNO is not visible / not allowed)
     */
    fun canPlayerCallUno(
        state: GameState,
        playerId: String
    ): Boolean {
        val player = state.players.find { it.id == playerId } ?: return false
        if (player.unoCalled) return false
        if (player.hand.size > 2 || player.hand.isEmpty()) return false

        val topCard = state.discardPile.lastOrNull() ?: return false
        val hasPlayableCard = player.hand.any { card ->
            isCardPlayable(card, topCard, state.activeColor, state.pendingDrawCount, state.rules)
        }
        return hasPlayableCard
    }

    /**
     * Player calls UNO for themselves
     */
    fun executeCallUno(
        state: GameState,
        playerId: String
    ): Pair<GameState, Boolean> {
        val playerIndex = state.players.indexOfFirst { it.id == playerId }
        if (playerIndex == -1) return Pair(state, false)

        val player = state.players[playerIndex]
        if (canPlayerCallUno(state, playerId)) {
            val playersList = state.players.toMutableList()
            playersList[playerIndex] = player.copy(unoCalled = true)

            val nextState = state.copy(
                players = playersList,
                unoCatchablePlayerId = null,
                lastAction = LastAction(
                    playerId = playerId,
                    text = "📢 ${player.name} called \"UNO!\""
                )
            )
            return Pair(nextState, true)
        }
        return Pair(state, false)
    }

    /**
     * Opponent catches player who didn't call UNO
     */
    fun executeCatchUno(
        state: GameState,
        catcherId: String
    ): Pair<GameState, Boolean> {
        val targetId = state.unoCatchablePlayerId
        if (targetId == null || targetId == catcherId) return Pair(state, false)

        val targetIndex = state.players.indexOfFirst { it.id == targetId }
        if (targetIndex == -1) return Pair(state, false)

        val catcher = state.players.find { it.id == catcherId }
        val target = state.players[targetIndex]

        val penaltyCount = state.rules.unoPenaltyCards
        val (drawn, newDeck, newDiscard) = drawFromDeck(state.deck, state.discardPile, penaltyCount)

        val playersList = state.players.toMutableList()
        playersList[targetIndex] = target.copy(
            hand = target.hand + drawn,
            unoCalled = false
        )

        val nextState = state.copy(
            deck = newDeck,
            discardPile = newDiscard,
            players = playersList,
            unoCatchablePlayerId = null,
            lastAction = LastAction(
                playerId = catcherId,
                text = "🚨 ${catcher?.name ?: "Player"} CAUGHT ${target.name} for not calling UNO! (+$penaltyCount penalty)"
            )
        )

        return Pair(nextState, true)
    }

    data class AiDecision(
        val action: String, // "play", "draw", "callUno"
        val cardId: String? = null,
        val chosenColor: CardColor = CardColor.RED,
        val callUnoFirst: Boolean = false
    )

    /**
     * AI Decision Maker
     */
    fun getAiDecision(
        state: GameState,
        aiPlayerId: String
    ): AiDecision {
        val player = state.players.find { it.id == aiPlayerId } ?: return AiDecision("draw")
        val topCard = state.discardPile.last()

        // 85% chance AI catches opponent if catchable
        if (state.unoCatchablePlayerId != null && state.unoCatchablePlayerId != aiPlayerId) {
            if (Random.nextFloat() < 0.85f) {
                return AiDecision("callUno")
            }
        }

        val playableCards = player.hand.filter {
            isCardPlayable(it, topCard, state.activeColor, state.pendingDrawCount, state.rules)
        }

        if (playableCards.isEmpty()) {
            return if (state.hasDrawnThisTurn) AiDecision("skip") else AiDecision("draw")
        }

        // Evaluate color counts
        val colorCounts = mutableMapOf(
            CardColor.RED to 0,
            CardColor.BLUE to 0,
            CardColor.GREEN to 0,
            CardColor.YELLOW to 0
        )
        for (card in player.hand) {
            if (card.color != CardColor.WILD) {
                colorCounts[card.color] = (colorCounts[card.color] ?: 0) + 1
            }
        }
        val bestColor = colorCounts.maxByOrNull { it.value }?.key ?: CardColor.RED

        val difficulty = state.rules.aiDifficulty
        val selectedCard: Card

        if (difficulty == AiDifficulty.EASY) {
            selectedCard = playableCards.random()
        } else {
            // Hard/Medium AI
            val drawCards = playableCards.filter { it.value == CardValue.DRAW2 || it.value == CardValue.WILD4 }
            if (state.pendingDrawCount > 0 && drawCards.isNotEmpty()) {
                selectedCard = drawCards.first()
            } else {
                val turnStealers = playableCards.filter { it.value == CardValue.SKIP || it.value == CardValue.REVERSE }
                val actionAttackers = playableCards.filter { it.value == CardValue.DRAW2 }
                val matchingColor = playableCards.filter { it.color == bestColor }
                val normalCards = playableCards.filter { it.color != CardColor.WILD }

                selectedCard = when {
                    turnStealers.isNotEmpty() -> turnStealers.first()
                    actionAttackers.isNotEmpty() -> actionAttackers.first()
                    matchingColor.isNotEmpty() -> matchingColor.first()
                    normalCards.isNotEmpty() -> normalCards.first()
                    else -> playableCards.first()
                }
            }
        }

        val callUnoFirst = player.hand.size == 2

        return AiDecision(
            action = "play",
            cardId = selectedCard.id,
            chosenColor = bestColor,
            callUnoFirst = callUnoFirst
        )
    }

    fun calculateHandScore(hand: List<Card>): Int {
        return hand.sumOf { it.score }
    }
}
