import { v4 as uuidv4 } from 'uuid';
import {
  AuthoritativeGameState,
  Card,
  CardColor,
  CardValue,
  ClientGameState,
  GameRules,
  GameStatus,
  LastAction,
  Player,
} from '../types';

export class UnoEngine {
  static createDeck(): Card[] {
    const colors: CardColor[] = ['RED', 'BLUE', 'GREEN', 'YELLOW'];
    const deck: Card[] = [];

    for (const color of colors) {
      // One 0 per color
      deck.push({ id: uuidv4(), color, value: 'NUM_0', score: 0 });

      // Two of each 1-9
      const numbers: CardValue[] = [
        'NUM_1', 'NUM_2', 'NUM_3', 'NUM_4', 'NUM_5',
        'NUM_6', 'NUM_7', 'NUM_8', 'NUM_9',
      ];
      for (const num of numbers) {
        const val = parseInt(num.replace('NUM_', ''), 10);
        deck.push({ id: uuidv4(), color, value: num, score: val });
        deck.push({ id: uuidv4(), color, value: num, score: val });
      }

      // Two Skips, Reverses, Draw Twos
      deck.push({ id: uuidv4(), color, value: 'SKIP', score: 20 });
      deck.push({ id: uuidv4(), color, value: 'SKIP', score: 20 });
      deck.push({ id: uuidv4(), color, value: 'REVERSE', score: 20 });
      deck.push({ id: uuidv4(), color, value: 'REVERSE', score: 20 });
      deck.push({ id: uuidv4(), color, value: 'DRAW2', score: 20 });
      deck.push({ id: uuidv4(), color, value: 'DRAW2', score: 20 });
    }

    // Four Wilds and four Wild Draw Fours
    for (let i = 0; i < 4; i++) {
      deck.push({ id: uuidv4(), color: 'WILD', value: 'WILD', score: 50 });
      deck.push({ id: uuidv4(), color: 'WILD', value: 'WILD4', score: 50 });
    }

    return this.shuffle(deck);
  }

  static shuffle<T>(array: T[]): T[] {
    const arr = [...array];
    for (let i = arr.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [arr[i], arr[j]] = [arr[j], arr[i]];
    }
    return arr;
  }

  static initializeMatch(
    player1: Player,
    player2: Player,
    roomCode: string,
    rules: GameRules = {
      stackDrawCards: true,
      drawToMatch: false,
      reverseAsSkipInTwoPlayer: true,
      sevenZeroRule: false,
      jumpIn: false,
      unoPenaltyCards: 2,
      challengeWild4: false,
    }
  ): AuthoritativeGameState {
    let deck = this.createDeck();

    // Deal 7 cards to each player
    const p1Hand = deck.slice(0, 7);
    const p2Hand = deck.slice(7, 14);
    deck = deck.slice(14);

    // Initial discard card (cannot start on WILD4)
    let initialIndex = deck.findIndex((c) => c.value !== 'WILD4');
    if (initialIndex === -1) initialIndex = 0;
    const initialCard = deck.splice(initialIndex, 1)[0];

    const activeColor: CardColor =
      initialCard.color === 'WILD' ? 'RED' : initialCard.color;

    const p1: Player = { ...player1, hand: p1Hand, unoCalled: false };
    const p2: Player = { ...player2, hand: p2Hand, unoCalled: false };

    let currentTurnIndex = 0;
    let pendingDraw = 0;

    // Handle initial action card
    if (initialCard.value === 'SKIP' || initialCard.value === 'REVERSE') {
      currentTurnIndex = 1; // P1 skipped, P2 starts
    } else if (initialCard.value === 'DRAW2') {
      if (rules.stackDrawCards) {
        pendingDraw = 2;
      } else {
        // P1 draws 2 and turn skips to P2
        const penalty = deck.splice(0, 2);
        p1.hand.push(...penalty);
        currentTurnIndex = 1;
      }
    }

    return {
      id: uuidv4(),
      deck,
      discardPile: [initialCard],
      players: [p1, p2],
      currentTurnIndex,
      activeColor,
      direction: 1,
      pendingDrawCount: pendingDraw,
      status: 'PLAYING',
      winnerId: null,
      lastAction: {
        playerId: 'system',
        text: `Match started! First card is ${initialCard.color} ${initialCard.value}`,
        timestamp: Date.now(),
        card: initialCard,
      },
      rules,
      unoCatchablePlayerId: null,
      turnSecondsRemaining: 20,
      hasDrawnThisTurn: false,
      isCustomRoom: true,
      roomCode,
      isBotTakeover: false,
      roomClosed: false,
      isRoomWaiting: false,
      cardsDrawnCount: 0,
      turnDeadline: Date.now() + 20000,
    };
  }

  static isCardPlayable(
    card: Card,
    topCard: Card,
    activeColor: CardColor,
    pendingDrawCount: number,
    rules: GameRules
  ): boolean {
    if (pendingDrawCount > 0) {
      if (!rules.stackDrawCards) return false;
      if (topCard.value === 'DRAW2' && card.value === 'DRAW2') return true;
      if (topCard.value === 'WILD4' && card.value === 'WILD4') return true;
      return false;
    }

    if (card.color === 'WILD' || card.value === 'WILD' || card.value === 'WILD4') {
      return true;
    }

    if (card.color === activeColor) return true;
    if (card.value === topCard.value) return true;

    return false;
  }

  static canPlayerCallUno(state: AuthoritativeGameState, playerId: string): boolean {
    const player = state.players.find((p) => p.id === playerId);
    if (!player) return false;
    if (player.unoCalled) return false;

    // Rule: available if holding <= 2 cards AND has at least 1 valid playable card
    if (player.hand.length > 2) return false;

    const topCard = state.discardPile[state.discardPile.length - 1];
    if (!topCard) return false;

    return player.hand.some((c) =>
      this.isCardPlayable(c, topCard, state.activeColor, state.pendingDrawCount, state.rules)
    );
  }

  static executePlayCard(
    state: AuthoritativeGameState,
    playerId: string,
    cardId: string,
    chosenColor?: CardColor
  ): { nextState: AuthoritativeGameState; success: boolean; error?: string } {
    if (state.status !== 'PLAYING') {
      return { nextState: state, success: false, error: 'Game is not in active state' };
    }

    const currentPlayer = state.players[state.currentTurnIndex];
    if (currentPlayer.id !== playerId) {
      return { nextState: state, success: false, error: 'Not your turn' };
    }

    const cardIndex = currentPlayer.hand.findIndex((c) => c.id === cardId);
    if (cardIndex === -1) {
      return { nextState: state, success: false, error: 'Card not found in your hand' };
    }

    const card = currentPlayer.hand[cardIndex];
    const topCard = state.discardPile[state.discardPile.length - 1];

    if (!this.isCardPlayable(card, topCard, state.activeColor, state.pendingDrawCount, state.rules)) {
      return { nextState: state, success: false, error: 'Illegal move for this card' };
    }

    // Valid move! Remove from hand
    const newHand = [...currentPlayer.hand];
    newHand.splice(cardIndex, 1);

    const opponentIndex = 1 - state.currentTurnIndex;
    const opponent = state.players[opponentIndex];

    const nextDiscard = [...state.discardPile, card];
    let nextActiveColor: CardColor = card.color;
    if (card.color === 'WILD') {
      nextActiveColor = chosenColor || 'RED';
    }

    let nextTurnIndex = opponentIndex;
    let nextPendingDraw = state.pendingDrawCount;
    let nextDeck = [...state.deck];

    let unoCatchable: string | null = null;
    let playerUnoCalled = currentPlayer.unoCalled;

    // Check UNO state
    if (newHand.length === 1) {
      if (!playerUnoCalled) {
        unoCatchable = currentPlayer.id; // Vulnerable to being caught!
      }
    } else if (newHand.length === 0) {
      playerUnoCalled = false;
    } else {
      playerUnoCalled = false;
    }

    const updatedPlayer: Player = {
      ...currentPlayer,
      hand: newHand,
      unoCalled: playerUnoCalled,
    };

    const updatedOpponent: Player = { ...opponent };

    // Action handling in 2-player UNO
    if (card.value === 'SKIP' || card.value === 'REVERSE') {
      // In 2 players, both Skip and Reverse skip the other player -> current player plays again!
      nextTurnIndex = state.currentTurnIndex;
    } else if (card.value === 'DRAW2') {
      if (state.rules.stackDrawCards) {
        nextPendingDraw += 2;
        nextTurnIndex = opponentIndex;
      } else {
        // Standard non-stacking: opponent draws 2 immediately and is skipped!
        const drawn = this.drawFromDeck(nextDeck, nextDiscard, 2);
        nextDeck = drawn.deck;
        updatedOpponent.hand = [...updatedOpponent.hand, ...drawn.cards];
        nextTurnIndex = state.currentTurnIndex; // Opponent skipped, player plays again!
      }
    } else if (card.value === 'WILD4') {
      if (state.rules.stackDrawCards) {
        nextPendingDraw += 4;
        nextTurnIndex = opponentIndex;
      } else {
        // Standard: opponent draws 4 and is skipped!
        const drawn = this.drawFromDeck(nextDeck, nextDiscard, 4);
        nextDeck = drawn.deck;
        updatedOpponent.hand = [...updatedOpponent.hand, ...drawn.cards];
        nextTurnIndex = state.currentTurnIndex;
      }
    }

    const players = state.currentTurnIndex === 0
      ? [updatedPlayer, updatedOpponent]
      : [updatedOpponent, updatedPlayer];

    // Check winner
    let winnerId: string | null = null;
    let status: GameStatus = state.status;
    if (newHand.length === 0) {
      winnerId = currentPlayer.id;
      status = 'GAMEOVER';
    }

    const nextState: AuthoritativeGameState = {
      ...state,
      deck: nextDeck,
      discardPile: nextDiscard,
      players,
      currentTurnIndex: nextTurnIndex,
      activeColor: nextActiveColor,
      pendingDrawCount: nextPendingDraw,
      status,
      winnerId,
      lastAction: {
        playerId: currentPlayer.id,
        text: `${currentPlayer.name} played ${card.color} ${card.value}`,
        timestamp: Date.now(),
        card,
      },
      unoCatchablePlayerId: unoCatchable,
      turnSecondsRemaining: 20,
      hasDrawnThisTurn: false,
      turnDeadline: Date.now() + 20000,
    };

    return { nextState, success: true };
  }

  static executeDrawCard(
    state: AuthoritativeGameState,
    playerId: string
  ): { nextState: AuthoritativeGameState; drawnCards: Card[] } {
    const currentPlayer = state.players[state.currentTurnIndex];
    if (currentPlayer.id !== playerId || state.status !== 'PLAYING') {
      return { nextState: state, drawnCards: [] };
    }

    let nextDeck = [...state.deck];
    const nextDiscard = [...state.discardPile];

    let countToDraw = 1;
    let nextTurnIndex = state.currentTurnIndex;
    let nextPendingDraw = state.pendingDrawCount;

    if (state.pendingDrawCount > 0) {
      // Must draw all stacked penalty cards, and turn passes!
      countToDraw = state.pendingDrawCount;
      nextPendingDraw = 0;
      nextTurnIndex = 1 - state.currentTurnIndex; // Turn ends after drawing penalty
    }

    const drawnResult = this.drawFromDeck(nextDeck, nextDiscard, countToDraw);
    nextDeck = drawnResult.deck;
    const drawnCards = drawnResult.cards;

    const updatedPlayer: Player = {
      ...currentPlayer,
      hand: [...currentPlayer.hand, ...drawnCards],
      unoCalled: false,
    };

    const players = state.currentTurnIndex === 0
      ? [updatedPlayer, state.players[1]]
      : [state.players[0], updatedPlayer];

    const nextState: AuthoritativeGameState = {
      ...state,
      deck: nextDeck,
      players,
      currentTurnIndex: nextTurnIndex,
      pendingDrawCount: nextPendingDraw,
      hasDrawnThisTurn: countToDraw === 1,
      cardsDrawnCount: state.cardsDrawnCount + countToDraw,
      lastAction: {
        playerId: currentPlayer.id,
        text: `${currentPlayer.name} drew ${countToDraw} card${countToDraw > 1 ? 's' : ''}`,
        timestamp: Date.now(),
      },
      turnSecondsRemaining: 20,
      turnDeadline: Date.now() + 20000,
    };

    return { nextState, drawnCards };
  }

  static executeSkipTurn(
    state: AuthoritativeGameState,
    playerId: string
  ): AuthoritativeGameState {
    const currentPlayer = state.players[state.currentTurnIndex];
    if (currentPlayer.id !== playerId || !state.hasDrawnThisTurn) {
      return state;
    }

    const nextTurnIndex = 1 - state.currentTurnIndex;
    return {
      ...state,
      currentTurnIndex: nextTurnIndex,
      hasDrawnThisTurn: false,
      turnSecondsRemaining: 20,
      turnDeadline: Date.now() + 20000,
      lastAction: {
        playerId: currentPlayer.id,
        text: `${currentPlayer.name} passed turn`,
        timestamp: Date.now(),
      },
    };
  }

  static executeCallUno(
    state: AuthoritativeGameState,
    playerId: string
  ): { nextState: AuthoritativeGameState; success: boolean } {
    if (!this.canPlayerCallUno(state, playerId)) {
      return { nextState: state, success: false };
    }

    const playerIndex = state.players.findIndex((p) => p.id === playerId);
    if (playerIndex === -1) return { nextState: state, success: false };

    const updatedPlayers = [...state.players];
    updatedPlayers[playerIndex] = {
      ...updatedPlayers[playerIndex],
      unoCalled: true,
    };

    const nextState: AuthoritativeGameState = {
      ...state,
      players: updatedPlayers,
      unoCatchablePlayerId: null, // Saved from being caught!
      lastAction: {
        playerId,
        text: `${updatedPlayers[playerIndex].name} called UNO! 📢`,
        timestamp: Date.now(),
      },
    };

    return { nextState, success: true };
  }

  static executeCatchUno(
    state: AuthoritativeGameState,
    catcherId: string
  ): { nextState: AuthoritativeGameState; success: boolean } {
    if (!state.unoCatchablePlayerId) {
      return { nextState: state, success: false };
    }

    const caughtId = state.unoCatchablePlayerId;
    if (caughtId === catcherId) {
      return { nextState: state, success: false };
    }

    const caughtIndex = state.players.findIndex((p) => p.id === caughtId);
    if (caughtIndex === -1) return { nextState: state, success: false };

    let nextDeck = [...state.deck];
    const nextDiscard = [...state.discardPile];

    // Penalty: draw 2 cards
    const drawn = this.drawFromDeck(nextDeck, nextDiscard, state.rules.unoPenaltyCards || 2);
    nextDeck = drawn.deck;

    const caughtPlayer = state.players[caughtIndex];
    const updatedCaught: Player = {
      ...caughtPlayer,
      hand: [...caughtPlayer.hand, ...drawn.cards],
      unoCalled: false,
    };

    const updatedPlayers = [...state.players];
    updatedPlayers[caughtIndex] = updatedCaught;

    const catcher = state.players.find((p) => p.id === catcherId);

    const nextState: AuthoritativeGameState = {
      ...state,
      deck: nextDeck,
      players: updatedPlayers,
      unoCatchablePlayerId: null,
      lastAction: {
        playerId: catcherId,
        text: `${catcher?.name || 'Player'} caught ${caughtPlayer.name} forgetting UNO! +2 penalty cards`,
        timestamp: Date.now(),
      },
    };

    return { nextState, success: true };
  }

  static executeTimerTimeout(state: AuthoritativeGameState): AuthoritativeGameState {
    if (state.status !== 'PLAYING') return state;

    const currentPlayer = state.players[state.currentTurnIndex];
    let nextDeck = [...state.deck];
    const nextDiscard = [...state.discardPile];

    // Penalty for running out of time: draw 1 card and turn passes!
    const drawn = this.drawFromDeck(nextDeck, nextDiscard, 1);
    nextDeck = drawn.deck;

    const updatedPlayer: Player = {
      ...currentPlayer,
      hand: [...currentPlayer.hand, ...drawn.cards],
      unoCalled: false,
    };

    const nextTurnIndex = 1 - state.currentTurnIndex;
    const players = state.currentTurnIndex === 0
      ? [updatedPlayer, state.players[1]]
      : [state.players[0], updatedPlayer];

    return {
      ...state,
      deck: nextDeck,
      players,
      currentTurnIndex: nextTurnIndex,
      pendingDrawCount: 0,
      hasDrawnThisTurn: false,
      turnSecondsRemaining: 20,
      turnDeadline: Date.now() + 20000,
      lastAction: {
        playerId: 'system',
        text: `${currentPlayer.name} ran out of time! Drew 1 card penalty`,
        timestamp: Date.now(),
      },
    };
  }

  private static drawFromDeck(
    deck: Card[],
    discard: Card[],
    count: number
  ): { deck: Card[]; cards: Card[] } {
    let currentDeck = [...deck];
    const drawn: Card[] = [];

    for (let i = 0; i < count; i++) {
      if (currentDeck.length === 0) {
        if (discard.length > 1) {
          // Keep top card, reshuffle remaining discard
          const topCard = discard[discard.length - 1];
          const cardsToReshuffle = discard.slice(0, discard.length - 1);
          currentDeck = this.shuffle(cardsToReshuffle);
          discard.length = 0;
          discard.push(topCard);
        } else {
          break; // No cards left
        }
      }
      if (currentDeck.length > 0) {
        drawn.push(currentDeck.shift()!);
      }
    }

    return { deck: currentDeck, cards: drawn };
  }

  /**
   * Produce the client-facing view for a specific player:
   * Replaces the opponent's private hand with an empty array,
   * while keeping handCount accurate for UI rendering.
   */
  static sanitizeForClient(
    state: AuthoritativeGameState,
    recipientGuestId: string
  ): ClientGameState {
    return {
      id: state.id,
      deckCount: state.deck.length,
      discardPile: state.discardPile,
      players: state.players.map((p) => ({
        id: p.id,
        name: p.name,
        avatar: p.avatar,
        isAi: p.isAi,
        // PRIVATE HAND: Only recipient gets their real cards! Opponent gets empty list
        hand: p.id === recipientGuestId ? p.hand : [],
        handCount: p.hand.length,
        unoCalled: p.unoCalled,
        score: p.score,
        isReady: p.isReady,
        isConnected: p.isConnected,
      })),
      currentTurnIndex: state.currentTurnIndex,
      activeColor: state.activeColor,
      direction: state.direction,
      pendingDrawCount: state.pendingDrawCount,
      status: state.status,
      winnerId: state.winnerId,
      lastAction: state.lastAction,
      rules: state.rules,
      unoCatchablePlayerId: state.unoCatchablePlayerId,
      turnSecondsRemaining: Math.max(
        0,
        Math.min(20, Math.ceil((state.turnDeadline - Date.now()) / 1000))
      ),
      hasDrawnThisTurn: state.hasDrawnThisTurn,
      isCustomRoom: state.isCustomRoom,
      roomCode: state.roomCode,
      isBotTakeover: state.isBotTakeover,
      roomClosed: state.roomClosed,
      isRoomWaiting: state.isRoomWaiting,
      cardsDrawnCount: state.cardsDrawnCount,
    };
  }
}
