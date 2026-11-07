# UNO Royale: 2-Player Duels (Android)

A fast-paced, portrait 2-player UNO mobile card game rewritten in **Kotlin** and **Jetpack Compose** using **Material Design 3**.

## 📱 Features

- **Ported Engine**: Complete UNO card engine supporting all standard 108 cards (Numbers 0-9, Skip, Reverse, Draw Two (+2), Wild, and Wild Draw Four (+4)).
- **Intelligent AI Opponent**: Autonomous AI opponent with multiple difficulty tiers (Easy, Medium, Hard), tactical card-color counting, and aggressive attack card chaining.
- **Custom House Rules Engine**:
  - **Stack Draw Cards**: Stack +2 onto +2 or +4 onto +4 to pass card draw penalty to your opponent.
  - **2-Player Reverse Skips Turn**: Official 2-player UNO rule where playing Reverse immediately gives you another turn.
  - **7-0 Hand Swap**: Playing a 7 swaps hands with the opponent.
  - **Draw-to-Match Mode**: Keep drawing from the deck until a playable card is found.
  - **Jump-In Rule**: Play an identical card anytime out of turn.
  - **Adjustable UNO Miss Penalty**: Configurable 1 to 4 penalty cards for failing to call UNO.
- **Visual Design & Cards**:
  - High-fidelity UNO card rendering with authentic curved inner swooshes, corner index pips, and smooth tactile elevation.
  - Invalid card shake animation with haptic/buzzer feedback.
  - Dynamic active-color glowing border and last-action announcement ticker.
  - Celebratory victory fanfare and confetti modal.
- **Audio & Haptics**:
  - Tonal audio and haptic feedback for card play, draw, buzzer, UNO shout, and win fanfare.
  - Full mute and background music toggles.
- **Player Customization & Daily Rewards**:
  - Custom duelist profile with emoji avatar selector and name editing.
  - Win/loss match statistics tracker.
  - Daily Surprise Gift box with rewards.

## 🏗️ Android Architecture

- **UI Framework**: Jetpack Compose (M3)
- **Design System**: Material Design 3 (`UnoRoyaleTheme`, dynamic color scheme)
- **Architecture**: MVVM with `StateFlow` and unidirectional data flow
- **Language**: Kotlin 2.0+
- **Build System**: Gradle Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`)
- **Package / Namespace**: `com.example.uno`
- **Application ID**: `com.aistudio.unoroyale.kpdzqm`

## 🗂️ Project Structure

```
app/
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/example/uno/
│   │   ├── MainActivity.kt
│   │   ├── model/
│   │   │   └── Models.kt
│   │   ├── logic/
│   │   │   └── UnoEngine.kt
│   │   ├── sound/
│   │   │   └── SoundEffects.kt
│   │   ├── viewmodel/
│   │   │   └── GameViewModel.kt
│   │   └── ui/
│   │       ├── HomeScreen.kt
│   │       ├── GameArenaScreen.kt
│   │       ├── LobbyScreen.kt
│   │       ├── SettingsScreen.kt
│   │       ├── theme/
│   │       │   ├── Color.kt
│   │       │   ├── Theme.kt
│   │       │   └── Type.kt
│   │       └── components/
│   │           ├── UnoCardView.kt
│   │           └── Modals.kt
│   └── res/
│       ├── drawable/
│       ├── mipmap-*/
│       └── values/
│           ├── colors.xml
│           ├── strings.xml
│           └── themes.xml
└── src/test/java/com/example/uno/
    └── UnoEngineTest.kt
```
