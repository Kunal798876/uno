// Web Audio API procedural sound engine for UNO game
class SoundEngine {
  private ctx: AudioContext | null = null;
  private muted: boolean = false;

  private musicOn: boolean = true;
  private musicPlaying: boolean = false;
  private musicGainNode: GainNode | null = null;
  private musicTimer: number | null = null;
  private musicStep: number = 0;

  constructor() {
    // Check localStorage for saved sound preference
    try {
      const saved = localStorage.getItem('uno_muted');
      if (saved !== null) {
        this.muted = saved === 'true';
      }
      const savedMusic = localStorage.getItem('uno_music_on');
      if (savedMusic !== null) {
        this.musicOn = savedMusic === 'true';
      }
    } catch {
      // ignore
    }
  }

  private initCtx(): AudioContext | null {
    if (this.muted) return null;
    if (!this.ctx) {
      const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      if (AudioCtx) {
        this.ctx = new AudioCtx();
      }
    }
    if (this.ctx && this.ctx.state === 'suspended') {
      this.ctx.resume().catch(() => {});
    }
    return this.ctx;
  }

  public isMuted(): boolean {
    return this.muted;
  }

  public toggleMute(): boolean {
    this.muted = !this.muted;
    try {
      localStorage.setItem('uno_muted', String(this.muted));
    } catch {
      // ignore
    }
    if (this.muted) {
      this.stopMusic();
    } else if (this.musicOn) {
      this.startMusic();
    }
    return this.muted;
  }

  public isMusicOn(): boolean {
    return this.musicOn;
  }

  public setMusicOn(on: boolean) {
    this.musicOn = on;
    try {
      localStorage.setItem('uno_music_on', String(on));
    } catch {
      // ignore
    }
    if (on && !this.muted) {
      this.startMusic();
    } else {
      this.stopMusic();
    }
  }

  public toggleMusic(): boolean {
    this.setMusicOn(!this.musicOn);
    return this.musicOn;
  }

  // Starts or resumes background music loop
  public startMusic() {
    if (!this.musicOn || this.muted) return;
    if (this.musicPlaying) return;

    if (!this.ctx) {
      const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      if (AudioCtx) {
        this.ctx = new AudioCtx();
      }
    }
    if (!this.ctx) return;

    if (this.ctx.state === 'suspended') {
      this.ctx.resume().catch(() => {});
    }

    this.musicPlaying = true;
    if (!this.musicGainNode) {
      this.musicGainNode = this.ctx.createGain();
      this.musicGainNode.gain.setValueAtTime(0.07, this.ctx.currentTime);
      this.musicGainNode.connect(this.ctx.destination);
    } else {
      this.musicGainNode.gain.setValueAtTime(0.07, this.ctx.currentTime);
    }

    this.scheduleNextMusicNote();
  }

  public stopMusic() {
    this.musicPlaying = false;
    if (this.musicTimer !== null) {
      window.clearTimeout(this.musicTimer);
      this.musicTimer = null;
    }
    if (this.musicGainNode && this.ctx) {
      this.musicGainNode.gain.setValueAtTime(0.0001, this.ctx.currentTime);
    }
  }

  private scheduleNextMusicNote() {
    if (!this.musicPlaying || !this.ctx || !this.musicGainNode) return;

    // Upbeat casual game melody notes (Pentatonic in C / A minor)
    // 16-step sequence looping smoothly
    const melodyNotes: (number | null)[] = [
      523.25, 659.25, 783.99, 1046.50, // C5, E5, G5, C6
      880.00, null,   659.25, 783.99,  // A5, -, E5, G5
      698.46, 880.00, 1046.50, 880.00, // F5, A5, C6, A5
      783.99, null,   587.33, 659.25,  // G5, -, D5, E5
    ];

    const bassNotes: (number | null)[] = [
      130.81, null,   196.00, null,    // C3, -, G3, -
      110.00, null,   164.81, null,    // A2, -, E3, -
      87.31,  null,   130.81, null,    // F2, -, C3, -
      98.00,  null,   146.83, null,    // G2, -, D3, -
    ];

    const now = this.ctx.currentTime;
    const stepDuration = 0.16; // ~94 BPM 16th groove

    const melodyFreq = melodyNotes[this.musicStep % melodyNotes.length];
    const bassFreq = bassNotes[this.musicStep % bassNotes.length];

    // Play melody note with soft triangle / lowpass filter (marimba feel)
    if (melodyFreq) {
      const osc = this.ctx.createOscillator();
      const gain = this.ctx.createGain();
      const filter = this.ctx.createBiquadFilter();

      osc.type = 'triangle';
      osc.frequency.setValueAtTime(melodyFreq, now);

      filter.type = 'lowpass';
      filter.frequency.setValueAtTime(1800, now);

      gain.gain.setValueAtTime(0.08, now);
      gain.gain.exponentialRampToValueAtTime(0.001, now + stepDuration * 1.4);

      osc.connect(filter);
      filter.connect(gain);
      gain.connect(this.musicGainNode);

      osc.start(now);
      osc.stop(now + stepDuration * 1.5);
    }

    // Play subtle soft warm sine bass note on beats
    if (bassFreq) {
      const bassOsc = this.ctx.createOscillator();
      const bassGain = this.ctx.createGain();

      bassOsc.type = 'sine';
      bassOsc.frequency.setValueAtTime(bassFreq, now);

      bassGain.gain.setValueAtTime(0.12, now);
      bassGain.gain.exponentialRampToValueAtTime(0.001, now + stepDuration * 2.2);

      bassOsc.connect(bassGain);
      bassGain.connect(this.musicGainNode);

      bassOsc.start(now);
      bassOsc.stop(now + stepDuration * 2.4);
    }

    this.musicStep++;
    this.musicTimer = window.setTimeout(() => {
      this.scheduleNextMusicNote();
    }, stepDuration * 1000);
  }

  // Light card swoosh / draw sound
  public playCardDraw() {
    const ctx = this.initCtx();
    if (!ctx) return;

    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    const filter = ctx.createBiquadFilter();

    osc.type = 'sine';
    const now = ctx.currentTime;
    osc.frequency.setValueAtTime(320, now);
    osc.frequency.exponentialRampToValueAtTime(700, now + 0.12);

    filter.type = 'lowpass';
    filter.frequency.setValueAtTime(1500, now);

    gain.gain.setValueAtTime(0.01, now);
    gain.gain.linearRampToValueAtTime(0.18, now + 0.03);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.15);

    osc.connect(filter);
    filter.connect(gain);
    gain.connect(ctx.destination);

    osc.start(now);
    osc.stop(now + 0.16);
  }

  // Crisp card snap / play sound
  public playCardPlay() {
    const ctx = this.initCtx();
    if (!ctx) return;

    const now = ctx.currentTime;

    // Pop oscillator
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();

    osc.type = 'triangle';
    osc.frequency.setValueAtTime(450, now);
    osc.frequency.exponentialRampToValueAtTime(140, now + 0.08);

    gain.gain.setValueAtTime(0.25, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.09);

    osc.connect(gain);
    gain.connect(ctx.destination);

    osc.start(now);
    osc.stop(now + 0.1);
  }

  // Action card sound (Skip, Reverse, +2)
  public playAction(actionType: string) {
    const ctx = this.initCtx();
    if (!ctx) return;

    const now = ctx.currentTime;

    if (actionType === 'skip' || actionType === 'reverse') {
      // Quick double warble
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(520, now);
      osc.frequency.exponentialRampToValueAtTime(980, now + 0.08);
      osc.frequency.exponentialRampToValueAtTime(650, now + 0.16);

      gain.gain.setValueAtTime(0.2, now);
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.2);

      osc.connect(gain);
      gain.connect(ctx.destination);

      osc.start(now);
      osc.stop(now + 0.22);
    } else if (actionType === 'draw2' || actionType === 'wild4') {
      // Heavy attack chord
      [280, 420, 560].forEach((freq, idx) => {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = 'sawtooth';
        osc.frequency.setValueAtTime(freq, now + idx * 0.04);
        osc.frequency.exponentialRampToValueAtTime(freq * 0.7, now + idx * 0.04 + 0.18);

        gain.gain.setValueAtTime(0.12, now + idx * 0.04);
        gain.gain.exponentialRampToValueAtTime(0.001, now + idx * 0.04 + 0.22);

        osc.connect(gain);
        gain.connect(ctx.destination);

        osc.start(now + idx * 0.04);
        osc.stop(now + idx * 0.04 + 0.24);
      });
    }
  }

  // Color picker selection chime
  public playColorSelect() {
    const ctx = this.initCtx();
    if (!ctx) return;

    const now = ctx.currentTime;
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();

    osc.type = 'sine';
    osc.frequency.setValueAtTime(600, now);
    osc.frequency.exponentialRampToValueAtTime(1200, now + 0.12);

    gain.gain.setValueAtTime(0.18, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.18);

    osc.connect(gain);
    gain.connect(ctx.destination);

    osc.start(now);
    osc.stop(now + 0.2);
  }

  // Trumpet / brass synth for "UNO!"
  public playUnoCall() {
    const ctx = this.initCtx();
    if (!ctx) return;

    const now = ctx.currentTime;
    const freqs = [440, 554, 659, 880]; // A major fanfare

    freqs.forEach((freq, i) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'triangle';
      const startT = now + i * 0.06;
      osc.frequency.setValueAtTime(freq, startT);

      gain.gain.setValueAtTime(0.2, startT);
      gain.gain.exponentialRampToValueAtTime(0.001, startT + 0.35);

      osc.connect(gain);
      gain.connect(ctx.destination);

      osc.start(startT);
      osc.stop(startT + 0.38);
    });
  }

  // Catch penalty buzzer
  public playBuzzer() {
    const ctx = this.initCtx();
    if (!ctx) return;

    const now = ctx.currentTime;
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();

    osc.type = 'sawtooth';
    osc.frequency.setValueAtTime(140, now);
    osc.frequency.setValueAtTime(120, now + 0.1);

    gain.gain.setValueAtTime(0.25, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.3);

    osc.connect(gain);
    gain.connect(ctx.destination);

    osc.start(now);
    osc.stop(now + 0.32);
  }

  // Victory fanfare
  public playVictory() {
    const ctx = this.initCtx();
    if (!ctx) return;

    const now = ctx.currentTime;
    const notes = [
      { f: 523.25, d: 0.15 }, // C5
      { f: 659.25, d: 0.15 }, // E5
      { f: 783.99, d: 0.18 }, // G5
      { f: 1046.5, d: 0.45 }, // C6
    ];

    let t = now;
    notes.forEach((note) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();

      osc.type = 'sine';
      osc.frequency.setValueAtTime(note.f, t);

      gain.gain.setValueAtTime(0.25, t);
      gain.gain.exponentialRampToValueAtTime(0.001, t + note.d);

      osc.connect(gain);
      gain.connect(ctx.destination);

      osc.start(t);
      osc.stop(t + note.d + 0.05);

      t += note.d * 0.85;
    });
  }

  // Defeat sad tone
  public playDefeat() {
    const ctx = this.initCtx();
    if (!ctx) return;

    const now = ctx.currentTime;
    const notes = [440, 415, 392, 349]; // descending minor chords
    let t = now;
    notes.forEach((freq) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(freq, t);

      gain.gain.setValueAtTime(0.18, t);
      gain.gain.exponentialRampToValueAtTime(0.001, t + 0.28);

      osc.connect(gain);
      gain.connect(ctx.destination);

      osc.start(t);
      osc.stop(t + 0.3);
      t += 0.22;
    });
  }

  // Simple UI button click
  public playClick() {
    const ctx = this.initCtx();
    if (!ctx) return;

    const now = ctx.currentTime;
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();

    osc.type = 'sine';
    osc.frequency.setValueAtTime(800, now);
    osc.frequency.exponentialRampToValueAtTime(400, now + 0.04);

    gain.gain.setValueAtTime(0.1, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.05);

    osc.connect(gain);
    gain.connect(ctx.destination);

    osc.start(now);
    osc.stop(now + 0.06);
  }
}

export const sounds = new SoundEngine();
