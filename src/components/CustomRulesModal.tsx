import React from 'react';
import { GameRules, AiDifficulty } from '../game/types';
import { sounds } from '../utils/audio';
import { Sliders, Check, RotateCcw, X } from 'lucide-react';

interface CustomRulesModalProps {
  isOpen: boolean;
  rules: GameRules;
  onUpdateRules: (newRules: GameRules) => void;
  onClose: () => void;
  isHost?: boolean;
}

export const CustomRulesModal: React.FC<CustomRulesModalProps> = ({
  isOpen,
  rules,
  onUpdateRules,
  onClose,
  isHost = true,
}) => {
  if (!isOpen) return null;

  const toggleRule = (key: keyof GameRules) => {
    if (!isHost) return;
    sounds.playClick();
    onUpdateRules({
      ...rules,
      [key]: !rules[key],
    });
  };

  const setDifficulty = (diff: AiDifficulty) => {
    if (!isHost) return;
    sounds.playClick();
    onUpdateRules({
      ...rules,
      aiDifficulty: diff,
    });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/85 backdrop-blur-xs p-4 animate-fadeIn">
      <div className="relative w-full max-w-md bg-[#1f0301] border-2 border-[#ff1b04]/60 rounded-3xl p-6 shadow-2xl text-white max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-neutral-800 mb-4">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-xl bg-amber-500/20 text-amber-400">
              <Sliders className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-xl font-black">House & Match Rules</h2>
              <p className="text-xs text-neutral-400">Customize how your UNO duels play</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-2 rounded-xl bg-neutral-800 text-neutral-400 hover:text-white cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* AI Difficulty */}
        <div className="mb-5">
          <label className="block text-xs font-bold uppercase tracking-wider text-neutral-400 mb-2">
            AI Opponent Difficulty
          </label>
          <div className="grid grid-cols-3 gap-2">
            {(['easy', 'medium', 'hard'] as AiDifficulty[]).map((d) => (
              <button
                key={d}
                type="button"
                onClick={() => setDifficulty(d)}
                className={`py-2 px-3 rounded-xl border text-xs font-bold capitalize transition-all cursor-pointer ${
                  rules.aiDifficulty === d
                    ? 'bg-amber-400 text-neutral-950 border-amber-300 shadow-md ring-1 ring-amber-300'
                    : 'bg-neutral-800 text-neutral-300 border-neutral-700 hover:bg-neutral-700'
                }`}
              >
                {d}
              </button>
            ))}
          </div>
        </div>

        {/* Rule Toggles */}
        <div className="space-y-3 mb-6">
          {/* Stacking Rule */}
          <div
            onClick={() => toggleRule('stackDrawCards')}
            className={`p-3.5 rounded-2xl border transition-all cursor-pointer flex items-center justify-between ${
              rules.stackDrawCards
                ? 'bg-neutral-800/90 border-amber-500/50'
                : 'bg-neutral-800/40 border-neutral-700/60 opacity-70'
            }`}
          >
            <div>
              <div className="text-sm font-extrabold flex items-center gap-2">
                <span>Draw Stacking (+2 / +4)</span>
                {rules.stackDrawCards && (
                  <span className="text-[10px] bg-amber-500/20 text-amber-300 px-2 py-0.5 rounded-full font-bold">
                    ON
                  </span>
                )}
              </div>
              <p className="text-xs text-neutral-400 mt-0.5">
                Respond to a +2 with another +2 (or +4 on +4) to pass the penalty to opponent
              </p>
            </div>
            <div
              className={`w-6 h-6 rounded-lg flex items-center justify-center border ${
                rules.stackDrawCards
                  ? 'bg-amber-400 border-amber-400 text-neutral-950'
                  : 'border-neutral-600'
              }`}
            >
              {rules.stackDrawCards && <Check className="w-4 h-4 stroke-[3]" />}
            </div>
          </div>

          {/* 2-Player Reverse as Skip */}
          <div
            onClick={() => toggleRule('reverseAsSkipInTwoPlayer')}
            className={`p-3.5 rounded-2xl border transition-all cursor-pointer flex items-center justify-between ${
              rules.reverseAsSkipInTwoPlayer
                ? 'bg-neutral-800/90 border-amber-500/50'
                : 'bg-neutral-800/40 border-neutral-700/60 opacity-70'
            }`}
          >
            <div>
              <div className="text-sm font-extrabold flex items-center gap-2">
                <span>2-Player Reverse Extra Turn</span>
                {rules.reverseAsSkipInTwoPlayer && (
                  <span className="text-[10px] bg-amber-500/20 text-amber-300 px-2 py-0.5 rounded-full font-bold">
                    OFFICIAL
                  </span>
                )}
              </div>
              <p className="text-xs text-neutral-400 mt-0.5">
                In official 2-player UNO, playing Reverse skips your opponent and lets you play again
              </p>
            </div>
            <div
              className={`w-6 h-6 rounded-lg flex items-center justify-center border ${
                rules.reverseAsSkipInTwoPlayer
                  ? 'bg-amber-400 border-amber-400 text-neutral-950'
                  : 'border-neutral-600'
              }`}
            >
              {rules.reverseAsSkipInTwoPlayer && <Check className="w-4 h-4 stroke-[3]" />}
            </div>
          </div>

          {/* Draw to Match vs Draw One */}
          <div
            onClick={() => toggleRule('drawToMatch')}
            className={`p-3.5 rounded-2xl border transition-all cursor-pointer flex items-center justify-between ${
              rules.drawToMatch
                ? 'bg-neutral-800/90 border-amber-500/50'
                : 'bg-neutral-800/40 border-neutral-700/60 opacity-70'
            }`}
          >
            <div>
              <div className="text-sm font-extrabold flex items-center gap-2">
                <span>Draw-to-Match Mode</span>
                {rules.drawToMatch && (
                  <span className="text-[10px] bg-amber-500/20 text-amber-300 px-2 py-0.5 rounded-full font-bold">
                    CHAOS
                  </span>
                )}
              </div>
              <p className="text-xs text-neutral-400 mt-0.5">
                Keep drawing cards repeatedly until you pull a playable match (high stakes!)
              </p>
            </div>
            <div
              className={`w-6 h-6 rounded-lg flex items-center justify-center border ${
                rules.drawToMatch
                  ? 'bg-amber-400 border-amber-400 text-neutral-950'
                  : 'border-neutral-600'
              }`}
            >
              {rules.drawToMatch && <Check className="w-4 h-4 stroke-[3]" />}
            </div>
          </div>

          {/* 7-0 Hand Swap */}
          <div
            onClick={() => toggleRule('sevenZeroRule')}
            className={`p-3.5 rounded-2xl border transition-all cursor-pointer flex items-center justify-between ${
              rules.sevenZeroRule
                ? 'bg-neutral-800/90 border-amber-500/50'
                : 'bg-neutral-800/40 border-neutral-700/60 opacity-70'
            }`}
          >
            <div>
              <div className="text-sm font-extrabold flex items-center gap-2">
                <span>7 Swap Rule</span>
                {rules.sevenZeroRule && (
                  <span className="text-[10px] bg-amber-500/20 text-amber-300 px-2 py-0.5 rounded-full font-bold">
                    ACTIVE
                  </span>
                )}
              </div>
              <p className="text-xs text-neutral-400 mt-0.5">
                Playing a 7 swaps your entire hand with your opponent!
              </p>
            </div>
            <div
              className={`w-6 h-6 rounded-lg flex items-center justify-center border ${
                rules.sevenZeroRule
                  ? 'bg-amber-400 border-amber-400 text-neutral-950'
                  : 'border-neutral-600'
              }`}
            >
              {rules.sevenZeroRule && <Check className="w-4 h-4 stroke-[3]" />}
            </div>
          </div>

          {/* Jump-In */}
          <div
            onClick={() => toggleRule('jumpIn')}
            className={`p-3.5 rounded-2xl border transition-all cursor-pointer flex items-center justify-between ${
              rules.jumpIn
                ? 'bg-neutral-800/90 border-amber-500/50'
                : 'bg-neutral-800/40 border-neutral-700/60 opacity-70'
            }`}
          >
            <div>
              <div className="text-sm font-extrabold flex items-center gap-2">
                <span>Jump-In Rule</span>
                {rules.jumpIn && (
                  <span className="text-[10px] bg-amber-500/20 text-amber-300 px-2 py-0.5 rounded-full font-bold">
                    ACTIVE
                  </span>
                )}
              </div>
              <p className="text-xs text-neutral-400 mt-0.5">
                Play an exact identical card (same color + value) immediately out-of-turn
              </p>
            </div>
            <div
              className={`w-6 h-6 rounded-lg flex items-center justify-center border ${
                rules.jumpIn
                  ? 'bg-amber-400 border-amber-400 text-neutral-950'
                  : 'border-neutral-600'
              }`}
            >
              {rules.jumpIn && <Check className="w-4 h-4 stroke-[3]" />}
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="flex gap-3">
          <button
            type="button"
            onClick={onClose}
            className="w-full py-3.5 px-4 rounded-2xl bg-gradient-to-r from-white via-pink-100 to-pink-300 hover:from-pink-50 hover:to-pink-200 text-black font-black text-sm transition-all shadow-lg shadow-pink-500/20 cursor-pointer border border-white/60 active:scale-95"
          >
            Save & Done
          </button>
        </div>
      </div>
    </div>
  );
};
