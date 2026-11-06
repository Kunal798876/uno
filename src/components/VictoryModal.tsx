import React, { useEffect, useState } from 'react';
import confetti from 'canvas-confetti';
import { Player } from '../game/types';
import { sounds } from '../utils/audio';
import { Trophy, RotateCcw, Home, Sparkles } from 'lucide-react';

interface VictoryModalProps {
  isOpen: boolean;
  winner: Player | null;
  localPlayerId: string;
  opponent: Player | null;
  onRematch: () => void;
  onHome: () => void;
  isOnline?: boolean;
}

export const VictoryModal: React.FC<VictoryModalProps> = ({
  isOpen,
  winner,
  localPlayerId,
  opponent,
  onRematch,
  onHome,
}) => {
  const [particles, setParticles] = useState<Array<{ id: number; left: number; delay: number; size: number }>>([]);

  if (!isOpen || !winner) return null;

  const isLocalWinner = winner.id === localPlayerId;

  useEffect(() => {
    if (isLocalWinner) {
      sounds.playVictory();

      // Multi-stage celebratory confetti cannons
      try {
        confetti({
          particleCount: 80,
          spread: 80,
          origin: { y: 0.55 },
          colors: ['#FF69B4', '#FFFFFF', '#FFD700', '#00A651', '#0072BC'],
        });

        setTimeout(() => {
          confetti({
            particleCount: 60,
            angle: 60,
            spread: 60,
            origin: { x: 0.1, y: 0.65 },
            colors: ['#FFFFFF', '#FFB6C1', '#FF1493'],
          });
        }, 250);

        setTimeout(() => {
          confetti({
            particleCount: 60,
            angle: 120,
            spread: 60,
            origin: { x: 0.9, y: 0.65 },
            colors: ['#FFFFFF', '#FFB6C1', '#FF69B4'],
          });
        }, 450);

        setTimeout(() => {
          confetti({
            particleCount: 100,
            spread: 100,
            origin: { y: 0.45 },
            shapes: ['circle', 'square'],
            colors: ['#FFFFFF', '#FFD700', '#FF69B4'],
          });
        }, 750);
      } catch (e) {
        console.error('Confetti trigger error', e);
      }

      const newParticles = Array.from({ length: 24 }).map((_, i) => ({
        id: i,
        left: Math.random() * 90 + 5,
        delay: Math.random() * 1.5,
        size: Math.random() * 14 + 10,
      }));
      setParticles(newParticles);
    } else {
      sounds.playDefeat();
    }
  }, [isLocalWinner]);

  const opponentCardsRemaining = opponent ? opponent.hand.length : 0;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/90 backdrop-blur-xs p-4 animate-fadeIn overflow-hidden">
      {/* Celebratory SVG Particle Layer */}
      {isLocalWinner && (
        <div className="absolute inset-0 pointer-events-none z-10">
          {particles.map((p) => (
            <div
              key={p.id}
              style={{
                left: `${p.left}%`,
                animationDelay: `${p.delay}s`,
                width: `${p.size}px`,
                height: `${p.size}px`,
              }}
              className="absolute -top-6 animate-confettiFall"
            >
              {p.id % 2 === 0 ? (
                <Sparkles className="w-full h-full text-pink-300 drop-shadow-md" />
              ) : (
                <div className="w-full h-full rotate-45 bg-gradient-to-br from-white to-pink-400 rounded-xs shadow-sm" />
              )}
            </div>
          ))}
        </div>
      )}

      {/* Main Victory Dialog with Red Accents */}
      <div className="relative z-20 w-full max-w-xs bg-[#1f0301] border-2 border-[#ff1b04]/60 rounded-3xl p-6 shadow-2xl text-white text-center animate-scaleUp">
        {/* Header Pill */}
        <div className="absolute -top-4 left-1/2 -translate-x-1/2 bg-gradient-to-r from-white via-pink-100 to-pink-300 border border-white px-6 py-1 rounded-full shadow-lg">
          <span className="text-black font-black text-sm tracking-wider uppercase">
            {isLocalWinner ? 'VICTORY!' : 'GAME OVER'}
          </span>
        </div>

        {/* Center Trophy */}
        <div className="mt-4 mb-3 relative inline-block">
          <div
            className={`w-20 h-20 rounded-3xl flex items-center justify-center text-4xl shadow-xl mx-auto border-2 ${
              isLocalWinner
                ? 'bg-gradient-to-tr from-pink-500 to-rose-400 border-white text-white'
                : 'bg-neutral-900 border-neutral-800 text-neutral-500'
            }`}
          >
            {isLocalWinner ? <Trophy className="w-10 h-10 stroke-[2.5]" /> : winner.avatar}
          </div>
        </div>

        <h3 className="text-2xl font-black mb-1 drop-shadow-md">
          {isLocalWinner ? 'YOU WON!' : `${winner.name} Won!`}
        </h3>
        <p className="text-xs text-neutral-400 mb-5 font-medium">
          {isLocalWinner
            ? 'You discarded all cards and won the duel!'
            : 'Better luck in the next round!'}
        </p>

        {opponent && (
          <div className="bg-neutral-900/80 border border-neutral-800 rounded-xl p-3 mb-5 flex items-center justify-between text-xs text-neutral-300">
            <span>Opponent cards left:</span>
            <span className="font-black text-white">{opponentCardsRemaining}</span>
          </div>
        )}

        {/* Action Buttons: White-Pink Gradient with Black Bold Text */}
        <div className="space-y-3">
          <button
            data-start="true"
            onClick={() => {
              sounds.playClick();
              onRematch();
            }}
            className="w-full py-3.5 px-4 rounded-2xl bg-gradient-to-r from-white via-pink-100 to-pink-300 hover:from-pink-50 hover:to-pink-200 border border-white/60 shadow-lg text-black font-black text-base tracking-wide transition-all cursor-pointer flex items-center justify-center gap-2 active:scale-95"
          >
            <RotateCcw className="w-4 h-4 stroke-[3]" /> Play Again
          </button>

          <button
            onClick={() => {
              sounds.playClick();
              onHome();
            }}
            className="w-full py-3 px-4 rounded-2xl bg-neutral-900 hover:bg-neutral-800 border border-neutral-700 text-white font-bold text-xs transition-colors cursor-pointer flex items-center justify-center gap-1.5 active:scale-95"
          >
            <Home className="w-4 h-4" /> Return to Menu
          </button>
        </div>
      </div>
    </div>
  );
};
