import React from 'react';
import { Card, CardColor } from '../game/types';

interface UnoCardProps {
  card?: Card;
  isBack?: boolean;
  onClick?: () => void;
  scale?: number;
  rotation?: number;
  className?: string;
  isHoverable?: boolean;
  shake?: boolean;
}

const COLOR_CONFIG: Record<CardColor, { bg: string; fill: string; border: string; accent: string }> = {
  yellow: {
    bg: 'bg-[#FFCC00]',
    fill: '#FFCC00',
    border: 'border-[#E6B800]',
    accent: 'text-[#D49E00]',
  },
  red: {
    bg: 'bg-[#EE2737]',
    fill: '#EE2737',
    border: 'border-[#C81A28]',
    accent: 'text-[#B01220]',
  },
  green: {
    bg: 'bg-[#00A651]',
    fill: '#00A651',
    border: 'border-[#008A42]',
    accent: 'text-[#007034]',
  },
  blue: {
    bg: 'bg-[#0072BC]',
    fill: '#0072BC',
    border: 'border-[#005B96]',
    accent: 'text-[#004B7D]',
  },
  wild: {
    bg: 'bg-neutral-900',
    fill: '#1A1A1A',
    border: 'border-neutral-700',
    accent: 'text-neutral-400',
  },
};

export const UnoCard: React.FC<UnoCardProps> = ({
  card,
  isBack = false,
  onClick,
  rotation = 0,
  className = '',
  isHoverable = true,
  shake = false,
}) => {
  // CARD BACK (as in Image 3 draw pile: rounded, black border, 4-color quad with DUO/UNO text)
  if (isBack || !card) {
    return (
      <div
        onClick={onClick}
        style={{
          transform: `rotate(${rotation}deg)`,
        }}
        className={`relative w-16 h-24 sm:w-20 sm:h-30 rounded-xl bg-white p-1 border-2 border-black shadow-lg select-none transition-all duration-150 flex items-center justify-center cursor-pointer ${
          isHoverable ? 'hover:-translate-y-1 hover:shadow-xl active:scale-95' : ''
        } ${className}`}
      >
        <div className="w-full h-full rounded-lg bg-neutral-900 border border-neutral-700 p-1 flex flex-col items-center justify-center relative overflow-hidden">
          {/* 4 Color corner accents */}
          <div className="absolute top-0 left-0 w-3.5 h-3.5 bg-emerald-500 rounded-br-lg" />
          <div className="absolute top-0 right-0 w-3.5 h-3.5 bg-rose-500 rounded-bl-lg" />
          <div className="absolute bottom-0 left-0 w-3.5 h-3.5 bg-blue-500 rounded-tr-lg" />
          <div className="absolute bottom-0 right-0 w-3.5 h-3.5 bg-amber-400 rounded-tl-lg" />

          {/* DUO / UNO Center Badge */}
          <div className="z-10 bg-black/90 px-2 py-1 rounded-md border border-neutral-600 shadow-inner flex items-center justify-center">
            <span className="text-white font-black italic tracking-tighter text-xs sm:text-sm drop-shadow-md">
              DUO
            </span>
          </div>
        </div>
      </div>
    );
  }

  const { color, value } = card;
  const config = COLOR_CONFIG[color] || COLOR_CONFIG.wild;

  // Render top-left corner indicator matching Image 3 (white background box with bold crisp symbol)
  const renderIndex = () => {
    switch (value) {
      case 'skip':
        return (
          <span className="font-black text-sm sm:text-base leading-none text-neutral-900">
            ⊘
          </span>
        );
      case 'reverse':
        return (
          <span className="font-black text-sm sm:text-base leading-none text-neutral-900">
            ⇄
          </span>
        );
      case 'draw2':
        return (
          <span className="font-black text-xs sm:text-sm leading-none text-neutral-900 tracking-tighter">
            +2
          </span>
        );
      case 'wild':
        return (
          <div className="w-3.5 h-3.5 rounded-xs grid grid-cols-2 grid-rows-2 gap-0.5 p-0.5 bg-black/10">
            <div className="bg-red-500 rounded-xs" />
            <div className="bg-yellow-400 rounded-xs" />
            <div className="bg-blue-500 rounded-xs" />
            <div className="bg-emerald-500 rounded-xs" />
          </div>
        );
      case 'wild4':
        return (
          <span className="font-black text-xs sm:text-sm leading-none text-neutral-900 tracking-tighter">
            +4
          </span>
        );
      case '6':
      case '9':
        return (
          <span className="font-black text-sm sm:text-base leading-none text-neutral-900 underline underline-offset-2">
            {value}
          </span>
        );
      default:
        return (
          <span className="font-black text-sm sm:text-base leading-none text-neutral-900">
            {value}
          </span>
        );
    }
  };

  // Render center graphic matching Image 3
  const renderCenterGraphic = () => {
    switch (value) {
      case 'skip':
        return (
          <svg className="w-8 h-8 sm:w-10 sm:h-10 text-white drop-shadow-md" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3">
            <circle cx="12" cy="12" r="9" />
            <line x1="5.6" y1="5.6" x2="18.4" y2="18.4" />
          </svg>
        );
      case 'reverse':
        return (
          <svg className="w-8 h-8 sm:w-10 sm:h-10 text-white drop-shadow-md" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3">
            <path d="M4 7l4-4m0 0L4 0m4 3H15a5 5 0 0 1 5 5v1" />
            <path d="M20 17l-4 4m0 0l4 4m-4-4H9a5 5 0 0 1-5-5v-1" />
          </svg>
        );
      case 'draw2':
        return (
          <div className="flex flex-col items-center justify-center">
            <span className="text-xl sm:text-2xl font-black italic tracking-tighter text-white drop-shadow-md">
              +2
            </span>
          </div>
        );
      case 'wild':
        return (
          <div className="w-9 h-9 sm:w-12 sm:h-12 rounded-xl grid grid-cols-2 grid-rows-2 gap-1 p-1 bg-neutral-800 shadow-md border border-neutral-600">
            <div className="bg-red-500 rounded-sm shadow-xs" />
            <div className="bg-yellow-400 rounded-sm shadow-xs" />
            <div className="bg-blue-500 rounded-sm shadow-xs" />
            <div className="bg-emerald-500 rounded-sm shadow-xs" />
          </div>
        );
      case 'wild4':
        return (
          <div className="flex flex-col items-center justify-center">
            <div className="w-7 h-7 sm:w-9 sm:h-9 rounded-lg grid grid-cols-2 grid-rows-2 gap-0.5 p-0.5 bg-neutral-800 shadow-xs mb-0.5">
              <div className="bg-red-500 rounded-xs" />
              <div className="bg-yellow-400 rounded-xs" />
              <div className="bg-blue-500 rounded-xs" />
              <div className="bg-emerald-500 rounded-xs" />
            </div>
            <span className="text-sm sm:text-base font-black italic tracking-tighter text-white drop-shadow-md">
              +4
            </span>
          </div>
        );
      case '6':
      case '9':
        return (
          <span className="text-3xl sm:text-5xl font-black italic tracking-tighter text-white underline underline-offset-4 drop-shadow-md">
            {value}
          </span>
        );
      default:
        return (
          <span className="text-3xl sm:text-5xl font-black italic tracking-tighter text-white drop-shadow-md">
            {value}
          </span>
        );
    }
  };

  return (
    <div
      onClick={onClick}
      style={{
        transform: `rotate(${rotation}deg)`,
      }}
      className={`relative w-16 h-24 sm:w-20 sm:h-30 rounded-xl bg-white p-1 border-2 border-black shadow-lg select-none transition-all duration-150 flex flex-col justify-between overflow-hidden cursor-pointer ${
        isHoverable ? 'hover:-translate-y-2 hover:shadow-2xl hover:z-50 active:scale-95' : ''
      } ${shake ? 'animate-shake ring-2 ring-red-500' : ''} ${className}`}
    >
      {/* Top Header Row with Index Pip on Left (Image 3 Style) */}
      <div className="z-10 flex items-center justify-between px-1 pt-0.5">
        <div className="bg-white/95 px-1.5 py-0.5 rounded-md border border-neutral-300 shadow-xs flex items-center justify-center min-w-[20px]">
          {renderIndex()}
        </div>
        {/* Subtle mini color badge on top right */}
        <div className={`w-2.5 h-2.5 rounded-full ${config.bg} border border-black/20 shadow-xs`} />
      </div>

      {/* Main Body Color Swoop (Image 3 layout) */}
      <div className="absolute inset-x-1 bottom-1 top-6 rounded-lg overflow-hidden flex items-center justify-center shadow-inner">
        <div className={`w-full h-full ${config.bg} flex items-center justify-center relative`}>
          {/* Smooth curved card swoop reflection */}
          <div className="absolute inset-0 bg-gradient-to-tr from-black/20 via-transparent to-white/20 pointer-events-none" />
          <div className="absolute -top-12 -right-12 w-24 h-24 rounded-full bg-white/15 pointer-events-none" />

          {/* Center Graphic */}
          <div className="z-10 flex items-center justify-center">
            {renderCenterGraphic()}
          </div>
        </div>
      </div>

      {/* Bottom Right Inverted Index */}
      <div className="z-10 flex items-center justify-end px-1 pb-0.5 rotate-180 pointer-events-none">
        <div className="bg-white/95 px-1 py-0.5 rounded-xs border border-neutral-300 shadow-xs flex items-center justify-center text-[10px] leading-none">
          {renderIndex()}
        </div>
      </div>
    </div>
  );
};
