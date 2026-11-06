import React, { useState } from 'react';
import { sounds } from '../utils/audio';
import { Check } from 'lucide-react';

interface SettingsScreenProps {
  onBack: () => void;
  onHowToPlay: () => void;
  isSoundOn: boolean;
  onToggleSound: () => void;
  isMusicOn: boolean;
  onToggleMusic: () => void;
}

const LANGUAGES = [
  { code: 'en', name: 'ENGLISH' },
  { code: 'fr', name: 'FRENCH' },
  { code: 'de', name: 'DEUTSCH' },
  { code: 'es', name: 'ESPAÑOL' },
  { code: 'it', name: 'ITALIANO' },
  { code: 'pt', name: 'PORTUGUÊS' },
];

export const SettingsScreen: React.FC<SettingsScreenProps> = ({
  onBack,
  onHowToPlay,
  isSoundOn,
  onToggleSound,
  isMusicOn,
  onToggleMusic,
}) => {
  const [selectedLang, setSelectedLang] = useState('ENGLISH');

  return (
    <div className="w-full max-w-md min-h-screen flex flex-col justify-between p-5 text-center relative overflow-hidden bg-[#ff1b04] bg-[radial-gradient(circle_at_50%_25%,_#ff3820_0%,_#ff1b04_55%,_#9e0e00_100%)] text-white font-sans">
      {/* Subtle floating ambient UNO glow & dot texture */}
      <div className="absolute inset-0 pointer-events-none opacity-15 bg-[radial-gradient(#ffffff_2px,transparent_2px)] [background-size:24px_24px]" />
      <div className="absolute -top-12 -left-12 w-48 h-48 rounded-full bg-white/10 blur-2xl pointer-events-none" />
      <div className="absolute -bottom-12 -right-12 w-52 h-52 rounded-full bg-black/25 blur-2xl pointer-events-none" />

      {/* Top Header */}
      <div className="relative z-10 flex items-center justify-between pt-2">
        <button
          onClick={() => {
            sounds.playClick();
            onBack();
          }}
          className="w-11 h-11 rounded-2xl bg-gradient-to-r from-white via-pink-100 to-pink-300 text-black font-black text-2xl shadow-md active:scale-95 flex items-center justify-center transition-transform cursor-pointer"
        >
          ‹
        </button>

        <h1 className="text-3xl font-black text-white tracking-wide">
          Settings
        </h1>

        <div className="w-11" />
      </div>

      {/* Buttons with White-Pink Gradient and Black Bold Text */}
      <div className="relative z-10 my-auto py-6 space-y-4 max-w-xs mx-auto w-full">
        {/* Music Toggle */}
        <button
          onClick={() => {
            sounds.playClick();
            onToggleMusic();
          }}
          className="w-full py-4 px-6 rounded-2xl bg-gradient-to-r from-white via-pink-100 to-pink-300 text-black font-black text-base shadow-md active:scale-95 flex items-center justify-between transition-all cursor-pointer"
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
          className="w-full py-4 px-6 rounded-2xl bg-gradient-to-r from-white via-pink-100 to-pink-300 text-black font-black text-base shadow-md active:scale-95 flex items-center justify-between transition-all cursor-pointer"
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
          className="w-full py-4 px-6 rounded-2xl bg-gradient-to-r from-white via-pink-100 to-pink-300 text-black font-black text-base shadow-md active:scale-95 transition-all cursor-pointer"
        >
          How to play
        </button>

        {/* Language Section */}
        <div className="pt-6">
          <h2 className="text-xl font-black text-white mb-4">
            Language:
          </h2>
          <div className="grid grid-cols-2 gap-x-4 gap-y-3 text-sm font-black">
            {LANGUAGES.map((lang) => {
              const isSelected = selectedLang === lang.name;
              return (
                <button
                  key={lang.code}
                  onClick={() => {
                    sounds.playClick();
                    setSelectedLang(lang.name);
                  }}
                  className={`py-2 px-3 rounded-xl transition-all cursor-pointer ${
                    isSelected
                      ? 'bg-gradient-to-r from-white via-pink-100 to-pink-300 text-black shadow-md scale-105'
                      : 'text-neutral-400 hover:text-white'
                  }`}
                >
                  {lang.name}
                </button>
              );
            })}
          </div>
        </div>
      </div>

      {/* Version 1.0 */}
      <div className="pb-3 text-xs text-neutral-500 font-bold">
        v 1.0
      </div>
    </div>
  );
};
