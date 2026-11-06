import React from 'react';
import { sounds } from '../utils/audio';

interface ColorPickerModalProps {
  isOpen: boolean;
  onSelectColor: (color: 'red' | 'blue' | 'green' | 'yellow') => void;
}

export const ColorPickerModal: React.FC<ColorPickerModalProps> = ({ isOpen, onSelectColor }) => {
  if (!isOpen) return null;

  const handlePick = (c: 'red' | 'blue' | 'green' | 'yellow') => {
    sounds.playColorSelect();
    onSelectColor(c);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-xs p-4 animate-fadeIn">
      <div className="bg-neutral-900 border-2 border-neutral-700 rounded-3xl p-6 max-w-xs w-full shadow-2xl text-center">
        <h3 className="text-xl font-black text-white mb-1 uppercase tracking-wider">
          Choose Color
        </h3>
        <p className="text-xs text-neutral-400 mb-5">
          Select the active color for the next play
        </p>

        {/* 4 Quadrants */}
        <div className="grid grid-cols-2 gap-3.5 max-w-[220px] mx-auto aspect-square">
          {/* Red */}
          <button
            onClick={() => handlePick('red')}
            className="group relative rounded-2xl bg-gradient-to-br from-red-500 to-red-700 p-4 flex flex-col items-center justify-center text-white shadow-lg shadow-red-600/30 hover:scale-105 active:scale-95 transition-all border-2 border-red-400 cursor-pointer"
          >
            <div className="w-5 h-5 rounded-full bg-white/20 mb-1 group-hover:bg-white/40" />
            <span className="font-extrabold text-sm tracking-wide">RED</span>
          </button>

          {/* Blue */}
          <button
            onClick={() => handlePick('blue')}
            className="group relative rounded-2xl bg-gradient-to-br from-blue-500 to-blue-700 p-4 flex flex-col items-center justify-center text-white shadow-lg shadow-blue-600/30 hover:scale-105 active:scale-95 transition-all border-2 border-blue-400 cursor-pointer"
          >
            <div className="w-5 h-5 rounded-full bg-white/20 mb-1 group-hover:bg-white/40" />
            <span className="font-extrabold text-sm tracking-wide">BLUE</span>
          </button>

          {/* Yellow */}
          <button
            onClick={() => handlePick('yellow')}
            className="group relative rounded-2xl bg-gradient-to-br from-amber-400 to-yellow-500 p-4 flex flex-col items-center justify-center text-neutral-900 shadow-lg shadow-amber-500/30 hover:scale-105 active:scale-95 transition-all border-2 border-yellow-200 cursor-pointer"
          >
            <div className="w-5 h-5 rounded-full bg-black/10 mb-1 group-hover:bg-black/20" />
            <span className="font-extrabold text-sm tracking-wide">YELLOW</span>
          </button>

          {/* Green */}
          <button
            onClick={() => handlePick('green')}
            className="group relative rounded-2xl bg-gradient-to-br from-emerald-500 to-green-700 p-4 flex flex-col items-center justify-center text-white shadow-lg shadow-emerald-600/30 hover:scale-105 active:scale-95 transition-all border-2 border-emerald-400 cursor-pointer"
          >
            <div className="w-5 h-5 rounded-full bg-white/20 mb-1 group-hover:bg-white/40" />
            <span className="font-extrabold text-sm tracking-wide">GREEN</span>
          </button>
        </div>
      </div>
    </div>
  );
};
