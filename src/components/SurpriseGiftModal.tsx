import React, { useState, useEffect } from 'react';
import confetti from 'canvas-confetti';
import { sounds } from '../utils/audio';
import { Gift, Play, X, CheckCircle, Sparkles, Volume2, VolumeX } from 'lucide-react';

interface SurpriseGiftModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const SurpriseGiftModal: React.FC<SurpriseGiftModalProps> = ({ isOpen, onClose }) => {
  const [adState, setAdState] = useState<'prompt' | 'playing' | 'rewarded'>('prompt');
  const [secondsLeft, setSecondsLeft] = useState(5);
  const [isAdMuted, setIsAdMuted] = useState(false);

  useEffect(() => {
    if (!isOpen) {
      setAdState('prompt');
      setSecondsLeft(5);
    }
  }, [isOpen]);

  useEffect(() => {
    let timer: NodeJS.Timeout;
    if (adState === 'playing') {
      if (secondsLeft > 0) {
        timer = setTimeout(() => {
          setSecondsLeft((s) => s - 1);
        }, 1000);
      } else {
        // Video finished!
        setAdState('rewarded');
        sounds.playVictory();
        try {
          confetti({
            particleCount: 80,
            spread: 70,
            origin: { y: 0.6 },
            colors: ['#FF69B4', '#FFFFFF', '#FFD700', '#00A651'],
          });
        } catch {
          // ignore
        }
      }
    }
    return () => clearTimeout(timer);
  }, [adState, secondsLeft]);

  if (!isOpen) return null;

  const handleStartAd = () => {
    sounds.playClick();
    setAdState('playing');
    setSecondsLeft(5);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/85 backdrop-blur-xs p-4 animate-fadeIn">
      <div className="relative w-full max-w-sm bg-[#1f0301] border-2 border-[#ff1b04]/60 rounded-3xl p-6 shadow-2xl text-white text-center">
        {/* Close button */}
        {adState !== 'playing' && (
          <button
            onClick={() => {
              sounds.playClick();
              onClose();
            }}
            className="absolute top-4 right-4 p-2 rounded-xl bg-neutral-900 text-neutral-400 hover:text-white cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        )}

        {/* STATE 1: PROMPT */}
        {adState === 'prompt' && (
          <div className="space-y-4">
            <div className="w-16 h-16 rounded-2xl bg-gradient-to-tr from-pink-500 to-rose-400 p-0.5 mx-auto flex items-center justify-center shadow-lg shadow-pink-500/20">
              <Gift className="w-8 h-8 text-white" />
            </div>

            <div>
              <h3 className="text-2xl font-black text-white">Surprise Gift</h3>
              <p className="text-xs text-neutral-400 mt-1">
                Watch a short 5-second sponsor video to unlock your secret surprise gift!
              </p>
            </div>

            <div className="bg-neutral-900 border border-neutral-800 rounded-2xl p-3 flex items-center justify-center gap-2 text-xs text-pink-300 font-bold">
              <Sparkles className="w-4 h-4" />
              <span>Includes Special Avatar Badges & Emotes!</span>
            </div>

            <button
              onClick={handleStartAd}
              className="w-full py-3.5 px-6 rounded-2xl bg-gradient-to-r from-white via-pink-100 to-pink-300 hover:from-pink-50 hover:to-pink-200 text-black font-black text-sm shadow-lg shadow-pink-500/20 transition-all cursor-pointer flex items-center justify-center gap-2"
            >
              <Play className="w-4 h-4 fill-black" />
              <span>Watch Video (5s)</span>
            </button>
          </div>
        )}

        {/* STATE 2: PLAYING SIMULATED VIDEO AD */}
        {adState === 'playing' && (
          <div className="space-y-4">
            <div className="relative w-full aspect-video rounded-2xl overflow-hidden bg-gradient-to-br from-indigo-950 via-purple-950 to-pink-950 border border-pink-400/30 flex flex-col items-center justify-between p-3">
              {/* Ad Header */}
              <div className="w-full flex items-center justify-between text-[11px] font-bold">
                <span className="bg-black/60 px-2 py-0.5 rounded-md text-pink-300">
                  Ad · Reward in {secondsLeft}s
                </span>
                <button
                  onClick={() => setIsAdMuted(!isAdMuted)}
                  className="p-1 rounded-md bg-black/60 text-white"
                >
                  {isAdMuted ? <VolumeX className="w-3.5 h-3.5" /> : <Volume2 className="w-3.5 h-3.5" />}
                </button>
              </div>

              {/* Animated sponsor logo */}
              <div className="flex flex-col items-center animate-pulse">
                <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-pink-500 to-amber-400 flex items-center justify-center text-white shadow-xl mb-2">
                  <Sparkles className="w-6 h-6" />
                </div>
                <div className="text-sm font-black text-white tracking-wide">
                  UNO ROYALE PREMIUM
                </div>
                <div className="text-[10px] text-pink-200">
                  Exclusive Tournaments & Custom Themes
                </div>
              </div>

              {/* Progress bar */}
              <div className="w-full bg-black/60 h-1.5 rounded-full overflow-hidden">
                <div
                  style={{ width: `${((5 - secondsLeft) / 5) * 100}%` }}
                  className="h-full bg-gradient-to-r from-pink-400 to-rose-300 transition-all duration-1000"
                />
              </div>
            </div>

            <p className="text-xs text-neutral-400">
              Please wait while the video plays...
            </p>
          </div>
        )}

        {/* STATE 3: REWARD UNLOCKED */}
        {adState === 'rewarded' && (
          <div className="space-y-4 animate-scaleUp">
            <div className="w-16 h-16 rounded-2xl bg-gradient-to-tr from-emerald-500 to-green-400 p-0.5 mx-auto flex items-center justify-center shadow-lg shadow-emerald-500/20">
              <CheckCircle className="w-8 h-8 text-white" />
            </div>

            <div>
              <h3 className="text-2xl font-black text-white">Surprise Unlocked!</h3>
              <p className="text-xs text-neutral-400 mt-1">
                You earned the <strong className="text-pink-300 font-bold">Golden Crown</strong> badge & exclusive match reactions!
              </p>
            </div>

            <div className="bg-neutral-900 border border-neutral-800 rounded-2xl p-3 flex items-center justify-center gap-3">
              <span className="text-3xl">👑</span>
              <div className="text-left">
                <div className="text-xs font-black text-white">VIP Crown Badge</div>
                <div className="text-[10px] text-pink-300">Active on your profile</div>
              </div>
            </div>

            <button
              onClick={() => {
                sounds.playClick();
                onClose();
              }}
              className="w-full py-3.5 px-6 rounded-2xl bg-gradient-to-r from-white via-pink-100 to-pink-300 hover:from-pink-50 hover:to-pink-200 text-black font-black text-sm shadow-lg shadow-pink-500/20 transition-all cursor-pointer"
            >
              Claim & Continue
            </button>
          </div>
        )}
      </div>
    </div>
  );
};
