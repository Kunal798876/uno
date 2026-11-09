import { v4 as uuidv4 } from 'uuid';
import { redisDel, redisExists, redisGet, redisSet } from '../redis/redisClient';
import {
  AuthoritativeGameState,
  ClientGameState,
  Player,
  RoomMetadata,
  SessionData,
} from '../types';
import { UnoEngine } from './unoEngine';

const ROOM_TTL_SECONDS = 7200; // 2 hours

export class RoomManager {
  private static turnTimerIntervals = new Map<string, NodeJS.Timeout>();

  /**
   * Generates a unique 4-digit room code (1000..9999)
   */
  static async generateUniqueRoomCode(): Promise<string> {
    for (let attempts = 0; attempts < 50; attempts++) {
      const code = Math.floor(1000 + Math.random() * 9000).toString();
      const exists = await redisExists(`room:${code}`);
      if (!exists) {
        return code;
      }
    }
    // Fallback if heavily occupied
    return Math.floor(1000 + Math.random() * 9000).toString();
  }

  static async createRoom(
    nickname: string,
    avatar: string
  ): Promise<{ room: RoomMetadata; guestId: string; reconnectToken: string }> {
    const roomCode = await this.generateUniqueRoomCode();
    const guestId = `guest_${uuidv4().substring(0, 8)}`;
    const reconnectToken = `token_${uuidv4()}`;

    const hostPlayer = {
      guestId,
      name: nickname.trim().substring(0, 16) || 'Host',
      avatar: avatar || '👑',
      isReady: true,
      isConnected: true,
      isHost: true,
    };

    const room: RoomMetadata = {
      roomCode,
      hostGuestId: guestId,
      guestGuestId: null,
      createdAt: Date.now(),
      lastActiveAt: Date.now(),
      status: 'LOBBY',
      players: [hostPlayer],
    };

    const session: SessionData = {
      guestId,
      reconnectToken,
      nickname: hostPlayer.name,
      avatar: hostPlayer.avatar,
      roomCode,
      lastSeenAt: Date.now(),
    };

    await redisSet(`room:${roomCode}`, JSON.stringify(room), ROOM_TTL_SECONDS);
    await redisSet(`session:${reconnectToken}`, JSON.stringify(session), ROOM_TTL_SECONDS);
    await redisSet(`guest_session:${guestId}`, reconnectToken, ROOM_TTL_SECONDS);

    return { room, guestId, reconnectToken };
  }

  static async joinRoom(
    roomCode: string,
    nickname: string,
    avatar: string,
    existingReconnectToken?: string
  ): Promise<{
    room?: RoomMetadata;
    guestId?: string;
    reconnectToken?: string;
    error?: string;
  }> {
    const cleanCode = roomCode.trim();
    const raw = await redisGet(`room:${cleanCode}`);
    if (!raw) {
      return { error: 'Room not found or has expired. Please check the 4-digit code.' };
    }

    const room: RoomMetadata = JSON.parse(raw);

    // If joining with existing session
    if (existingReconnectToken) {
      const sessionRaw = await redisGet(`session:${existingReconnectToken}`);
      if (sessionRaw) {
        const session: SessionData = JSON.parse(sessionRaw);
        if (session.roomCode === cleanCode) {
          // Re-joining own room
          const p = room.players.find((pl) => pl.guestId === session.guestId);
          if (p) {
            p.isConnected = true;
            await redisSet(`room:${cleanCode}`, JSON.stringify(room), ROOM_TTL_SECONDS);
            return { room, guestId: session.guestId, reconnectToken: existingReconnectToken };
          }
        }
      }
    }

    // Check if room is full (strictly 2 players)
    if (room.players.length >= 2) {
      return { error: 'Room is already full! Exactly 2 players are permitted.' };
    }

    if (room.status !== 'LOBBY') {
      return { error: 'Game is already in progress or completed.' };
    }

    const guestId = `guest_${uuidv4().substring(0, 8)}`;
    const reconnectToken = `token_${uuidv4()}`;

    const guestPlayer = {
      guestId,
      name: nickname.trim().substring(0, 16) || 'Player 2',
      avatar: avatar || '🦊',
      isReady: true,
      isConnected: true,
      isHost: false,
    };

    room.guestGuestId = guestId;
    room.players.push(guestPlayer);
    room.lastActiveAt = Date.now();

    const session: SessionData = {
      guestId,
      reconnectToken,
      nickname: guestPlayer.name,
      avatar: guestPlayer.avatar,
      roomCode: cleanCode,
      lastSeenAt: Date.now(),
    };

    await redisSet(`room:${cleanCode}`, JSON.stringify(room), ROOM_TTL_SECONDS);
    await redisSet(`session:${reconnectToken}`, JSON.stringify(session), ROOM_TTL_SECONDS);
    await redisSet(`guest_session:${guestId}`, reconnectToken, ROOM_TTL_SECONDS);

    return { room, guestId, reconnectToken };
  }

  static async getRoom(roomCode: string): Promise<RoomMetadata | null> {
    const raw = await redisGet(`room:${roomCode.trim()}`);
    return raw ? JSON.parse(raw) : null;
  }

  static async setPlayerReady(
    roomCode: string,
    guestId: string,
    isReady: boolean
  ): Promise<{ room?: RoomMetadata; error?: string }> {
    const room = await this.getRoom(roomCode);
    if (!room) return { error: 'Room not found' };

    const player = room.players.find((p) => p.guestId === guestId);
    if (!player) return { error: 'Player not in room' };

    player.isReady = isReady;
    room.lastActiveAt = Date.now();

    await redisSet(`room:${roomCode}`, JSON.stringify(room), ROOM_TTL_SECONDS);
    return { room };
  }

  static async startGame(
    roomCode: string,
    hostGuestId: string
  ): Promise<{
    gameState?: AuthoritativeGameState;
    room?: RoomMetadata;
    error?: string;
  }> {
    const room = await this.getRoom(roomCode);
    if (!room) return { error: 'Room not found' };

    if (room.hostGuestId !== hostGuestId) {
      return { error: 'Only the room creator can start the game' };
    }

    if (room.players.length < 2) {
      return { error: 'Waiting for second player to join before starting' };
    }

    const p1Data = room.players[0];
    const p2Data = room.players[1];

    const player1: Player = {
      id: p1Data.guestId,
      name: p1Data.name,
      avatar: p1Data.avatar,
      isAi: false,
      hand: [],
      unoCalled: false,
      score: 0,
      isReady: true,
      isConnected: p1Data.isConnected,
    };

    const player2: Player = {
      id: p2Data.guestId,
      name: p2Data.name,
      avatar: p2Data.avatar,
      isAi: false,
      hand: [],
      unoCalled: false,
      score: 0,
      isReady: true,
      isConnected: p2Data.isConnected,
    };

    const gameState = UnoEngine.initializeMatch(player1, player2, roomCode);
    room.status = 'PLAYING';
    room.lastActiveAt = Date.now();

    await redisSet(`game:${roomCode}`, JSON.stringify(gameState), ROOM_TTL_SECONDS);
    await redisSet(`room:${roomCode}`, JSON.stringify(room), ROOM_TTL_SECONDS);

    return { gameState, room };
  }

  static async getGameState(roomCode: string): Promise<AuthoritativeGameState | null> {
    const raw = await redisGet(`game:${roomCode}`);
    return raw ? JSON.parse(raw) : null;
  }

  static async saveGameState(gameState: AuthoritativeGameState): Promise<void> {
    await redisSet(`game:${gameState.roomCode}`, JSON.stringify(gameState), ROOM_TTL_SECONDS);
  }

  static async restoreSession(reconnectToken: string): Promise<{
    session?: SessionData;
    room?: RoomMetadata;
    gameState?: ClientGameState;
    error?: string;
  }> {
    const rawSession = await redisGet(`session:${reconnectToken}`);
    if (!rawSession) {
      return { error: 'Session not found or expired' };
    }

    const session: SessionData = JSON.parse(rawSession);
    session.lastSeenAt = Date.now();
    await redisSet(`session:${reconnectToken}`, JSON.stringify(session), ROOM_TTL_SECONDS);

    if (!session.roomCode) {
      return { session };
    }

    const room = await this.getRoom(session.roomCode);
    if (!room) {
      return { session, error: 'Previous room expired or removed' };
    }

    // Mark connected
    const pl = room.players.find((p) => p.guestId === session.guestId);
    if (pl) {
      pl.isConnected = true;
      await redisSet(`room:${session.roomCode}`, JSON.stringify(room), ROOM_TTL_SECONDS);
    }

    let clientGame: ClientGameState | undefined;
    const authGame = await this.getGameState(session.roomCode);
    if (authGame) {
      const gPlayer = authGame.players.find((p) => p.id === session.guestId);
      if (gPlayer) {
        gPlayer.isConnected = true;
        await this.saveGameState(authGame);
      }
      clientGame = UnoEngine.sanitizeForClient(authGame, session.guestId);
    }

    return { session, room, gameState: clientGame };
  }

  static async setPlayerConnection(
    guestId: string,
    isConnected: boolean
  ): Promise<{ room?: RoomMetadata; roomCode?: string }> {
    const token = await redisGet(`guest_session:${guestId}`);
    if (!token) return {};

    const sessionRaw = await redisGet(`session:${token}`);
    if (!sessionRaw) return {};

    const session: SessionData = JSON.parse(sessionRaw);
    if (!session.roomCode) return {};

    const room = await this.getRoom(session.roomCode);
    if (!room) return {};

    const pl = room.players.find((p) => p.guestId === guestId);
    if (pl) {
      pl.isConnected = isConnected;
      await redisSet(`room:${session.roomCode}`, JSON.stringify(room), ROOM_TTL_SECONDS);
    }

    const game = await this.getGameState(session.roomCode);
    if (game) {
      const gp = game.players.find((p) => p.id === guestId);
      if (gp) {
        gp.isConnected = isConnected;
        await this.saveGameState(game);
      }
    }

    return { room, roomCode: session.roomCode };
  }

  static async leaveRoom(
    guestId: string,
    roomCode: string
  ): Promise<{ room?: RoomMetadata; leftPlayerName?: string }> {
    const room = await this.getRoom(roomCode);
    if (!room) return {};

    const playerIndex = room.players.findIndex((p) => p.guestId === guestId);
    if (playerIndex === -1) return { room };

    const leftPlayer = room.players[playerIndex];
    room.players.splice(playerIndex, 1);

    if (room.players.length === 0) {
      // Room empty, clean up
      await redisDel(`room:${roomCode}`);
      await redisDel(`game:${roomCode}`);
      return {};
    }

    // If host left, assign remaining player as host
    if (room.hostGuestId === guestId && room.players.length > 0) {
      room.hostGuestId = room.players[0].guestId;
      room.players[0].isHost = true;
    }

    await redisSet(`room:${roomCode}`, JSON.stringify(room), ROOM_TTL_SECONDS);
    return { room, leftPlayerName: leftPlayer.name };
  }
}
