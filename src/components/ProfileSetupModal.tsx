import React, { useState } from 'react';
import { UserProfile } from '../game/types';
import { sounds } from '../utils/audio';
import { Bell, Sparkles, Check, Dice5 } from 'lucide-react';

interface ProfileSetupModalProps {
  isOpen: boolean;
  initialProfile: UserProfile;
  onSave: (profile: UserProfile) => void;
  onClose?: () => void;
  canClose?: boolean;
}

const AVATARS = [
  { emoji: '🦊', label: 'Swift Fox', bg: 'bg-orange-500/20 border-orange-500' },
  { emoji: '🤖', label: 'Cyber Bot', bg: 'bg-cyan-500/20 border-cyan-500' },
  { emoji: '🦁', label: 'Golden Lion', bg: 'bg-amber-500/20 border-amber-500' },
  { emoji: '🐯', label: 'Wild Tiger', bg: 'bg-red-500/20 border-red-500' },
  { emoji: '🐼', label: 'Zen Panda', bg: 'bg-emerald-500/20 border-emerald-500' },
  { emoji: '🦄', label: 'Cosmic Horn', bg: 'bg-purple-500/20 border-purple-500' },
  { emoji: '🐲', label: 'Fire Dragon', bg: 'bg-rose-500/20 border-rose-500' },
  { emoji: '⚡', label: 'Volt Spark', bg: 'bg-yellow-500/20 border-yellow-500' },
];

const RANDOM_NAMES = [
  'UnoChampion',
  'CardShark',
  'WildAce',
  'ReverseMaster',
  'StackKing',
  'LuckyDraw',
  'SkipPro',
  'NeonGamer',
  'BlitzDuellist',
  'DeckBoss',
];

export const ProfileSetupModal: React.FC<ProfileSetupModalProps> = ({
  isOpen,
  initialProfile,
  onSave,
  onClose,
  canClose = false,
}) => {
  const [name, setName] = useState(initialProfile.name || 'Player 1');
  const [avatar, setAvatar] = useState(initialProfile.avatar || '🦊');
  const [notificationStatus, setNotificationStatus] = useState<NotificationPermission>(
    typeof Notification !== 'undefined' ? Notification.permission : 'default'
  );

  if (!isOpen) return null;

  const handleRandomizeName = () => {
    sounds.playClick();
    const chosen = RANDOM_NAMES[Math.floor(Math.random() * RANDOM_NAMES.length)];
    setName(chosen);
  };

  const requestNotifications = async () => {
    sounds.playClick();
    if (typeof Notification !== 'undefined') {
      try {
        const perm = await Notification.requestPermission();
        setNotificationStatus(perm);
      } catch (e) {
        console.error('Notification permission error', e);
      }
    }
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    sounds.playClick();
    onSave({
      ...initialProfile,
      name: name.trim(),
      avatar,
    });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/85 backdrop-blur-xs p-4 animate-fadeIn">
      <div className="relative w-full max-w-md bg-[#1f0301] border-2 border-[#ff1b04]/60 rounded-3xl p-6 shadow-2xl text-white">
        <div className="text-center mb-6">
          <div className="inline-flex p-3 rounded-2xl bg-gradient-to-tr from-red-600 to-amber-500 mb-2 shadow-lg shadow-red-600/20">
            <Sparkles className="w-6 h-6 text-white" />
          </div>
          <h2 className="text-2xl font-black tracking-tight">Choose Name & Avatar</h2>
          <p className="text-xs text-neutral-400 mt-1">
            Set up your identity before entering match lobbies
          </p>
        </div>

        <form onSubmit={handleSave} className="space-y-5">
          {/* Avatar Grid */}
          <div>
            <label className="block text-xs font-bold uppercase tracking-wider text-neutral-400 mb-2.5">
              Select Avatar
            </label>
            <div className="grid grid-cols-4 gap-2.5">
              {AVATARS.map((item) => {
                const isSelected = avatar === item.emoji;
                return (
                  <button
                    key={item.emoji}
                    type="button"
                    onClick={() => {
                      sounds.playClick();
                      setAvatar(item.emoji);
                    }}
                    className={`relative p-3 rounded-2xl flex flex-col items-center justify-center border-2 transition-all cursor-pointer ${
                      isSelected
                        ? `${item.bg} ring-2 ring-amber-400 scale-105 shadow-md`
                        : 'bg-neutral-800/60 border-neutral-700/80 hover:bg-neutral-800 hover:border-neutral-600'
                    }`}
                  >
                    <span className="text-2xl">{item.emoji}</span>
                    <span className="text-[10px] text-neutral-400 mt-1 truncate max-w-full">
                      {item.label.split(' ')[0]}
                    </span>
                    {isSelected && (
                      <div className="absolute top-1 right-1 w-3.5 h-3.5 bg-amber-400 rounded-full flex items-center justify-center text-black text-[9px] font-bold">
                        ✓
                      </div>
                    )}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Name Input */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <label htmlFor="player-name-input" className="text-xs font-bold uppercase tracking-wider text-neutral-400">
                Your Nickname
              </label>
              <button
                type="button"
                onClick={handleRandomizeName}
                className="text-xs text-amber-400 hover:text-amber-300 flex items-center gap-1 cursor-pointer font-medium"
              >
                <Dice5 className="w-3.5 h-3.5" /> Randomize
              </button>
            </div>
            <div className="relative">
              <input
                id="player-name-input"
                type="text"
                value={name}
                maxLength={16}
                onChange={(e) => setName(e.target.value)}
                placeholder="Enter player name..."
                className="w-full bg-neutral-800 border-2 border-neutral-700 rounded-2xl px-4 py-3 text-white placeholder-neutral-500 focus:outline-hidden focus:border-amber-400 transition-colors font-medium text-sm"
              />
              <span className="absolute right-3 top-3 text-xs text-neutral-500">
                {name.length}/16
              </span>
            </div>
          </div>

          {/* Notification Permission Card */}
          <div className="bg-neutral-800/70 border border-neutral-700 rounded-2xl p-3.5 flex items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-xl bg-neutral-700/60 text-amber-400">
                <Bell className="w-4 h-4" />
              </div>
              <div className="text-left">
                <div className="text-xs font-bold text-neutral-200">Match Notifications</div>
                <div className="text-[11px] text-neutral-400">
                  {notificationStatus === 'granted'
                    ? 'Permissions enabled for match alerts'
                    : 'Get pinged when a friend joins or plays'}
                </div>
              </div>
            </div>

            {notificationStatus === 'granted' ? (
              <span className="inline-flex items-center gap-1 text-[11px] font-bold text-emerald-400 bg-emerald-500/10 px-2.5 py-1 rounded-full border border-emerald-500/30">
                <Check className="w-3 h-3" /> Enabled
              </span>
            ) : (
              <button
                type="button"
                onClick={requestNotifications}
                className="text-[11px] font-bold text-neutral-900 bg-amber-400 hover:bg-amber-300 px-3 py-1.5 rounded-xl cursor-pointer transition-colors shadow-xs"
              >
                Allow
              </button>
            )}
          </div>

          {/* Action Buttons */}
          <div className="flex gap-3 pt-2">
            {canClose && onClose && (
              <button
                type="button"
                onClick={onClose}
                className="flex-1 py-3 px-4 rounded-2xl border border-neutral-700 text-neutral-300 hover:bg-neutral-800 font-bold text-sm transition-colors cursor-pointer"
              >
                Cancel
              </button>
            )}
            <button
              type="submit"
              disabled={!name.trim()}
              className="flex-1 py-3.5 px-4 rounded-2xl bg-gradient-to-r from-white via-pink-100 to-pink-300 hover:from-pink-50 hover:to-pink-200 text-black font-black text-sm shadow-lg shadow-pink-500/20 transition-all cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed border border-white/60 active:scale-95"
            >
              Confirm & Continue
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
