import React, { useState } from 'react';
import { sounds } from '../utils/audio';
import { LogIn, X, Delete } from 'lucide-react';

interface JoinRoomModalProps {
  isOpen: boolean;
  onJoin: (code: string) => void;
  onClose: () => void;
  isLoading?: boolean;
  errorMessage?: string | null;
}

export const JoinRoomModal: React.FC<JoinRoomModalProps> = ({
  isOpen,
  onJoin,
  onClose,
  isLoading = false,
  errorMessage = null,
}) => {
  const [code, setCode] = useState('');

  if (!isOpen) return null;

  const handleDigit = (digit: string) => {
    if (code.length < 5) {
      sounds.playClick();
      const next = code + digit;
      setCode(next);
      if (next.length === 5) {
        onJoin(next);
      }
    }
  };

  const handleDelete = () => {
    sounds.playClick();
    setCode((prev) => prev.slice(0, -1));
  };

  const handleFormSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (code.length >= 4) {
      sounds.playClick();
      onJoin(code);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/85 backdrop-blur-xs p-4 animate-fadeIn">
      <div className="relative w-full max-w-sm bg-[#1f0301] border-2 border-[#ff1b04]/60 rounded-3xl p-6 shadow-2xl text-white text-center font-sans">
        {/* Close Button */}
        <button
          onClick={onClose}
          className="absolute top-4 right-4 p-2 rounded-xl bg-neutral-900 text-neutral-400 hover:text-white cursor-pointer"
        >
          <X className="w-5 h-5" />
        </button>

        <div className="inline-flex p-3 rounded-2xl bg-pink-500/10 text-pink-300 mb-3 border border-pink-500/30">
          <LogIn className="w-6 h-6" />
        </div>
        <h2 className="text-xl font-black">Enter Room Code</h2>
        <p className="text-xs text-neutral-400 mt-1 mb-5">
          Type the 5-digit match code to join your friend
        </p>

        {/* 5 Digit Slot Display */}
        <div className="flex justify-center gap-2 mb-4">
          {[0, 1, 2, 3, 4].map((i) => {
            const char = code[i] || '';
            const isActive = code.length === i;
            return (
              <div
                key={i}
                className={`w-12 h-14 rounded-2xl border-2 flex items-center justify-center font-mono text-2xl font-black transition-all ${
                  char
                    ? 'bg-neutral-900 border-pink-400 text-white shadow-md'
                    : isActive
                    ? 'border-pink-400 bg-neutral-900/60 animate-pulse'
                    : 'border-neutral-800 bg-neutral-900/30 text-neutral-600'
                }`}
              >
                {char || (isActive ? '·' : '')}
              </div>
            );
          })}
        </div>

        {errorMessage && (
          <p className="text-xs text-red-400 font-bold mb-3 animate-fadeIn bg-red-950/40 border border-red-800 p-2 rounded-xl">
            {errorMessage}
          </p>
        )}

        {/* Keypad */}
        <div className="grid grid-cols-3 gap-2 max-w-[260px] mx-auto mb-5">
          {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map((digit) => (
            <button
              key={digit}
              type="button"
              onClick={() => handleDigit(digit)}
              className="py-3 rounded-xl bg-neutral-900 hover:bg-neutral-800 active:scale-95 border border-neutral-800 text-lg font-black transition-all cursor-pointer shadow-xs text-white"
            >
              {digit}
            </button>
          ))}
          <button
            type="button"
            onClick={() => setCode('')}
            className="py-3 rounded-xl bg-neutral-900/50 hover:bg-neutral-800 active:scale-95 border border-neutral-800 text-xs font-bold text-neutral-400 transition-all cursor-pointer"
          >
            Clear
          </button>
          <button
            type="button"
            onClick={() => handleDigit('0')}
            className="py-3 rounded-xl bg-neutral-900 hover:bg-neutral-800 active:scale-95 border border-neutral-800 text-lg font-black transition-all cursor-pointer shadow-xs text-white"
          >
            0
          </button>
          <button
            type="button"
            onClick={handleDelete}
            className="py-3 rounded-xl bg-neutral-900/50 hover:bg-neutral-800 active:scale-95 border border-neutral-800 flex items-center justify-center text-neutral-300 transition-all cursor-pointer"
          >
            <Delete className="w-5 h-5" />
          </button>
        </div>

        {/* Submit with White-Pink Gradient and Black Bold Text */}
        <form onSubmit={handleFormSubmit}>
          <button
            type="submit"
            disabled={code.length < 5 || isLoading}
            className="w-full py-3.5 px-4 rounded-2xl bg-gradient-to-r from-white via-pink-100 to-pink-300 hover:from-pink-50 hover:to-pink-200 text-black font-black text-sm shadow-lg shadow-pink-500/20 transition-all cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed active:scale-95 border border-white/60"
          >
            {isLoading ? 'Connecting to Room...' : 'Join Match'}
          </button>
        </form>
      </div>
    </div>
  );
};
