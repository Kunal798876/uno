/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  GameState,
  GameRules,
  DEFAULT_RULES,
  UserProfile,
  Player,
} from './game/types';
import {
  initializeMatch,
  executePlayCard,
  executeDrawCard,
  executeCallUno,
  executeCatchUno,
  getAiDecision,
} from './game/unoLogic';
import { GameArena } from './components/GameArena';
import { RoomLobby } from './components/RoomLobby';
import { JoinRoomModal } from './components/JoinRoomModal';
import { CustomRulesModal } from './components/CustomRulesModal';
import { HowToPlayModal } from './components/HowToPlayModal';
import { ProfileSetupModal } from './components/ProfileSetupModal';
import { SettingsScreen } from './components/SettingsScreen';
import { SurpriseGiftModal } from './components/SurpriseGiftModal';
import { sounds } from './utils/audio';
import {
  Settings,
  Star,
  Share2,
  Gift,
  Bot,
  Users,
  PlusCircle,
  LogIn,
  ChevronDown,
} from 'lucide-react';

const LOCAL_STORAGE_PROFILE_KEY = 'uno_royale_user_profile';

const DEFAULT_PROFILE: UserProfile = {
  name: 'Kannu',
  avatar: '🧔',
  gamesWon: 0,
  gamesPlayed: 0,
};

type AppScreen = 'home' | 'settings' | 'lobby' | 'playing';

export default function App() {
  const [screen, setScreen] = useState<AppScreen>('home');
  const [showFriendsOptions, setShowFriendsOptions] = useState(false);

  const [profile, setProfile] = useState<UserProfile>(() => {
    try {
      const saved = localStorage.getItem(LOCAL_STORAGE_PROFILE_KEY);
      if (saved) return JSON.parse(saved);
    } catch {
      // ignore
    }
    return DEFAULT_PROFILE;
  });

  const [hasCompletedInitialProfile, setHasCompletedInitialProfile] = useState<boolean>(() => {
    try {
      return localStorage.getItem('uno_profile_configured') === 'true';
    } catch {
      return false;
    }
  });

  const [showProfileModal, setShowProfileModal] = useState(false);
  const [showRulesModal, setShowRulesModal] = useState(false);
  const [showHowToPlayModal, setShowHowToPlayModal] = useState(false);
  const [showJoinModal, setShowJoinModal] = useState(false);
  const [showGiftModal, setShowGiftModal] = useState(false);
  const [joinError, setJoinError] = useState<string | null>(null);

  const [rules, setRules] = useState<GameRules>(DEFAULT_RULES);
  const [gameState, setGameState] = useState<GameState | null>(null);
  const [isOnline, setIsOnline] = useState<boolean>(false);
  const [roomCode, setRoomCode] = useState<string>('');
  const [isHost, setIsHost] = useState<boolean>(false);
  const [lobbyPlayers, setLobbyPlayers] = useState<
    { id: string; name: string; avatar: string; ready: boolean }[]
  >([]);
  const [isMuted, setIsMuted] = useState(sounds.isMuted());
  const [isMusicOn, setIsMusicOn] = useState(() => sounds.isMusicOn());

  // Global user interaction listener to start BGM smoothly upon first click/tap
  useEffect(() => {
    const startBgmOnGesture = () => {
      if (sounds.isMusicOn() && !sounds.isMuted()) {
        sounds.startMusic();
      }
    };
    window.addEventListener('click', startBgmOnGesture, { once: true });
    window.addEventListener('touchstart', startBgmOnGesture, { once: true });
    window.addEventListener('keydown', startBgmOnGesture, { once: true });
    return () => {
      window.removeEventListener('click', startBgmOnGesture);
      window.removeEventListener('touchstart', startBgmOnGesture);
      window.removeEventListener('keydown', startBgmOnGesture);
    };
  }, []);

  const handleToggleMusic = () => {
    const next = sounds.toggleMusic();
    setIsMusicOn(next);
  };

  // Emote bubbles
  const [activeEmotes, setActiveEmotes] = useState<
    Record<string, { emote: string; text: string; id: number }>
  >({});

  // WebSocket ref
  const wsRef = useRef<WebSocket | null>(null);
  const localPlayerIdRef = useRef<string>(`local_${Date.now()}`);

  // Sync state to body dataset
  useEffect(() => {
    if (screen === 'home') {
      document.body.dataset.state = 'title';
    } else if (screen === 'playing') {
      if (gameState?.status === 'gameover') {
        document.body.dataset.state = 'gameover';
      } else {
        document.body.dataset.state = 'playing';
      }
    } else {
      document.body.dataset.state = 'lobby';
    }
  }, [screen, gameState?.status]);

  const handleSaveProfile = (newProfile: UserProfile) => {
    setProfile(newProfile);
    setHasCompletedInitialProfile(true);
    setShowProfileModal(false);
    try {
      localStorage.setItem(LOCAL_STORAGE_PROFILE_KEY, JSON.stringify(newProfile));
      localStorage.setItem('uno_profile_configured', 'true');
    } catch {
      // ignore
    }

    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      wsRef.current.send(
        JSON.stringify({
          type: 'INIT_PROFILE',
          payload: { name: newProfile.name, avatar: newProfile.avatar },
        })
      );
    }
  };

  const ensureWebSocket = useCallback((): Promise<WebSocket> => {
    return new Promise((resolve) => {
      if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
        resolve(wsRef.current);
        return;
      }

      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const wsUrl = `${protocol}//${window.location.host}/ws`;
      const ws = new WebSocket(wsUrl);

      ws.onopen = () => {
        ws.send(
          JSON.stringify({
            type: 'INIT_PROFILE',
            payload: { name: profile.name, avatar: profile.avatar },
          })
        );
        resolve(ws);
      };

      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          const { type, payload } = data;

          switch (type) {
            case 'PROFILE_UPDATED':
              if (payload.playerId) {
                localPlayerIdRef.current = payload.playerId;
              }
              break;

            case 'ROOM_CREATED':
              setRoomCode(payload.roomCode);
              setIsHost(true);
              setLobbyPlayers(payload.players);
              setRules(payload.rules);
              setScreen('lobby');
              break;

            case 'ROOM_UPDATED':
              setRoomCode(payload.roomCode);
              setIsHost(payload.hostId === localPlayerIdRef.current);
              setLobbyPlayers(payload.players);
              setRules(payload.rules);
              setScreen('lobby');
              break;

            case 'RULES_UPDATED':
              setRules(payload.rules);
              break;

            case 'GAME_STARTED':
              setGameState(payload.gameState);
              setIsOnline(true);
              setScreen('playing');
              sounds.playCardDraw();
              break;

            case 'GAME_STATE_UPDATE':
              setGameState(payload.gameState);
              sounds.playCardPlay();
              break;

            case 'UNO_SHOUTED':
              setGameState(payload.gameState);
              sounds.playUnoCall();
              break;

            case 'UNO_CAUGHT':
              setGameState(payload.gameState);
              sounds.playBuzzer();
              break;

            case 'EMOTE_RECEIVED':
              setActiveEmotes((prev) => ({
                ...prev,
                [payload.playerId]: {
                  emote: payload.emote,
                  text: payload.text,
                  id: Date.now(),
                },
              }));
              setTimeout(() => {
                setActiveEmotes((prev) => {
                  const updated = { ...prev };
                  delete updated[payload.playerId];
                  return updated;
                });
              }, 3000);
              break;

            case 'PLAYER_LEFT':
            case 'PLAYER_DISCONNECTED':
              sounds.playBuzzer();
              if (screen === 'playing') {
                alert(`${payload.name} left the match.`);
                setScreen('home');
                setGameState(null);
              } else if (screen === 'lobby') {
                setLobbyPlayers((prev) => prev.filter((p) => p.id !== payload.playerId));
              }
              break;

            case 'ERROR':
              setJoinError(payload.message || 'Error occurred');
              break;
          }
        } catch (err) {
          console.error('WS parse error:', err);
        }
      };

      wsRef.current = ws;
    });
  }, [profile, screen]);

  // Start Playing with Computer AI
  const startSoloGame = useCallback(() => {
    sounds.playCardDraw();
    const human: Player = {
      id: localPlayerIdRef.current,
      name: profile.name,
      avatar: profile.avatar,
      isAi: false,
      hand: [],
      unoCalled: false,
      score: 0,
    };

    const ai: Player = {
      id: 'ai-opponent',
      name: 'Doris',
      avatar: '👱‍♀️',
      isAi: true,
      hand: [],
      unoCalled: false,
      score: 0,
    };

    const initialGame = initializeMatch(human, ai, rules);
    setGameState(initialGame);
    setIsOnline(false);
    setScreen('playing');

    setProfile((p) => {
      const updated = { ...p, gamesPlayed: p.gamesPlayed + 1 };
      try {
        localStorage.setItem(LOCAL_STORAGE_PROFILE_KEY, JSON.stringify(updated));
      } catch {
        // ignore
      }
      return updated;
    });
  }, [profile, rules]);

  // Autostart query parameter support
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    if (params.get('autostart') === '1') {
      startSoloGame();
    }
  }, [startSoloGame]);

  // Enter/Space key starts game from home
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (screen === 'home') {
        if (e.code === 'Enter' || e.code === 'Space') {
          if (!showProfileModal && !showRulesModal && !showHowToPlayModal && !showJoinModal && !showGiftModal) {
            e.preventDefault();
            startSoloGame();
          }
        }
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [screen, showProfileModal, showRulesModal, showHowToPlayModal, showJoinModal, showGiftModal, startSoloGame]);

  // AI Turn Handling in Solo Mode
  useEffect(() => {
    if (isOnline || screen !== 'playing' || !gameState || gameState.status !== 'playing') {
      return;
    }

    const currentTurnPlayer = gameState.players[gameState.currentTurnIndex];
    if (currentTurnPlayer && currentTurnPlayer.isAi) {
      const timer = setTimeout(() => {
        setGameState((currentState) => {
          if (!currentState || currentState.status !== 'playing') return currentState;
          const currentAi = currentState.players[currentState.currentTurnIndex];
          if (!currentAi || !currentAi.isAi) return currentState;

          const decision = getAiDecision(currentState, currentAi.id);

          if (decision.action === 'callUno') {
            const catchResult = executeCatchUno(currentState, currentAi.id);
            if (catchResult.success) {
              sounds.playBuzzer();
              return catchResult.nextState;
            }
          }

          if (decision.callUnoFirst) {
            const unoResult = executeCallUno(currentState, currentAi.id);
            if (unoResult.success) {
              sounds.playUnoCall();
              currentState = unoResult.nextState;
            }
          }

          if (decision.action === 'play' && decision.cardId) {
            const playResult = executePlayCard(
              currentState,
              currentAi.id,
              decision.cardId,
              decision.chosenColor
            );
            if (playResult.success) {
              sounds.playCardPlay();
              return playResult.nextState;
            }
          }

          // Fallback: draw card
          const drawResult = executeDrawCard(currentState, currentAi.id);
          if (drawResult.success) {
            sounds.playCardDraw();
            return drawResult.nextState;
          }

          return currentState;
        });
      }, 1300);

      return () => clearTimeout(timer);
    }
  }, [gameState, isOnline, screen]);

  // Play card handler
  const handlePlayCard = (cardId: string, chosenColor?: 'red' | 'blue' | 'green' | 'yellow') => {
    if (isOnline) {
      if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
        wsRef.current.send(
          JSON.stringify({
            type: 'PLAY_CARD',
            payload: { cardId, chosenColor },
          })
        );
      }
      return;
    }

    if (!gameState) return;
    const result = executePlayCard(gameState, localPlayerIdRef.current, cardId, chosenColor);
    if (result.success) {
      sounds.playCardPlay();
      setGameState(result.nextState);
      if (result.nextState.status === 'gameover' && result.nextState.winnerId === localPlayerIdRef.current) {
        setProfile((p) => {
          const updated = { ...p, gamesWon: p.gamesWon + 1 };
          try {
            localStorage.setItem(LOCAL_STORAGE_PROFILE_KEY, JSON.stringify(updated));
          } catch {
            // ignore
          }
          return updated;
        });
      }
    }
  };

  const handleDrawCard = () => {
    if (isOnline) {
      if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
        wsRef.current.send(JSON.stringify({ type: 'DRAW_CARD' }));
      }
      return;
    }

    if (!gameState) return;
    const result = executeDrawCard(gameState, localPlayerIdRef.current);
    if (result.success) {
      sounds.playCardDraw();
      setGameState(result.nextState);
    }
  };

  const handleCallUno = () => {
    if (isOnline) {
      if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
        wsRef.current.send(JSON.stringify({ type: 'CALL_UNO' }));
      }
      return;
    }

    if (!gameState) return;
    const result = executeCallUno(gameState, localPlayerIdRef.current);
    if (result.success) {
      sounds.playUnoCall();
      setGameState(result.nextState);
    }
  };

  const handleCatchUno = () => {
    if (isOnline) {
      if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
        wsRef.current.send(JSON.stringify({ type: 'CATCH_UNO' }));
      }
      return;
    }

    if (!gameState) return;
    const result = executeCatchUno(gameState, localPlayerIdRef.current);
    if (result.success) {
      sounds.playBuzzer();
      setGameState(result.nextState);
    }
  };

  const handleSendEmote = (emote: string, text: string) => {
    if (isOnline && wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      wsRef.current.send(
        JSON.stringify({
          type: 'SEND_EMOTE',
          payload: { emote, text },
        })
      );
    } else {
      setActiveEmotes((prev) => ({
        ...prev,
        [localPlayerIdRef.current]: { emote, text, id: Date.now() },
      }));
      setTimeout(() => {
        setActiveEmotes((prev) => {
          const updated = { ...prev };
          delete updated[localPlayerIdRef.current];
          return updated;
        });
      }, 3000);
    }
  };

  const handleCreateRoom = async () => {
    if (!hasCompletedInitialProfile) {
      setShowProfileModal(true);
      return;
    }
    sounds.playClick();
    const ws = await ensureWebSocket();
    ws.send(
      JSON.stringify({
        type: 'CREATE_ROOM',
        payload: { rules },
      })
    );
  };

  const handleOpenJoinModal = () => {
    if (!hasCompletedInitialProfile) {
      setShowProfileModal(true);
      return;
    }
    sounds.playClick();
    setJoinError(null);
    setShowJoinModal(true);
  };

  const handleJoinWithCode = async (code: string) => {
    sounds.playClick();
    setJoinError(null);
    const ws = await ensureWebSocket();
    ws.send(
      JSON.stringify({
        type: 'JOIN_ROOM',
        payload: { roomCode: code.trim() },
      })
    );
    setShowJoinModal(false);
  };

  const handleStartOnlineMatch = () => {
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({ type: 'START_MATCH' }));
    }
  };

  const handleLeaveGame = () => {
    sounds.playClick();
    if (isOnline && wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({ type: 'LEAVE_ROOM' }));
    }
    setScreen('home');
    setGameState(null);
  };

  const handleRematch = () => {
    if (isOnline) {
      if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
        wsRef.current.send(JSON.stringify({ type: 'REMATCH' }));
      }
    } else {
      startSoloGame();
    }
  };

  return (
    <div className="w-full min-h-screen bg-[#ff1b04] bg-[radial-gradient(circle_at_50%_25%,_#ff3820_0%,_#ff1b04_55%,_#9e0e00_100%)] text-white flex justify-center items-center font-sans antialiased selection:bg-pink-400 selection:text-black">
      {/* ========================================================
          HOME SCREEN (RED #ff1b04 GRADIENT BACKGROUND, WHITE-PINK GRADIENT BUTTONS, BLACK BOLD TEXT)
         ======================================================== */}
      {screen === 'home' && (
        <div className="w-full max-w-md min-h-screen flex flex-col justify-between p-4 sm:p-5 text-center relative overflow-hidden bg-[#ff1b04] bg-[radial-gradient(circle_at_50%_25%,_#ff3820_0%,_#ff1b04_55%,_#9e0e00_100%)] text-white">
          {/* Subtle floating ambient UNO glow & dot texture */}
          <div className="absolute inset-0 pointer-events-none opacity-15 bg-[radial-gradient(#ffffff_2px,transparent_2px)] [background-size:24px_24px]" />
          <div className="absolute -top-12 -left-12 w-48 h-48 rounded-full bg-white/10 blur-2xl pointer-events-none" />
          <div className="absolute -bottom-12 -right-12 w-52 h-52 rounded-full bg-black/25 blur-2xl pointer-events-none" />

          {/* Top Bar: Profile Pill + Settings Button (NO COINS) */}
          <div className="relative z-10 flex items-center justify-between pt-1">
            {/* Player Profile Pill: White-Pink Gradient with Black Bold Text */}
            <button
              onClick={() => {
                sounds.playClick();
                setShowProfileModal(true);
              }}
              className="bg-gradient-to-r from-white via-pink-100 to-pink-300 border border-white/60 shadow-md px-3.5 py-1.5 rounded-2xl flex items-center gap-2 text-black font-black text-sm active:scale-95 transition-transform cursor-pointer"
            >
              <div className="w-6 h-6 rounded-full bg-neutral-900 border border-pink-400/40 flex items-center justify-center text-sm shadow-inner">
                {profile.avatar}
              </div>
              <span className="truncate max-w-[100px]">{profile.name}</span>
            </button>

            {/* Settings Gear Button: White-Pink Gradient with Black Icon */}
            <button
              onClick={() => {
                sounds.playClick();
                setScreen('settings');
              }}
              className="w-10 h-10 rounded-2xl bg-gradient-to-r from-white via-pink-100 to-pink-300 border border-white/60 shadow-md active:scale-95 flex items-center justify-center text-black font-black transition-transform cursor-pointer"
              title="Settings"
            >
              <Settings className="w-5 h-5 fill-black text-black" />
            </button>
          </div>

          {/* App Title Lockup */}
          <div className="relative z-10 pt-2 pb-1">
            <h1 className="text-4xl sm:text-5xl font-black italic tracking-tight text-white drop-shadow-[0_2px_12px_rgba(255,182,193,0.3)]">
              UNO ROYALE
            </h1>
            <p className="text-xs text-neutral-400 font-bold mt-0.5">
              2-Player Duels
            </p>
          </div>

          {/* ========================================================
              TWO CORE OPTIONS IN HOME SCREEN:
              1. Play Online with Computer AI (automatically starts)
              2. Play with Friends (shows Create Room & Join Room)
             ======================================================== */}
          <div className="relative z-10 my-auto py-2 space-y-4 max-w-xs mx-auto w-full">
            {/* 1. PLAY ONLINE WITH COMPUTER AI */}
            <button
              data-start="true"
              onClick={startSoloGame}
              className="w-full py-4 px-6 rounded-2xl bg-gradient-to-r from-white via-pink-100 to-pink-300 hover:from-pink-50 hover:to-pink-200 border border-white/80 shadow-lg shadow-pink-500/20 text-black font-black text-base tracking-wide flex items-center justify-center gap-3 transition-all cursor-pointer active:scale-95"
            >
              <Bot className="w-5 h-5 stroke-[2.5]" />
              <span>Play Online with Computer AI</span>
            </button>

            {/* 2. PLAY WITH FRIENDS (EXPANDS INTO CREATE / JOIN) */}
            <div className="space-y-2">
              <button
                onClick={() => {
                  sounds.playClick();
                  setShowFriendsOptions(!showFriendsOptions);
                }}
                className="w-full py-4 px-6 rounded-2xl bg-gradient-to-r from-white via-pink-100 to-pink-300 hover:from-pink-50 hover:to-pink-200 border border-white/80 shadow-lg shadow-pink-500/20 text-black font-black text-base tracking-wide flex items-center justify-between transition-all cursor-pointer active:scale-95"
              >
                <div className="flex items-center gap-3">
                  <Users className="w-5 h-5 stroke-[2.5]" />
                  <span>Play with Friends</span>
                </div>
                <ChevronDown
                  className={`w-5 h-5 transition-transform duration-200 ${
                    showFriendsOptions ? 'rotate-180' : ''
                  }`}
                />
              </button>

              {/* Sub-options: Create Room & Join Room */}
              {showFriendsOptions && (
                <div className="space-y-2 pt-1 animate-fadeIn">
                  {/* Create Room */}
                  <button
                    onClick={handleCreateRoom}
                    className="w-full py-3.5 px-5 rounded-2xl bg-gradient-to-r from-white via-pink-100 to-pink-300 hover:from-pink-50 hover:to-pink-200 border border-white/60 text-black font-black text-sm flex items-center justify-center gap-2 transition-all cursor-pointer active:scale-95 shadow-md shadow-pink-500/10"
                  >
                    <PlusCircle className="w-4 h-4 stroke-[2.5]" />
                    <span>Create Room (5-Digit Code)</span>
                  </button>

                  {/* Join Room */}
                  <button
                    onClick={handleOpenJoinModal}
                    className="w-full py-3.5 px-5 rounded-2xl bg-gradient-to-r from-white via-pink-100 to-pink-300 hover:from-pink-50 hover:to-pink-200 border border-white/60 text-black font-black text-sm flex items-center justify-center gap-2 transition-all cursor-pointer active:scale-95 shadow-md shadow-pink-500/10"
                  >
                    <LogIn className="w-4 h-4 stroke-[2.5]" />
                    <span>Join Room (Enter Code)</span>
                  </button>
                </div>
              )}
            </div>

            {/* Quick House Rules Preview Button */}
            <div className="pt-2">
              <button
                onClick={() => {
                  sounds.playClick();
                  setShowRulesModal(true);
                }}
                className="text-xs text-neutral-400 hover:text-pink-300 transition-colors font-bold cursor-pointer"
              >
                Custom House Rules: Stacking · 2P Extra Turn
              </button>
            </div>
          </div>

          {/* ========================================================
              BOTTOM BAR: Star, Share, Version 1.0, Surprise Gift (Video Ad)
             ======================================================== */}
          <div className="relative z-10 flex items-center justify-between pt-2 pb-1">
            <div className="flex items-center gap-2">
              {/* Star Button */}
              <button
                onClick={() => {
                  sounds.playClick();
                  alert('Thank you for rating UNO Royale 5 Stars!');
                }}
                className="w-10 h-10 rounded-2xl bg-gradient-to-r from-white via-pink-100 to-pink-300 border border-white/60 shadow-md active:scale-95 flex items-center justify-center text-black font-black transition-transform cursor-pointer"
                title="Rate Game"
              >
                <Star className="w-5 h-5 fill-black text-black" />
              </button>

              {/* Share Button */}
              <button
                onClick={() => {
                  sounds.playClick();
                  if (navigator.share) {
                    navigator.share({
                      title: 'UNO Royale',
                      text: 'Join me for a 2-player UNO duel!',
                      url: window.location.href,
                    }).catch(() => {});
                  } else {
                    navigator.clipboard.writeText(window.location.href);
                    alert('Game link copied to clipboard!');
                  }
                }}
                className="w-10 h-10 rounded-2xl bg-gradient-to-r from-white via-pink-100 to-pink-300 border border-white/60 shadow-md active:scale-95 flex items-center justify-center text-black font-black transition-transform cursor-pointer"
                title="Share Game"
              >
                <Share2 className="w-5 h-5 stroke-[2.5]" />
              </button>
            </div>

            {/* Version 1.0 */}
            <div className="text-xs font-black text-neutral-400">
              v 1.0
            </div>

            {/* Surprise Gift Button (Replaces Free Coins & Lucky Draw) */}
            <button
              onClick={() => {
                sounds.playClick();
                setShowGiftModal(true);
              }}
              className="py-2 px-3.5 rounded-2xl bg-gradient-to-r from-white via-pink-100 to-pink-300 hover:from-pink-50 hover:to-pink-200 border border-white/60 shadow-md text-black font-black text-xs flex items-center gap-1.5 active:scale-95 transition-all cursor-pointer"
            >
              <Gift className="w-4 h-4 fill-pink-600 text-pink-600" />
              <span>Surprise Gift</span>
            </button>
          </div>
        </div>
      )}

      {/* ========================================================
          SETTINGS SCREEN (RED #ff1b04 GRADIENT BACKGROUND, V1.0)
         ======================================================== */}
      {screen === 'settings' && (
        <SettingsScreen
          onBack={() => setScreen('home')}
          onHowToPlay={() => setShowHowToPlayModal(true)}
          isSoundOn={!isMuted}
          onToggleSound={() => setIsMuted(sounds.toggleMute())}
          isMusicOn={isMusicOn}
          onToggleMusic={handleToggleMusic}
        />
      )}

      {/* ========================================================
          ROOM LOBBY (WAITING FOR FRIEND WITH 5-DIGIT CODE)
         ======================================================== */}
      {screen === 'lobby' && (
        <RoomLobby
          roomCode={roomCode}
          isHost={isHost}
          players={lobbyPlayers}
          rules={rules}
          profile={profile}
          onStartMatch={handleStartOnlineMatch}
          onOpenRules={() => setShowRulesModal(true)}
          onLeave={() => {
            if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
              wsRef.current.send(JSON.stringify({ type: 'LEAVE_ROOM' }));
            }
            setScreen('home');
          }}
        />
      )}

      {/* ========================================================
          MAIN GAME ARENA (MATCHING IMAGE 3 LAYOUT ON BLACK BACKGROUND)
         ======================================================== */}
      {screen === 'playing' && gameState && (
        <GameArena
          gameState={gameState}
          localPlayerId={localPlayerIdRef.current}
          isOnline={isOnline}
          roomCode={roomCode}
          onPlayCard={handlePlayCard}
          onDrawCard={handleDrawCard}
          onCallUno={handleCallUno}
          onCatchUno={handleCatchUno}
          onSendEmote={handleSendEmote}
          onLeaveGame={handleLeaveGame}
          onRematch={handleRematch}
          onOpenRules={() => setShowRulesModal(true)}
          activeEmotes={activeEmotes}
        />
      )}

      {/* MODALS */}
      <ProfileSetupModal
        isOpen={showProfileModal}
        initialProfile={profile}
        onSave={handleSaveProfile}
        onClose={() => setShowProfileModal(false)}
        canClose={hasCompletedInitialProfile}
      />

      <CustomRulesModal
        isOpen={showRulesModal}
        rules={rules}
        onUpdateRules={(newRules) => {
          setRules(newRules);
          if (isOnline && isHost && wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
            wsRef.current.send(
              JSON.stringify({
                type: 'UPDATE_RULES',
                payload: { rules: newRules },
              })
            );
          }
        }}
        onClose={() => setShowRulesModal(false)}
        isHost={isHost || !isOnline}
      />

      <HowToPlayModal
        isOpen={showHowToPlayModal}
        onClose={() => setShowHowToPlayModal(false)}
      />

      <JoinRoomModal
        isOpen={showJoinModal}
        onJoin={handleJoinWithCode}
        onClose={() => setShowJoinModal(false)}
        errorMessage={joinError}
      />

      <SurpriseGiftModal
        isOpen={showGiftModal}
        onClose={() => setShowGiftModal(false)}
      />
    </div>
  );
}
