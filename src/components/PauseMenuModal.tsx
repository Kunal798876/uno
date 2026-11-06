import React, { useState } from 'react';
import { sounds } from '../utils/audio';
import { Check } from 'lucide-react';

interface PauseMenuModalProps {
  isOpen: boolean;
  onResume: () => void;
  onQuit: () => void;
  onHowToPlay: () => void;
  isSoundOn: boolean;
  onToggleSound: () => void;
  isMusicOn: boolean;
  onToggleMusic: () => void;
}

export const PauseMenuModal: React.FC<PauseMenuModalProps> = ({
  isOpen,
  onResume,
  onQuit,
  onHowToPlay,
  isSoundOn,
  onToggleSound,
  isMusicOn,
  onToggleMusic,
}) => {
  const [showQuitConfirm, setShowQuitConfirm] = useState(false);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/90 backdrop-blur-xs p-4 animate-fadeIn">
      {!showQuitConfirm ? (
        <div className="relative w-full max-w-xs flex flex-col items-center gap-3 text-center">
          {/* Resume */}
          <button
            onClick={() => {
              sounds.playClick();
              onResume();
            }}
            className="w-full py-4 px-6 rounded-2xl bg-gradient-to-r from-white via-pink-100 to-pink-300 hover:from-pink-50 hover:to-pink-200 border border-white/60 shadow-lg text-black font-black text-lg tracking-wide uppercase transition-all cursor-pointer active:scale-95"
          >
            Resume
          </button>

          {/* Music Toggle */}
          <button
            onClick={() => {
              sounds.playClick();
              onToggleMusic();
            }}
            className="w-full py-3.5 px-6 rounded-2xl bg-gradient-to-r from-white via-pink-100 to-pink-300 hover:from-pink-50 hover:to-pink-200 border border-white/60 shadow-lg text-black font-black text-base tracking-wide flex items-center justify-between transition-all cursor-pointer active:scale-95"
          >
            <span>Music</span>
            <div
              className={`w-6 h-6 rounded-lg border-2 flex items-center justify-center transition-colors ${
                isMusicOn
                  ? 'bg-pink-600 border-pink-700 text-white'
                  : 'bg-black/20 border-black/40'
              }`}
            >
              {isMusicOn && <Check className="w-4 h-4 stroke-[3]" />}
            </div>
          </button>

          {/* Sound Toggle */}
          <button
            onClick={() => {
              sounds.playClick();
              onToggleSound();
            }}
            className="w-full py-3.5 px-6 rounded-2xl bg-gradient-to-r from-white via-pink-100 to-pink-300 hover:from-pink-50 hover:to-pink-200 border border-white/60 shadow-lg text-black font-black text-base tracking-wide flex items-center justify-between transition-all cursor-pointer active:scale-95"
          >
            <span>Sound</span>
            <div
              className={`w-6 h-6 rounded-lg border-2 flex items-center justify-center transition-colors ${
                isSoundOn
                  ? 'bg-pink-600 border-pink-700 text-white'
                  : 'bg-black/20 border-black/40'
              }`}
            >
              {isSoundOn && <Check className="w-4 h-4 stroke-[3]" />}
            </div>
          </button>

          {/* How to Play */}
          <button
            onClick={() => {
              sounds.playClick();
              onHowToPlay();
            }}
            className="w-full py-3.5 px-6 rounded-2xl bg-gradient-to-r from-white via-pink-100 to-pink-300 hover:from-pink-50 hover:to-pink-200 border border-white/60 shadow-lg text-black font-black text-base tracking-wide transition-all cursor-pointer active:scale-95"
          >
            How to play
          </button>

          {/* Quit */}
          <button
            onClick={() => {
              sounds.playClick();
              setShowQuitConfirm(true);
            }}
            className="w-full py-3.5 px-6 rounded-2xl bg-gradient-to-r from-white via-pink-100 to-pink-300 hover:from-pink-50 hover:to-pink-200 border border-white/60 shadow-lg text-black font-black text-base tracking-wide transition-all cursor-pointer active:scale-95"
          >
            Quit
          </button>
        </div>
      ) : (
        /* Quit Confirmation Dialog */
        <div className="relative w-full max-w-xs bg-[#1f0301] border-2 border-[#ff1b04]/60 rounded-3xl p-6 shadow-2xl text-center animate-scaleUp">
          {/* Header Pill */}
          <div className="absolute -top-4 left-1/2 -translate-x-1/2 bg-red-600 border-2 border-white px-6 py-1 rounded-full shadow-lg">
            <span className="text-white font-black text-xs tracking-wider uppercase">
              QUIT
            </span>
          </div>

          <p className="text-white font-bold text-base mt-3 mb-6">
            Are you sure you want to quit the match?
          </p>

          <div className="flex gap-3">
            <button
              onClick={() => {
                sounds.playClick();
                onQuit();
              }}
              className="flex-1 py-3 px-4 rounded-2xl bg-gradient-to-r from-white via-pink-100 to-pink-300 text-black font-black text-sm shadow-md transition-all cursor-pointer active:scale-95"
            >
              Quit
            </button>
            <button
              onClick={() => {
                sounds.playClick();
                setShowQuitConfirm(false);
              }}
              className="flex-1 py-3 px-4 rounded-2xl bg-neutral-800 hover:bg-neutral-700 text-white font-bold text-sm border border-neutral-700 transition-all cursor-pointer active:scale-95"
            >
              Cancel
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
