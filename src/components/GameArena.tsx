import React, { useState } from 'react';
import { GameState, Card, Player } from '../game/types';
import { UnoCard } from './UnoCard';
import { ColorPickerModal } from './ColorPickerModal';
import { VictoryModal } from './VictoryModal';
import { PauseMenuModal } from './PauseMenuModal';
import { sounds } from '../utils/audio';
import {
  Volume2,
  VolumeX,
  Music,
  Smile,
  Flame,
  AlertTriangle,
} from 'lucide-react';

interface GameArenaProps {
  gameState: GameState;
  localPlayerId: string;
  isOnline: boolean;
  roomCode?: string;
  onPlayCard: (cardId: string, chosenColor?: 'red' | 'blue' | 'green' | 'yellow') => void;
  onDrawCard: () => void;
  onCallUno: () => void;
  onCatchUno: () => void;
  onSendEmote: (emote: string, text: string) => void;
  onLeaveGame: () => void;
  onRematch: () => void;
  onOpenRules: () => void;
  activeEmotes: Record<string, { emote: string; text: string; id: number }>;
}

const COLOR_BORDER_MAP = {
  yellow: '#FFCC00',
  red: '#EE2737',
  green: '#00A651',
  blue: '#0072BC',
};

const EMOTE_OPTIONS = [
  { emoji: '👋', text: 'Hello!' },
  { emoji: '🔥', text: 'UNO incoming!' },
  { emoji: '😱', text: 'No way!' },
  { emoji: '😎', text: 'Too easy!' },
  { emoji: '🤔', text: 'Thinking...' },
  { emoji: '💥', text: 'Boom!' },
];

export const GameArena: React.FC<GameArenaProps> = ({
  gameState,
  localPlayerId,
  isOnline,
  onPlayCard,
  onDrawCard,
  onCallUno,
  onCatchUno,
  onSendEmote,
  onLeaveGame,
  onRematch,
  onOpenRules,
  activeEmotes,
}) => {
  const [pendingWildCardId, setPendingWildCardId] = useState<string | null>(null);
  const [showPauseMenu, setShowPauseMenu] = useState(false);
  const [showEmotePicker, setShowEmotePicker] = useState(false);
  const [isMuted, setIsMuted] = useState(() => sounds.isMuted());
  const [isMusicOn, setIsMusicOn] = useState(() => sounds.isMusicOn());
  const [shakingCardId, setShakingCardId] = useState<string | null>(null);

  const { players, currentTurnIndex, discardPile, deck, activeColor, status, winnerId, lastAction } = gameState;

  const localPlayerIndex = players.findIndex((p) => p.id === localPlayerId);
  const opponentIndex = localPlayerIndex === 0 ? 1 : 0;

  const localPlayer: Player = players[localPlayerIndex] || players[0];
  const opponent: Player = players[opponentIndex] || players[1];

  const isLocalTurn = currentTurnIndex === localPlayerIndex;
  const topCard = discardPile[discardPile.length - 1];

  const toggleSound = () => {
    const next = sounds.toggleMute();
    setIsMuted(next);
  };

  const toggleMusic = () => {
    const next = sounds.toggleMusic();
    setIsMusicOn(next);
  };

  // Check if a card is legally playable right now
  const isCardLegal = (card: Card) => {
    if (!isLocalTurn) return false;
    if (card.color === 'wild' || card.value === 'wild' || card.value === 'wild4') return true;
    if (card.color === activeColor) return true;
    if (card.value === topCard.value) return true;
    return false;
  };

  // Card click with NO HINTS for cards:
  // Tapping an invalid card plays a brief shake animation with tactile sound
  const handleCardClick = (card: Card) => {
    if (!isLocalTurn) {
      sounds.playBuzzer();
      return;
    }

    if (!isCardLegal(card)) {
      sounds.playBuzzer();
      setShakingCardId(card.id);
      setTimeout(() => setShakingCardId(null), 500);
      return;
    }

    if (card.color === 'wild' || card.value === 'wild' || card.value === 'wild4') {
      setPendingWildCardId(card.id);
    } else {
      sounds.playCardPlay();
      onPlayCard(card.id);
    }
  };

  const handleSelectColor = (color: 'red' | 'blue' | 'green' | 'yellow') => {
    if (pendingWildCardId) {
      sounds.playCardPlay();
      onPlayCard(pendingWildCardId, color);
      setPendingWildCardId(null);
    }
  };

  const canCallUno = localPlayer.hand.length <= 2 && !localPlayer.unoCalled;
  const canCatchOpponent = gameState.unoCatchablePlayerId === opponent.id;

  const opponentEmote = activeEmotes[opponent.id];
  const localEmote = activeEmotes[localPlayer.id];

  const activeColorHex = COLOR_BORDER_MAP[activeColor] || '#FFCC00';

  return (
    <div className="relative w-full max-w-md mx-auto h-screen max-h-screen flex flex-col justify-between overflow-hidden bg-[#ff1b04] bg-[radial-gradient(circle_at_50%_35%,_#ff3820_0%,_#ff1b04_55%,_#9b0b00_100%)] select-none text-white font-sans">
      {/* Subtle floating ambient UNO glow & dot texture */}
      <div className="absolute inset-0 pointer-events-none opacity-15 bg-[radial-gradient(#ffffff_2px,transparent_2px)] [background-size:24px_24px]" />
      <div className="absolute top-1/4 -left-16 w-56 h-56 rounded-full bg-white/10 blur-3xl pointer-events-none" />
      <div className="absolute bottom-1/4 -right-16 w-56 h-56 rounded-full bg-black/25 blur-3xl pointer-events-none" />

      {/* ========================================================
          IMAGE 3 TOP BAR (Red #ff1b04 Gradient Background, No Coins)
         ======================================================== */}
      <header className="relative z-20 flex items-center justify-between px-3 pt-3 pb-1">
        {/* Left Side: White-Pink Gradient '<' Button + Sound + Music */}
        <div className="flex items-center gap-2">
          {/* Back Button */}
          <button
            onClick={() => {
              sounds.playClick();
              setShowPauseMenu(true);
            }}
            className="w-10 h-10 rounded-xl bg-gradient-to-r from-white via-pink-100 to-pink-300 border border-white/60 shadow-md active:scale-95 flex items-center justify-center text-black font-black text-2xl transition-transform cursor-pointer"
            title="Menu"
          >
            ‹
          </button>

          {/* Sound Button */}
          <button
            onClick={toggleSound}
            className="w-10 h-10 rounded-xl bg-black/40 border border-white/30 shadow-md flex items-center justify-center text-white transition-transform active:scale-95 cursor-pointer backdrop-blur-xs"
            title={isMuted ? 'Unmute' : 'Mute'}
          >
            {isMuted ? <VolumeX className="w-5 h-5 text-pink-300" /> : <Volume2 className="w-5 h-5 text-white" />}
          </button>

          {/* Music Button */}
          <button
            onClick={toggleMusic}
            className={`w-10 h-10 rounded-xl border border-white/30 shadow-md flex items-center justify-center transition-transform active:scale-95 cursor-pointer backdrop-blur-xs ${
              isMusicOn ? 'bg-gradient-to-r from-white via-pink-100 to-pink-300 text-black border-white' : 'bg-black/40 text-white/50'
            }`}
            title="Music Toggle"
          >
            <Music className="w-5 h-5" />
          </button>
        </div>

        {/* Turn Status Pill */}
        <div className="flex items-center">
          <div
            className={`px-3.5 py-1.5 rounded-full text-xs font-black uppercase tracking-wider transition-all border ${
              isLocalTurn
                ? 'bg-gradient-to-r from-white via-pink-100 to-pink-300 text-black border-white shadow-lg animate-pulse'
                : 'bg-black/50 text-white/90 border-white/30 backdrop-blur-xs'
            }`}
          >
            {isLocalTurn ? 'Your Turn' : `${opponent.name}'s Turn`}
          </div>
        </div>
      </header>

      {/* ========================================================
          IMAGE 3 OPPONENT: Top-Left Avatar (Card Count Hidden)
         ======================================================== */}
      <div className="relative z-10 px-4 pt-1 flex items-start justify-between">
        <div className="relative flex flex-col items-center">
          {/* Avatar frame */}
          <div className="relative">
            <div
              className={`w-16 h-16 sm:w-18 sm:h-18 rounded-2xl bg-black/40 p-1 shadow-xl transition-all ${
                !isLocalTurn
                  ? 'ring-4 ring-pink-300 ring-offset-2 ring-offset-[#ff1b04] scale-105 animate-pulse'
                  : 'border-2 border-white/30'
              }`}
            >
              <div className="w-full h-full rounded-xl bg-neutral-900 border border-neutral-700 flex items-center justify-center overflow-hidden text-3xl sm:text-4xl shadow-inner">
                {opponent.avatar || '👱‍♀️'}
              </div>
            </div>

            {/* UNO Call Badge */}
            {opponent.unoCalled && (
              <div className="absolute -bottom-2 -left-2 bg-pink-600 text-white text-[10px] font-black px-1.5 py-0.5 rounded-md border border-white shadow-md animate-bounce">
                UNO!
              </div>
            )}
          </div>

          {/* Opponent Name */}
          <div className="mt-1 text-center font-bold text-xs text-white drop-shadow-sm">
            {opponent.name}
          </div>

          {/* Opponent Emote Bubble */}
          {opponentEmote && (
            <div className="absolute left-20 top-2 bg-white text-black px-3 py-1.5 rounded-2xl shadow-2xl font-bold text-xs flex items-center gap-1.5 animate-bounce z-40 border-2 border-pink-400 whitespace-nowrap">
              <span>{opponentEmote.emote}</span>
              <span>{opponentEmote.text}</span>
            </div>
          )}
        </div>
      </div>

      {/* ========================================================
          IMAGE 3 CENTER PLAYFIELD:
          - Left: Draw Pile (DUO card back) - No number shown!
          - Center: Circular Active Color Ring with Orbit Arrows + Discard Card
         ======================================================== */}
      <div className="relative z-10 flex-1 flex items-center justify-center px-4 my-auto">
        <div className="relative w-full max-w-xs flex items-center justify-between">
          {/* DRAW PILE ON LEFT */}
          <div className="flex flex-col items-center">
            <div
              onClick={() => {
                if (isLocalTurn) {
                  sounds.playCardDraw();
                  onDrawCard();
                }
              }}
              className={`relative cursor-pointer transition-transform ${
                isLocalTurn ? 'hover:scale-105 active:scale-95' : 'opacity-85'
              }`}
            >
              {/* Stack depth under draw card */}
              <div className="absolute top-1 left-1 w-16 h-24 sm:w-20 sm:h-30 rounded-xl bg-black/40 border border-white/20 pointer-events-none" />
              <div className="absolute top-2 left-2 w-16 h-24 sm:w-20 sm:h-30 rounded-xl bg-black/60 border border-white/10 pointer-events-none" />

              <UnoCard isBack isHoverable={isLocalTurn} />
            </div>
            <span className="mt-1 text-[11px] font-bold text-white/90 drop-shadow-sm">
              Draw Pile
            </span>
          </div>

          {/* CENTER CIRCULAR ACTIVE COLOR RING WITH ROTATING ORBIT ARROWS */}
          <div className="relative flex items-center justify-center">
            <div
              style={{
                borderColor: activeColorHex,
                boxShadow: `0 0 30px ${activeColorHex}60, inset 0 0 20px ${activeColorHex}30`,
              }}
              className="relative w-36 h-36 sm:w-44 sm:h-44 rounded-full border-4 flex items-center justify-center transition-all duration-300"
            >
              {/* Animated Orbit Arrows */}
              <div
                className={`absolute inset-0 pointer-events-none ${
                  gameState.direction === 1 ? 'animate-spin-slow' : 'animate-spin-slow-reverse'
                }`}
              >
                <div className="absolute -top-3 right-2 text-2xl font-black text-pink-400 drop-shadow-md">
                  <svg className="w-8 h-8 fill-current" viewBox="0 0 24 24">
                    <path d="M12 2l4 4h-3v6h-2V6H8l4-4z" transform="rotate(45 12 12)" />
                  </svg>
                </div>
                <div className="absolute -bottom-3 left-2 text-2xl font-black text-pink-400 drop-shadow-md">
                  <svg className="w-8 h-8 fill-current" viewBox="0 0 24 24">
                    <path d="M12 2l4 4h-3v6h-2V6H8l4-4z" transform="rotate(225 12 12)" />
                  </svg>
                </div>
              </div>

              {/* Top Discard Card in Center */}
              <div className="z-10 transform scale-90 sm:scale-100 shadow-2xl">
                <UnoCard card={topCard} isHoverable={false} />
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* ========================================================
          IMAGE 3 PLAYER AVATAR: Mid-Right Above Hand
         ======================================================== */}
      <div className="relative z-10 px-4 flex items-center justify-between">
        {/* Left Action Controls (Emote, Catch Uno, UNO) */}
        <div className="flex items-center gap-2">
          {/* Reaction Button */}
          <div className="relative">
            <button
              onClick={() => setShowEmotePicker(!showEmotePicker)}
              className="p-2.5 rounded-2xl bg-neutral-900 border border-neutral-800 text-white hover:bg-neutral-800 transition-all shadow-md cursor-pointer flex items-center gap-1 active:scale-95"
            >
              <Smile className="w-5 h-5 text-pink-300" />
            </button>

            {/* Reaction Popover */}
            {showEmotePicker && (
              <div className="absolute bottom-12 left-0 bg-neutral-950 border-2 border-neutral-800 rounded-2xl p-2.5 shadow-2xl flex flex-col gap-1.5 z-40 w-44 animate-fadeIn">
                <div className="grid grid-cols-3 gap-1.5">
                  {EMOTE_OPTIONS.map((e) => (
                    <button
                      key={e.text}
                      onClick={() => {
                        sounds.playClick();
                        onSendEmote(e.emoji, e.text);
                        setShowEmotePicker(false);
                      }}
                      className="p-2 rounded-xl bg-neutral-900 hover:bg-neutral-800 text-lg flex items-center justify-center transition-transform hover:scale-110 cursor-pointer"
                      title={e.text}
                    >
                      {e.emoji}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {localEmote && (
              <div className="absolute left-0 -top-10 bg-white text-black px-3 py-1.5 rounded-2xl shadow-xl font-bold text-xs flex items-center gap-1.5 animate-bounce z-30 border-2 border-pink-400 whitespace-nowrap">
                <span>{localEmote.emote}</span>
                <span>{localEmote.text}</span>
              </div>
            )}
          </div>

          {/* Catch UNO Penalty Button */}
          {canCatchOpponent && (
            <button
              onClick={() => {
                sounds.playBuzzer();
                onCatchUno();
              }}
              className="py-2.5 px-3.5 rounded-2xl bg-gradient-to-r from-red-600 to-rose-600 text-white font-black text-xs shadow-lg border border-white animate-bounce cursor-pointer flex items-center gap-1"
            >
              <AlertTriangle className="w-4 h-4" />
              <span>CATCH! (+2)</span>
            </button>
          )}

          {/* UNO Button: White-Pink Gradient with Black Bold Text */}
          {canCallUno && (
            <button
              onClick={() => {
                sounds.playUnoCall();
                onCallUno();
              }}
              className="py-2.5 px-4 rounded-2xl bg-gradient-to-r from-white via-pink-100 to-pink-300 border border-white text-black font-black text-xs tracking-wider shadow-xl animate-pulse ring-4 ring-pink-400/40 cursor-pointer flex items-center gap-1.5 active:scale-95"
            >
              <Flame className="w-4 h-4 fill-pink-600 text-pink-600" />
              <span>UNO!</span>
            </button>
          )}
        </div>

        {/* Local Player Avatar Profile on Right */}
        <div className="relative flex flex-col items-center">
          <div
            className={`w-16 h-16 sm:w-18 sm:h-18 rounded-2xl bg-neutral-900 p-1 shadow-xl transition-all ${
              isLocalTurn
                ? 'ring-4 ring-emerald-400 ring-offset-2 ring-offset-black scale-105 animate-pulse'
                : 'border-2 border-neutral-800'
            }`}
          >
            <div className="w-full h-full rounded-xl bg-neutral-800 border border-neutral-700 flex items-center justify-center overflow-hidden text-3xl sm:text-4xl">
              {localPlayer.avatar || '👨'}
            </div>
          </div>

          <div className="mt-1 text-center font-bold text-xs text-white">
            {localPlayer.name}
          </div>

          {localPlayer.unoCalled && (
            <div className="absolute -top-2 -left-2 bg-pink-600 text-white text-[10px] font-black px-1.5 py-0.5 rounded-md border border-white shadow-md animate-bounce">
              UNO!
            </div>
          )}
        </div>
      </div>

      {/* ========================================================
          IMAGE 3 PLAYER HAND: Overlapping Horizontal Layout
          NO HINTS for cards - clean authentic feel!
         ======================================================== */}
      <footer className="relative z-20 pb-4 pt-1 px-2">
        {/* Last Action Announcement Pill */}
        {lastAction && (
          <div className="text-center mb-1 text-[11px] font-bold text-neutral-300 bg-neutral-900/90 py-1 px-3 rounded-full max-w-xs mx-auto truncate border border-neutral-800">
            {lastAction.text}
          </div>
        )}

        {/* Overlapping Cards Horizontal Deck */}
        <div className="relative overflow-x-auto py-2 px-1 flex items-end justify-center no-scrollbar min-h-[125px]">
          <div className="flex items-end -space-x-8 sm:-space-x-10 pb-1">
            {localPlayer.hand.map((card, idx) => {
              return (
                <div
                  key={card.id}
                  style={{ zIndex: idx }}
                  className="transition-all duration-150 origin-bottom hover:!z-50 hover:-translate-y-3"
                >
                  <UnoCard
                    card={card}
                    shake={shakingCardId === card.id}
                    onClick={() => handleCardClick(card)}
                  />
                </div>
              );
            })}
          </div>
        </div>
      </footer>

      {/* COLOR PICKER MODAL (WILD) */}
      <ColorPickerModal
        isOpen={pendingWildCardId !== null}
        onSelectColor={handleSelectColor}
      />

      {/* PAUSE / IN-GAME MENU MODAL */}
      <PauseMenuModal
        isOpen={showPauseMenu}
        onResume={() => setShowPauseMenu(false)}
        onQuit={() => {
          setShowPauseMenu(false);
          onLeaveGame();
        }}
        onHowToPlay={() => {
          setShowPauseMenu(false);
          onOpenRules();
        }}
        isSoundOn={!isMuted}
        onToggleSound={toggleSound}
        isMusicOn={isMusicOn}
        onToggleMusic={toggleMusic}
      />

      {/* VICTORY CELEBRATION MODAL WITH CELEBRATORY CONFETTI */}
      <VictoryModal
        isOpen={status === 'gameover'}
        winner={players.find((p) => p.id === winnerId) || null}
        localPlayerId={localPlayerId}
        opponent={opponent}
        onRematch={onRematch}
        onHome={onLeaveGame}
        isOnline={isOnline}
      />
    </div>
  );
};
