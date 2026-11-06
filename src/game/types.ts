export type CardColor = 'red' | 'blue' | 'green' | 'yellow' | 'wild';

export type CardValue = 
  | '0' | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9'
  | 'skip' | 'reverse' | 'draw2'
  | 'wild' | 'wild4';

export interface Card {
  id: string;
  color: CardColor;
  value: CardValue;
  score: number;
}

export type AiDifficulty = 'easy' | 'medium' | 'hard';

export interface GameRules {
  stackDrawCards: boolean;          // Can stack +2 on +2 or +4 on +4
  drawToMatch: boolean;             // Keep drawing until a playable card is drawn (vs draw 1)
  reverseAsSkipInTwoPlayer: boolean;// In official 2-player UNO, Reverse skips opponent (gives you another turn)
  sevenZeroRule: boolean;           // 7 swaps hand with opponent, 0 passes hands
  jumpIn: boolean;                  // Play identical card anytime even if not your turn
  unoPenaltyCards: number;          // Penalty cards for failing to call UNO (default 2)
  challengeWild4: boolean;          // Opponent can challenge Wild +4 play
  aiDifficulty: AiDifficulty;
}

export const DEFAULT_RULES: GameRules = {
  stackDrawCards: true,
  drawToMatch: false,
  reverseAsSkipInTwoPlayer: true,
  sevenZeroRule: false,
  jumpIn: false,
  unoPenaltyCards: 2,
  challengeWild4: false,
  aiDifficulty: 'medium',
};

export interface Player {
  id: string;
  name: string;
  avatar: string;
  isAi: boolean;
  hand: Card[];
  unoCalled: boolean;
  score: number;
}

export type GameStatus = 'waiting' | 'playing' | 'gameover';

export interface GameState {
  id: string;
  deck: Card[];
  discardPile: Card[];
  players: Player[];
  currentTurnIndex: number;
  activeColor: 'red' | 'blue' | 'green' | 'yellow';
  direction: 1 | -1;
  pendingDrawCount: number;
  status: GameStatus;
  winnerId: string | null;
  lastAction: {
    playerId: string;
    text: string;
    timestamp: number;
    card?: Card;
  } | null;
  rules: GameRules;
  unoCatchablePlayerId: string | null; // Player who has 1 card but hasn't called UNO yet
}

export interface UserProfile {
  name: string;
  avatar: string;
  gamesWon: number;
  gamesPlayed: number;
}
