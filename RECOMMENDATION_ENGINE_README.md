# Pocket Therapist - Recommendation Engine

## Overview

This implementation provides AI-powered recommendation capabilities for the Pocket Therapist app, integrating emotion detection, sentiment analysis, and personalized suggestions using Google Gemini API.

## Files Created

### 1. **RecommendationEngine.kt**
Main recommendation engine with three Gemini API wrappers:

#### a. **Song Recommendations** (`getSongRecommendations`)
- Analyzes user's emotional state and journal entry
- Returns 5-7 personalized song/music recommendations
- Considers emotion type (sadness, joy, anger, etc.) for appropriate music therapy
- **Input**: Journal text + Emotion/Sentiment data
- **Output**: Song list, mood description, reasoning

#### b. **Nearby Help** (`getNearbyHelp`)
- Provides mental health resources and professional help
- Returns therapist types, support groups, and crisis hotlines
- Location-aware recommendations (when GPS data available)
- **Input**: Journal text + Emotion/Sentiment data + Location
- **Output**: Resources list, emergency contacts, reasoning

#### c. **Wellness Suggestions** (`getWellnessSuggestions`)
- Offers practical micro-interventions (2-10 minutes)
- Evidence-based coping strategies and activities
- Emotion-specific activities (breathing exercises, grounding techniques, etc.)
- **Input**: Journal text + Emotion/Sentiment data
- **Output**: Wellness tips, activities, reasoning

**API Used**: Google Gemini Pro API
**API Key**: Configured in the class (AIzaSyCC5SCVEfG62IX5jK00t3hGTP4Z0QIynQg)

---

### 2. **EmotionModelPredictor.kt**
Wrapper for the RoBERTa emotion classification model.

#### Model Details:
- **Architecture**: RoBERTa-base (125M parameters)
- **Dataset**: DairAI Emotion Dataset
- **Accuracy**: ~93%
- **F1 Score**: ~93%

#### Emotion Classes (6):
1. **sadness** - Feeling down, depressed, unhappy
2. **joy** - Feeling happy, excited, delighted
3. **love** - Affection, caring, romantic feelings
4. **anger** - Mad, frustrated, irritated
5. **fear** - Anxious, worried, scared
6. **surprise** - Shocked, amazed, astonished

#### Implementation:
- **Current**: Rule-based keyword matching (fallback)
- **Future**: TensorFlow Lite on-device inference
- **Model File**: `assets/models/emotion_model.safetensors` (copied from training folder)

#### Functions:
- `predictEmotion(text)` - Returns emotion, confidence, all scores
- `getEmotionDescription(emotion)` - User-friendly description
- `getEmotionEmoji(emotion)` - Emoji representation

---

### 3. **SentimentModelPredictor.kt**
**PLACEHOLDER** for the RoBERTa sentiment classification model.

#### Model Details (Expected):
- **Architecture**: RoBERTa-base (125M parameters)
- **Dataset**: TweetEval Sentiment (~60,000 tweets)
- **Accuracy**: 70-74% (expected)
- **F1 Score**: 70-74% (expected)

#### Sentiment Classes (3):
1. **Negative** - Overall negative tone
2. **Neutral** - Balanced or neutral tone
3. **Positive** - Overall positive tone

#### Implementation:
- **Current**: Rule-based keyword matching (temporary)
- **Status**: **WAITING FOR SENTIMENT MODEL**
- **Integration**: Ready to accept TFLite model when provided

#### Functions:
- `predictSentiment(text)` - Returns sentiment, confidence, all scores
- `getSentimentDescription(sentiment)` - User-friendly description
- `getSentimentEmoji(sentiment)` - Emoji representation
- `getSentimentColor(sentiment)` - Color for UI visualization

---

### 4. **RecommendationEngineExample.kt**
Complete usage examples and integration guide.

#### Features:
- **Example scenarios**: Anxiety, happiness, sadness
- **Integration examples**: How to use with VoiceJournalFragment
- **Test functions**: Quick verification of all components
- **UI display**: Sample code for showing results

---

## Architecture

```
User Input (Voice/Text)
         ↓
   Journal Entry
         ↓
    ┌────┴────┐
    ↓         ↓
Emotion   Sentiment
Predictor Predictor
    ↓         ↓
    └────┬────┘
         ↓
   EmotionData
         ↓
Recommendation Engine
    ↓    ↓    ↓
Songs  Help  Wellness
```

## Usage Example

```kotlin
// Initialize components
val emotionPredictor = EmotionModelPredictor(context)
val sentimentPredictor = SentimentModelPredictor(context)
val recommendationEngine = RecommendationEngine(context)

// Analyze journal text
val emotion = emotionPredictor.predictEmotion(journalText)
val sentiment = sentimentPredictor.predictSentiment(journalText)

// Create emotion data
val emotionData = RecommendationEngine.createEmotionData(
    emotion.emotion,
    emotion.confidence,
    sentiment.sentiment,
    sentiment.confidence
)

// Get recommendations
val songs = recommendationEngine.getSongRecommendations(journalText, emotionData)
val help = recommendationEngine.getNearbyHelp(journalText, emotionData)
val wellness = recommendationEngine.getWellnessSuggestions(journalText, emotionData)
```

## Integration Steps

### 1. Add Dependencies to `build.gradle`

```gradle
dependencies {
    // Kotlin Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1'

    // JSON parsing (already in Android)
    // No additional dependencies needed
}
```

### 2. Add Permissions to `AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

### 3. Integrate with VoiceJournalFragment

```kotlin
class VoiceJournalFragment : Fragment() {

    private val emotionPredictor by lazy { EmotionModelPredictor(requireContext()) }
    private val sentimentPredictor by lazy { SentimentModelPredictor(requireContext()) }
    private val recommendationEngine by lazy { RecommendationEngine(requireContext()) }

    private fun analyzeJournal(transcribedText: String) {
        lifecycleScope.launch {
            // Get emotion and sentiment
            val emotion = emotionPredictor.predictEmotion(transcribedText)
            val sentiment = sentimentPredictor.predictSentiment(transcribedText)

            if (emotion != null && sentiment != null) {
                val emotionData = RecommendationEngine.createEmotionData(
                    emotion.emotion,
                    emotion.confidence,
                    sentiment.sentiment,
                    sentiment.confidence
                )

                // Get recommendations
                val songs = recommendationEngine.getSongRecommendations(
                    transcribedText,
                    emotionData
                )

                // Update UI
                updateUI(emotion, sentiment, songs)
            }
        }
    }
}
```

## Model Files

### Current Structure:
```
PocketTherapist/
├── app/
│   └── src/
│       └── main/
│           ├── assets/
│           │   └── models/
│           │       └── emotion_model.safetensors ✓ (copied)
│           │       └── sentiment_model.tflite ✗ (pending)
│           ├── ml/
│           │   └── (future TFLite models)
│           └── java/
│               └── com/example/pockettherapist/
│                   ├── RecommendationEngine.kt ✓
│                   ├── EmotionModelPredictor.kt ✓
│                   ├── SentimentModelPredictor.kt ✓ (placeholder)
│                   └── RecommendationEngineExample.kt ✓
```

### Emotion Model:
- **Status**: ✓ Copied from `roberta_emotion_model-20251206T214409Z-3-003/`
- **Location**: `assets/models/emotion_model.safetensors`
- **Type**: SafeTensors format
- **Note**: Currently using fallback rule-based detection. Convert to TFLite for production.

### Sentiment Model:
- **Status**: ✗ PENDING - Not yet provided
- **Placeholder**: Using rule-based sentiment analysis
- **Expected Location**: `assets/models/sentiment_model.tflite`
- **Action Needed**: Provide trained sentiment model for integration

---

## TODO: Sentiment Model Integration

When the sentiment model is ready:

1. **Convert to TensorFlow Lite**:
   ```python
   # Using Python/TensorFlow
   converter = tf.lite.TFLiteConverter.from_saved_model(model_path)
   tflite_model = converter.convert()

   with open('sentiment_model.tflite', 'wb') as f:
       f.write(tflite_model)
   ```

2. **Copy files to Android project**:
   ```
   sentiment_model.tflite → app/src/main/assets/models/
   vocab.json → app/src/main/assets/models/
   tokenizer_config.json → app/src/main/assets/models/
   ```

3. **Update `SentimentModelPredictor.kt`**:
   - Implement `loadModelFromAssets()` with TFLite Interpreter
   - Replace `ruleBasedSentimentAnalysis()` with model inference
   - Add tokenization logic

---

## API Configuration

### Gemini API Key
The Gemini API key is currently hardcoded in `RecommendationEngine.kt`:
```kotlin
private val GEMINI_API_KEY = "AIzaSyCC5SCVEfG62IX5jK00t3hGTP4Z0QIynQg"
```

**For production:**
- Move API key to `local.properties` or environment variables
- Use BuildConfig to access securely
- Implement API key rotation

### API Limits
- **Gemini Free Tier**: 60 requests per minute
- **Rate limiting**: Consider caching responses
- **Error handling**: Implemented with try-catch and fallbacks

---

## Testing

### Quick Test:
```kotlin
// In MainActivity or any Activity
RecommendationEngineExample.runQuickTest(this)
```

### Test Scenarios:
1. **Anxiety** - Tests calming recommendations
2. **Happiness** - Tests celebratory content
3. **Sadness** - Tests supportive resources

Check **Logcat** for detailed output.

---

## Key Features

✓ **Text-in, Text-out**: Simple interface (input: text, output: recommendations)
✓ **Emotion Detection**: 6-class emotion classification
✓ **Sentiment Analysis**: 3-class sentiment detection (placeholder ready)
✓ **Song Recommendations**: AI-powered music therapy
✓ **Mental Health Resources**: Crisis support and professional help
✓ **Wellness Activities**: Micro-interventions and coping strategies
✓ **Privacy-First**: On-device processing (when using TFLite)
✓ **Async Processing**: Kotlin Coroutines for smooth UX
✓ **Error Handling**: Fallback mechanisms and graceful degradation

---

## Next Steps

1. **Provide sentiment model** → Replace placeholder in `SentimentModelPredictor.kt`
2. **Convert emotion model to TFLite** → For on-device inference
3. **Add database** → Store journal entries and analysis results
4. **Implement UI** → Display recommendations in fragments
5. **Add location services** → For localized help resources
6. **Integrate Spotify/Calm APIs** → For actual music playback
7. **User testing** → Validate recommendation quality

---

## Questions?

For any questions or issues:
- Check `RecommendationEngineExample.kt` for usage patterns
- Review Logcat output for debugging
- Ensure internet permission is granted
- Verify Gemini API key is valid

---

## Summary

**Created**: 4 new Kotlin files
**Status**: Ready for testing
**Sentiment Model**: Awaiting integration
**Emotion Model**: Extracted and copied
**Recommendation Engine**: Fully functional with Gemini API

All components use **text input → text output** as specified.
