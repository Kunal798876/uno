import React, { useState } from 'react';
import { GameRules, UserProfile } from '../game/types';
import { sounds } from '../utils/audio';
import { Copy, Check, Users, ArrowLeft, Play, Sliders } from 'lucide-react';

interface RoomLobbyProps {
  roomCode: string;
  isHost: boolean;
  players: { id: string; name: string; avatar: string; ready: boolean }[];
  rules: GameRules;
  profile: UserProfile;
  onStartMatch: () => void;
  onOpenRules: () => void;
  onLeave: () => void;
}

export const RoomLobby: React.FC<RoomLobbyProps> = ({
  roomCode,
  isHost,
  players,
  rules,
  onStartMatch,
  onOpenRules,
  onLeave,
}) => {
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    sounds.playClick();
    if (navigator.clipboard) {
      navigator.clipboard.writeText(roomCode);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const isFull = players.length >= 2;

  return (
    <div className="w-full max-w-md mx-auto min-h-screen flex flex-col justify-between p-4 sm:p-6 text-white bg-[#ff1b04] bg-[radial-gradient(circle_at_50%_25%,_#ff3820_0%,_#ff1b04_55%,_#9e0e00_100%)] font-sans animate-fadeIn relative overflow-hidden">
      {/* Subtle floating ambient UNO glow */}
      <div className="absolute inset-0 pointer-events-none opacity-15 bg-[radial-gradient(#ffffff_2px,transparent_2px)] [background-size:24px_24px]" />
      <div className="absolute -top-12 -left-12 w-48 h-48 rounded-full bg-white/10 blur-2xl pointer-events-none" />
      <div className="absolute -bottom-12 -right-12 w-52 h-52 rounded-full bg-black/25 blur-2xl pointer-events-none" />

      {/* Top Bar */}
      <div className="flex items-center justify-between">
        <button
          onClick={onLeave}
          className="flex items-center gap-1.5 text-xs font-bold text-neutral-300 hover:text-white bg-neutral-900 border border-neutral-800 px-3.5 py-2 rounded-xl transition-all cursor-pointer active:scale-95"
        >
          <ArrowLeft className="w-4 h-4" /> Leave Lobby
        </button>

        <button
          onClick={onOpenRules}
          className="flex items-center gap-1.5 text-xs font-bold text-pink-300 hover:text-white bg-neutral-900 border border-neutral-800 px-3.5 py-2 rounded-xl transition-all cursor-pointer active:scale-95"
        >
          <Sliders className="w-4 h-4" /> House Rules
        </button>
      </div>

      {/* Center Content: Room Code & Slots */}
      <div className="my-auto py-6 space-y-6">
        {/* Monospace Code Display */}
        <div className="text-center">
          <span className="text-xs font-bold uppercase tracking-widest text-pink-300 bg-pink-500/10 px-3 py-1 rounded-full border border-pink-400/20 inline-block mb-3">
            Private Match Code
          </span>
          <h2 className="text-sm text-neutral-400 mb-2 font-medium">
            Share this 5-digit code with your friend anywhere in the world:
          </h2>

          <div className="inline-flex items-center gap-3 bg-neutral-950 border-2 border-pink-400/40 rounded-3xl p-3 px-6 shadow-xl shadow-pink-500/10">
            <span className="text-4xl sm:text-5xl font-black font-mono tracking-widest text-white drop-shadow-md">
              {roomCode}
            </span>
            <button
              onClick={handleCopy}
              className={`p-2.5 rounded-2xl border transition-all cursor-pointer flex items-center justify-center active:scale-95 ${
                copied
                  ? 'bg-emerald-500 border-emerald-400 text-white'
                  : 'bg-neutral-900 border-neutral-700 text-neutral-300 hover:text-white'
              }`}
              title="Copy Code"
            >
              {copied ? <Check className="w-5 h-5" /> : <Copy className="w-5 h-5" />}
            </button>
          </div>
          {copied && (
            <p className="text-xs text-emerald-400 font-bold mt-2 animate-fadeIn">
              ✓ Room code copied to clipboard!
            </p>
          )}
        </div>

        {/* 2 Player Slots */}
        <div className="space-y-3">
          <div className="text-xs font-bold uppercase tracking-wider text-neutral-400 flex items-center justify-between px-1">
            <span>Players in Room</span>
            <span>{players.length}/2 Connected</span>
          </div>

          {/* Slot 1: Host */}
          <div className="bg-neutral-950 border border-neutral-800 rounded-2xl p-4 flex items-center justify-between shadow-md">
            <div className="flex items-center gap-3">
              <div className="text-3xl p-2 rounded-xl bg-neutral-900 border border-neutral-800">
                {players[0]?.avatar || '👑'}
              </div>
              <div>
                <div className="text-sm font-extrabold flex items-center gap-2">
                  <span>{players[0]?.name || 'Host'}</span>
                  <span className="text-[10px] bg-pink-600/30 text-pink-300 border border-pink-500/30 px-2 py-0.5 rounded-full font-bold">
                    HOST
                  </span>
                </div>
                <div className="text-xs text-emerald-400 font-medium flex items-center gap-1.5 mt-0.5">
                  <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" /> Ready to Duel
                </div>
              </div>
            </div>
          </div>

          {/* Slot 2: Guest or Waiting */}
          {players[1] ? (
            <div className="bg-neutral-950 border border-neutral-800 rounded-2xl p-4 flex items-center justify-between shadow-md animate-fadeIn">
              <div className="flex items-center gap-3">
                <div className="text-3xl p-2 rounded-xl bg-neutral-900 border border-neutral-800">
                  {players[1].avatar}
                </div>
                <div>
                  <div className="text-sm font-extrabold flex items-center gap-2">
                    <span>{players[1].name}</span>
                    <span className="text-[10px] bg-blue-600/30 text-blue-300 border border-blue-500/30 px-2 py-0.5 rounded-full font-bold">
                      GUEST
                    </span>
                  </div>
                  <div className="text-xs text-emerald-400 font-medium flex items-center gap-1.5 mt-0.5">
                    <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" /> Connected & Ready
                  </div>
                </div>
              </div>
            </div>
          ) : (
            <div className="border-2 border-dashed border-neutral-800 rounded-2xl p-5 flex flex-col items-center justify-center text-center bg-neutral-950/60">
              <div className="relative mb-2">
                <div className="w-10 h-10 rounded-full bg-pink-400/10 border border-pink-400/30 flex items-center justify-center text-pink-300">
                  <Users className="w-5 h-5 animate-pulse" />
                </div>
                <div className="absolute inset-0 rounded-full border border-pink-400/20 animate-ping" />
              </div>
              <div className="text-xs font-bold text-neutral-300">Waiting for friend to join...</div>
              <div className="text-[11px] text-neutral-500 mt-0.5">
                Tell your friend to enter code <strong className="text-pink-300 font-mono">{roomCode}</strong> on their device
              </div>
            </div>
          )}
        </div>

        {/* Rules summary banner */}
        <div className="bg-neutral-950 border border-neutral-800 rounded-2xl p-3.5 text-xs text-neutral-400">
          <div className="font-bold text-neutral-300 mb-1.5 flex items-center justify-between">
            <span>Rules for this match:</span>
            {isHost && (
              <span className="text-[10px] text-pink-300 cursor-pointer hover:underline" onClick={onOpenRules}>
                Edit Rules
              </span>
            )}
          </div>
          <div className="flex flex-wrap gap-1.5">
            <span className="bg-neutral-900 px-2 py-1 rounded-lg text-[10px] border border-neutral-800">
              Auto +2/+4: ✓ Direct Draw
            </span>
            <span className="bg-neutral-900 px-2 py-1 rounded-lg text-[10px] border border-neutral-800">
              2P Reverse: {rules.reverseAsSkipInTwoPlayer ? '✓ Extra Turn' : 'Standard'}
            </span>
            <span className="bg-neutral-900 px-2 py-1 rounded-lg text-[10px] border border-neutral-800">
              Draw Mode: {rules.drawToMatch ? 'To Match' : 'Draw 1'}
            </span>
            {rules.sevenZeroRule && (
              <span className="bg-neutral-900 px-2 py-1 rounded-lg text-[10px] text-pink-300 border border-neutral-800">
                7 Hand Swap: ✓
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Start Button: White-Pink Gradient with Black Bold Text */}
      <div className="pt-4">
        {isHost ? (
          <button
            onClick={() => {
              if (isFull) {
                sounds.playCardPlay();
                onStartMatch();
              }
            }}
            disabled={!isFull}
            className={`w-full py-4 px-6 rounded-2xl font-black text-base flex items-center justify-center gap-2 transition-all cursor-pointer shadow-xl active:scale-95 ${
              isFull
                ? 'bg-gradient-to-r from-white via-pink-100 to-pink-300 hover:from-pink-50 hover:to-pink-200 text-black border border-white/60 shadow-pink-500/20'
                : 'bg-neutral-900 text-neutral-500 border border-neutral-800 cursor-not-allowed'
            }`}
          >
            <Play className="w-5 h-5 fill-current" />
            {isFull ? 'START UNO DUEL!' : 'Waiting for Player 2 to Join...'}
          </button>
        ) : (
          <div className="text-center p-4 rounded-2xl bg-neutral-950 border border-neutral-800 text-xs text-neutral-400">
            Waiting for host to launch the match...
          </div>
        )}
      </div>
    </div>
  );
};
