import test from 'node:test';
import assert from 'node:assert/strict';
import {
  createStandardDeck,
  isCardPlayable,
  initializeMatch,
  executePlayCard,
  executeDrawCard,
  executeCallUno,
  executeCatchUno,
} from './unoLogic';
import { DEFAULT_RULES, Card } from './types';

test('createStandardDeck creates a valid 108 card UNO deck', () => {
  const deck = createStandardDeck();
  assert.equal(deck.length, 108);

  const wildCount = deck.filter((c) => c.value === 'wild').length;
  const wild4Count = deck.filter((c) => c.value === 'wild4').length;
  assert.equal(wildCount, 4);
  assert.equal(wild4Count, 4);

  const skips = deck.filter((c) => c.value === 'skip').length;
  const reverses = deck.filter((c) => c.value === 'reverse').length;
  const draw2s = deck.filter((c) => c.value === 'draw2').length;
  assert.equal(skips, 8);
  assert.equal(reverses, 8);
  assert.equal(draw2s, 8);
});

test('isCardPlayable correctly validates standard matching rules', () => {
  const topCard: Card = { id: '1', color: 'red', value: '5', score: 5 };

  // Match color
  const sameColor: Card = { id: '2', color: 'red', value: '9', score: 9 };
  assert.equal(isCardPlayable(sameColor, topCard, 'red', 0, DEFAULT_RULES), true);

  // Match value
  const sameValue: Card = { id: '3', color: 'blue', value: '5', score: 5 };
  assert.equal(isCardPlayable(sameValue, topCard, 'red', 0, DEFAULT_RULES), true);

  // Wild is always playable
  const wildCard: Card = { id: '4', color: 'wild', value: 'wild', score: 50 };
  assert.equal(isCardPlayable(wildCard, topCard, 'red', 0, DEFAULT_RULES), true);

  // Mismatch
  const mismatch: Card = { id: '5', color: 'green', value: '8', score: 8 };
  assert.equal(isCardPlayable(mismatch, topCard, 'red', 0, DEFAULT_RULES), false);
});

test('playing Draw2 automatically adds 2 cards to opponent hand and skips their turn', () => {
  const p1 = { id: 'p1', name: 'Alice', avatar: '🦊', isAi: false };
  const p2 = { id: 'p2', name: 'Bob', avatar: '🤖', isAi: true };
  const state = initializeMatch(p1, p2);

  const draw2Card: Card = { id: 'test-d2', color: state.activeColor, value: 'draw2', score: 20 };
  state.players[0].hand = [draw2Card, { id: 'c2', color: 'blue', value: '1', score: 1 }];
  state.currentTurnIndex = 0;
  const initialP2HandSize = state.players[1].hand.length;

  const result = executePlayCard(state, 'p1', 'test-d2');
  assert.equal(result.success, true);
  // P2 should have received 2 extra cards automatically
  assert.equal(result.nextState.players[1].hand.length, initialP2HandSize + 2);
  // In 2-player UNO, Draw2 skips opponent, giving P1 another turn
  assert.equal(result.nextState.currentTurnIndex, 0);
});

test('2-player Reverse and Skip give another turn to current player', () => {
  const p1 = { id: 'p1', name: 'Alice', avatar: '🦊', isAi: false };
  const p2 = { id: 'p2', name: 'Bob', avatar: '🤖', isAi: true };
  const state = initializeMatch(p1, p2, { ...DEFAULT_RULES, reverseAsSkipInTwoPlayer: true });

  // Manually give p1 a skip card matching top card
  const skipCard: Card = { id: 'test-skip', color: state.activeColor, value: 'skip', score: 20 };
  state.players[0].hand = [skipCard, { id: 'c2', color: 'blue', value: '1', score: 1 }];
  state.currentTurnIndex = 0;
  state.pendingDrawCount = 0;

  const result = executePlayCard(state, 'p1', 'test-skip');
  assert.equal(result.success, true);
  // Turn should remain with p1 because in 2-player UNO skip gives another turn
  assert.equal(result.nextState.currentTurnIndex, 0);
});

test('Drawing cards passes the turn and clears penalty', () => {
  const p1 = { id: 'p1', name: 'Alice', avatar: '🦊', isAi: false };
  const p2 = { id: 'p2', name: 'Bob', avatar: '🤖', isAi: true };
  const state = initializeMatch(p1, p2);

  state.currentTurnIndex = 0;
  state.pendingDrawCount = 2; // e.g. from +2
  const initialHandSize = state.players[0].hand.length;

  const result = executeDrawCard(state, 'p1');
  assert.equal(result.success, true);
  assert.equal(result.drawnCards.length, 2);
  assert.equal(result.nextState.players[0].hand.length, initialHandSize + 2);
  assert.equal(result.nextState.pendingDrawCount, 0);
  assert.equal(result.nextState.currentTurnIndex, 1);
});

test('UNO call and Catch UNO penalty works correctly', () => {
  const p1 = { id: 'p1', name: 'Alice', avatar: '🦊', isAi: false };
  const p2 = { id: 'p2', name: 'Bob', avatar: '🤖', isAi: true };
  const state = initializeMatch(p1, p2);

  // p1 has 2 cards, plays 1 card without calling uno
  const cardToPlay: Card = { id: 'play1', color: state.activeColor, value: '1', score: 1 };
  state.players[0].hand = [cardToPlay, { id: 'rem1', color: 'green', value: '2', score: 2 }];
  state.currentTurnIndex = 0;
  state.pendingDrawCount = 0;

  const playResult = executePlayCard(state, 'p1', 'play1');
  assert.equal(playResult.success, true);
  assert.equal(playResult.nextState.players[0].hand.length, 1);
  assert.equal(playResult.nextState.unoCatchablePlayerId, 'p1');

  // p2 catches p1
  const catchResult = executeCatchUno(playResult.nextState, 'p2');
  assert.equal(catchResult.success, true);
  // p1 should receive 2 penalty cards
  assert.equal(catchResult.nextState.players[0].hand.length, 3);
  assert.equal(catchResult.nextState.unoCatchablePlayerId, null);
});

test('Winning condition when last card is played', () => {
  const p1 = { id: 'p1', name: 'Alice', avatar: '🦊', isAi: false };
  const p2 = { id: 'p2', name: 'Bob', avatar: '🤖', isAi: true };
  const state = initializeMatch(p1, p2);

  const lastCard: Card = { id: 'last-card', color: state.activeColor, value: '3', score: 3 };
  state.players[0].hand = [lastCard];
  state.players[0].unoCalled = true;
  state.currentTurnIndex = 0;
  state.pendingDrawCount = 0;

  const result = executePlayCard(state, 'p1', 'last-card');
  assert.equal(result.success, true);
  assert.equal(result.nextState.status, 'gameover');
  assert.equal(result.nextState.winnerId, 'p1');
});
