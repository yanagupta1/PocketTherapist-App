package com.example.pockettherapist

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Spotify API Integration
 *
 * Uses Spotify Web API to:
 * 1. Search for songs and get real Spotify URIs
 * 2. Create playlists
 * 3. Generate deep links to Spotify app
 *
 * Credentials:
 * - Client ID: e28db4ae63574536a2785bcde69dfaee
 * - Client Secret: 4954ac98eafc45bf946aa6ba90aca514
 * - Status: Development mode
 */
class SpotifyIntegration(private val context: Context) {

    private val TAG = "SpotifyIntegration"

    // Spotify API Credentials
    private val CLIENT_ID = "e28db4ae63574536a2785bcde69dfaee"
    private val CLIENT_SECRET = "4954ac98eafc45bf946aa6ba90aca514"
    private val TOKEN_URL = "https://accounts.spotify.com/api/token"
    private val SEARCH_URL = "https://api.spotify.com/v1/search"

    // Cached access token (valid for 1 hour)
    private var accessToken: String? = null
    private var tokenExpiry: Long = 0

    /**
     * Data class for Spotify track
     */
    data class SpotifyTrack(
        val id: String,
        val name: String,
        val artist: String,
        val uri: String,              // spotify:track:xxxxx
        val url: String,              // https://open.spotify.com/track/xxxxx
        val previewUrl: String?,      // 30s preview MP3
        val albumArt: String?         // Album cover image URL
    )

    /**
     * Data class for Spotify playlist
     */
    data class SpotifyPlaylist(
        val tracks: List<SpotifyTrack>,
        val playlistUrl: String,      // Opens full playlist in Spotify
        val webUrl: String            // Opens in web browser
    )

    // ==================== AUTHENTICATION ====================

    /**
     * Get Spotify access token using Client Credentials Flow
     * Token is valid for 1 hour and is cached
     */
    private suspend fun getAccessToken(): String {
        // Return cached token if still valid
        if (accessToken != null && System.currentTimeMillis() < tokenExpiry) {
            return accessToken!!
        }

        return withContext(Dispatchers.IO) {
            try {
                val url = URL(TOKEN_URL)
                val connection = url.openConnection() as HttpURLConnection

                // Create Basic Auth header
                val credentials = "$CLIENT_ID:$CLIENT_SECRET"
                val encodedCredentials = Base64.encodeToString(
                    credentials.toByteArray(),
                    Base64.NO_WRAP
                )

                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Basic $encodedCredentials")
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.doOutput = true

                // Request body
                val postData = "grant_type=client_credentials"
                connection.outputStream.use { os ->
                    os.write(postData.toByteArray())
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(response)

                    val token = jsonResponse.getString("access_token")
                    val expiresIn = jsonResponse.getInt("expires_in") // seconds

                    // Cache token
                    accessToken = token
                    tokenExpiry = System.currentTimeMillis() + (expiresIn * 1000)

                    Log.d(TAG, "Successfully obtained Spotify access token")
                    token
                } else {
                    val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    throw Exception("Failed to get access token: $responseCode - $errorStream")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting Spotify access token", e)
                throw e
            }
        }
    }

    // ==================== SEARCH ====================

    /**
     * Search for a track on Spotify
     *
     * @param title Song title
     * @param artist Artist name
     * @return SpotifyTrack with real Spotify data, or null if not found
     */
    suspend fun searchTrack(title: String, artist: String): SpotifyTrack? {
        return withContext(Dispatchers.IO) {
            try {
                val token = getAccessToken()
                val query = URLEncoder.encode("track:$title artist:$artist", "UTF-8")
                val searchUrl = "$SEARCH_URL?q=$query&type=track&limit=1"

                val url = URL(searchUrl)
                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $token")

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    parseTrackResponse(response)
                } else {
                    Log.e(TAG, "Search failed: $responseCode")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error searching track: $title - $artist", e)
                null
            }
        }
    }

    /**
     * Search for multiple tracks
     *
     * @param songs List of (title, artist) pairs
     * @return List of SpotifyTracks (may be smaller if some songs not found)
     */
    suspend fun searchTracks(songs: List<Pair<String, String>>): List<SpotifyTrack> {
        return withContext(Dispatchers.IO) {
            val tracks = mutableListOf<SpotifyTrack>()

            for ((title, artist) in songs) {
                val track = searchTrack(title, artist)
                if (track != null) {
                    tracks.add(track)
                } else {
                    Log.w(TAG, "Track not found: $title - $artist")
                }
            }

            tracks
        }
    }

    private fun parseTrackResponse(response: String): SpotifyTrack? {
        try {
            val jsonResponse = JSONObject(response)
            val tracks = jsonResponse.getJSONObject("tracks")
            val items = tracks.getJSONArray("items")

            if (items.length() == 0) {
                return null
            }

            val track = items.getJSONObject(0)
            val id = track.getString("id")
            val name = track.getString("name")
            val uri = track.getString("uri")
            val externalUrls = track.getJSONObject("external_urls")
            val spotifyUrl = externalUrls.getString("spotify")
            val previewUrl = track.optString("preview_url").takeIf { it.isNotEmpty() }

            // Get artist name
            val artists = track.getJSONArray("artists")
            val artistName = if (artists.length() > 0) {
                artists.getJSONObject(0).getString("name")
            } else {
                "Unknown Artist"
            }

            // Get album art
            val album = track.getJSONObject("album")
            val images = album.getJSONArray("images")
            val albumArt = if (images.length() > 0) {
                images.getJSONObject(0).getString("url")
            } else {
                null
            }

            return SpotifyTrack(
                id = id,
                name = name,
                artist = artistName,
                uri = uri,
                url = spotifyUrl,
                previewUrl = previewUrl,
                albumArt = albumArt
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing track response", e)
            return null
        }
    }

    // ==================== PLAYLIST GENERATION ====================

    /**
     * Create a Spotify playlist from list of songs
     *
     * @param songs List of (title, artist) pairs
     * @return SpotifyPlaylist with all found tracks and URLs
     */
    suspend fun createPlaylist(songs: List<Pair<String, String>>): SpotifyPlaylist {
        return withContext(Dispatchers.IO) {
            val tracks = searchTracks(songs)

            // Create playlist URL from track URIs
            val trackIds = tracks.map { it.id }
            val playlistUrl = createPlaylistDeepLink(trackIds)
            val webUrl = createPlaylistWebUrl(trackIds)

            SpotifyPlaylist(
                tracks = tracks,
                playlistUrl = playlistUrl,
                webUrl = webUrl
            )
        }
    }

    /**
     * Create deep link that opens in Spotify app
     * Uses spotify:track: URIs
     */
    private fun createPlaylistDeepLink(trackIds: List<String>): String {
        // Spotify doesn't support direct playlist creation via URI without authentication
        // Instead, we create a search URL with all track IDs
        val trackUris = trackIds.joinToString(",") { "spotify:track:$it" }
        return "spotify:search:${trackUris}"
    }

    /**
     * Create web URL for playlist
     * Opens in browser and can be saved to Spotify account
     */
    private fun createPlaylistWebUrl(trackIds: List<String>): String {
        // Create a search URL with all tracks
        val query = trackIds.joinToString(",")
        return "https://open.spotify.com/search/${URLEncoder.encode(query, "UTF-8")}"
    }

    // ==================== UTILITY FUNCTIONS ====================

    /**
     * Create a clickable Spotify deep link for a single track
     */
    fun createTrackDeepLink(trackId: String): String {
        return "spotify:track:$trackId"
    }

    /**
     * Create a web URL for a single track
     */
    fun createTrackWebUrl(trackId: String): String {
        return "https://open.spotify.com/track/$trackId"
    }

    /**
     * Create search URL for a song (fallback if track not found)
     */
    fun createSearchUrl(title: String, artist: String): String {
        val query = URLEncoder.encode("$title $artist", "UTF-8")
        return "https://open.spotify.com/search/$query"
    }

    /**
     * Create search deep link for Spotify app (fallback)
     */
    fun createSearchDeepLink(title: String, artist: String): String {
        val query = URLEncoder.encode("$title $artist", "UTF-8")
        return "spotify:search:$query"
    }

    // ==================== USER AUTHENTICATED PLAYLIST CREATION ====================

    /**
     * Data class for created playlist
     */
    data class CreatedPlaylist(
        val playlistId: String,
        val name: String,
        val url: String,
        val uri: String
    )

    /**
     * Get current user's Spotify ID
     * Requires user access token with user-read-private scope
     *
     * @param userAccessToken User's access token from OAuth flow
     * @return User's Spotify ID
     */
    suspend fun getUserId(userAccessToken: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.spotify.com/v1/me")
                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $userAccessToken")

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(response)
                    jsonResponse.getString("id")
                } else {
                    Log.e(TAG, "Failed to get user ID: $responseCode")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting user ID", e)
                null
            }
        }
    }

    /**
     * Create a Spotify playlist for the user
     * Requires user access token with playlist-modify-public or playlist-modify-private scope
     *
     * @param userAccessToken User's access token from OAuth flow
     * @param playlistName Name of the playlist
     * @param description Description of the playlist
     * @param isPublic Whether the playlist should be public (default: false)
     * @return CreatedPlaylist with ID and URL
     */
    suspend fun createUserPlaylist(
        userAccessToken: String,
        playlistName: String,
        description: String = "",
        isPublic: Boolean = false
    ): CreatedPlaylist? {
        return withContext(Dispatchers.IO) {
            try {
                // First get user ID
                val userId = getUserId(userAccessToken)
                if (userId == null) {
                    Log.e(TAG, "Cannot create playlist: failed to get user ID")
                    return@withContext null
                }

                val url = URL("https://api.spotify.com/v1/users/$userId/playlists")
                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Bearer $userAccessToken")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                // Request body
                val requestBody = JSONObject().apply {
                    put("name", playlistName)
                    put("description", description)
                    put("public", isPublic)
                }

                connection.outputStream.use { os ->
                    os.write(requestBody.toString().toByteArray())
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(response)

                    CreatedPlaylist(
                        playlistId = jsonResponse.getString("id"),
                        name = jsonResponse.getString("name"),
                        url = jsonResponse.getJSONObject("external_urls").getString("spotify"),
                        uri = jsonResponse.getString("uri")
                    )
                } else {
                    val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    Log.e(TAG, "Failed to create playlist: $responseCode - $errorStream")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating playlist", e)
                null
            }
        }
    }

    /**
     * Add tracks to an existing playlist
     * Requires user access token with playlist-modify-public or playlist-modify-private scope
     *
     * @param userAccessToken User's access token from OAuth flow
     * @param playlistId ID of the playlist
     * @param trackUris List of Spotify track URIs (spotify:track:xxx)
     * @return True if successful
     */
    suspend fun addTracksToPlaylist(
        userAccessToken: String,
        playlistId: String,
        trackUris: List<String>
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.spotify.com/v1/playlists/$playlistId/tracks")
                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Bearer $userAccessToken")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                // Request body
                val requestBody = JSONObject().apply {
                    put("uris", JSONArray(trackUris))
                }

                connection.outputStream.use { os ->
                    os.write(requestBody.toString().toByteArray())
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "Successfully added ${trackUris.size} tracks to playlist")
                    true
                } else {
                    val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    Log.e(TAG, "Failed to add tracks: $responseCode - $errorStream")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding tracks to playlist", e)
                false
            }
        }
    }

    /**
     * Create a complete playlist with tracks in one call
     * This combines playlist creation and adding tracks
     *
     * @param userAccessToken User's access token from OAuth flow
     * @param playlistName Name of the playlist
     * @param description Description of the playlist
     * @param songs List of (title, artist) pairs
     * @param isPublic Whether the playlist should be public
     * @return CreatedPlaylist with URL, or null if failed
     */
    suspend fun createPlaylistWithTracks(
        userAccessToken: String,
        playlistName: String,
        description: String,
        songs: List<Pair<String, String>>,
        isPublic: Boolean = false
    ): CreatedPlaylist? {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Search for all tracks
                Log.d(TAG, "Searching for ${songs.size} tracks...")
                val tracks = searchTracks(songs)

                if (tracks.isEmpty()) {
                    Log.e(TAG, "No tracks found")
                    return@withContext null
                }

                Log.d(TAG, "Found ${tracks.size} tracks")

                // 2. Create playlist
                val playlist = createUserPlaylist(userAccessToken, playlistName, description, isPublic)
                if (playlist == null) {
                    Log.e(TAG, "Failed to create playlist")
                    return@withContext null
                }

                Log.d(TAG, "Created playlist: ${playlist.name}")

                // 3. Add tracks to playlist
                val trackUris = tracks.map { it.uri }
                val success = addTracksToPlaylist(userAccessToken, playlist.playlistId, trackUris)

                if (success) {
                    Log.d(TAG, "Successfully created playlist with ${tracks.size} tracks")
                    playlist
                } else {
                    Log.w(TAG, "Playlist created but failed to add tracks")
                    playlist // Still return playlist even if adding tracks failed
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating playlist with tracks", e)
                null
            }
        }
    }

    companion object {
        /**
         * Check if Spotify app is installed on device
         */
        fun isSpotifyInstalled(context: Context): Boolean {
            return try {
                context.packageManager.getPackageInfo("com.spotify.music", 0)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
