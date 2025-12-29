package com.example.pockettherapist

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local cache for recommendations to avoid repeated API calls.
 * Cache is invalidated when:
 * 1. A new journal entry is added (detected by comparing latest journal ID)
 * 2. User pulls down to refresh
 */
object RecommendationsCache {

    private const val PREFS_NAME = "recommendations_cache"
    private const val KEY_LATEST_JOURNAL_ID = "latest_journal_id"
    private const val KEY_WELLNESS_TITLE = "wellness_title"
    private const val KEY_WELLNESS_DESCRIPTION = "wellness_description"
    private const val KEY_WELLNESS_DETAILED_EXPLANATION = "wellness_detailed_explanation"
    private const val KEY_WELLNESS_YOUTUBE_URL = "wellness_youtube_url"
    private const val KEY_WELLNESS_REASONING = "wellness_reasoning"
    private const val KEY_SONGS_JSON = "songs_json"
    private const val KEY_SONGS_REASONING = "songs_reasoning"
    private const val KEY_EMOTION = "emotion"
    private const val KEY_SENTIMENT = "sentiment"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Check if cache is valid for the given journal entry ID.
     * Returns true if cached data exists and matches the latest journal.
     */
    fun isCacheValid(latestJournalId: String): Boolean {
        val cachedJournalId = prefs?.getString(KEY_LATEST_JOURNAL_ID, null)
        return cachedJournalId == latestJournalId && hasCachedData()
    }

    /**
     * Check if we have any cached data
     */
    fun hasCachedData(): Boolean {
        val wellnessTitle = prefs?.getString(KEY_WELLNESS_TITLE, null)
        return !wellnessTitle.isNullOrEmpty()
    }

    /**
     * Save wellness recommendation to cache
     */
    fun saveWellness(journalId: String, title: String, description: String, detailedExplanation: String = "", youtubeUrl: String = "", reasoning: String = "") {
        prefs?.edit()?.apply {
            putString(KEY_LATEST_JOURNAL_ID, journalId)
            putString(KEY_WELLNESS_TITLE, title)
            putString(KEY_WELLNESS_DESCRIPTION, description)
            putString(KEY_WELLNESS_DETAILED_EXPLANATION, detailedExplanation)
            putString(KEY_WELLNESS_YOUTUBE_URL, youtubeUrl)
            putString(KEY_WELLNESS_REASONING, reasoning)
            apply()
        }
    }

    /**
     * Get cached wellness title
     */
    fun getWellnessTitle(): String? {
        return prefs?.getString(KEY_WELLNESS_TITLE, null)
    }

    /**
     * Get cached wellness description
     */
    fun getWellnessDescription(): String? {
        return prefs?.getString(KEY_WELLNESS_DESCRIPTION, null)
    }

    /**
     * Get cached wellness detailed explanation
     */
    fun getWellnessDetailedExplanation(): String? {
        return prefs?.getString(KEY_WELLNESS_DETAILED_EXPLANATION, null)
    }

    /**
     * Get cached wellness YouTube URL
     */
    fun getWellnessYoutubeUrl(): String? {
        return prefs?.getString(KEY_WELLNESS_YOUTUBE_URL, null)
    }

    /**
     * Get cached wellness reasoning
     */
    fun getWellnessReasoning(): String? {
        return prefs?.getString(KEY_WELLNESS_REASONING, null)
    }

    /**
     * Save songs to cache as JSON
     */
    fun saveSongs(songs: List<SpotifyIntegration.SpotifyTrack>, reasoning: String = "") {
        prefs?.edit()?.putString(KEY_SONGS_REASONING, reasoning)?.apply()
        val jsonArray = JSONArray()
        for (song in songs) {
            val songJson = JSONObject().apply {
                put("id", song.id)
                put("name", song.name)
                put("artist", song.artist)
                put("uri", song.uri)
                put("url", song.url)
                put("previewUrl", song.previewUrl ?: "")
                put("albumArt", song.albumArt ?: "")
            }
            jsonArray.put(songJson)
        }
        prefs?.edit()?.putString(KEY_SONGS_JSON, jsonArray.toString())?.apply()
    }

    /**
     * Get cached songs from JSON
     */
    fun getSongs(): List<SpotifyIntegration.SpotifyTrack>? {
        val jsonString = prefs?.getString(KEY_SONGS_JSON, null) ?: return null

        return try {
            val jsonArray = JSONArray(jsonString)
            val songs = mutableListOf<SpotifyIntegration.SpotifyTrack>()

            for (i in 0 until jsonArray.length()) {
                val songJson = jsonArray.getJSONObject(i)
                songs.add(SpotifyIntegration.SpotifyTrack(
                    id = songJson.getString("id"),
                    name = songJson.getString("name"),
                    artist = songJson.getString("artist"),
                    uri = songJson.getString("uri"),
                    url = songJson.getString("url"),
                    previewUrl = songJson.optString("previewUrl").takeIf { it.isNotEmpty() },
                    albumArt = songJson.optString("albumArt").takeIf { it.isNotEmpty() }
                ))
            }

            if (songs.isEmpty()) null else songs
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get cached songs reasoning
     */
    fun getSongsReasoning(): String? {
        return prefs?.getString(KEY_SONGS_REASONING, null)
    }

    /**
     * Save emotion and sentiment for generating friendly messages
     */
    fun saveEmotionData(emotion: String, sentiment: String) {
        prefs?.edit()?.apply {
            putString(KEY_EMOTION, emotion)
            putString(KEY_SENTIMENT, sentiment)
            apply()
        }
    }

    /**
     * Get cached emotion
     */
    fun getEmotion(): String? {
        return prefs?.getString(KEY_EMOTION, null)
    }

    /**
     * Get cached sentiment
     */
    fun getSentiment(): String? {
        return prefs?.getString(KEY_SENTIMENT, null)
    }

    /**
     * Clear all cached data
     */
    fun clearCache() {
        prefs?.edit()?.clear()?.apply()
    }
}
