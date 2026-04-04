# Scooby Jump 🐕🚀

**Scooby Jump** is a native Android 2D infinite platformer built entirely with Kotlin and the Android Canvas API. Experience thrilling, high-speed vertical gameplay where you dodge hazards, collect dynamic power-ups, and reach for the stars in a procedurally generated universe!

## ✨ Features

* **Infinite Procedural Generation:** A heavily engineered chunk-spawning algorithm ensures infinite, zigzagging platforms (Static, Moving, Breakable, and Spring) while dynamically tuning difficulty as your altitude increases!
* **Native Canvas Physics:** Built ground-up on Android's `SurfaceView` utilizing custom frame loops, rigid-body physics, solid ceiling collision bounding, and momentum constraints.
* **Intense Power-Up Roster:** Collect game-changing buffs along the way:
    * 🛡️ **Shield:** Protection from immediate hazards.
    * 🧲 **Magnetic Aura:** Pull coins directly towards you.
    * 👻 **Ghost Sprint:** Phasing invincibility blast upwards.
    * 🌌 **Anti-Gravity Hover Bubble:** Smooth float mechanics over complex traps.
* **Deep Economy & Progression:**
    * 💰 **In-Game Currency System:** Hoard Coins to purchase game-saving revives or the tactical **Emergency Dash** mechanic (-100 Coins to save yourself from plummeting).
    * 🏆 **Skill-Based Missions System:** Dynamic Daily Missions (e.g., "Pacifist," "Combo King," "Thread the Needle") tied to tiered rarity rewards (Bronze to Legendary Loot).
    * 🔥 **Ghost Path Tracking:** Re-traces your steps with a transparent phantom showing your top run!
* **Dynamic Environment Modes:** Reach score milestones to shatter the atmosphere and transition seamlessly into **Space Mode** with augmented gravity and tweaked physics!

## 🛠️ Architecture

Scooby Jump bypasses external game engines (like Unity) to demonstrate the raw capability of Android's native Graphics Canvas running on Kotlin.

* **Language:** Kotlin
* **Rendering Engine:** Native Android Canvas & Paint (`GameView.kt`)
* **Thread Controller:** Independent background `GameThread.kt` handling 60FPS physics ticking.
* **Persistence:** Android `SharedPreferences` + bespoke `SQLite` `LocalHistoryManager` for High-scores and Ghost coordinate serialization.

## 🚀 Getting Started

### Prerequisites
* Android Studio (Ladybug or newer recommended)
* JDK 17
* Android SDK API Level 34

### Building & Running
1. Clone the repository to your local machine.
2. Open the project folder (`ScoobyJumpAppNew`) in Android Studio.
3. Allow Gradle to synchronize dependencies.
4. Click **Run > Run 'app'** (`Shift + F10`) to compile and launch on your connected Android device or emulator.

## 🎮 How to Play
* **Navigation:** Tap and hold the Left or Right sides of your screen to direct Scooby's momentum.
* **Double Tap:** Trigger the **Emergency Dash** (Costs 100 Coins) for an explosive upward boost out of immediate danger!
* **Fast Fall:** Swipe Down to violently slam down to the nearest platform.

## 📸 Screenshots
*(TBD - Add screenshots of Main Menu, Gameplay in Space Mode, and Daily Missions UI)*

## 📄 License
This project is for personal educational portfolio purposes.
