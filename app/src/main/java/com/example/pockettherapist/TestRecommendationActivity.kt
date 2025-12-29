package com.example.pockettherapist

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Test Activity for Recommendation Engine
 *
 * This activity tests all three recommendation functions:
 * 1. Song Recommendations (with real Spotify songs)
 * 2. Nearby Help Resources
 * 3. Wellness Suggestions
 */
class TestRecommendationActivity : AppCompatActivity() {

    private val TAG = "TestRecommendation"

    private lateinit var emotionPredictor: EmotionModelPredictor
    private lateinit var sentimentPredictor: SentimentModelPredictor
    private lateinit var recommendationEngine: RecommendationEngine

    private lateinit var outputText: TextView
    private lateinit var testButton1: Button
    private lateinit var testButton2: Button
    private lateinit var testButton3: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create simple layout programmatically
        val layout = androidx.constraintlayout.widget.ConstraintLayout(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(32, 32, 32, 32)
        }

        // Initialize components
        emotionPredictor = EmotionModelPredictor(this)
        sentimentPredictor = SentimentModelPredictor(this)
        recommendationEngine = RecommendationEngine(this)

        // Create UI elements
        val titleText = TextView(this).apply {
            id = android.view.View.generateViewId()
            text = "Recommendation Engine Test"
            textSize = 24f
            setPadding(0, 0, 0, 32)
        }

        testButton1 = Button(this).apply {
            id = android.view.View.generateViewId()
            text = "Test Anxious Journal"
            setOnClickListener { testAnxiousJournal() }
        }

        testButton2 = Button(this).apply {
            id = android.view.View.generateViewId()
            text = "Test Happy Journal"
            setOnClickListener { testHappyJournal() }
        }

        testButton3 = Button(this).apply {
            id = android.view.View.generateViewId()
            text = "Test Sad Journal"
            setOnClickListener { testSadJournal() }
        }

        val scrollView = ScrollView(this).apply {
            id = android.view.View.generateViewId()
        }

        outputText = TextView(this).apply {
            text = "Click a button to test the recommendation engine.\n\nNote: This requires internet connection to call Gemini API."
            textSize = 14f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFF2D2D2D.toInt())
            setTextColor(0xFFE0E0E0.toInt())
        }

        scrollView.addView(outputText)

        // Add views to layout
        layout.addView(titleText)
        layout.addView(testButton1)
        layout.addView(testButton2)
        layout.addView(testButton3)
        layout.addView(scrollView)

        // Set constraints
        val set = androidx.constraintlayout.widget.ConstraintSet()
        set.clone(layout)

        // Title constraints
        set.connect(titleText.id, androidx.constraintlayout.widget.ConstraintSet.TOP,
            androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.TOP)
        set.connect(titleText.id, androidx.constraintlayout.widget.ConstraintSet.START,
            androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)

        // Button 1 constraints
        set.connect(testButton1.id, androidx.constraintlayout.widget.ConstraintSet.TOP,
            titleText.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, 16)
        set.connect(testButton1.id, androidx.constraintlayout.widget.ConstraintSet.START,
            androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)
        set.connect(testButton1.id, androidx.constraintlayout.widget.ConstraintSet.END,
            androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END)

        // Button 2 constraints
        set.connect(testButton2.id, androidx.constraintlayout.widget.ConstraintSet.TOP,
            testButton1.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, 16)
        set.connect(testButton2.id, androidx.constraintlayout.widget.ConstraintSet.START,
            androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)
        set.connect(testButton2.id, androidx.constraintlayout.widget.ConstraintSet.END,
            androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END)

        // Button 3 constraints
        set.connect(testButton3.id, androidx.constraintlayout.widget.ConstraintSet.TOP,
            testButton2.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, 16)
        set.connect(testButton3.id, androidx.constraintlayout.widget.ConstraintSet.START,
            androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)
        set.connect(testButton3.id, androidx.constraintlayout.widget.ConstraintSet.END,
            androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END)

        // ScrollView constraints
        set.connect(scrollView.id, androidx.constraintlayout.widget.ConstraintSet.TOP,
            testButton3.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, 32)
        set.connect(scrollView.id, androidx.constraintlayout.widget.ConstraintSet.START,
            androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)
        set.connect(scrollView.id, androidx.constraintlayout.widget.ConstraintSet.END,
            androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END)
        set.connect(scrollView.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM,
            androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.BOTTOM)

        set.applyTo(layout)

        setContentView(layout)
    }

    private fun testAnxiousJournal() {
        val journalText = """
            I'm feeling really anxious today. Work has been overwhelming and I can't seem to
            focus on anything. My mind keeps racing with worries about deadlines and whether
            I'm doing a good job. I feel tense and on edge all the time. I tried to meditate
            but my thoughts kept interrupting. I just want to feel calm again.
        """.trimIndent()

        runFullTest(journalText, "Chicago, Illinois, USA")
    }

    private fun testHappyJournal() {
        val journalText = """
            Had an amazing day today! Got promoted at work and celebrated with friends.
            Everything feels so positive right now. I'm grateful for all the wonderful
            people in my life and excited about the future. Can't stop smiling!
            This is exactly what I needed after such a tough year.
        """.trimIndent()

        runFullTest(journalText, "San Francisco, California, USA")
    }

    private fun testSadJournal() {
        val journalText = """
            Feeling really down today. I miss my family and feel lonely. Nothing seems
            to make me happy anymore. I've been crying a lot and just want to stay in bed.
            Everything feels hopeless. I don't know how to pull myself out of this darkness.
            The days feel so long and empty.
        """.trimIndent()

        runFullTest(journalText, "New York, New York, USA")
    }

    private fun runFullTest(journalText: String, location: String) {
        outputText.text = "Testing recommendation engine...\n\nPlease wait, calling Gemini API...\n"

        // Disable buttons during test
        testButton1.isEnabled = false
        testButton2.isEnabled = false
        testButton3.isEnabled = false

        lifecycleScope.launch {
            try {
                val output = StringBuilder()

                output.append("=" .repeat(60) + "\n")
                output.append("RECOMMENDATION ENGINE TEST\n")
                output.append("=" .repeat(60) + "\n\n")

                output.append("📝 JOURNAL ENTRY:\n")
                output.append("-".repeat(60) + "\n")
                output.append("$journalText\n\n")
                output.append("📍 Location: $location\n\n")

                // Step 1: Analyze Emotion
                output.append("🧠 STEP 1: EMOTION ANALYSIS\n")
                output.append("-".repeat(60) + "\n")
                val emotionResult = emotionPredictor.predictEmotion(journalText)
                if (emotionResult != null) {
                    output.append("Detected Emotion: ${emotionResult.emotion.uppercase()} ")
                    output.append("${emotionPredictor.getEmotionEmoji(emotionResult.emotion)}\n")
                    output.append("Confidence: ${String.format("%.2f", emotionResult.confidence * 100)}%\n")
                    output.append("Description: ${emotionPredictor.getEmotionDescription(emotionResult.emotion)}\n")
                    output.append("\nAll Emotion Scores:\n")
                    emotionResult.allScores.entries.sortedByDescending { it.value }.forEach { (emotion, score) ->
                        output.append("  • ${emotion.padEnd(10)}: ${String.format("%.2f", score * 100)}%\n")
                    }
                    output.append("\n")
                } else {
                    output.append("❌ Failed to analyze emotion\n\n")
                    updateOutput(output.toString())
                    return@launch
                }

                // Step 2: Analyze Sentiment
                output.append("💭 STEP 2: SENTIMENT ANALYSIS\n")
                output.append("-".repeat(60) + "\n")
                val sentimentResult = sentimentPredictor.predictSentiment(journalText)
                if (sentimentResult != null) {
                    output.append("Detected Sentiment: ${sentimentResult.sentiment.uppercase()} ")
                    output.append("${sentimentPredictor.getSentimentEmoji(sentimentResult.sentiment)}\n")
                    output.append("Confidence: ${String.format("%.2f", sentimentResult.confidence * 100)}%\n")
                    output.append("Description: ${sentimentPredictor.getSentimentDescription(sentimentResult.sentiment)}\n")
                    output.append("\nAll Sentiment Scores:\n")
                    sentimentResult.allScores.entries.sortedByDescending { it.value }.forEach { (sentiment, score) ->
                        output.append("  • ${sentiment.padEnd(10)}: ${String.format("%.2f", score * 100)}%\n")
                    }
                    output.append("\n")
                } else {
                    output.append("❌ Failed to analyze sentiment\n\n")
                    updateOutput(output.toString())
                    return@launch
                }

                // Create EmotionData
                val emotionData = RecommendationEngine.createEmotionData(
                    emotion = emotionResult.emotion,
                    emotionScore = emotionResult.confidence,
                    sentiment = sentimentResult.sentiment,
                    sentimentScore = sentimentResult.confidence
                )

                updateOutput(output.toString())

                // Step 3: Song Recommendations
                output.append("🎵 STEP 3: SONG RECOMMENDATIONS\n")
                output.append("-".repeat(60) + "\n")
                updateOutput(output.toString())

                val songRecs = recommendationEngine.getSongRecommendations(journalText, emotionData)
                if (songRecs != null) {
                    output.append("Mood: ${songRecs.mood}\n\n")
                    output.append("Recommended Songs:\n")
                    songRecs.songs.forEachIndexed { index, song ->
                        output.append("  ${index + 1}. ${song.title} - ${song.artist}\n")
                    }
                    output.append("\nReasoning:\n${songRecs.reasoning}\n\n")
                } else {
                    output.append("❌ Failed to get song recommendations\n\n")
                }
                updateOutput(output.toString())

                // Step 4: Nearby Help
                output.append("🆘 STEP 4: MENTAL HEALTH RESOURCES\n")
                output.append("-".repeat(60) + "\n")
                updateOutput(output.toString())

                val helpResources = recommendationEngine.getNearbyHelp(journalText, emotionData, location)
                if (helpResources != null) {
                    output.append("Resources:\n")
                    helpResources.resources.forEachIndexed { index, resource ->
                        output.append("  ${index + 1}. ${resource.name} (${resource.type})\n")
                        output.append("     ${resource.description}\n")
                        resource.phone?.let { output.append("     Phone: $it\n") }
                        resource.website?.let { output.append("     Web: $it\n") }
                    }
                    output.append("\nEmergency Contacts:\n")
                    helpResources.emergencyContacts.forEach { contact ->
                        output.append("  📞 ${contact.name}: ${contact.number} (${contact.type})\n")
                    }
                    output.append("\nReasoning:\n${helpResources.reasoning}\n\n")
                } else {
                    output.append("❌ Failed to get help resources\n\n")
                }
                updateOutput(output.toString())

                // Step 5: Wellness Suggestions
                output.append("💪 STEP 5: WELLNESS SUGGESTIONS\n")
                output.append("-".repeat(60) + "\n")
                updateOutput(output.toString())

                val wellnessSuggestions = recommendationEngine.getWellnessSuggestions(journalText, emotionData)
                if (wellnessSuggestions != null) {
                    output.append("Wellness Techniques:\n")
                    wellnessSuggestions.techniques.forEachIndexed { index, technique ->
                        output.append("  ${index + 1}. ${technique.name}\n")
                        output.append("     Category: ${technique.category} | Duration: ${technique.duration}\n")
                        output.append("     ${technique.description}\n")
                        output.append("     Benefits: ${technique.benefits.take(2).joinToString(", ")}\n\n")
                    }
                    output.append("Reasoning:\n${wellnessSuggestions.reasoning}\n\n")
                } else {
                    output.append("❌ Failed to get wellness suggestions\n\n")
                }

                output.append("=" .repeat(60) + "\n")
                output.append("✅ TEST COMPLETED\n")
                output.append("=" .repeat(60) + "\n")

                updateOutput(output.toString())

                Log.d(TAG, "Test completed successfully")

            } catch (e: Exception) {
                val errorOutput = "❌ ERROR:\n${e.message}\n\n${e.stackTraceToString()}"
                updateOutput(errorOutput)
                Log.e(TAG, "Test failed", e)
            } finally {
                // Re-enable buttons
                testButton1.isEnabled = true
                testButton2.isEnabled = true
                testButton3.isEnabled = true
            }
        }
    }

    private fun updateOutput(text: String) {
        runOnUiThread {
            outputText.text = text
        }
    }
}
