import React from 'react';
import { X, HelpCircle, Flame, ShieldAlert, Award } from 'lucide-react';

interface HowToPlayModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const HowToPlayModal: React.FC<HowToPlayModalProps> = ({ isOpen, onClose }) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/85 backdrop-blur-xs p-4 animate-fadeIn">
      <div className="relative w-full max-w-md bg-[#1f0301] border-2 border-[#ff1b04]/60 rounded-3xl p-6 shadow-2xl text-white max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-neutral-800 mb-4">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-xl bg-amber-500/20 text-amber-400">
              <HelpCircle className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-xl font-black">How to Play UNO</h2>
              <p className="text-xs text-neutral-400">2-Player Duel Edition</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-2 rounded-xl bg-neutral-800 text-neutral-400 hover:text-white cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="space-y-4 text-xs text-neutral-300">
          {/* Goal */}
          <div className="p-3 rounded-2xl bg-neutral-800/60 border border-neutral-700/80">
            <div className="font-extrabold text-sm text-amber-400 flex items-center gap-1.5 mb-1">
              <Award className="w-4 h-4" /> Objective
            </div>
            <p>
              Be the first player to discard all cards from your hand. You start with 7 cards. Match the discard pile by <strong className="text-white">Color</strong> or <strong className="text-white">Number/Action</strong>.
            </p>
          </div>

          {/* Action Cards */}
          <div>
            <div className="font-extrabold text-sm text-neutral-200 mb-2">Special Action Cards</div>
            <div className="grid grid-cols-2 gap-2">
              <div className="p-2.5 rounded-xl bg-neutral-800/80 border border-neutral-700">
                <div className="font-bold text-red-400 mb-0.5">Skip (⊘)</div>
                <div className="text-[11px] text-neutral-400">
                  In 2-player UNO, skips your opponent, granting you an immediate extra turn!
                </div>
              </div>
              <div className="p-2.5 rounded-xl bg-neutral-800/80 border border-neutral-700">
                <div className="font-bold text-blue-400 mb-0.5">Reverse (⇄)</div>
                <div className="text-[11px] text-neutral-400">
                  Reverses turn order. In 2-player rules, this also acts as a Skip so you go again!
                </div>
              </div>
              <div className="p-2.5 rounded-xl bg-neutral-800/80 border border-neutral-700">
                <div className="font-bold text-emerald-400 mb-0.5">Draw Two (+2)</div>
                <div className="text-[11px] text-neutral-400">
                  Opponent must draw 2 cards and forfeit their turn (unless Stacking is on).
                </div>
              </div>
              <div className="p-2.5 rounded-xl bg-neutral-800/80 border border-neutral-700">
                <div className="font-bold text-purple-400 mb-0.5">Wild / Wild +4</div>
                <div className="text-[11px] text-neutral-400">
                  Playable on any card. Pick the new active color; +4 forces opponent to draw 4.
                </div>
              </div>
            </div>
          </div>

          {/* The UNO Rule */}
          <div className="p-3 rounded-2xl bg-amber-500/10 border border-amber-500/30">
            <div className="font-extrabold text-sm text-amber-400 flex items-center gap-1.5 mb-1">
              <Flame className="w-4 h-4" /> The Golden "UNO!" Rule
            </div>
            <p>
              When playing your second-to-last card (or holding 1 card), press the glowing <strong className="text-white">UNO!</strong> button! If your opponent catches you before you call it, they can press <strong className="text-white">CATCH!</strong> to give you a 2-card penalty!
            </p>
          </div>

          {/* House Rules */}
          <div className="p-3 rounded-2xl bg-neutral-800/60 border border-neutral-700/80">
            <div className="font-extrabold text-sm text-neutral-200 flex items-center gap-1.5 mb-1">
              <ShieldAlert className="w-4 h-4" /> Customizable House Rules
            </div>
            <ul className="list-disc list-inside space-y-1 text-neutral-400 text-[11px]">
              <li><strong className="text-neutral-200">Draw Stacking:</strong> Stack +2 on +2 or +4 on +4 to pass the combined draw penalty.</li>
              <li><strong className="text-neutral-200">7-Swap:</strong> Playing a 7 swaps your entire hand with your opponent!</li>
              <li><strong className="text-neutral-200">Draw to Match:</strong> Keep drawing cards until you find one you can play.</li>
              <li><strong className="text-neutral-200">Jump-In:</strong> Play an identical card out of turn.</li>
            </ul>
          </div>
        </div>

        <div className="pt-4 mt-2">
          <button
            type="button"
            onClick={onClose}
            className="w-full py-3.5 rounded-2xl bg-gradient-to-r from-white via-pink-100 to-pink-300 hover:from-pink-50 hover:to-pink-200 text-black font-black text-sm transition-all shadow-lg shadow-pink-500/20 cursor-pointer border border-white/60 active:scale-95"
          >
            Got it, Let's Play!
          </button>
        </div>
      </div>
    </div>
  );
};
