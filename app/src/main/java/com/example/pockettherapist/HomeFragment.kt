package com.example.pockettherapist

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pockettherapist.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


class HomeFragment : Fragment() {

    private lateinit var binding: FragmentHomeBinding
    private lateinit var adapter: JournalAdapter
    private lateinit var recommendationEngine: RecommendationEngine
    private var audioHelper: AudioTextHelper? = null

    private val micPermission = Manifest.permission.RECORD_AUDIO
    private val micRequestCode = 101

    private var journalEntries = mutableListOf<JournalEntry>()
    private var isOverlayVisible = false
    private var editingEntry: JournalEntry? = null  // Track if we're editing an existing entry
    private var selectedMood: String = ""  // Track selected mood emoji

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val username = UserStore.getCurrentUsername()  // IMPORTANT

        // SHOW ONBOARDING ONE TIME FOR THIS USER ONLY
        if (FirstTimeStore.isFirstTime(requireContext(), username)) {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, OnboardingFragment.newInstance(username))
                .commit()
            return
        }


        recommendationEngine = RecommendationEngine(requireContext())

        // Check if user needs to see AI consent (for users who completed onboarding before this feature)
        checkAndShowAIConsentIfNeeded()

        checkMicPermission()
        setupAudioHelper()
        setupRecyclerView()
        setupFab()
        setupOverlay()
        setupMoodSelector()
        loadJournalEntries()
    }

    private fun checkAndShowAIConsentIfNeeded() {
        AIConsentManager.init(requireContext())

        // Show consent dialog if user hasn't made a decision yet
        if (!AIConsentManager.hasUserMadeConsentDecision()) {
            AIConsentManager.showConsentDialog(
                context = requireActivity(),
                onConsent = {
                    // User enabled AI - nothing extra to do
                },
                onDecline = {
                    // User declined - nothing extra to do
                }
            )
        }
    }

    private fun setupMoodSelector() {
        val moods = listOf(
            binding.btnMoodHappy to "😊",
            binding.btnMoodSad to "😢",
            binding.btnMoodAngry to "😤",
            binding.btnMoodAnxious to "😰",
            binding.btnMoodCalm to "😌"
        )

        moods.forEach { (button, emoji) ->
            button.setOnClickListener {
                // Toggle selection
                if (selectedMood == emoji) {
                    selectedMood = ""
                    button.alpha = 1.0f
                } else {
                    selectedMood = emoji
                    // Reset all alphas
                    moods.forEach { (btn, _) -> btn.alpha = 0.4f }
                    button.alpha = 1.0f
                }
            }
        }
    }

    private fun resetMoodSelector() {
        selectedMood = ""
        listOf(
            binding.btnMoodHappy,
            binding.btnMoodSad,
            binding.btnMoodAngry,
            binding.btnMoodAnxious,
            binding.btnMoodCalm
        ).forEach { it.alpha = 1.0f }
    }

    private fun checkMicPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), micPermission)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(micPermission),
                micRequestCode
            )
        }
    }

    private fun setupAudioHelper() {
        audioHelper = AudioTextHelper(
            requireContext(),
            onResult = { text ->
                // Check which field has focus and append to that one
                val targetField = if (binding.etEntryTitle.hasFocus()) {
                    binding.etEntryTitle
                } else {
                    binding.etNewEntry
                }
                val current = targetField.text.toString()
                val appended = if (current.isEmpty()) text else "$current $text"
                targetField.setText(appended)
                targetField.setSelection(appended.length)
            },
            onError = { msg ->
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun setupRecyclerView() {
        adapter = JournalAdapter(
            onEditClick = { entry -> showEditDialog(entry) },
            onDeleteClick = { entry -> showDeleteConfirmation(entry) }
        )
        binding.journalRecycler.adapter = adapter
        binding.journalRecycler.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupFab() {
        binding.fabContainer.setOnClickListener {
            if (!isOverlayVisible) {
                showOverlay()
            }
        }

        // Hold-to-speak when overlay is visible (FAB becomes mic)
        binding.fabContainer.setOnTouchListener { _, event ->
            if (isOverlayVisible) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Gray background, blue icon when pressed
                        setFabColor(R.color.dark_gray)
                        binding.fabIcon.setColorFilter(
                            ContextCompat.getColor(requireContext(), R.color.accent)
                        )
                        audioHelper?.startListening()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // Return to accent background, white icon
                        setFabColor(R.color.accent)
                        binding.fabIcon.setColorFilter(
                            ContextCompat.getColor(requireContext(), R.color.white)
                        )
                        audioHelper?.stopListening()
                        true
                        }
                    else -> false
                }
            } else {
                false // Let onClick handle it
            }
        }
    }

    private fun setFabColor(colorRes: Int) {
        val drawable = binding.fabContainer.background as? GradientDrawable
        drawable?.setColor(ContextCompat.getColor(requireContext(), colorRes))
    }

    private fun setupOverlay() {
        // Clicking dim overlay closes and saves
        binding.dimOverlay.setOnClickListener {
            if (isOverlayVisible) {
                hideOverlayAndSave()
            }
        }
    }

    private fun showOverlay() {
        isOverlayVisible = true
        binding.dimOverlay.visibility = View.VISIBLE

        // Set height to 70% of screen
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val params = binding.entryOverlay.layoutParams
        params.height = (screenHeight * 0.70).toInt()
        binding.entryOverlay.layoutParams = params

        binding.entryOverlay.visibility = View.VISIBLE

        // Animate plus to mic with full rotation (360 so icon stays upright)
        animateFabIcon(R.drawable.ic_mic)

        binding.etNewEntry.requestFocus()
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etNewEntry, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun animateFabIcon(newIconRes: Int) {
        // Scale down, change icon, scale back up
        val scaleDownX = ObjectAnimator.ofFloat(binding.fabIcon, "scaleX", 1f, 0f)
        val scaleDownY = ObjectAnimator.ofFloat(binding.fabIcon, "scaleY", 1f, 0f)
        val rotate = ObjectAnimator.ofFloat(binding.fabIcon, "rotation", 0f, 90f)

        val scaleDown = AnimatorSet()
        scaleDown.playTogether(scaleDownX, scaleDownY, rotate)
        scaleDown.duration = 150
        scaleDown.interpolator = AccelerateDecelerateInterpolator()

        val scaleUpX = ObjectAnimator.ofFloat(binding.fabIcon, "scaleX", 0f, 1f)
        val scaleUpY = ObjectAnimator.ofFloat(binding.fabIcon, "scaleY", 0f, 1f)
        val rotateBack = ObjectAnimator.ofFloat(binding.fabIcon, "rotation", 90f, 0f)

        val scaleUp = AnimatorSet()
        scaleUp.playTogether(scaleUpX, scaleUpY, rotateBack)
        scaleUp.duration = 150
        scaleUp.interpolator = AccelerateDecelerateInterpolator()

        scaleDown.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                binding.fabIcon.setImageResource(newIconRes)
            }
        })

        val animatorSet = AnimatorSet()
        animatorSet.playSequentially(scaleDown, scaleUp)
        animatorSet.start()
    }

    private fun hideOverlayAndSave() {
        val title = binding.etEntryTitle.text.toString().trim()
        val text = binding.etNewEntry.text.toString().trim()
        val mood = selectedMood

        // Hide keyboard
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etNewEntry.windowToken, 0)

        // Hide overlay
        isOverlayVisible = false
        binding.dimOverlay.visibility = View.GONE
        binding.entryOverlay.visibility = View.GONE
        binding.etEntryTitle.setText("")
        binding.etNewEntry.setText("")
        resetMoodSelector()

        // Animate mic back to plus
        animateFabIcon(R.drawable.ic_plus)

        // Check for crisis language IMMEDIATELY before saving (check both title and text)
        val hasCrisisLanguage = CrisisDetector.containsCrisisLanguage(title) ||
                                CrisisDetector.containsCrisisLanguage(text)

        // Save if there's text
        if (text.isNotEmpty()) {
            val entryBeingEdited = editingEntry
            if (entryBeingEdited != null) {
                // Update existing entry
                updateJournalEntry(entryBeingEdited.id, title, text, mood, hasCrisisLanguage)
            } else {
                // Create new entry
                saveJournalEntry(title, text, mood, hasCrisisLanguage)
            }
        }

        // Clear editing state
        editingEntry = null
    }

    private fun loadJournalEntries() {
        UserStore.loadJournalEntries(
            onSuccess = { entries ->
                journalEntries = entries.toMutableList()
                updateList()
            },
            onFailure = { error ->
                Toast.makeText(requireContext(), "Failed to load entries: $error", Toast.LENGTH_SHORT).show()
                updateList()
            }
        )
    }

    private fun saveJournalEntry(title: String, text: String, mood: String, hasCrisisLanguage: Boolean) {
        // Show crisis alert IMMEDIATELY if crisis language detected
        if (hasCrisisLanguage) {
            CrisisDetector.showCrisisAlert(requireActivity())
        }

        UserStore.saveJournalEntry(
            title = title,
            text = text,
            mood = mood,
            onSuccess = { entry ->
                journalEntries.add(0, entry)
                updateList()

                // Only show companion response if NO crisis language
                if (!hasCrisisLanguage) {
                    // Check for AI consent before showing companion response
                    AIConsentManager.requireConsentForAI(
                        context = requireContext(),
                        onAllowed = {
                            // User has consented, show AI companion response
                            showCompanionResponseDialog(text, mood)
                        },
                        onDenied = {
                            // User declined AI, just show simple toast
                            Toast.makeText(requireContext(), "Entry saved!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            },
            onFailure = { error ->
                Toast.makeText(requireContext(), "Failed to save: $error", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showCompanionResponseDialog(journalText: String, mood: String) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_companion_response, null)

        val txtResponse = dialogView.findViewById<TextView>(R.id.txtCompanionResponse)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressCompanion)
        val txtPrompt = dialogView.findViewById<TextView>(R.id.txtRecommendationsPrompt)
        val btnRecommendations = dialogView.findViewById<Button>(R.id.btnViewRecommendations)
        val btnDismiss = dialogView.findViewById<Button>(R.id.btnDismissCompanion)

        val dialog = AlertDialog.Builder(requireContext(), R.style.Theme_PocketTherapist_AlertDialog)
            .setView(dialogView)
            .setCancelable(false)  // Prevent dismissing while loading
            .create()

        // Hide dismiss button while loading
        btnDismiss.visibility = View.GONE

        // Fetch AI response
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = recommendationEngine.getCompanionResponse(journalText, mood)

                progressBar.visibility = View.GONE
                btnDismiss.visibility = View.VISIBLE
                dialog.setCancelable(true)  // Allow dismissing after loaded

                if (response != null) {
                    txtResponse.text = response
                    txtPrompt.visibility = View.VISIBLE
                    btnRecommendations.visibility = View.VISIBLE
                } else {
                    txtResponse.text = "Entry saved successfully!"
                    txtPrompt.visibility = View.VISIBLE
                    btnRecommendations.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                btnDismiss.visibility = View.VISIBLE
                dialog.setCancelable(true)
                txtResponse.text = "Entry saved successfully!"
                txtPrompt.visibility = View.VISIBLE
                btnRecommendations.visibility = View.VISIBLE
            }
        }

        btnRecommendations.setOnClickListener {
            dialog.dismiss()
            // Navigate to recommendations tab
            navigateToRecommendations()
        }

        btnDismiss.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun navigateToRecommendations() {
        // Find the MainActivity and switch to recommendations tab
        val activity = requireActivity()
        if (activity is MainActivity) {
            activity.navigateToTab(R.id.nav_recommendations)
        }
    }

    private fun updateList() {
        val items = buildListItems(journalEntries)
        adapter.submitList(items)
    }

    private fun buildListItems(entries: List<JournalEntry>): List<JournalListItem> {
        val items = mutableListOf<JournalListItem>()

        if (entries.isEmpty()) {
            return items
        }

        // Group entries by date
        val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

        var currentDateString = ""

        for (entry in entries) {
            val entryDate = Date(entry.timestamp)
            val entryCalendar = Calendar.getInstance().apply { time = entryDate }

            val dateString = when {
                isSameDay(entryCalendar, today) -> "Today"
                isSameDay(entryCalendar, yesterday) -> "Yesterday"
                else -> dateFormat.format(entryDate)
            }

            if (dateString != currentDateString) {
                currentDateString = dateString
                items.add(JournalListItem.DateHeader(dateString))
            }

            items.add(JournalListItem.Entry(entry))
        }

        return items
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    override fun onPause() {
        super.onPause()
        // Auto-save when leaving the fragment
        if (isOverlayVisible) {
            hideOverlayAndSave()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        audioHelper?.destroy()
        audioHelper = null
    }

    private fun showEditDialog(entry: JournalEntry) {
        // Set editing state and open the overlay with existing data
        editingEntry = entry
        showOverlay()
        binding.etEntryTitle.setText(entry.title)
        binding.etNewEntry.setText(entry.text)
        binding.etNewEntry.setSelection(entry.text.length)

        // Set mood if available
        if (entry.mood.isNotEmpty()) {
            selectedMood = entry.mood
            val moods = mapOf(
                "😊" to binding.btnMoodHappy,
                "😢" to binding.btnMoodSad,
                "😤" to binding.btnMoodAngry,
                "😰" to binding.btnMoodAnxious,
                "😌" to binding.btnMoodCalm
            )
            // Dim all, highlight selected
            moods.values.forEach { it.alpha = 0.4f }
            moods[entry.mood]?.alpha = 1.0f
        }
    }

    private fun updateJournalEntry(entryId: String, newTitle: String, newText: String, newMood: String, hasCrisisLanguage: Boolean) {
        // Show crisis alert IMMEDIATELY if crisis language detected
        if (hasCrisisLanguage) {
            CrisisDetector.showCrisisAlert(requireActivity())
        }

        UserStore.updateJournalEntry(
            entryId = entryId,
            newTitle = newTitle,
            newText = newText,
            newMood = newMood,
            onSuccess = { updatedEntry ->
                // Update local list
                val index = journalEntries.indexOfFirst { it.id == entryId }
                if (index != -1) {
                    journalEntries[index] = updatedEntry
                    updateList()
                }
                Toast.makeText(requireContext(), "Entry updated", Toast.LENGTH_SHORT).show()
            },
            onFailure = { error ->
                Toast.makeText(requireContext(), "Failed to update: $error", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showDeleteConfirmation(entry: JournalEntry) {
        AlertDialog.Builder(requireContext(), R.style.Theme_PocketTherapist_AlertDialog)
            .setTitle("Delete Entry")
            .setMessage("Are you sure you want to delete this journal entry? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                UserStore.deleteJournalEntry(
                    entryId = entry.id,
                    onSuccess = {
                        journalEntries.removeAll { it.id == entry.id }
                        updateList()
                        Toast.makeText(requireContext(), "Entry deleted", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { error ->
                        Toast.makeText(requireContext(), "Failed to delete: $error", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
