import express, { Request, Response } from 'express';
import http from 'http';
import { WebSocketServer, WebSocket } from 'ws';
import path from 'path';
import { fileURLToPath } from 'url';
import {
  GameState,
  GameRules,
  DEFAULT_RULES,
  Card,
} from './src/game/types.ts';
import {
  initializeMatch,
  executePlayCard,
  executeDrawCard,
  executeCallUno,
  executeCatchUno,
} from './src/game/unoLogic.ts';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const server = http.createServer(app);
const wss = new WebSocketServer({ noServer: true });

app.use(express.json());

interface ConnectedClient {
  ws: WebSocket;
  playerId: string;
  name: string;
  avatar: string;
  roomCode?: string;
}

interface Room {
  code: string;
  hostId: string;
  players: {
    id: string;
    name: string;
    avatar: string;
    ws: WebSocket;
    ready: boolean;
  }[];
  rules: GameRules;
  gameState: GameState | null;
  createdAt: number;
}

const clients = new Map<WebSocket, ConnectedClient>();
const rooms = new Map<string, Room>();

// Generate a random 5-digit code
function generateRoomCode(): string {
  let code = '';
  do {
    code = Math.floor(10000 + Math.random() * 90000).toString();
  } while (rooms.has(code));
  return code;
}

function broadcastToRoom(room: Room, message: object) {
  const payload = JSON.stringify(message);
  for (const p of room.players) {
    if (p.ws.readyState === WebSocket.OPEN) {
      p.ws.send(payload);
    }
  }
}

// REST endpoints for room queries and status
app.get('/api/health', (_req: Request, res: Response) => {
  res.json({ status: 'ok', activeRooms: rooms.size, timestamp: Date.now() });
});

app.post('/api/rooms/create', (req: Request, res: Response) => {
  const { name, avatar } = req.body;
  const code = generateRoomCode();
  res.json({ success: true, roomCode: code });
});

app.get('/api/rooms/:code', (req: Request, res: Response) => {
  const code = req.params.code;
  const room = rooms.get(code);
  if (!room) {
    return res.status(404).json({ success: false, error: 'Room not found' });
  }
  return res.json({
    success: true,
    code: room.code,
    playersCount: room.players.length,
    status: room.gameState ? room.gameState.status : 'waiting',
    rules: room.rules,
  });
});

// WebSocket connection handling
server.on('upgrade', (request, socket, head) => {
  const pathname = new URL(request.url || '', `http://${request.headers.host}`).pathname;
  if (pathname === '/ws') {
    wss.handleUpgrade(request, socket, head, (ws) => {
      wss.emit('connection', ws, request);
    });
  } else {
    socket.destroy();
  }
});

wss.on('connection', (ws: WebSocket) => {
  clients.set(ws, {
    ws,
    playerId: `p_${Date.now()}_${Math.floor(Math.random() * 1000)}`,
    name: 'Player',
    avatar: '😎',
  });

  ws.on('message', (raw: string) => {
    try {
      const data = JSON.parse(raw);
      const client = clients.get(ws);
      if (!client) return;

      const { type, payload } = data;

      switch (type) {
        case 'INIT_PROFILE': {
          client.name = payload.name || client.name;
          client.avatar = payload.avatar || client.avatar;
          ws.send(JSON.stringify({ type: 'PROFILE_UPDATED', payload: { playerId: client.playerId } }));
          break;
        }

        case 'CREATE_ROOM': {
          const roomCode = generateRoomCode();
          const rules: GameRules = payload.rules || DEFAULT_RULES;
          const newRoom: Room = {
            code: roomCode,
            hostId: client.playerId,
            players: [
              {
                id: client.playerId,
                name: client.name,
                avatar: client.avatar,
                ws,
                ready: true,
              },
            ],
            rules,
            gameState: null,
            createdAt: Date.now(),
          };

          rooms.set(roomCode, newRoom);
          client.roomCode = roomCode;

          ws.send(
            JSON.stringify({
              type: 'ROOM_CREATED',
              payload: {
                roomCode,
                isHost: true,
                players: newRoom.players.map((p) => ({ id: p.id, name: p.name, avatar: p.avatar, ready: p.ready })),
                rules: newRoom.rules,
              },
            })
          );
          break;
        }

        case 'JOIN_ROOM': {
          const code = payload.roomCode?.trim();
          const room = rooms.get(code);

          if (!room) {
            ws.send(JSON.stringify({ type: 'ERROR', payload: { message: `Room #${code} not found.` } }));
            return;
          }

          if (room.players.length >= 2) {
            // Check if player is reconnecting
            const existing = room.players.find((p) => p.id === client.playerId);
            if (!existing) {
              ws.send(JSON.stringify({ type: 'ERROR', payload: { message: `Room #${code} is already full (2/2 players).` } }));
              return;
            }
          }

          // Add or update player
          const existingIdx = room.players.findIndex((p) => p.id === client.playerId);
          if (existingIdx !== -1) {
            room.players[existingIdx].ws = ws;
          } else {
            room.players.push({
              id: client.playerId,
              name: client.name,
              avatar: client.avatar,
              ws,
              ready: true,
            });
          }

          client.roomCode = code;

          // Notify everyone in the room
          broadcastToRoom(room, {
            type: 'ROOM_UPDATED',
            payload: {
              roomCode: room.code,
              hostId: room.hostId,
              players: room.players.map((p) => ({ id: p.id, name: p.name, avatar: p.avatar, ready: p.ready })),
              rules: room.rules,
              gameState: room.gameState,
            },
          });
          break;
        }

        case 'UPDATE_RULES': {
          if (!client.roomCode) return;
          const room = rooms.get(client.roomCode);
          if (!room || room.hostId !== client.playerId) return;

          room.rules = { ...room.rules, ...payload.rules };
          broadcastToRoom(room, {
            type: 'RULES_UPDATED',
            payload: { rules: room.rules },
          });
          break;
        }

        case 'START_MATCH': {
          if (!client.roomCode) return;
          const room = rooms.get(client.roomCode);
          if (!room || room.players.length !== 2) return;

          const p1 = room.players[0];
          const p2 = room.players[1];

          room.gameState = initializeMatch(
            { id: p1.id, name: p1.name, avatar: p1.avatar, isAi: false },
            { id: p2.id, name: p2.name, avatar: p2.avatar, isAi: false },
            room.rules
          );

          broadcastToRoom(room, {
            type: 'GAME_STARTED',
            payload: { gameState: room.gameState },
          });
          break;
        }

        case 'PLAY_CARD': {
          if (!client.roomCode) return;
          const room = rooms.get(client.roomCode);
          if (!room || !room.gameState) return;

          const { cardId, chosenColor } = payload;
          const result = executePlayCard(room.gameState, client.playerId, cardId, chosenColor);

          if (result.success) {
            room.gameState = result.nextState;
            broadcastToRoom(room, {
              type: 'GAME_STATE_UPDATE',
              payload: { gameState: room.gameState },
            });
          } else {
            ws.send(JSON.stringify({ type: 'ACTION_FAILED', payload: { error: result.error } }));
          }
          break;
        }

        case 'DRAW_CARD': {
          if (!client.roomCode) return;
          const room = rooms.get(client.roomCode);
          if (!room || !room.gameState) return;

          const result = executeDrawCard(room.gameState, client.playerId);
          if (result.success) {
            room.gameState = result.nextState;
            broadcastToRoom(room, {
              type: 'GAME_STATE_UPDATE',
              payload: { gameState: room.gameState, drawnCards: result.drawnCards, playerId: client.playerId },
            });
          }
          break;
        }

        case 'CALL_UNO': {
          if (!client.roomCode) return;
          const room = rooms.get(client.roomCode);
          if (!room || !room.gameState) return;

          const result = executeCallUno(room.gameState, client.playerId);
          if (result.success) {
            room.gameState = result.nextState;
            broadcastToRoom(room, {
              type: 'UNO_SHOUTED',
              payload: { gameState: room.gameState, callerId: client.playerId, callerName: client.name },
            });
          }
          break;
        }

        case 'CATCH_UNO': {
          if (!client.roomCode) return;
          const room = rooms.get(client.roomCode);
          if (!room || !room.gameState) return;

          const result = executeCatchUno(room.gameState, client.playerId);
          if (result.success) {
            room.gameState = result.nextState;
            broadcastToRoom(room, {
              type: 'UNO_CAUGHT',
              payload: { gameState: room.gameState, catcherId: client.playerId, message: result.message },
            });
          }
          break;
        }

        case 'SEND_EMOTE': {
          if (!client.roomCode) return;
          const room = rooms.get(client.roomCode);
          if (!room) return;

          broadcastToRoom(room, {
            type: 'EMOTE_RECEIVED',
            payload: { playerId: client.playerId, emote: payload.emote, text: payload.text },
          });
          break;
        }

        case 'REMATCH': {
          if (!client.roomCode) return;
          const room = rooms.get(client.roomCode);
          if (!room || room.players.length !== 2) return;

          const p1 = room.players[0];
          const p2 = room.players[1];

          room.gameState = initializeMatch(
            { id: p1.id, name: p1.name, avatar: p1.avatar, isAi: false },
            { id: p2.id, name: p2.name, avatar: p2.avatar, isAi: false },
            room.rules
          );

          broadcastToRoom(room, {
            type: 'GAME_STARTED',
            payload: { gameState: room.gameState },
          });
          break;
        }

        case 'LEAVE_ROOM': {
          if (!client.roomCode) return;
          const code = client.roomCode;
          const room = rooms.get(code);
          if (room) {
            room.players = room.players.filter((p) => p.id !== client.playerId);
            if (room.players.length === 0) {
              rooms.delete(code);
            } else {
              broadcastToRoom(room, {
                type: 'PLAYER_LEFT',
                payload: { playerId: client.playerId, name: client.name },
              });
            }
          }
          client.roomCode = undefined;
          break;
        }
      }
    } catch (err) {
      console.error('WebSocket message handling error:', err);
    }
  });

  ws.on('close', () => {
    const client = clients.get(ws);
    if (client && client.roomCode) {
      const room = rooms.get(client.roomCode);
      if (room) {
        room.players = room.players.filter((p) => p.ws !== ws);
        if (room.players.length === 0) {
          rooms.delete(client.roomCode);
        } else {
          broadcastToRoom(room, {
            type: 'PLAYER_DISCONNECTED',
            payload: { playerId: client.playerId, name: client.name },
          });
        }
      }
    }
    clients.delete(ws);
  });
});

// Dev vs Prod Vite Integration
const isProd = process.env.NODE_ENV === 'production';
const PORT = process.env.PORT || 3000;

async function startServer() {
  if (!isProd) {
    const { createServer: createViteServer } = await import('vite');
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    app.use(express.static(path.resolve(__dirname, 'dist')));
    app.get('*', (_req: Request, res: Response) => {
      res.sendFile(path.resolve(__dirname, 'dist', 'index.html'));
    });
  }

  server.listen(PORT, () => {
    console.log(`UNO Royale server listening on port ${PORT}`);
  });
}

startServer();
