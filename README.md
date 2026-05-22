# PocketTherapist - AI-powered mental Wellness App

> **🏆 Best Capstone Project — 1st place out of 42 teams**

An AI-powered journaling and mental wellness companion for Android. PocketTherapist combines voice and text journaling with on-device emotion classification, passive activity tracking, and context-aware wellness recommendations — designed for low friction, high privacy, and real daily use.

---

## Overview

Young adults face record levels of stress and burnout, yet most mental wellness apps either demand too much effort or offer generic, decontextualized advice. PocketTherapist addresses this by fusing three systems that are usually siloed — **journaling**, **emotional inference**, and **contextual sensing** — into a single, cohesive experience.

The result is a mobile companion that understands not just *what* you write, but *when* and *how* you're feeling when you write it.

---

## Features

### Low-Friction Journaling
- **Voice journaling** — speak naturally; speech is transcribed on-device in real time
- **Text journaling** — traditional input with easy switching between modes
- Edit and confirm transcriptions before saving; no lost entries on interruption

### On-Device Emotion & Sentiment Analysis
- **Emotion classification** using a RoBERTa-base model fine-tuned on the DairAI Emotion dataset
  - 6 classes: sadness, joy, love, anger, fear, surprise
  - ~93% accuracy / ~93% F1
- **Sentiment analysis** (positive / neutral / negative) via RoBERTa-base fine-tuned on TweetEval
- Both models run locally — journal text never leaves the device unless you opt in

### Passive Sensor Fusion & Activity Recognition
- Fuses accelerometer, gyroscope, step counter, light sensor, GPS, and timestamp
- Kalman filter smooths motion signals over time for robust, device-agnostic activity inference
- Relative change detection (not absolute thresholds) ensures consistent behavior across hardware
- Sensor summaries — not raw streams — are stored, dramatically reducing storage and battery impact

### Context-Aware Recommendation Engine
Powered by Google Gemini Pro, the engine cross-references emotional state, activity context, and recent journal history to surface three types of support:

| Type | What you get |
|---|---|
| **Wellness Tips** | Evidence-based micro-interventions (2–10 min): breathing, grounding, gratitude, movement |
| **Music** | Emotion-matched song suggestions for music therapy |
| **Nearby Help** | Mental health resources, support groups, and crisis hotlines — location-aware |

Every recommendation includes a brief explanation tying it to your recent journal entry or activity pattern. Recommendations are throttled and rotated to prevent repetition fatigue.

### 🔐 Privacy-First Design
- All core features work fully offline; server-side processing is always opt-in
- Permissions are requested contextually — only when the relevant feature is first used
- Explicit deletion semantics: you own your data and can remove it at any time
- Crisis detection runs locally with immediate access to emergency resources

---

## Architecture

```
┌─────────────────────────────────────────────────┐
│                  SENSOR LAYER                   │
│  Accelerometer · Gyroscope · Light · GPS · Time │
└───────────────────┬─────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────┐
│               PROCESSING LAYER                  │
│  Speech-to-Text · Kalman Filter · AI Model      │
│  Keyword Extraction · Sensor Fusion             │
└───────────────────┬─────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────┐
│              APPLICATION LAYER                  │
│  Emotion Classifier · Recommendation Engine     │
│  Privacy Controls · Journal UI · Notifications  │
└─────────────────────────────────────────────────┘
```

**Key data flow:** User input (voice or text) → emotion + sentiment inference → fused with sensor context → Recommendation Engine → personalized, explainable wellness suggestions delivered back to the UI.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Platform | Android (Kotlin), minSdk 24 / targetSdk 36 |
| ML — Emotion | RoBERTa-base · DairAI Emotion · TFLite |
| ML — Sentiment | RoBERTa-base · TweetEval · TFLite |
| Recommendations | Google Gemini Pro API |
| Sensor Fusion | Kalman filter over accelerometer, gyroscope, step counter |
| Background Services | Android Foreground Services + BootReceiver |
| Auth | Firebase (Google Sign-In) |
| Local Storage | Room / SharedPreferences |
| APIs | OpenStreetMap (Overpass), Ticketmaster, Spotify |
| Async | Kotlin Coroutines |

---

## Project Structure

```
app/src/main/java/com/example/pockettherapist/
├── MainActivity.kt                  # Entry point, navigation
├── HomeFragment.kt                  # Dashboard + journal summary
├── VoiceJournalFragment.kt          # Voice input + STT pipeline
├── TextJournalFragment.kt           # Text input
├── JournalFragment.kt               # Journal history
├── RecommendationsFragment.kt       # Wellness tips UI
├── ActivitiesFragment.kt            # Nearby events + step tracking
├── ProfileFragment.kt               # User settings + data controls
│
├── EmotionModelPredictor.kt         # RoBERTa emotion classification
├── SentimentModelPredictor.kt       # RoBERTa sentiment classification
├── RecommendationEngine.kt          # Gemini-powered recommendation logic
├── RecommendationsCache.kt          # Response caching
│
├── ActivityTracker.kt               # Sensor reading + Kalman fusion
├── ActivityTrackingService.kt       # Foreground service for background sensing
├── DailyActivityStore.kt            # Summarized sensor feature storage
│
├── CrisisDetector.kt                # On-device crisis signal detection
├── AIConsentManager.kt              # Explicit opt-in/opt-out for server AI
├── UserStore.kt                     # User profile + journal persistence
│
├── SpotifyIntegration.kt            # Music recommendations
└── api/
    ├── OSMApi.kt                    # OpenStreetMap nearby amenities
    └── TicketMasterApi.kt           # Local wellness events
```

---

## Getting Started

### Prerequisites
- Android Studio Hedgehog or later
- Android device or emulator running API 24+
- Google account (for Firebase Auth)
- Gemini API key ([get one here](https://aistudio.google.com/))

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/your-username/PocketTherapist-App.git
   cd PocketTherapist-App
   ```

2. **Add your API key**

   In `RecommendationEngine.kt`, replace the placeholder:
   ```kotlin
   private val GEMINI_API_KEY = "YOUR_GEMINI_API_KEY"
   ```
   For production builds, move this to `local.properties` and access via `BuildConfig`.

3. **Firebase setup**

   Replace `app/google-services.json` with your own Firebase project config (Authentication → Google Sign-In enabled).

4. **Build and run**

   Open in Android Studio → sync Gradle → run on device or emulator.

### Permissions

The app requests the following permissions contextually (only when the relevant feature is first used):

| Permission | Used for |
|---|---|
| `RECORD_AUDIO` | Voice journaling |
| `ACTIVITY_RECOGNITION` | Step counting and motion detection |
| `ACCESS_FINE_LOCATION` | Location-aware nearby resources |
| `FOREGROUND_SERVICE` | Background activity tracking |
| `POST_NOTIFICATIONS` | Wellness reminders |
| `HIGH_SAMPLING_RATE_SENSORS` | Accurate motion fusion |

All permissions are optional — the app degrades gracefully when denied.

---

## Evaluation

### ML Model Performance

| Model | Dataset | Accuracy | F1 Score |
|---|---|---|---|
| Emotion (RoBERTa-base) | DairAI Emotion | ~93% | ~93% |
| Sentiment (RoBERTa-base) | TweetEval | 70–74% | 70–74% |

### Latency
On-device inference produced consistently lower latency and tighter variance than server-based inference across all tested devices — enabling reliable real-time feedback without network dependency.

### User Study 
- Participants rated the app as easy to use, quick to learn, and well-integrated
- Voice journaling was successfully used by most participants on first attempt
- Context-aware recommendations were well received, especially when brief explanations were included
- Pre-study: time burden and privacy concerns were the top reasons users abandoned prior journaling apps — both directly addressed by PocketTherapist's design

---

