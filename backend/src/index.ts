import http from 'http';
import express, { Request, Response } from 'express';
import cors from 'cors';
import { Server, Socket } from 'socket.io';
import { createAdapter } from '@socket.io/redis-adapter';
import dotenv from 'dotenv';
import { initRedisClients } from './redis/redisClient';
import { RoomManager } from './game/roomManager';
import { UnoEngine } from './game/unoEngine';
import { CardColor } from './types';

dotenv.config();

const app = express();
app.use(cors());
app.use(express.json());

const PORT = parseInt(process.env.PORT || process.env.DEFAULT_APP_PORT || '3000', 10);

// Health check endpoint for Google Cloud Run
app.get('/health', (req: Request, res: Response) => {
  res.status(200).json({
    status: 'ok',
    service: 'uno-royale-multiplayer-backend',
    timestamp: new Date().toISOString(),
    uptimeSeconds: Math.floor(process.uptime()),
    activeConnections: io ? io.engine.clientsCount : 0,
  });
});

app.get('/', (req: Request, res: Response) => {
  res.status(200).send('UNO Royale Multiplayer Backend running on Google Cloud Run.');
});

const server = http.createServer(app);

// Initialize Redis clients
const { pub, sub, isRedisReady } = initRedisClients();

const io = new Server(server, {
  cors: {
    origin: '*',
    methods: ['GET', 'POST'],
  },
  pingInterval: 10000,
  pingTimeout: 5000,
  transports: ['websocket', 'polling'],
});

// Configure Socket.IO Redis Adapter for multi-instance Cloud Run scaling
if (pub && sub) {
  try {
    io.adapter(createAdapter(pub, sub));
    console.log('[Socket.IO] Redis adapter registered for multi-instance scaling.');
  } catch (err: any) {
    console.warn('[Socket.IO] Failed to attach Redis adapter:', err.message);
  }
}

// Map socketId -> guestId & roomCode
const socketMeta = new Map<string, { guestId: string; roomCode?: string }>();

// Map roomCode -> active 5-second auto-start countdown timer
const roomCountdowns = new Map<string, NodeJS.Timeout>();

// Helper to broadcast game state privately to both players in a room
async function broadcastSanitizedGameState(
  roomCode: string,
  extra?: { cardPlayedId?: string; isDraw?: boolean; isAttack?: boolean; attackCount?: number }
) {
  const gameState = await RoomManager.getGameState(roomCode);
  if (!gameState) return;

  const room = await RoomManager.getRoom(roomCode);
  if (!room) return;

  for (const player of room.players) {
    const clientState = UnoEngine.sanitizeForClient(gameState, player.guestId);
    io.to(`user_${player.guestId}`).emit('game_state_sync', {
      gameState: clientState,
      ...extra,
    });
  }
}

io.on('connection', (socket: Socket) => {
  console.log(`[Socket Connected] ID: ${socket.id}`);

  // 1. CREATE CUSTOM ROOM (4-digit code)
  socket.on('create_room', async (data: { nickname: string; avatar: string }, callback) => {
    try {
      const { room, guestId, reconnectToken } = await RoomManager.createRoom(
        data.nickname || 'Host',
        data.avatar || '👑'
      );

      socketMeta.set(socket.id, { guestId, roomCode: room.roomCode });
      socket.join(`room_${room.roomCode}`);
      socket.join(`user_${guestId}`);

      console.log(`[Room Created] Code: ${room.roomCode} by ${data.nickname} (${guestId})`);

      if (typeof callback === 'function') {
        callback({
          success: true,
          roomCode: room.roomCode,
          guestId,
          reconnectToken,
          room,
        });
      }
    } catch (err: any) {
      console.error('[create_room error]:', err);
      if (typeof callback === 'function') {
        callback({ success: false, error: err.message || 'Failed to create room' });
      }
    }
  });

  // 2. JOIN CUSTOM ROOM (using 4-digit code)
  socket.on(
    'join_room',
    async (
      data: { roomCode: string; nickname: string; avatar: string; reconnectToken?: string },
      callback
    ) => {
      try {
        const result = await RoomManager.joinRoom(
          data.roomCode,
          data.nickname || 'Player 2',
          data.avatar || '🦊',
          data.reconnectToken
        );

        if (result.error || !result.room || !result.guestId) {
          if (typeof callback === 'function') {
            callback({ success: false, error: result.error || 'Failed to join room' });
          }
          return;
        }

        const { room, guestId, reconnectToken } = result;
        socketMeta.set(socket.id, { guestId, roomCode: room.roomCode });
        socket.join(`room_${room.roomCode}`);
        socket.join(`user_${guestId}`);

        console.log(`[Room Joined] Code: ${room.roomCode} by ${data.nickname} (${guestId})`);

        // Notify room players
        io.to(`room_${room.roomCode}`).emit('room_updated', { room });

        // When both players are present, start 5s countdown to begin without manual permissions
        if (room.players.length === 2) {
          console.log(`[Auto Countdown] Room ${room.roomCode} has 2 players. Starting 5s countdown!`);
          io.to(`room_${room.roomCode}`).emit('countdown_started', {
            roomCode: room.roomCode,
            durationSeconds: 5,
          });

          if (roomCountdowns.has(room.roomCode)) {
            clearTimeout(roomCountdowns.get(room.roomCode)!);
          }

          const timer = setTimeout(async () => {
            roomCountdowns.delete(room.roomCode);
            try {
              const currentRoom = await RoomManager.getRoom(room.roomCode);
              if (!currentRoom || currentRoom.players.length < 2) return;

              const { gameState, room: updatedRoom, error } = await RoomManager.startGame(
                room.roomCode,
                currentRoom.hostGuestId
              );

              if (gameState && updatedRoom) {
                console.log(`[Auto Start] 5s elapsed. Game started in room ${room.roomCode}`);
                io.to(`room_${room.roomCode}`).emit('game_started', { roomCode: room.roomCode });
                await broadcastSanitizedGameState(room.roomCode);
              } else if (error) {
                console.warn(`[Auto Start Game Failed]: ${error}`);
              }
            } catch (autoErr: any) {
              console.error('[Auto Start Exception]:', autoErr);
            }
          }, 5000);

          roomCountdowns.set(room.roomCode, timer);
        }

        if (typeof callback === 'function') {
          callback({
            success: true,
            room,
            guestId,
            reconnectToken,
          });
        }
      } catch (err: any) {
        console.error('[join_room error]:', err);
        if (typeof callback === 'function') {
          callback({ success: false, error: err.message || 'Failed to join room' });
        }
      }
    }
  );

  // 3. RECONNECT SESSION (resumes session using reconnectToken)
  socket.on('reconnect_session', async (data: { reconnectToken: string }, callback) => {
    try {
      const result = await RoomManager.restoreSession(data.reconnectToken);
      if (result.error || !result.session) {
        if (typeof callback === 'function') {
          callback({ success: false, error: result.error || 'Session restore failed' });
        }
        return;
      }

      const { session, room, gameState } = result;
      socketMeta.set(socket.id, { guestId: session.guestId, roomCode: session.roomCode || undefined });
      socket.join(`user_${session.guestId}`);

      if (session.roomCode) {
        socket.join(`room_${session.roomCode}`);
        if (room) {
          io.to(`room_${session.roomCode}`).emit('room_updated', { room });
          io.to(`room_${session.roomCode}`).emit('player_connection_changed', {
            guestId: session.guestId,
            isConnected: true,
          });
        }
      }

      console.log(`[Session Restored] GuestId: ${session.guestId}, Room: ${session.roomCode}`);

      if (typeof callback === 'function') {
        callback({
          success: true,
          guestId: session.guestId,
          nickname: session.nickname,
          avatar: session.avatar,
          roomCode: session.roomCode,
          room,
          gameState,
        });
      }
    } catch (err: any) {
      console.error('[reconnect_session error]:', err);
      if (typeof callback === 'function') {
        callback({ success: false, error: err.message });
      }
    }
  });

  // 4. SET READY STATUS
  socket.on('set_ready', async (data: { roomCode: string; isReady: boolean }, callback) => {
    try {
      const meta = socketMeta.get(socket.id);
      if (!meta) return;

      const { room, error } = await RoomManager.setPlayerReady(data.roomCode, meta.guestId, data.isReady);
      if (error || !room) {
        if (typeof callback === 'function') callback({ success: false, error });
        return;
      }

      io.to(`room_${data.roomCode}`).emit('room_updated', { room });
      if (typeof callback === 'function') callback({ success: true, room });
    } catch (err: any) {
      if (typeof callback === 'function') callback({ success: false, error: err.message });
    }
  });

  // 5. START GAME (Host only, when both ready)
  socket.on('start_game', async (data: { roomCode: string }, callback) => {
    try {
      const meta = socketMeta.get(socket.id);
      if (!meta) return;

      const { gameState, room, error } = await RoomManager.startGame(data.roomCode, meta.guestId);
      if (error || !gameState || !room) {
        if (typeof callback === 'function') callback({ success: false, error });
        return;
      }

      console.log(`[Game Started] Room: ${data.roomCode}`);

      // Notify room of game started
      io.to(`room_${data.roomCode}`).emit('game_started', { roomCode: data.roomCode });

      // Send sanitized game state to each player
      await broadcastSanitizedGameState(data.roomCode);

      if (typeof callback === 'function') callback({ success: true });
    } catch (err: any) {
      if (typeof callback === 'function') callback({ success: false, error: err.message });
    }
  });

  // 6. PLAY CARD (Authoritative server validation)
  socket.on(
    'play_card',
    async (
      data: { roomCode: string; cardId: string; chosenColor?: CardColor; sourceXOffset?: number },
      callback
    ) => {
      try {
        const meta = socketMeta.get(socket.id);
        if (!meta) return;

        const authGame = await RoomManager.getGameState(data.roomCode);
        if (!authGame) {
          if (typeof callback === 'function') callback({ success: false, error: 'Match not found' });
          return;
        }

        const { nextState, success, error } = UnoEngine.executePlayCard(
          authGame,
          meta.guestId,
          data.cardId,
          data.chosenColor
        );

        if (!success) {
          if (typeof callback === 'function') callback({ success: false, error });
          return;
        }

        await RoomManager.saveGameState(nextState);

        const card = nextState.discardPile[nextState.discardPile.length - 1];
        const isAttack = card.value === 'DRAW2' || card.value === 'WILD4';
        const attackCount = card.value === 'WILD4' ? 4 : card.value === 'DRAW2' ? 2 : 0;

        await broadcastSanitizedGameState(data.roomCode, {
          cardPlayedId: data.cardId,
          isAttack,
          attackCount,
        });

        if (typeof callback === 'function') callback({ success: true });
      } catch (err: any) {
        if (typeof callback === 'function') callback({ success: false, error: err.message });
      }
    }
  );

  // 7. DRAW CARD
  socket.on('draw_card', async (data: { roomCode: string }, callback) => {
    try {
      const meta = socketMeta.get(socket.id);
      if (!meta) return;

      const authGame = await RoomManager.getGameState(data.roomCode);
      if (!authGame) return;

      const { nextState, drawnCards } = UnoEngine.executeDrawCard(authGame, meta.guestId);
      if (drawnCards.length > 0) {
        await RoomManager.saveGameState(nextState);
        await broadcastSanitizedGameState(data.roomCode, { isDraw: true });
      }

      if (typeof callback === 'function') callback({ success: true, count: drawnCards.length });
    } catch (err: any) {
      if (typeof callback === 'function') callback({ success: false, error: err.message });
    }
  });

  // 8. SKIP TURN
  socket.on('skip_turn', async (data: { roomCode: string }, callback) => {
    try {
      const meta = socketMeta.get(socket.id);
      if (!meta) return;

      const authGame = await RoomManager.getGameState(data.roomCode);
      if (!authGame) return;

      const nextState = UnoEngine.executeSkipTurn(authGame, meta.guestId);
      await RoomManager.saveGameState(nextState);
      await broadcastSanitizedGameState(data.roomCode);

      if (typeof callback === 'function') callback({ success: true });
    } catch (err: any) {
      if (typeof callback === 'function') callback({ success: false, error: err.message });
    }
  });

  // 9. CALL UNO
  socket.on('call_uno', async (data: { roomCode: string }, callback) => {
    try {
      const meta = socketMeta.get(socket.id);
      if (!meta) return;

      const authGame = await RoomManager.getGameState(data.roomCode);
      if (!authGame) return;

      const { nextState, success } = UnoEngine.executeCallUno(authGame, meta.guestId);
      if (success) {
        await RoomManager.saveGameState(nextState);
        await broadcastSanitizedGameState(data.roomCode);
      }

      if (typeof callback === 'function') callback({ success });
    } catch (err: any) {
      if (typeof callback === 'function') callback({ success: false, error: err.message });
    }
  });

  // 10. CATCH UNO
  socket.on('catch_uno', async (data: { roomCode: string }, callback) => {
    try {
      const meta = socketMeta.get(socket.id);
      if (!meta) return;

      const authGame = await RoomManager.getGameState(data.roomCode);
      if (!authGame) return;

      const { nextState, success } = UnoEngine.executeCatchUno(authGame, meta.guestId);
      if (success) {
        await RoomManager.saveGameState(nextState);
        await broadcastSanitizedGameState(data.roomCode);
      }

      if (typeof callback === 'function') callback({ success });
    } catch (err: any) {
      if (typeof callback === 'function') callback({ success: false, error: err.message });
    }
  });

  // 11. EMOTES
  socket.on('send_emote', (data: { roomCode: string; emoji: string; text: string }) => {
    const meta = socketMeta.get(socket.id);
    if (!meta) return;

    io.to(`room_${data.roomCode}`).emit('emote_received', {
      senderId: meta.guestId,
      emoji: data.emoji,
      text: data.text,
    });
  });

  // 12. REMATCH
  socket.on('rematch', async (data: { roomCode: string }, callback) => {
    try {
      const meta = socketMeta.get(socket.id);
      if (!meta) return;

      const room = await RoomManager.getRoom(data.roomCode);
      if (!room || room.players.length < 2) return;

      const p1 = {
        id: room.players[0].guestId,
        name: room.players[0].name,
        avatar: room.players[0].avatar,
        isAi: false,
        hand: [],
        unoCalled: false,
        score: 0,
        isReady: true,
        isConnected: room.players[0].isConnected,
      };

      const p2 = {
        id: room.players[1].guestId,
        name: room.players[1].name,
        avatar: room.players[1].avatar,
        isAi: false,
        hand: [],
        unoCalled: false,
        score: 0,
        isReady: true,
        isConnected: room.players[1].isConnected,
      };

      const freshGame = UnoEngine.initializeMatch(p1, p2, data.roomCode);
      await RoomManager.saveGameState(freshGame);
      await broadcastSanitizedGameState(data.roomCode);

      if (typeof callback === 'function') callback({ success: true });
    } catch (err: any) {
      if (typeof callback === 'function') callback({ success: false, error: err.message });
    }
  });

  // 13. LEAVE ROOM
  socket.on('leave_room', async (data: { roomCode: string }, callback) => {
    try {
      const meta = socketMeta.get(socket.id);
      if (!meta) return;

      const { room, leftPlayerName } = await RoomManager.leaveRoom(meta.guestId, data.roomCode);
      socket.leave(`room_${data.roomCode}`);

      if (roomCountdowns.has(data.roomCode)) {
        clearTimeout(roomCountdowns.get(data.roomCode)!);
        roomCountdowns.delete(data.roomCode);
        io.to(`room_${data.roomCode}`).emit('countdown_cancelled', { roomCode: data.roomCode });
      }

      if (room) {
        io.to(`room_${data.roomCode}`).emit('room_updated', { room });
        io.to(`room_${data.roomCode}`).emit('player_left', {
          guestId: meta.guestId,
          name: leftPlayerName,
        });
      }

      if (typeof callback === 'function') callback({ success: true });
    } catch (err: any) {
      if (typeof callback === 'function') callback({ success: false, error: err.message });
    }
  });

  // DISCONNECT
  socket.on('disconnect', async () => {
    const meta = socketMeta.get(socket.id);
    if (meta) {
      console.log(`[Socket Disconnected] GuestId: ${meta.guestId}`);
      if (meta.roomCode && roomCountdowns.has(meta.roomCode)) {
        clearTimeout(roomCountdowns.get(meta.roomCode)!);
        roomCountdowns.delete(meta.roomCode);
        io.to(`room_${meta.roomCode}`).emit('countdown_cancelled', { roomCode: meta.roomCode });
      }
      const { room, roomCode } = await RoomManager.setPlayerConnection(meta.guestId, false);
      if (room && roomCode) {
        io.to(`room_${roomCode}`).emit('player_connection_changed', {
          guestId: meta.guestId,
          isConnected: false,
        });
        io.to(`room_${roomCode}`).emit('room_updated', { room });
      }
      socketMeta.delete(socket.id);
    }
  });
});

server.listen(PORT, () => {
  console.log(`🚀 [UNO Royale Server] Running on port ${PORT}`);
  console.log(`📡 [Health Check] Available at http://localhost:${PORT}/health`);
});
