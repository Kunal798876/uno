package com.example.uno

import com.example.uno.logic.UnoEngine
import com.example.uno.model.*
import org.junit.Assert.*
import org.junit.Test

class UnoEngineTest {

    @Test
    fun standardDeckHas108Cards() {
        val deck = UnoEngine.createStandardDeck()
        assertEquals(108, deck.size)
    }

    @Test
    fun matchingColorCardIsPlayable() {
        val topCard = Card("c1", CardColor.RED, CardValue.NUM_5, 5)
        val candidate = Card("c2", CardColor.RED, CardValue.NUM_8, 8)
        val playable = UnoEngine.isCardPlayable(candidate, topCard, CardColor.RED, 0, GameRules())
        assertTrue(playable)
    }

    @Test
    fun matchingValueCardIsPlayableAcrossColors() {
        val topCard = Card("c1", CardColor.BLUE, CardValue.NUM_7, 7)
        val candidate = Card("c2", CardColor.YELLOW, CardValue.NUM_7, 7)
        val playable = UnoEngine.isCardPlayable(candidate, topCard, CardColor.BLUE, 0, GameRules())
        assertTrue(playable)
    }

    @Test
    fun wildCardAlwaysPlayable() {
        val topCard = Card("c1", CardColor.GREEN, CardValue.NUM_3, 3)
        val wild = Card("w1", CardColor.WILD, CardValue.WILD, 50)
        val playable = UnoEngine.isCardPlayable(wild, topCard, CardColor.GREEN, 0, GameRules())
        assertTrue(playable)
    }

    @Test
    fun callUnoWithTwoCardsSucceeds() {
        val p1 = Player("p1", "Alice", hand = listOf(
            Card("1", CardColor.RED, CardValue.NUM_1, 1),
            Card("2", CardColor.RED, CardValue.NUM_2, 2)
        ))
        val p2 = Player("p2", "Bob", hand = listOf(
            Card("3", CardColor.BLUE, CardValue.NUM_3, 3)
        ))
        val state = GameState(
            id = "test",
            deck = emptyList(),
            discardPile = listOf(Card("top", CardColor.RED, CardValue.NUM_5, 5)),
            players = listOf(p1, p2),
            status = GameStatus.PLAYING
        )

        val (nextState, success) = UnoEngine.executeCallUno(state, "p1")
        assertTrue(success)
        assertTrue(nextState.players[0].unoCalled)
    }

    @Test
    fun callUnoWithTwoInvalidCardsFails() {
        val p1 = Player("p1", "Alice", hand = listOf(
            Card("1", CardColor.BLUE, CardValue.NUM_1, 1),
            Card("2", CardColor.YELLOW, CardValue.NUM_2, 2)
        ))
        val p2 = Player("p2", "Bob", hand = listOf(
            Card("3", CardColor.GREEN, CardValue.NUM_3, 3)
        ))
        // Top card is RED 5, so neither Blue 1 nor Yellow 2 is playable
        val state = GameState(
            id = "test",
            deck = emptyList(),
            discardPile = listOf(Card("top", CardColor.RED, CardValue.NUM_5, 5)),
            activeColor = CardColor.RED,
            players = listOf(p1, p2),
            status = GameStatus.PLAYING
        )

        assertFalse(UnoEngine.canPlayerCallUno(state, "p1"))
        val (nextState, success) = UnoEngine.executeCallUno(state, "p1")
        assertFalse(success)
        assertFalse(nextState.players[0].unoCalled)
    }

    @Test
    fun callUnoWithOneValidAndOneInvalidCardSucceeds() {
        val p1 = Player("p1", "Alice", hand = listOf(
            Card("1", CardColor.RED, CardValue.NUM_1, 1),    // Matches RED color!
            Card("2", CardColor.YELLOW, CardValue.NUM_2, 2) // Does not match
        ))
        val p2 = Player("p2", "Bob", hand = emptyList())
        val state = GameState(
            id = "test",
            deck = emptyList(),
            discardPile = listOf(Card("top", CardColor.RED, CardValue.NUM_5, 5)),
            activeColor = CardColor.RED,
            players = listOf(p1, p2),
            status = GameStatus.PLAYING
        )

        assertTrue(UnoEngine.canPlayerCallUno(state, "p1"))
        val (nextState, success) = UnoEngine.executeCallUno(state, "p1")
        assertTrue(success)
        assertTrue(nextState.players[0].unoCalled)
    }

    @Test
    fun reshuffleAfter25CardsDrawn() {
        val dummyDiscard = (1..30).map { Card("d$it", CardColor.RED, CardValue.NUM_1, 1) }
        val (newDeck, newDiscard, count) = UnoEngine.checkAndReshuffleAfter25Drawn(
            deck = emptyList(),
            discardPile = dummyDiscard,
            currentDrawnCount = 24,
            newlyDrawn = 1
        )
        // 25 cards threshold reached: discard pile reshuffled into deck except the top card
        assertEquals(29, newDeck.size)
        assertEquals(1, newDiscard.size)
        assertEquals(0, count)
    }

    @Test
    fun gameStateSerializationRoundtrip() {
        val p1 = Player("p1", "Host", "👑", false, listOf(Card("c1", CardColor.BLUE, CardValue.DRAW2, 20)))
        val p2 = Player("p2", "Guest", "🦊", false, listOf(Card("c2", CardColor.RED, CardValue.WILD4, 50)))
        val state = UnoEngine.initializeMatch(p1, p2, GameRules()).copy(
            isCustomRoom = true,
            roomCode = "54321",
            pendingDrawCount = 2
        )

        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val encoded = json.encodeToString(GameState.serializer(), state)
        val decoded = json.decodeFromString(GameState.serializer(), encoded)

        assertEquals(state.id, decoded.id)
        assertEquals(state.roomCode, decoded.roomCode)
        assertEquals(state.pendingDrawCount, decoded.pendingDrawCount)
        assertEquals(state.players[0].name, decoded.players[0].name)
        assertEquals(state.players[1].name, decoded.players[1].name)
        assertEquals(state.players[0].hand.size, decoded.players[0].hand.size)
        assertEquals(state.players[1].hand.size, decoded.players[1].hand.size)
    }

    @Test
    fun onlySkipAndReverseGrantExtraCardTurn() {
        val p1 = Player("p1", "Alice", hand = listOf(
            Card("s1", CardColor.RED, CardValue.SKIP, 20),
            Card("r1", CardColor.RED, CardValue.REVERSE, 20),
            Card("d1", CardColor.RED, CardValue.DRAW2, 20),
            Card("n1", CardColor.RED, CardValue.NUM_5, 5)
        ))
        val p2 = Player("p2", "Bob", hand = listOf(
            Card("b1", CardColor.BLUE, CardValue.NUM_1, 1)
        ))
        val state = GameState(
            id = "test",
            deck = listOf(
                Card("k1", CardColor.RED, CardValue.NUM_2, 2),
                Card("k2", CardColor.RED, CardValue.NUM_3, 3)
            ),
            discardPile = listOf(Card("top", CardColor.RED, CardValue.NUM_9, 9)),
            activeColor = CardColor.RED,
            players = listOf(p1, p2),
            currentTurnIndex = 0,
            status = GameStatus.PLAYING
        )

        // 1. Skip card: Player 1 keeps turn (plays 2nd card)
        val (stateAfterSkip, successSkip) = UnoEngine.executePlayCard(state, "p1", "s1")
        assertTrue(successSkip)
        assertEquals(0, stateAfterSkip.currentTurnIndex)

        // 2. Reverse card: Player 1 keeps turn (plays 2nd card in 2-player)
        val (stateAfterReverse, successReverse) = UnoEngine.executePlayCard(state, "p1", "r1")
        assertTrue(successReverse)
        assertEquals(0, stateAfterReverse.currentTurnIndex)

        // 3. Draw 2 card: Turn passes to opponent (player only plays 1 card)
        val (stateAfterDraw2, successDraw2) = UnoEngine.executePlayCard(state, "p1", "d1")
        assertTrue(successDraw2)
        assertEquals(1, stateAfterDraw2.currentTurnIndex)

        // 4. Number card: Turn passes to opponent (player only plays 1 card)
        val (stateAfterNum, successNum) = UnoEngine.executePlayCard(state, "p1", "n1")
        assertTrue(successNum)
        assertEquals(1, stateAfterNum.currentTurnIndex)
    }
}
