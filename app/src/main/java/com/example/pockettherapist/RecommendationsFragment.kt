package com.example.pockettherapist

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.pockettherapist.databinding.FragmentRecommendationsBinding
import kotlinx.coroutines.launch

class RecommendationsFragment : Fragment() {

    private lateinit var binding: FragmentRecommendationsBinding
    private lateinit var songAdapter: SongAdapter
    private lateinit var recommendationEngine: RecommendationEngine
    private lateinit var spotifyIntegration: SpotifyIntegration
    private lateinit var emotionPredictor: EmotionModelPredictor
    private lateinit var sentimentPredictor: SentimentModelPredictor

    private val TAG = "RecommendationsFragment"
    private var currentJournalId: String? = null
    private var isWellnessExpanded = false
    private var currentYoutubeUrl: String? = null
    private var currentEmotion: String = ""
    private var currentSentiment: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentRecommendationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize cache
        RecommendationsCache.init(requireContext())

        // Initialize services
        recommendationEngine = RecommendationEngine(requireContext())
        spotifyIntegration = SpotifyIntegration(requireContext())
        emotionPredictor = EmotionModelPredictor(requireContext())
        sentimentPredictor = SentimentModelPredictor(requireContext())

        // Setup RecyclerView with 2-column grid
        songAdapter = SongAdapter()
        binding.recyclerSongs.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = songAdapter
        }

        // Setup wellness card click to expand/collapse
        binding.cardWellness.setOnClickListener {
            toggleWellnessExpansion()
        }

        // Setup YouTube button click - opens in external YouTube app or browser
        binding.btnWatchVideo.setOnClickListener {
            currentYoutubeUrl?.let { url ->
                if (url.isNotEmpty()) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(Intent.createChooser(intent, "Open with"))
                }
            }
        }

        // Setup Enable AI button click
        binding.btnEnableAI.setOnClickListener {
            AIConsentManager.showConsentDialog(
                context = requireContext(),
                onConsent = {
                    // User enabled AI, now load recommendations
                    hideAIDisabledState()
                    loadRecommendations(forceRefresh = false)
                },
                onDecline = {
                    // User declined, stay on disabled state
                }
            )
        }

        // Check AI consent before loading recommendations
        checkConsentAndLoadRecommendations()
    }

    private fun checkConsentAndLoadRecommendations() {
        // Initialize consent manager
        AIConsentManager.init(requireContext())

        // Check if AI is enabled
        if (AIConsentManager.isAIEnabled()) {
            // AI is enabled, load recommendations
            hideAIDisabledState()
            loadRecommendations(forceRefresh = false)
        } else {
            // AI is disabled (either declined or not yet decided)
            // Show disabled state - user can click button to enable
            showAIDisabledState()
        }
    }

    private fun showAIDisabledState() {
        binding.progressWellness.visibility = View.GONE
        binding.cardWellness.visibility = View.GONE
        binding.layoutSongsLoading.visibility = View.GONE
        binding.recyclerSongs.visibility = View.GONE
        binding.txtMusicHeader.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE

        // Show the AI disabled layout
        binding.layoutAIDisabled.visibility = View.VISIBLE
    }

    private fun hideAIDisabledState() {
        binding.layoutAIDisabled.visibility = View.GONE
        binding.cardWellness.visibility = View.VISIBLE
    }

    private fun loadRecommendations(forceRefresh: Boolean) {
        // Double-check AI is enabled
        if (!AIConsentManager.isAIEnabled()) {
            showAIDisabledState()
            return
        }

        // Show loading states
        binding.progressWellness.visibility = View.VISIBLE
        binding.txtWellnessTitle.text = getString(R.string.loading)
        binding.txtWellnessDescription.visibility = View.GONE
        binding.layoutSongsLoading.visibility = View.VISIBLE
        binding.recyclerSongs.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE

        // Load most recent journal entry
        UserStore.loadJournalEntries(
            onSuccess = { entries ->
                if (entries.isEmpty()) {
                    showEmptyState()
                    return@loadJournalEntries
                }

                // Get most recent entry
                val mostRecent = entries.first()
                currentJournalId = mostRecent.id

                // Check cache validity
                if (!forceRefresh && RecommendationsCache.isCacheValid(mostRecent.id)) {
                    // Use cached data
                    Log.d(TAG, "Using cached recommendations")
                    displayCachedData()
                } else {
                    // Fetch fresh recommendations
                    Log.d(TAG, "Fetching fresh recommendations")
                    processJournalEntry(mostRecent.id, mostRecent.text)
                }
            },
            onFailure = { error ->
                Log.e(TAG, "Failed to load journal entries: $error")
                showEmptyState()
            }
        )
    }

    private fun displayCachedData() {
        activity?.runOnUiThread {
            // Load cached emotion data for generating friendly messages
            currentEmotion = RecommendationsCache.getEmotion() ?: ""
            currentSentiment = RecommendationsCache.getSentiment() ?: ""

            // Display cached wellness
            val wellnessTitle = RecommendationsCache.getWellnessTitle()
            val wellnessDescription = RecommendationsCache.getWellnessDescription()
            val wellnessDetailedExplanation = RecommendationsCache.getWellnessDetailedExplanation()
            val wellnessYoutubeUrl = RecommendationsCache.getWellnessYoutubeUrl()
            val wellnessReasoning = RecommendationsCache.getWellnessReasoning()

            binding.progressWellness.visibility = View.GONE
            if (!wellnessTitle.isNullOrEmpty()) {
                updateWellnessUI(
                    title = wellnessTitle,
                    description = wellnessDescription ?: "",
                    detailedExplanation = wellnessDetailedExplanation,
                    youtubeUrl = wellnessYoutubeUrl,
                    reasoning = wellnessReasoning
                )
            } else {
                binding.txtWellnessTitle.text = getString(R.string.no_recommendations)
            }

            // Display cached songs
            val cachedSongs = RecommendationsCache.getSongs()
            val songsReasoning = RecommendationsCache.getSongsReasoning()
            binding.layoutSongsLoading.visibility = View.GONE

            if (cachedSongs != null && cachedSongs.isNotEmpty()) {
                binding.recyclerSongs.visibility = View.VISIBLE
                binding.txtMusicHeader.visibility = View.VISIBLE
                songAdapter.updateSongs(cachedSongs)
                updateMusicReasoning(songsReasoning)
            } else {
                binding.txtMusicHeader.visibility = View.GONE
                binding.txtMusicReasoning.visibility = View.GONE
            }
        }
    }

    private fun processJournalEntry(journalId: String, journalText: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Analyze emotion and sentiment
                val emotionResult = emotionPredictor.predictEmotion(journalText)
                val sentimentResult = sentimentPredictor.predictSentiment(journalText)

                if (emotionResult == null || sentimentResult == null) {
                    showError()
                    return@launch
                }

                // Store emotion and sentiment for generating friendly messages
                currentEmotion = emotionResult.emotion.lowercase()
                currentSentiment = sentimentResult.sentiment.lowercase()

                // Cache emotion data for later use
                RecommendationsCache.saveEmotionData(currentEmotion, currentSentiment)

                // Create emotion data for recommendation engine
                val emotionData = RecommendationEngine.createEmotionData(
                    emotion = emotionResult.emotion,
                    emotionScore = emotionResult.confidence,
                    sentiment = sentimentResult.sentiment,
                    sentimentScore = sentimentResult.confidence
                )

                // Single API call for both wellness and songs (faster!)
                loadCombinedRecommendations(journalId, journalText, emotionData)

            } catch (e: Exception) {
                Log.e(TAG, "Error processing journal entry", e)
                showError()
            }
        }
    }

    private suspend fun loadCombinedRecommendations(
        journalId: String,
        journalText: String,
        emotionData: RecommendationEngine.EmotionData
    ) {
        try {
            val combined = recommendationEngine.getCombinedRecommendations(journalText, emotionData)

            if (combined == null) {
                activity?.runOnUiThread {
                    showError()
                }
                return
            }

            // Process wellness
            val wellness = combined.wellness

            // Process songs - search Spotify for album art
            val spotifyTracks = mutableListOf<SpotifyIntegration.SpotifyTrack>()
            for (song in combined.songs) {
                val track = spotifyIntegration.searchTrack(song.title, song.artist)
                if (track != null) {
                    spotifyTracks.add(track)
                }
            }

            activity?.runOnUiThread {
                // Update wellness UI
                binding.progressWellness.visibility = View.GONE
                if (wellness != null) {
                    updateWellnessUI(
                        title = wellness.name,
                        description = wellness.description,
                        detailedExplanation = wellness.detailedExplanation,
                        youtubeUrl = wellness.youtubeUrl,
                        reasoning = null
                    )

                    // Cache wellness
                    RecommendationsCache.saveWellness(
                        journalId,
                        wellness.name,
                        wellness.description,
                        wellness.detailedExplanation,
                        wellness.youtubeUrl,
                        ""
                    )
                } else {
                    binding.txtWellnessTitle.text = getString(R.string.no_recommendations)
                }

                // Update songs UI
                binding.layoutSongsLoading.visibility = View.GONE
                if (spotifyTracks.isNotEmpty()) {
                    binding.recyclerSongs.visibility = View.VISIBLE
                    binding.txtMusicHeader.visibility = View.VISIBLE
                    songAdapter.updateSongs(spotifyTracks)
                    updateMusicReasoning(null)

                    // Cache songs
                    RecommendationsCache.saveSongs(spotifyTracks, "")
                } else {
                    binding.txtMusicHeader.visibility = View.GONE
                    binding.txtMusicReasoning.visibility = View.GONE
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading combined recommendations", e)
            activity?.runOnUiThread {
                showError()
            }
        }
    }

    private suspend fun loadWellnessSuggestion(
        journalId: String,
        journalText: String,
        emotionData: RecommendationEngine.EmotionData
    ) {
        try {
            val wellness = recommendationEngine.getWellnessSuggestions(journalText, emotionData)

            activity?.runOnUiThread {
                binding.progressWellness.visibility = View.GONE
                
                if (wellness != null && wellness.techniques.isNotEmpty()) {
                    val technique = wellness.techniques.first()
                    val reasoning = wellness.reasoning

                    updateWellnessUI(
                        title = technique.name,
                        description = technique.description,
                        detailedExplanation = technique.detailedExplanation,
                        youtubeUrl = technique.youtubeUrl,
                        reasoning = reasoning
                    )

                    // Cache the wellness recommendation with new fields
                    RecommendationsCache.saveWellness(
                        journalId,
                        technique.name,
                        technique.description,
                        technique.detailedExplanation,
                        technique.youtubeUrl,
                        reasoning
                    )
                } else {
                    binding.txtWellnessTitle.text = getString(R.string.no_recommendations)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading wellness suggestion", e)
            activity?.runOnUiThread {
                binding.progressWellness.visibility = View.GONE
                binding.txtWellnessTitle.text = getString(R.string.no_recommendations)
                            }
        }
    }

    private suspend fun loadSongRecommendations(
        journalText: String,
        emotionData: RecommendationEngine.EmotionData
    ) {
        try {
            // Get song recommendations from Gemini
            val songRecommendation = recommendationEngine.getSongRecommendations(journalText, emotionData)

            if (songRecommendation == null || songRecommendation.songs.isEmpty()) {
                activity?.runOnUiThread {
                    binding.layoutSongsLoading.visibility = View.GONE
                    binding.txtMusicHeader.visibility = View.GONE
                                    }
                return
            }

            // Search for each song on Spotify to get album art
            val spotifyTracks = mutableListOf<SpotifyIntegration.SpotifyTrack>()
            for (song in songRecommendation.songs) {
                val track = spotifyIntegration.searchTrack(song.title, song.artist)
                if (track != null) {
                    spotifyTracks.add(track)
                }
            }

            val reasoning = songRecommendation.reasoning

            activity?.runOnUiThread {
                binding.layoutSongsLoading.visibility = View.GONE
                
                if (spotifyTracks.isNotEmpty()) {
                    binding.recyclerSongs.visibility = View.VISIBLE
                    binding.txtMusicHeader.visibility = View.VISIBLE
                    songAdapter.updateSongs(spotifyTracks)
                    updateMusicReasoning(reasoning)

                    // Cache the songs with reasoning
                    RecommendationsCache.saveSongs(spotifyTracks, reasoning)
                } else {
                    binding.txtMusicHeader.visibility = View.GONE
                    binding.txtMusicReasoning.visibility = View.GONE
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading song recommendations", e)
            activity?.runOnUiThread {
                binding.layoutSongsLoading.visibility = View.GONE
                binding.txtMusicHeader.visibility = View.GONE
                            }
        }
    }

    private fun showEmptyState() {
        activity?.runOnUiThread {
            binding.progressWellness.visibility = View.GONE
            binding.txtWellnessTitle.text = getString(R.string.no_journal_for_recommendations)
            binding.layoutSongsLoading.visibility = View.GONE
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.txtMusicHeader.visibility = View.GONE
        }
    }

    private fun showError() {
        activity?.runOnUiThread {
            binding.progressWellness.visibility = View.GONE
            binding.txtWellnessTitle.text = getString(R.string.no_recommendations)
            binding.layoutSongsLoading.visibility = View.GONE
        }
    }

    private var currentWellnessReasoning: String? = null
    private var currentMusicReasoning: String? = null

    private fun toggleWellnessExpansion() {
        isWellnessExpanded = !isWellnessExpanded

        if (isWellnessExpanded) {
            binding.layoutWellnessDetails.visibility = View.VISIBLE
            binding.txtTapToExpand.text = "Tap to collapse"
        } else {
            binding.layoutWellnessDetails.visibility = View.GONE
            binding.txtTapToExpand.text = "Tap to learn more"
        }
    }

    private fun updateWellnessUI(
        title: String,
        description: String,
        detailedExplanation: String?,
        youtubeUrl: String?,
        reasoning: String? = null
    ) {
        binding.txtWellnessTitle.text = title
        binding.txtWellnessDescription.text = description
        binding.txtWellnessDescription.visibility = View.VISIBLE

        // Generate and show friendly reasoning immediately (not on expand)
        val friendlyReasoning = generateFriendlyWellnessReasoning(title)
        currentWellnessReasoning = friendlyReasoning
        binding.txtWellnessReasoning.text = friendlyReasoning
        binding.txtWellnessReasoning.visibility = View.VISIBLE

        // Set up detailed explanation
        if (!detailedExplanation.isNullOrEmpty()) {
            binding.txtWellnessDetailedExplanation.text = detailedExplanation
            binding.txtTapToExpand.visibility = View.VISIBLE
        } else {
            binding.txtTapToExpand.visibility = View.GONE
        }

        // Set up YouTube button
        currentYoutubeUrl = youtubeUrl
        if (!youtubeUrl.isNullOrEmpty()) {
            binding.btnWatchVideo.visibility = View.VISIBLE
        } else {
            binding.btnWatchVideo.visibility = View.GONE
        }

        // Reset expansion state
        isWellnessExpanded = false
        binding.layoutWellnessDetails.visibility = View.GONE
    }

    private fun updateMusicReasoning(reasoning: String?) {
        currentMusicReasoning = reasoning
        // Show friendly music reasoning
        val friendlyReasoning = generateFriendlyMusicReasoning()
        binding.txtMusicReasoning.text = friendlyReasoning
        binding.txtMusicReasoning.visibility = View.VISIBLE
    }

    /**
     * Generate a sweet, concise 1-2 line message for why the wellness tip was chosen
     */
    private fun generateFriendlyWellnessReasoning(techniqueName: String): String {
        return when {
            currentEmotion.contains("sad") || currentSentiment.contains("negative") ->
                "We picked this to help lift your spirits and bring some calm to your day."
            currentEmotion.contains("anxious") || currentEmotion.contains("fear") ->
                "This technique can help ease your mind and find your center."
            currentEmotion.contains("angry") || currentEmotion.contains("anger") ->
                "We thought this might help you release some tension and feel more at peace."
            currentEmotion.contains("joy") || currentSentiment.contains("positive") ->
                "A great way to maintain your positive energy and keep the good vibes flowing!"
            currentEmotion.contains("surprise") ->
                "This can help you process your thoughts and stay grounded."
            else ->
                "We think this could be a nice moment of self-care for you today."
        }
    }

    /**
     * Generate a sweet, concise 1-2 line message for why these songs were chosen
     */
    private fun generateFriendlyMusicReasoning(): String {
        return when {
            currentEmotion.contains("sad") || currentSentiment.contains("negative") ->
                "We hope these songs bring you comfort and remind you that brighter days are ahead."
            currentEmotion.contains("anxious") || currentEmotion.contains("fear") ->
                "These calming tunes might help soothe your mind and ease some of that tension."
            currentEmotion.contains("angry") || currentEmotion.contains("anger") ->
                "Sometimes music helps us release what we're holding onto. We hope these help."
            currentEmotion.contains("joy") || currentSentiment.contains("positive") ->
                "Keep the good vibes going! These songs match your upbeat energy."
            currentEmotion.contains("surprise") ->
                "A mix of tunes to help you process and enjoy the moment."
            else ->
                "We curated these songs just for you. Hope you enjoy!"
        }
    }
}
