export type CardColor = 'RED' | 'BLUE' | 'GREEN' | 'YELLOW' | 'WILD';

export type CardValue =
  | 'NUM_0'
  | 'NUM_1'
  | 'NUM_2'
  | 'NUM_3'
  | 'NUM_4'
  | 'NUM_5'
  | 'NUM_6'
  | 'NUM_7'
  | 'NUM_8'
  | 'NUM_9'
  | 'SKIP'
  | 'REVERSE'
  | 'DRAW2'
  | 'WILD'
  | 'WILD4';

export interface Card {
  id: string;
  color: CardColor;
  value: CardValue;
  score: number;
}

export interface Player {
  id: string; // guestId
  name: string;
  avatar: string;
  isAi: boolean;
  hand: Card[];
  unoCalled: boolean;
  score: number;
  isReady: boolean;
  isConnected: boolean;
}

export type GameStatus = 'WAITING' | 'PLAYING' | 'GAMEOVER';

export interface LastAction {
  playerId: string;
  text: string;
  timestamp: number;
  card?: Card | null;
}

export interface GameRules {
  stackDrawCards: boolean;
  drawToMatch: boolean;
  reverseAsSkipInTwoPlayer: boolean;
  sevenZeroRule: boolean;
  jumpIn: boolean;
  unoPenaltyCards: number;
  challengeWild4: boolean;
}

export interface AuthoritativeGameState {
  id: string;
  deck: Card[];
  discardPile: Card[];
  players: Player[];
  currentTurnIndex: number;
  activeColor: CardColor;
  direction: number; // 1 or -1
  pendingDrawCount: number;
  status: GameStatus;
  winnerId: string | null;
  lastAction: LastAction | null;
  rules: GameRules;
  unoCatchablePlayerId: string | null;
  turnSecondsRemaining: number;
  hasDrawnThisTurn: boolean;
  isCustomRoom: boolean;
  roomCode: string;
  isBotTakeover: boolean;
  roomClosed: boolean;
  isRoomWaiting: boolean;
  cardsDrawnCount: number;
  turnDeadline: number; // epoch ms
}

/**
 * Sanitized GameState sent to a specific player:
 * Opponent's hand is private (hand array is empty or replaced with placeholder count)
 */
export interface ClientGameState {
  id: string;
  deckCount: number;
  discardPile: Card[];
  players: {
    id: string;
    name: string;
    avatar: string;
    isAi: boolean;
    hand: Card[]; // Only populated for the recipient's own player entry! Opponent has empty list.
    handCount: number;
    unoCalled: boolean;
    score: number;
    isReady: boolean;
    isConnected: boolean;
  }[];
  currentTurnIndex: number;
  activeColor: CardColor;
  direction: number;
  pendingDrawCount: number;
  status: GameStatus;
  winnerId: string | null;
  lastAction: LastAction | null;
  rules: GameRules;
  unoCatchablePlayerId: string | null;
  turnSecondsRemaining: number;
  hasDrawnThisTurn: boolean;
  isCustomRoom: boolean;
  roomCode: string;
  isBotTakeover: boolean;
  roomClosed: boolean;
  isRoomWaiting: boolean;
  cardsDrawnCount: number;
}

export interface RoomMetadata {
  roomCode: string; // 4-digit code e.g. "4829"
  hostGuestId: string;
  guestGuestId: string | null;
  createdAt: number;
  lastActiveAt: number;
  status: 'LOBBY' | 'PLAYING' | 'FINISHED';
  players: {
    guestId: string;
    name: string;
    avatar: string;
    isReady: boolean;
    isConnected: boolean;
    isHost: boolean;
  }[];
}

export interface SessionData {
  guestId: string;
  reconnectToken: string;
  nickname: string;
  avatar: string;
  roomCode: string | null;
  lastSeenAt: number;
}
