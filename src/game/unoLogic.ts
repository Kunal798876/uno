import { Card, CardColor, CardValue, GameRules, GameState, Player, DEFAULT_RULES } from './types';

export const COLORS: Array<'red' | 'blue' | 'green' | 'yellow'> = ['red', 'blue', 'green', 'yellow'];

/**
 * Creates a standard 108-card UNO deck:
 * - 19 Red, 19 Blue, 19 Green, 19 Yellow (one 0, two of 1-9)
 * - 8 Skip (2 per color)
 * - 8 Reverse (2 per color)
 * - 8 Draw Two (2 per color)
 * - 4 Wild
 * - 4 Wild Draw Four
 */
export function createStandardDeck(): Card[] {
  const cards: Card[] = [];
  let cardIndex = 0;

  for (const color of COLORS) {
    // One 0
    cards.push({
      id: `${color}-0-${cardIndex++}`,
      color,
      value: '0',
      score: 0,
    });

    // Two of 1-9
    for (let i = 1; i <= 9; i++) {
      const val = `${i}` as CardValue;
      cards.push({
        id: `${color}-${val}-a-${cardIndex++}`,
        color,
        value: val,
        score: i,
      });
      cards.push({
        id: `${color}-${val}-b-${cardIndex++}`,
        color,
        value: val,
        score: i,
      });
    }

    // Two Skip, Two Reverse, Two Draw Two
    const actions: CardValue[] = ['skip', 'reverse', 'draw2'];
    for (const action of actions) {
      cards.push({
        id: `${color}-${action}-a-${cardIndex++}`,
        color,
        value: action,
        score: 20,
      });
      cards.push({
        id: `${color}-${action}-b-${cardIndex++}`,
        color,
        value: action,
        score: 20,
      });
    }
  }

  // 4 Wild, 4 Wild Draw Four
  for (let i = 0; i < 4; i++) {
    cards.push({
      id: `wild-${i}-${cardIndex++}`,
      color: 'wild',
      value: 'wild',
      score: 50,
    });
    cards.push({
      id: `wild4-${i}-${cardIndex++}`,
      color: 'wild',
      value: 'wild4',
      score: 50,
    });
  }

  return cards;
}

/**
 * Fisher-Yates shuffle
 */
export function shuffleDeck(deck: Card[]): Card[] {
  const array = [...deck];
  for (let i = array.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [array[i], array[j]] = [array[j], array[i]];
  }
  return array;
}

/**
 * Check if a card can be legally played on top of the discard pile
 */
export function isCardPlayable(
  card: Card,
  topCard: Card,
  activeColor: CardColor,
  pendingDrawCount: number,
  rules: GameRules
): boolean {
  // If there's an active draw penalty stacked (+2 or +4)
  if (pendingDrawCount > 0) {
    if (rules.stackDrawCards) {
      // If +2 is active, can stack another +2
      if (topCard.value === 'draw2' && card.value === 'draw2') return true;
      // If +4 is active, can stack another +4
      if (topCard.value === 'wild4' && card.value === 'wild4') return true;
    }
    // Cannot play normal cards while pending draw penalty exists
    return false;
  }

  // Wild cards can always be played
  if (card.color === 'wild' || card.value === 'wild' || card.value === 'wild4') {
    return true;
  }

  // Matches the chosen active color
  if (card.color === activeColor) {
    return true;
  }

  // Matches the face value/action of top card (e.g. Red 5 on Blue 5, or Green Skip on Red Skip)
  if (card.value === topCard.value) {
    return true;
  }

  return false;
}

/**
 * Initialize a new 2-player match
 */
export function initializeMatch(
  player1: { id: string; name: string; avatar: string; isAi: boolean },
  player2: { id: string; name: string; avatar: string; isAi: boolean },
  rules: GameRules = DEFAULT_RULES
): GameState {
  let deck = shuffleDeck(createStandardDeck());

  const hand1 = deck.splice(0, 7);
  const hand2 = deck.splice(0, 7);

  // Top card cannot be a Wild4 initially according to official UNO rules
  let topCardIndex = deck.findIndex((c) => c.value !== 'wild4');
  if (topCardIndex === -1) topCardIndex = 0;
  const topCard = deck.splice(topCardIndex, 1)[0];

  const discardPile = [topCard];
  let activeColor = (topCard.color === 'wild' ? 'red' : topCard.color) as 'red' | 'blue' | 'green' | 'yellow';
  let currentTurnIndex = 0; // Player 1 starts
  let pendingDrawCount = 0;

  // Initial card action if top card is an action card
  let initialActionText = `Game started! Top card is ${topCard.color.toUpperCase()} ${topCard.value}.`;

  if (topCard.value === 'draw2') {
    const extra = deck.splice(0, 2);
    hand1.push(...extra);
    currentTurnIndex = 1; // Skips player 1, starts with player 2
    initialActionText = `Top card is Draw Two! 2 cards added to ${player1.name}, turn to ${player2.name}.`;
  } else if (topCard.value === 'skip' || (topCard.value === 'reverse' && rules.reverseAsSkipInTwoPlayer)) {
    currentTurnIndex = 1; // Skips player 1, starts with player 2
    initialActionText = `Top card is ${topCard.value.toUpperCase()}! Turn skipped to ${player2.name}.`;
  }

  const players: Player[] = [
    { ...player1, hand: hand1, unoCalled: false, score: 0 },
    { ...player2, hand: hand2, unoCalled: false, score: 0 },
  ];

  return {
    id: `game-${Date.now()}-${Math.floor(Math.random() * 1000)}`,
    deck,
    discardPile,
    players,
    currentTurnIndex,
    activeColor,
    direction: 1,
    pendingDrawCount,
    status: 'playing',
    winnerId: null,
    lastAction: {
      playerId: 'system',
      text: initialActionText,
      timestamp: Date.now(),
      card: topCard,
    },
    rules,
    unoCatchablePlayerId: null,
  };
}

/**
 * Handle drawing cards from deck (with reshuffle if deck is low)
 */
export function drawFromDeck(deck: Card[], discardPile: Card[], count: number): { drawn: Card[]; newDeck: Card[]; newDiscard: Card[] } {
  let currentDeck = [...deck];
  let currentDiscard = [...discardPile];
  const drawn: Card[] = [];

  for (let i = 0; i < count; i++) {
    if (currentDeck.length === 0) {
      if (currentDiscard.length <= 1) {
        // No cards left to shuffle
        break;
      }
      const topCard = currentDiscard.pop()!;
      currentDeck = shuffleDeck(currentDiscard);
      currentDiscard = [topCard];
    }
    const card = currentDeck.pop();
    if (card) drawn.push(card);
  }

  return { drawn, newDeck: currentDeck, newDiscard: currentDiscard };
}

/**
 * Play a card from player's hand
 */
export function executePlayCard(
  state: GameState,
  playerId: string,
  cardId: string,
  chosenColor?: 'red' | 'blue' | 'green' | 'yellow'
): { nextState: GameState; success: boolean; error?: string } {
  if (state.status !== 'playing') {
    return { nextState: state, success: false, error: 'Game is not in playing state' };
  }

  const playerIndex = state.players.findIndex((p) => p.id === playerId);
  if (playerIndex === -1) {
    return { nextState: state, success: false, error: 'Player not found' };
  }

  const isCurrentTurn = state.currentTurnIndex === playerIndex;

  const player = state.players[playerIndex];
  const cardIndex = player.hand.findIndex((c) => c.id === cardId);
  if (cardIndex === -1) {
    return { nextState: state, success: false, error: 'Card not in hand' };
  }

  const card = player.hand[cardIndex];
  const topCard = state.discardPile[state.discardPile.length - 1];

  // Jump-In rule check: if jumpIn enabled and player plays exact same color & value
  const isJumpIn = !isCurrentTurn && state.rules.jumpIn && card.color === topCard.color && card.value === topCard.value;

  if (!isCurrentTurn && !isJumpIn) {
    return { nextState: state, success: false, error: 'Not your turn' };
  }

  const legal = isCardPlayable(card, topCard, state.activeColor, state.pendingDrawCount, state.rules);
  if (!legal) {
    return { nextState: state, success: false, error: 'Illegal card play' };
  }

  // Remove card from hand
  const newHand = [...player.hand];
  newHand.splice(cardIndex, 1);

  // New discard pile
  const newDiscard = [...state.discardPile, card];

  // Determine active color
  let nextColor = state.activeColor;
  if (card.color === 'wild') {
    nextColor = chosenColor || 'red';
  } else {
    nextColor = card.color;
  }

  let nextDirection = state.direction;
  const opponentIndex = 1 - playerIndex;
  let nextTurnIndex = opponentIndex;
  let actionText = `${player.name} played ${card.color === 'wild' ? 'WILD' : card.color.toUpperCase()} ${card.value.toUpperCase()}`;
  let currentDeck = [...state.deck];
  let currentDiscard = newDiscard;

  // Clone players array
  let playersList = [...state.players];
  playersList[playerIndex] = {
    ...player,
    hand: newHand,
    unoCalled: newHand.length === 1 ? player.unoCalled : false,
  };

  // Action card mechanics
  if (card.value === 'draw2') {
    // Automatically draw 2 cards and add directly to opponent's hand, skipping opponent's turn!
    const { drawn, newDeck, newDiscard: disc } = drawFromDeck(currentDeck, currentDiscard, 2);
    currentDeck = newDeck;
    currentDiscard = disc;

    const opp = playersList[opponentIndex];
    playersList[opponentIndex] = {
      ...opp,
      hand: [...opp.hand, ...drawn],
      unoCalled: false,
    };

    // In 2-player UNO, +2 skips opponent, so current player gets another turn!
    nextTurnIndex = playerIndex;
    actionText += `! 2 cards automatically added to ${opp.name} (+ skips turn)`;
  } else if (card.value === 'wild4') {
    // Automatically draw 4 cards and add directly to opponent's hand, skipping opponent's turn!
    const { drawn, newDeck, newDiscard: disc } = drawFromDeck(currentDeck, currentDiscard, 4);
    currentDeck = newDeck;
    currentDiscard = disc;

    const opp = playersList[opponentIndex];
    playersList[opponentIndex] = {
      ...opp,
      hand: [...opp.hand, ...drawn],
      unoCalled: false,
    };

    // In 2-player UNO, +4 skips opponent, so current player gets another turn!
    nextTurnIndex = playerIndex;
    actionText += `! 4 cards automatically added to ${opp.name} & chose ${nextColor.toUpperCase()} (+ skips turn)`;
  } else if (card.value === 'skip') {
    // In 2-player UNO, Skip skips opponent, giving current player another turn!
    nextTurnIndex = playerIndex;
    actionText += ` (Skipped! ${player.name} gets another turn)`;
  } else if (card.value === 'reverse') {
    nextDirection = (nextDirection * -1) as 1 | -1;
    if (state.rules.reverseAsSkipInTwoPlayer) {
      // In official 2-player UNO, Reverse also skips opponent!
      nextTurnIndex = playerIndex;
      actionText += ` (Reversed! ${player.name} goes again)`;
    }
  } else if (card.color === 'wild') {
    actionText += ` and chose ${nextColor.toUpperCase()}`;
  }

  if (state.rules.sevenZeroRule) {
    if (card.value === '7') {
      // Swap hands with opponent
      const p1Hand = playersList[0].hand;
      const p2Hand = playersList[1].hand;
      playersList[0] = { ...playersList[0], hand: p2Hand, unoCalled: false };
      playersList[1] = { ...playersList[1], hand: p1Hand, unoCalled: false };
      actionText += ` (7 Rule: Swapped hands!)`;
    }
  }

  // UNO Check: if player now has 1 card
  let catchablePlayerId: string | null = null;
  const updatedPlayer = playersList[playerIndex];
  if (updatedPlayer.hand.length === 1) {
    if (!updatedPlayer.unoCalled) {
      // Opponent can catch them!
      catchablePlayerId = updatedPlayer.id;
    }
  } else {
    // If not 1 card, reset unoCalled
    playersList[playerIndex] = { ...updatedPlayer, unoCalled: false };
  }

  // Check Win Condition
  let winnerId: string | null = null;
  let gameStatus: GameState['status'] = 'playing';

  if (updatedPlayer.hand.length === 0) {
    winnerId = updatedPlayer.id;
    gameStatus = 'gameover';
    actionText = `🎉 ${updatedPlayer.name} has played their last card and WON THE GAME!`;
  }

  const nextState: GameState = {
    ...state,
    deck: currentDeck,
    discardPile: currentDiscard,
    players: playersList,
    currentTurnIndex: nextTurnIndex,
    activeColor: nextColor,
    direction: nextDirection,
    pendingDrawCount: 0,
    status: gameStatus,
    winnerId,
    lastAction: {
      playerId,
      text: actionText,
      timestamp: Date.now(),
      card,
    },
    unoCatchablePlayerId: catchablePlayerId,
  };

  return { nextState, success: true };
}

/**
 * Handle player drawing cards
 */
export function executeDrawCard(
  state: GameState,
  playerId: string
): { nextState: GameState; drawnCards: Card[]; success: boolean } {
  if (state.status !== 'playing') {
    return { nextState: state, drawnCards: [], success: false };
  }

  const playerIndex = state.players.findIndex((p) => p.id === playerId);
  if (playerIndex === -1 || state.currentTurnIndex !== playerIndex) {
    return { nextState: state, drawnCards: [], success: false };
  }

  const player = state.players[playerIndex];
  const countToDraw = state.pendingDrawCount > 0 ? state.pendingDrawCount : 1;
  const wasPenalty = state.pendingDrawCount > 0;

  const { drawn, newDeck, newDiscard } = drawFromDeck(state.deck, state.discardPile, countToDraw);

  const updatedHand = [...player.hand, ...drawn];
  const playersList = [...state.players];
  playersList[playerIndex] = {
    ...player,
    hand: updatedHand,
    unoCalled: false,
  };

  const opponentIndex = 1 - playerIndex;
  const nextTurnIndex = opponentIndex; // Turn passes after drawing
  const actionText = wasPenalty
    ? `${player.name} drew ${countToDraw} penalty cards!`
    : `${player.name} drew a card.`;

  const nextState: GameState = {
    ...state,
    deck: newDeck,
    discardPile: newDiscard,
    players: playersList,
    currentTurnIndex: nextTurnIndex,
    pendingDrawCount: 0,
    lastAction: {
      playerId,
      text: actionText,
      timestamp: Date.now(),
    },
    unoCatchablePlayerId: null,
  };

  return { nextState, drawnCards: drawn, success: true };
}

/**
 * Player calls UNO for themselves
 */
export function executeCallUno(
  state: GameState,
  playerId: string
): { nextState: GameState; success: boolean; message: string } {
  const playerIndex = state.players.findIndex((p) => p.id === playerId);
  if (playerIndex === -1) {
    return { nextState: state, success: false, message: 'Player not found' };
  }

  const player = state.players[playerIndex];
  // Can call UNO when at 2 cards (anticipation) or 1 card
  if (player.hand.length <= 2) {
    const playersList = [...state.players];
    playersList[playerIndex] = {
      ...player,
      unoCalled: true,
    };

    const nextState: GameState = {
      ...state,
      players: playersList,
      unoCatchablePlayerId: null, // Safe now!
      lastAction: {
        playerId,
        text: `📢 ${player.name} called "UNO!"`,
        timestamp: Date.now(),
      },
    };

    return { nextState, success: true, message: `${player.name} called UNO!` };
  }

  return { nextState: state, success: false, message: 'You have more than 2 cards!' };
}

/**
 * Opponent catches player who didn't call UNO
 */
export function executeCatchUno(
  state: GameState,
  catcherId: string
): { nextState: GameState; success: boolean; message: string } {
  const targetId = state.unoCatchablePlayerId;
  if (!targetId || targetId === catcherId) {
    return { nextState: state, success: false, message: 'No one to catch for UNO failure!' };
  }

  const targetIndex = state.players.findIndex((p) => p.id === targetId);
  if (targetIndex === -1) {
    return { nextState: state, success: false, message: 'Target not found' };
  }

  const catcher = state.players.find((p) => p.id === catcherId);
  const target = state.players[targetIndex];

  // Draw penalty cards
  const penaltyCount = state.rules.unoPenaltyCards || 2;
  const { drawn, newDeck, newDiscard } = drawFromDeck(state.deck, state.discardPile, penaltyCount);

  const playersList = [...state.players];
  playersList[targetIndex] = {
    ...target,
    hand: [...target.hand, ...drawn],
    unoCalled: false,
  };

  const nextState: GameState = {
    ...state,
    deck: newDeck,
    discardPile: newDiscard,
    players: playersList,
    unoCatchablePlayerId: null,
    lastAction: {
      playerId: catcherId,
      text: `🚨 ${catcher?.name || 'Player'} CAUGHT ${target.name} for not calling UNO! (+${penaltyCount} penalty)`,
      timestamp: Date.now(),
    },
  };

  return {
    nextState,
    success: true,
    message: `${catcher?.name || 'Player'} caught ${target.name}!`,
  };
}

/**
 * AI Decision Maker
 */
export function getAiDecision(
  state: GameState,
  aiPlayerId: string
): {
  action: 'play' | 'draw' | 'callUno';
  cardId?: string;
  chosenColor?: 'red' | 'blue' | 'green' | 'yellow';
  callUnoFirst?: boolean;
} {
  const player = state.players.find((p) => p.id === aiPlayerId);
  if (!player) return { action: 'draw' };

  const topCard = state.discardPile[state.discardPile.length - 1];

  // If opponent can be caught for UNO failure, AI catches them with high probability
  if (state.unoCatchablePlayerId && state.unoCatchablePlayerId !== aiPlayerId) {
    // 80% chance AI notices
    if (Math.random() < 0.85) {
      return { action: 'callUno' };
    }
  }

  // Find playable cards
  const playableCards = player.hand.filter((card) =>
    isCardPlayable(card, topCard, state.activeColor, state.pendingDrawCount, state.rules)
  );

  if (playableCards.length === 0) {
    return { action: 'draw' };
  }

  // Evaluate color counts in AI hand to pick best color for Wilds
  const colorCounts: Record<'red' | 'blue' | 'green' | 'yellow', number> = {
    red: 0,
    blue: 0,
    green: 0,
    yellow: 0,
  };
  for (const card of player.hand) {
    if (card.color !== 'wild') {
      colorCounts[card.color]++;
    }
  }

  let bestColor: 'red' | 'blue' | 'green' | 'yellow' = 'red';
  let maxCount = -1;
  for (const c of COLORS) {
    if (colorCounts[c] > maxCount) {
      maxCount = colorCounts[c];
      bestColor = c;
    }
  }

  // Strategy depending on difficulty
  const difficulty = state.rules.aiDifficulty;
  let selectedCard: Card;

  if (difficulty === 'easy') {
    // Pick randomly among playable cards
    selectedCard = playableCards[Math.floor(Math.random() * playableCards.length)];
  } else {
    // Smart/Hard AI: Prioritize attack/action cards or matching highest count color
    // 1. Stack if pending draw
    const drawCards = playableCards.filter((c) => c.value === 'draw2' || c.value === 'wild4');
    if (state.pendingDrawCount > 0 && drawCards.length > 0) {
      selectedCard = drawCards[0];
    } else {
      // Prioritize Skip/Reverse in 2-player mode to steal extra turn
      const turnStealers = playableCards.filter((c) => c.value === 'skip' || c.value === 'reverse');
      const actionAttackers = playableCards.filter((c) => c.value === 'draw2');
      const matchingColor = playableCards.filter((c) => c.color === bestColor);
      const normalCards = playableCards.filter((c) => c.color !== 'wild');

      if (turnStealers.length > 0) {
        selectedCard = turnStealers[0];
      } else if (actionAttackers.length > 0) {
        selectedCard = actionAttackers[0];
      } else if (matchingColor.length > 0) {
        selectedCard = matchingColor[0];
      } else if (normalCards.length > 0) {
        selectedCard = normalCards[0];
      } else {
        selectedCard = playableCards[0];
      }
    }
  }

  // Should AI call UNO when down to 2 cards?
  const willHaveOneCard = player.hand.length === 2;
  const callUnoFirst = willHaveOneCard;

  return {
    action: 'play',
    cardId: selectedCard.id,
    chosenColor: bestColor,
    callUnoFirst,
  };
}

/**
 * Score calculation for end-of-game victory points
 */
export function calculateHandScore(hand: Card[]): number {
  return hand.reduce((total, c) => total + c.score, 0);
}
