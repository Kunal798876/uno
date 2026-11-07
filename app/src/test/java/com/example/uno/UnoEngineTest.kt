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
}
