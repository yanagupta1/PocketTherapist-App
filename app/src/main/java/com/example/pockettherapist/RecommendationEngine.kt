package com.example.pockettherapist

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * RecommendationEngine - Enhanced version with:
 * 1. Detailed wellness technique descriptions
 * 2. Spotify playlist integration + deep links
 * 3. Google Maps integration for resources
 * 4. Fully structured JSON output
 */
class RecommendationEngine(private val context: Context) {

    private val TAG = "RecommendationEngine"
    private val GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY
    private val GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent?key=$GEMINI_API_KEY"

    data class EmotionData(
        val emotion: String,
        val emotionScore: Float,
        val sentiment: String,
        val sentimentScore: Float
    )

    data class SongRecommendation(
        val songs: List<SongDetail>,
        val mood: String,
        val reasoning: String,
        val spotifyPlaylistUrl: String,  // Opens in Spotify app
        val spotifyWebUrl: String         // Opens in web browser
    )

    data class SongDetail(
        val title: String,
        val artist: String,
        val spotifySearchUrl: String,
        val spotifyUri: String
    )

    data class NearbyHelpResource(
        val resources: List<ResourceDetail>,
        val emergencyContacts: List<EmergencyContact>,
        val reasoning: String
    )

    data class ResourceDetail(
        val name: String,
        val type: String,
        val description: String,
        val phone: String?,
        val website: String?,
        val address: String?,
        val googleMapsUrl: String?,
        val latitude: Double?,
        val longitude: Double?
    )

    data class EmergencyContact(
        val name: String,
        val number: String,
        val description: String,
        val type: String  // "call", "text", or "both"
    )

    data class WellnessSuggestion(
        val techniques: List<WellnessTechnique>,
        val reasoning: String
    )

    data class WellnessTechnique(
        val name: String,
        val category: String,
        val duration: String,
        val difficulty: String,
        val description: String,
        val detailedExplanation: String,
        val youtubeUrl: String,
        val instructions: List<String>,
        val benefits: List<String>,
        val tips: List<String>?
    )

    data class EventRecommendation(
        val events: List<EventDetail>,
        val reasoning: String
    )

    // Combined recommendations - single API call for both wellness + songs
    data class CombinedRecommendation(
        val wellness: WellnessTechnique?,
        val songs: List<SongDetail>,
        val songsMood: String
    )

    data class EventDetail(
        val name: String,
        val date: String,
        val time: String?,
        val venue: String,
        val address: String?,
        val description: String,
        val category: String,
        val url: String?,
        val latitude: Double?,
        val longitude: Double?,
        val googleMapsUrl: String?
    )

    // ==================== 1. AI COMPANION RESPONSE ====================

    suspend fun getCompanionResponse(
        journalText: String,
        mood: String = ""
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildCompanionPrompt(journalText, mood)
                val response = callGeminiAPI(prompt)
                parseCompanionResponse(response)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting companion response", e)
                null
            }
        }
    }

    private fun buildCompanionPrompt(journalText: String, mood: String): String {
        val moodContext = when (mood) {
            "😊" -> "happy"
            "😢" -> "sad"
            "😤" -> "frustrated"
            "😰" -> "anxious"
            "😌" -> "calm"
            else -> ""
        }

        return """
You are a warm, empathetic AI companion in a mental wellness journaling app. The user just saved a journal entry. Provide a brief, caring response.

User's journal entry: "$journalText"
${if (moodContext.isNotEmpty()) "User's selected mood: $moodContext" else ""}

Guidelines:
- Be warm, understanding, and supportive
- Keep it SHORT - 1-2 sentences maximum
- Acknowledge their feelings without being preachy
- Don't give advice unless they're clearly asking for it
- Sound natural, like a caring friend
- Don't use phrases like "I hear you" or "I understand" too much
- Vary your responses, don't be repetitive

Examples of good responses:
- "That sounds really tough. It's okay to feel overwhelmed sometimes."
- "What a lovely moment to capture! Those little joys really do matter."
- "Work stress can be so draining. Hope you get some rest tonight."
- "It takes courage to write about difficult feelings like this."

Return ONLY valid JSON:
{"response": "Your empathetic response here"}

Return ONLY the JSON object, nothing else.
        """.trimIndent()
    }

    private fun parseCompanionResponse(response: String): String {
        val jsonResponse = JSONObject(response)
        return jsonResponse.getString("response")
    }

    // ==================== COMBINED RECOMMENDATIONS (Single API Call) ====================

    suspend fun getCombinedRecommendations(
        journalText: String,
        emotionData: EmotionData
    ): CombinedRecommendation? {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildCombinedPrompt(journalText, emotionData)
                val response = callGeminiAPI(prompt)
                parseCombinedRecommendation(response)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting combined recommendations", e)
                null
            }
        }
    }

    private fun buildCombinedPrompt(journalText: String, emotionData: EmotionData): String {
        val randomSeed = System.currentTimeMillis() % 1000

        return """
You are a wellness and music therapist AI. Based on the user's journal entry and emotional state, recommend ONE wellness technique AND 4 songs.

User Journal: "$journalText"
Emotion: ${emotionData.emotion} (${emotionData.emotionScore})
Sentiment: ${emotionData.sentiment} (${emotionData.sentimentScore})
Random seed: $randomSeed

Return ONLY valid JSON in this EXACT format:
{
  "wellness": {
    "name": "Technique Name",
    "category": "breathing/mindfulness/movement/cognitive",
    "duration": "2-5 min",
    "difficulty": "easy",
    "description": "Brief one-sentence description",
    "detailedExplanation": "2-3 sentence explanation of the technique and why it helps"
  },
  "songs": [
    {"title": "Song Title", "artist": "Artist Name"},
    {"title": "Song Title", "artist": "Artist Name"},
    {"title": "Song Title", "artist": "Artist Name"},
    {"title": "Song Title", "artist": "Artist Name"}
  ],
  "mood": "Brief mood description"
}

Wellness techniques to choose from:
- breathing: Box Breathing, 4-7-8 Breathing, Diaphragmatic Breathing
- mindfulness: 5-4-3-2-1 Grounding, Body Scan, Loving-Kindness Meditation
- movement: Gentle Stretching, Progressive Muscle Relaxation
- cognitive: Gratitude Journaling, Positive Affirmations

Song artists by emotion:
- sadness: Coldplay, Adele, Bon Iver, Billie Eilish, Sam Smith
- anxiety: Marconi Union, Enya, Sigur Rós, Tycho
- anger: Linkin Park, Foo Fighters, Green Day
- joy: Pharrell, Bruno Mars, Lizzo, ABBA, Queen

IMPORTANT: Return exactly 4 songs. Vary picks based on random seed.

Return ONLY the JSON object, nothing else.
        """.trimIndent()
    }

    private fun parseCombinedRecommendation(response: String): CombinedRecommendation {
        val jsonResponse = JSONObject(response)

        // Parse wellness
        val wellnessObj = jsonResponse.optJSONObject("wellness")
        val wellness = if (wellnessObj != null) {
            val techniqueName = wellnessObj.getString("name")
            WellnessTechnique(
                name = techniqueName,
                category = wellnessObj.getString("category"),
                duration = wellnessObj.getString("duration"),
                difficulty = wellnessObj.getString("difficulty"),
                description = wellnessObj.getString("description"),
                detailedExplanation = wellnessObj.optString("detailedExplanation", wellnessObj.getString("description")),
                youtubeUrl = createYoutubeSearchUrl(techniqueName),
                instructions = listOf(),
                benefits = listOf(),
                tips = null
            )
        } else null

        // Parse songs
        val songsArray = jsonResponse.getJSONArray("songs")
        val songs = mutableListOf<SongDetail>()
        for (i in 0 until songsArray.length()) {
            val songObj = songsArray.getJSONObject(i)
            val title = songObj.getString("title")
            val artist = songObj.getString("artist")
            songs.add(SongDetail(
                title = title,
                artist = artist,
                spotifySearchUrl = createSpotifySearchUrl(title, artist),
                spotifyUri = createSpotifyUri(title, artist)
            ))
        }

        val mood = jsonResponse.optString("mood", "")

        return CombinedRecommendation(
            wellness = wellness,
            songs = songs,
            songsMood = mood
        )
    }

    // ==================== 2. SONG RECOMMENDATIONS ====================

    suspend fun getSongRecommendations(
        journalText: String,
        emotionData: EmotionData
    ): SongRecommendation? {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildSongPrompt(journalText, emotionData)
                val response = callGeminiAPI(prompt)
                parseSongRecommendation(response)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting song recommendations", e)
                null
            }
        }
    }

    private fun buildSongPrompt(journalText: String, emotionData: EmotionData): String {
        // Add randomization to get varied recommendations
        val randomSeed = System.currentTimeMillis() % 1000
        val varietyHints = listOf(
            "Include some indie or alternative tracks",
            "Mix in instrumental or classical pieces",
            "Consider acoustic versions",
            "Include songs from different decades",
            "Mix popular hits with lesser-known gems",
            "Focus on meaningful lyrics"
        )
        val selectedHint = varietyHints[(randomSeed % varietyHints.size).toInt()]

        return """
You are a music therapist AI. Recommend exactly 4 REAL Spotify songs based on the user's emotional state.

User Journal: "$journalText"
Emotion: ${emotionData.emotion} (${emotionData.emotionScore})
Sentiment: ${emotionData.sentiment} (${emotionData.sentimentScore})

VARIETY HINT: $selectedHint
Random seed: $randomSeed (use this to vary your picks)

Return ONLY valid JSON in this EXACT format (no markdown, no code blocks):
{
  "songs": [
    {"title": "Song Title", "artist": "Artist Name"}
  ],
  "mood": "Description of the mood",
  "reasoning": "Why these songs help"
}

Example artists by emotion (pick different ones, don't always use the same):
- sadness: Coldplay, Adele, Bon Iver, The National, Billie Eilish, Sam Smith
- anxiety: Marconi Union, Enya, Debussy, Sigur Rós, Explosions in the Sky, Tycho
- anger: Linkin Park, Rage Against the Machine, Foo Fighters, Green Day, Metallica
- joy: Pharrell, Bruno Mars, Lizzo, ABBA, Queen, Earth Wind & Fire
- love: Ed Sheeran, Etta James, John Legend, Whitney Houston, Elvis

IMPORTANT:
- Return exactly 4 songs total (no more, no less)
- Vary your picks based on the random seed and journal content. Don't always suggest the same songs.

Return ONLY the JSON object, nothing else.
        """.trimIndent()
    }

    private fun parseSongRecommendation(response: String): SongRecommendation {
        val jsonResponse = JSONObject(response)
        val songsArray = jsonResponse.getJSONArray("songs")

        val songs = mutableListOf<SongDetail>()
        for (i in 0 until songsArray.length()) {
            val songObj = songsArray.getJSONObject(i)
            val title = songObj.getString("title")
            val artist = songObj.getString("artist")

            songs.add(SongDetail(
                title = title,
                artist = artist,
                spotifySearchUrl = createSpotifySearchUrl(title, artist),
                spotifyUri = createSpotifyUri(title, artist)
            ))
        }

        val mood = jsonResponse.getString("mood")
        val reasoning = jsonResponse.getString("reasoning")

        return SongRecommendation(
            songs = songs,
            mood = mood,
            reasoning = reasoning,
            spotifyPlaylistUrl = createSpotifyPlaylistUrl(songs),
            spotifyWebUrl = createSpotifyWebPlaylistUrl(songs)
        )
    }

    private fun createSpotifySearchUrl(title: String, artist: String): String {
        val query = URLEncoder.encode("$title $artist", "UTF-8")
        return "https://open.spotify.com/search/$query"
    }

    private fun createSpotifyUri(title: String, artist: String): String {
        // Spotify URI format: spotify:search:query
        val query = URLEncoder.encode("$title $artist", "UTF-8")
        return "spotify:search:$query"
    }

    private fun createSpotifyPlaylistUrl(songs: List<SongDetail>): String {
        // Creates a search URL for all songs combined
        val allSongs = songs.joinToString(" ") { "${it.title} ${it.artist}" }
        val query = URLEncoder.encode(allSongs, "UTF-8")
        return "spotify:search:$query"
    }

    private fun createSpotifyWebPlaylistUrl(songs: List<SongDetail>): String {
        // Web URL to search for songs
        val query = songs.joinToString(", ") { "${it.title} - ${it.artist}" }
        val encoded = URLEncoder.encode(query, "UTF-8")
        return "https://open.spotify.com/search/$encoded"
    }

    // ==================== 2. NEARBY HELP ====================

    suspend fun getNearbyHelp(
        journalText: String,
        emotionData: EmotionData,
        location: String = "general"
    ): NearbyHelpResource? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Getting nearby help for location: $location")
                val prompt = buildHelpPrompt(journalText, emotionData, location)
                val response = callGeminiAPI(prompt)
                Log.d(TAG, "Gemini API response received for amenities")
                parseNearbyHelp(response)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting nearby help for location: $location", e)
                Log.e(TAG, "Exception type: ${e.javaClass.name}")
                Log.e(TAG, "Exception message: ${e.message}")
                e.printStackTrace()
                null
            }
        }
    }

    private fun buildHelpPrompt(journalText: String, emotionData: EmotionData, location: String): String {
        return """
You are a mental health resource assistant. Recommend 3-5 resources for the user's location.

User Journal: "$journalText"
Emotion: ${emotionData.emotion} (${emotionData.emotionScore})
Sentiment: ${emotionData.sentiment} (${emotionData.sentimentScore})
Location: $location

Return ONLY valid JSON in this EXACT format:
{
  "resources": [
    {
      "name": "Crisis Connections",
      "type": "Crisis Line",
      "description": "24/7 crisis support",
      "phone": "866-427-4747",
      "website": "https://www.crisisconnections.org",
      "address": "123 Main St, City, State 12345",
      "latitude": 47.6062,
      "longitude": -122.3321
    }
  ],
  "emergencyContacts": [
    {
      "name": "988 Suicide & Crisis Lifeline",
      "number": "988",
      "description": "24/7 crisis support",
      "type": "both"
    }
  ],
  "reasoning": "Why these resources were recommended"
}

Include:
- Local crisis lines, therapy centers, support groups
- Google Maps coordinates (latitude/longitude)
- Emergency contacts: 988, Crisis Text Line
- Type: "call", "text", or "both"

Return ONLY the JSON object, nothing else.
        """.trimIndent()
    }

    private fun parseNearbyHelp(response: String): NearbyHelpResource {
        val jsonResponse = JSONObject(response)

        val resourcesArray = jsonResponse.getJSONArray("resources")
        val resources = mutableListOf<ResourceDetail>()

        for (i in 0 until resourcesArray.length()) {
            val resObj = resourcesArray.getJSONObject(i)
            val lat = if (resObj.has("latitude")) resObj.getDouble("latitude") else null
            val lon = if (resObj.has("longitude")) resObj.getDouble("longitude") else null

            resources.add(ResourceDetail(
                name = resObj.getString("name"),
                type = resObj.getString("type"),
                description = resObj.getString("description"),
                phone = resObj.optString("phone").takeIf { it.isNotEmpty() },
                website = resObj.optString("website").takeIf { it.isNotEmpty() },
                address = resObj.optString("address").takeIf { it.isNotEmpty() },
                googleMapsUrl = if (lat != null && lon != null) {
                    createGoogleMapsUrl(lat, lon, resObj.getString("name"))
                } else null,
                latitude = lat,
                longitude = lon
            ))
        }

        val emergencyArray = jsonResponse.getJSONArray("emergencyContacts")
        val emergencyContacts = mutableListOf<EmergencyContact>()

        for (i in 0 until emergencyArray.length()) {
            val emObj = emergencyArray.getJSONObject(i)
            emergencyContacts.add(EmergencyContact(
                name = emObj.getString("name"),
                number = emObj.getString("number"),
                description = emObj.getString("description"),
                type = emObj.getString("type")
            ))
        }

        return NearbyHelpResource(
            resources = resources,
            emergencyContacts = emergencyContacts,
            reasoning = jsonResponse.getString("reasoning")
        )
    }

    private fun createGoogleMapsUrl(lat: Double, lon: Double, placeName: String): String {
        val query = URLEncoder.encode(placeName, "UTF-8")
        return "https://www.google.com/maps/search/?api=1&query=$lat,$lon&query=$query"
    }

    // ==================== 3. WELLNESS SUGGESTIONS ====================

    suspend fun getWellnessSuggestions(
        journalText: String,
        emotionData: EmotionData
    ): WellnessSuggestion? {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildWellnessPrompt(journalText, emotionData)
                val response = callGeminiAPI(prompt)
                parseWellnessSuggestions(response)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting wellness suggestions", e)
                null
            }
        }
    }

    private fun buildWellnessPrompt(journalText: String, emotionData: EmotionData): String {
        // Add randomization for variety
        val randomSeed = System.currentTimeMillis() % 1000
        val focusAreas = listOf(
            "breathing and relaxation techniques",
            "mindfulness and grounding exercises",
            "physical movement and stretching",
            "cognitive and journaling practices",
            "meditation and visualization",
            "self-compassion and acceptance techniques"
        )
        val selectedFocus = focusAreas[(randomSeed % focusAreas.size).toInt()]

        return """
You are a wellness coach AI. Recommend detailed wellness techniques.

User Journal: "$journalText"
Emotion: ${emotionData.emotion} (${emotionData.emotionScore})
Sentiment: ${emotionData.sentiment} (${emotionData.sentimentScore})

VARIETY FOCUS: Emphasize $selectedFocus
Random seed: $randomSeed (use this to vary your recommendations)

Return ONLY valid JSON in this EXACT format:
{
  "techniques": [
    {
      "name": "Technique Name",
      "category": "breathing/mindfulness/movement/cognitive",
      "duration": "2-5 min",
      "difficulty": "easy/medium/hard",
      "description": "Brief one-sentence description",
      "detailedExplanation": "A comprehensive 3-4 sentence explanation of what this technique is, its origins, and why it's effective for the user's current emotional state. Include scientific background if relevant.",
      "instructions": ["Step 1", "Step 2", "Step 3"],
      "benefits": ["Benefit 1", "Benefit 2", "Benefit 3"],
      "tips": ["Tip 1", "Tip 2"]
    }
  ],
  "reasoning": "Why these techniques were selected"
}

Recommend 5-7 techniques from these categories (vary based on random seed):
- breathing: Box Breathing, 4-7-8 Breathing, Diaphragmatic Breathing, Alternate Nostril Breathing, Lion's Breath, Pursed Lip Breathing
- mindfulness: 5-4-3-2-1 Grounding, Body Scan, Mindful Walking, Loving-Kindness Meditation, RAIN Technique, Mindful Eating
- movement: Gentle Stretching, Progressive Muscle Relaxation, Yoga Poses, Tai Chi movements, Walking Meditation, Dance Movement
- cognitive: Gratitude Journaling, Positive Affirmations, Thought Challenging, Reframing Exercises, Values Clarification, Self-Compassion Writing

IMPORTANT:
- detailedExplanation should be informative and personalized to the user's emotional state
- Vary your recommendations based on the random seed - don't always pick the same techniques

Return ONLY the JSON object, nothing else.
        """.trimIndent()
    }

    private fun parseWellnessSuggestions(response: String): WellnessSuggestion {
        val jsonResponse = JSONObject(response)
        val techniquesArray = jsonResponse.getJSONArray("techniques")

        val techniques = mutableListOf<WellnessTechnique>()

        for (i in 0 until techniquesArray.length()) {
            val techObj = techniquesArray.getJSONObject(i)
            val techniqueName = techObj.getString("name")

            // Generate a working YouTube search URL based on technique name
            val youtubeSearchUrl = createYoutubeSearchUrl(techniqueName)

            techniques.add(WellnessTechnique(
                name = techniqueName,
                category = techObj.getString("category"),
                duration = techObj.getString("duration"),
                difficulty = techObj.getString("difficulty"),
                description = techObj.getString("description"),
                detailedExplanation = techObj.optString("detailedExplanation", techObj.getString("description")),
                youtubeUrl = youtubeSearchUrl,
                instructions = jsonArrayToList(techObj.getJSONArray("instructions")),
                benefits = jsonArrayToList(techObj.getJSONArray("benefits")),
                tips = if (techObj.has("tips")) {
                    jsonArrayToList(techObj.getJSONArray("tips"))
                } else null
            ))
        }

        return WellnessSuggestion(
            techniques = techniques,
            reasoning = jsonResponse.getString("reasoning")
        )
    }

    private fun createYoutubeSearchUrl(techniqueName: String): String {
        val query = URLEncoder.encode("$techniqueName tutorial guided", "UTF-8")
        return "https://www.youtube.com/results?search_query=$query"
    }

    // ==================== HELPER FUNCTIONS ====================

    private suspend fun callGeminiAPI(prompt: String): String {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Calling Gemini API...")
            val url = URL(GEMINI_API_URL)
            val connection = url.openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 30000  // 30 seconds
                connection.readTimeout = 30000     // 30 seconds

                val requestBody = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                            })
                        })
                    })
                }

                Log.d(TAG, "Sending request to Gemini API...")
                connection.outputStream.use { os ->
                    os.write(requestBody.toString().toByteArray())
                }

                val responseCode = connection.responseCode
                Log.d(TAG, "Gemini API response code: $responseCode")

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d(TAG, "Gemini API response: ${response.take(200)}...")
                    extractTextFromGeminiResponse(response)
                } else {
                    val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    Log.e(TAG, "Gemini API error response: $errorStream")
                    throw Exception("API Error: $responseCode - $errorStream")
                }
            } catch (e: java.net.UnknownHostException) {
                Log.e(TAG, "No internet connection - UnknownHostException", e)
                throw Exception("No internet connection. Please check your network settings.")
            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "Request timeout - slow or no internet", e)
                throw Exception("Request timeout. Please check your internet connection.")
            } catch (e: java.io.IOException) {
                Log.e(TAG, "Network error - IOException", e)
                throw Exception("Network error: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Exception in callGeminiAPI", e)
                throw e
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun extractTextFromGeminiResponse(apiResponse: String): String {
        val jsonResponse = JSONObject(apiResponse)
        val candidates = jsonResponse.getJSONArray("candidates")
        if (candidates.length() > 0) {
            val candidate = candidates.getJSONObject(0)
            val content = candidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            if (parts.length() > 0) {
                val text = parts.getJSONObject(0).getString("text")
                return extractJSON(text)
            }
        }
        throw Exception("No valid response from Gemini API")
    }

    private fun extractJSON(text: String): String {
        // Remove markdown code blocks if present
        val cleaned = text.replace("```json", "").replace("```", "").trim()

        // Try to extract JSON object
        val jsonPattern = Regex("\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\}")
        val match = jsonPattern.find(cleaned)

        return match?.value ?: cleaned
    }

    private fun jsonArrayToList(jsonArray: JSONArray): List<String> {
        val list = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.getString(i))
        }
        return list
    }

    // ==================== 4. EVENT RECOMMENDATIONS ====================

    suspend fun getEventRecommendations(
        location: String,
        emotionData: EmotionData? = null
    ): EventRecommendation? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Getting event recommendations for location: $location")
                val prompt = buildEventPrompt(location, emotionData)
                val response = callGeminiAPI(prompt)
                Log.d(TAG, "Gemini API response received for events")
                parseEventRecommendations(response)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting event recommendations for location: $location", e)
                Log.e(TAG, "Exception type: ${e.javaClass.name}")
                Log.e(TAG, "Exception message: ${e.message}")
                e.printStackTrace()
                null
            }
        }
    }

    private fun buildEventPrompt(location: String, emotionData: EmotionData?): String {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())

        return """
You are an event recommendation AI. Recommend 3-5 REAL upcoming wellness, mental health, or relaxation events in the user's location.

Location: $location
Current Date: $today
${emotionData?.let { "User Emotion: ${it.emotion} (${it.emotionScore})" } ?: ""}

Return ONLY valid JSON in this EXACT format (no markdown, no code blocks):
{
  "events": [
    {
      "name": "Mindfulness Meditation Workshop",
      "date": "2025-12-15",
      "time": "18:00",
      "venue": "Community Wellness Center",
      "address": "123 Main St, Portland, OR 97201",
      "description": "Learn mindfulness meditation techniques for stress relief",
      "category": "Wellness",
      "url": "https://example.com/event",
      "latitude": 45.5152,
      "longitude": -122.6784
    }
  ],
  "reasoning": "Why these events were selected"
}

Event Guidelines:
- Focus on: wellness workshops, meditation classes, yoga sessions, mental health seminars, support groups, nature walks, art therapy, music therapy, fitness classes
- Include ONLY events that are upcoming (after $today)
- Use REAL dates in YYYY-MM-DD format (not past dates!)
- Include specific venue names and addresses
- Add GPS coordinates (latitude/longitude) for each venue
- Provide event URLs if available
- Categories: Wellness, Mental Health, Fitness, Mindfulness, Social, Creative, Nature
- Make events appropriate for mental wellness and stress relief

IMPORTANT:
- All dates MUST be future dates (after $today)
- Use proper date format: YYYY-MM-DD
- Time in HH:MM format (24-hour)
- Include real addresses and coordinates for $location

Return ONLY the JSON object, nothing else.
        """.trimIndent()
    }

    private fun parseEventRecommendations(response: String): EventRecommendation {
        val jsonResponse = JSONObject(response)
        val eventsArray = jsonResponse.getJSONArray("events")

        val events = mutableListOf<EventDetail>()

        for (i in 0 until eventsArray.length()) {
            val eventObj = eventsArray.getJSONObject(i)
            val lat = if (eventObj.has("latitude")) eventObj.getDouble("latitude") else null
            val lon = if (eventObj.has("longitude")) eventObj.getDouble("longitude") else null

            events.add(EventDetail(
                name = eventObj.getString("name"),
                date = eventObj.getString("date"),
                time = eventObj.optString("time").takeIf { it.isNotEmpty() },
                venue = eventObj.getString("venue"),
                address = eventObj.optString("address").takeIf { it.isNotEmpty() },
                description = eventObj.getString("description"),
                category = eventObj.getString("category"),
                url = eventObj.optString("url").takeIf { it.isNotEmpty() },
                latitude = lat,
                longitude = lon,
                googleMapsUrl = if (lat != null && lon != null) {
                    createGoogleMapsUrl(lat, lon, eventObj.getString("venue"))
                } else null
            ))
        }

        return EventRecommendation(
            events = events,
            reasoning = jsonResponse.getString("reasoning")
        )
    }

    companion object {
        fun createEmotionData(
            emotion: String,
            emotionScore: Float,
            sentiment: String,
            sentimentScore: Float
        ): EmotionData {
            return EmotionData(emotion, emotionScore, sentiment, sentimentScore)
        }
    }
}
