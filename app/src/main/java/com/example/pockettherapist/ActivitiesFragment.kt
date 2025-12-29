package com.example.pockettherapist

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pockettherapist.adapters.AmenityAdapter
import com.example.pockettherapist.adapters.TMEventAdapter
import com.example.pockettherapist.databinding.FragmentActivitiesBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class ActivitiesFragment : Fragment(), SensorEventListener {

    private lateinit var binding: FragmentActivitiesBinding
    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null
    private lateinit var recommendationEngine: RecommendationEngine
    private val gson = Gson()

    private var initialSteps = -1f
    private var currentSteps = 0
    private var stepGoal = DEFAULT_STEP_GOAL

    // Activity tracker with Kalman filter sensor fusion
    private var activityTracker: ActivityTracker? = null
    private var lastCalorieUpdateTime = 0L
    private var calorieGoal = DEFAULT_CALORIE_GOAL
    private var activityGoal = DEFAULT_ACTIVITY_GOAL

    // Cached data
    private var cachedAmenities: List<RecommendationEngine.ResourceDetail>? = null
    private var cachedEvents: List<RecommendationEngine.EventDetail>? = null

    private val activityPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            setupStepSensor()
        } else {
            binding.txtStepCount.text = "—"
            binding.txtProgressPercent.text = "Permission required for step counting"
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startBackgroundTracking()
        }
        // Silently continue even if denied - foreground tracking still works
    }

    companion object {
        private const val PREFS_NAME = "step_counter_prefs"
        private const val KEY_STEP_GOAL = "step_goal"
        private const val KEY_INITIAL_STEPS = "initial_steps"
        private const val KEY_LAST_SAVE_DATE = "last_save_date"
        private const val DEFAULT_STEP_GOAL = 7000

        private const val KEY_CALORIE_GOAL = "calorie_goal"
        private const val DEFAULT_CALORIE_GOAL = 500

        private const val KEY_ACTIVITY_GOAL = "activity_goal"
        private const val DEFAULT_ACTIVITY_GOAL = 30 // 30 minutes of moderate activity

        private const val CACHE_PREFS_NAME = "activities_cache"
        private const val KEY_CACHED_AMENITIES = "cached_amenities"
        private const val KEY_CACHED_EVENTS = "cached_events"
        private const val KEY_CACHE_LOCATION = "cache_location"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentActivitiesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize recommendation engine
        recommendationEngine = RecommendationEngine(requireContext())

        // Setup horizontal RecyclerViews
        binding.recyclerEvents.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerAmenities.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        // Setup SwipeRefreshLayout
        binding.swipeRefresh.setColorSchemeResources(R.color.accent_purple)
        binding.swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.dark_card)
        binding.swipeRefresh.setOnRefreshListener {
            refreshData()
        }

        // Step counter setup
        loadStepGoal()
        setupEditGoalButton()
        updateStepUI()
        checkActivityPermissionAndSetupSensor()

        // Activity tracker setup (Kalman filter sensor fusion)
        setupActivityTracker()

        // Load from cache first
        loadCachedData()
    }

    // -------------------------------------------------
    // ACTIVITY TRACKER (Kalman Filter Sensor Fusion)
    // -------------------------------------------------

    private fun setupActivityTracker() {
        activityTracker = ActivityTracker(requireContext())

        // Set user profile if available (for calorie calculation)
        // Default values used if not set
        activityTracker?.setUserProfile(
            weightKg = 70f,
            age = 30,
            isMale = true
        )

        activityTracker?.onActivityUpdate = { data ->
            activity?.runOnUiThread {
                updateActivityUI(data)
            }
        }

        lastCalorieUpdateTime = System.currentTimeMillis()

        // Load calorie goal
        loadCalorieGoal()

        // Setup calorie goal edit button
        setupEditCalorieGoalButton()

        // Load activity goal
        loadActivityGoal()

        // Setup activity goal edit button
        setupEditActivityGoalButton()

        // Auto-start background tracking
        autoStartBackgroundTracking()

        // Load daily data
        loadDailyData()
    }

    private fun autoStartBackgroundTracking() {
        // Always start background tracking automatically
        if (!ActivityTrackingService.isRunning(requireContext())) {
            checkNotificationPermissionAndStart()
        }
    }

    private fun loadCalorieGoal() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        calorieGoal = prefs.getInt(KEY_CALORIE_GOAL, DEFAULT_CALORIE_GOAL)
        updateCalorieGoalUI()
    }

    private fun saveCalorieGoal(goal: Int) {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_CALORIE_GOAL, goal).apply()
    }

    private fun updateCalorieGoalUI() {
        binding.txtCalorieGoal.text = "/ $calorieGoal cal"
    }

    private fun setupEditCalorieGoalButton() {
        binding.btnEditCalorieGoal.setOnClickListener {
            showEditCalorieGoalDialog()
        }
    }

    private fun showEditCalorieGoalDialog() {
        val editText = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Enter calorie goal"
            setText(calorieGoal.toString())
            selectAll()
        }

        val padding = (24 * resources.displayMetrics.density).toInt()
        editText.setPadding(padding, padding, padding, padding)

        AlertDialog.Builder(requireContext())
            .setTitle("Set Daily Calorie Goal")
            .setView(editText)
            .setPositiveButton(R.string.save) { _, _ ->
                val newGoal = editText.text.toString().toIntOrNull()
                if (newGoal != null && newGoal > 0) {
                    calorieGoal = newGoal
                    saveCalorieGoal(newGoal)
                    updateCalorieGoalUI()
                    loadDailyData() // Refresh to update progress
                } else {
                    Toast.makeText(requireContext(), "Please enter a valid number", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // -------------------------------------------------
    // ACTIVITY GOAL (Daily cumulative goal)
    // -------------------------------------------------

    private fun loadActivityGoal() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        activityGoal = prefs.getInt(KEY_ACTIVITY_GOAL, DEFAULT_ACTIVITY_GOAL)
    }

    private fun saveActivityGoal(goal: Int) {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_ACTIVITY_GOAL, goal).apply()
    }

    private fun setupEditActivityGoalButton() {
        binding.btnEditActivityGoal.setOnClickListener {
            showEditActivityGoalDialog()
        }
    }

    private fun showEditActivityGoalDialog() {
        val editText = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Enter activity goal (minutes)"
            setText(activityGoal.toString())
            selectAll()
        }

        val padding = (24 * resources.displayMetrics.density).toInt()
        editText.setPadding(padding, padding, padding, padding)

        AlertDialog.Builder(requireContext())
            .setTitle("Set Daily Activity Goal")
            .setMessage("Recommended: ${DailyActivityStore.getRecommendedActivityGoal(30)} minutes of moderate activity per day")
            .setView(editText)
            .setPositiveButton(R.string.save) { _, _ ->
                val newGoal = editText.text.toString().toIntOrNull()
                if (newGoal != null && newGoal > 0) {
                    activityGoal = newGoal
                    saveActivityGoal(newGoal)
                    loadDailyData() // Refresh to update progress
                } else {
                    Toast.makeText(requireContext(), "Please enter a valid number", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun checkNotificationPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    startBackgroundTracking()
                }
                else -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            startBackgroundTracking()
        }
    }

    private fun startBackgroundTracking() {
        ActivityTrackingService.start(requireContext())
    }

    private fun loadDailyData() {
        val dailyData = DailyActivityStore.getTodayData(requireContext())

        // Only show actively tracked calories
        val displayCalories = dailyData.totalCalories

        // Update calorie display
        binding.txtCaloriesBurned.text = String.format("%.0f", displayCalories)

        // Update calorie progress bar
        val caloriePercent = if (calorieGoal > 0) {
            ((displayCalories / calorieGoal) * 100).coerceAtMost(100f).toInt()
        } else {
            0
        }
        binding.progressCalories.progress = caloriePercent
        binding.txtCaloriePercent.text = "$caloriePercent% of daily goal"

        // Update activity progress (cumulative daily goal)
        updateActivityProgress(dailyData)

        // If we have steps from daily store, use them
        if (dailyData.totalSteps > 0) {
            currentSteps = dailyData.totalSteps
            updateStepUI()
        }
    }

    private fun updateActivityProgress(dailyData: DailyActivityStore.DailyActivityData) {
        // Calculate activity score (equivalent moderate minutes)
        val activityScore = dailyData.calculateActivityScore()

        // Calculate percentage toward goal
        val activityPercent = if (activityGoal > 0) {
            ((activityScore / activityGoal) * 100).coerceAtMost(100f).toInt()
        } else {
            0
        }

        // Update UI
        binding.txtActivityPercent.text = activityPercent.toString()
        binding.progressActivity.progress = activityPercent

        // Show active minutes vs goal
        val activeMinutes = dailyData.getTotalActiveMinutes()
        binding.txtActivityMinutes.text = "$activeMinutes / $activityGoal active minutes"
    }

    private fun updateActivityUI(data: ActivityTracker.ActivityData) {
        // Record activity sample to daily store (only if there's actual activity)
        val currentTime = System.currentTimeMillis()
        val elapsedSeconds = (currentTime - lastCalorieUpdateTime) / 1000f

        if (elapsedSeconds >= 1f) {
            // Only record calories and activity when there's actual movement (not resting)
            if (data.activityLevel > 0.1f) {
                DailyActivityStore.recordActivitySample(
                    context = requireContext(),
                    activityLevel = data.activityLevel,
                    activityState = data.activityState,
                    caloriesPerMinute = data.caloriesPerMinute,
                    durationSeconds = elapsedSeconds
                )
            }
            lastCalorieUpdateTime = currentTime

            // Update displays from daily store
            val dailyData = DailyActivityStore.getTodayData(requireContext())

            // Update calorie display (active calories only)
            binding.txtCaloriesBurned.text = String.format("%.0f", dailyData.totalCalories)

            // Update calorie progress
            val caloriePercent = if (calorieGoal > 0) {
                ((dailyData.totalCalories / calorieGoal) * 100).coerceAtMost(100f).toInt()
            } else {
                0
            }
            binding.progressCalories.progress = caloriePercent
            binding.txtCaloriePercent.text = "$caloriePercent% of daily goal"

            // Update activity progress (cumulative daily goal)
            updateActivityProgress(dailyData)
        }
    }

    // -------------------------------------------------
    // CACHING
    // -------------------------------------------------

    private fun loadCachedData() {
        val prefs = requireContext().getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE)

        // Load cached events
        val cachedEventsJson = prefs.getString(KEY_CACHED_EVENTS, null)
        if (cachedEventsJson != null) {
            try {
                val type = object : TypeToken<List<RecommendationEngine.EventDetail>>() {}.type
                cachedEvents = gson.fromJson(cachedEventsJson, type)
                if (cachedEvents != null && cachedEvents!!.isNotEmpty()) {
                    displayEvents(cachedEvents!!)
                }
            } catch (e: Exception) {
                android.util.Log.e("ActivitiesFragment", "Error parsing cached events", e)
            }
        }

        // Load cached amenities
        val cachedAmenitiesJson = prefs.getString(KEY_CACHED_AMENITIES, null)
        if (cachedAmenitiesJson != null) {
            try {
                val type = object : TypeToken<List<RecommendationEngine.ResourceDetail>>() {}.type
                cachedAmenities = gson.fromJson(cachedAmenitiesJson, type)
                if (cachedAmenities != null && cachedAmenities!!.isNotEmpty()) {
                    displayAmenities(cachedAmenities!!)
                }
            } catch (e: Exception) {
                android.util.Log.e("ActivitiesFragment", "Error parsing cached amenities", e)
            }
        }

        // If no cache, fetch fresh data
        if (cachedEvents == null && cachedAmenities == null) {
            checkLocationPermissionAndLoad()
        }
    }

    private fun saveCache(events: List<RecommendationEngine.EventDetail>?, amenities: List<RecommendationEngine.ResourceDetail>?, location: String) {
        val prefs = requireContext().getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        if (events != null) {
            editor.putString(KEY_CACHED_EVENTS, gson.toJson(events))
        }
        if (amenities != null) {
            editor.putString(KEY_CACHED_AMENITIES, gson.toJson(amenities))
        }
        editor.putString(KEY_CACHE_LOCATION, location)
        editor.apply()
    }

    private fun displayEvents(events: List<RecommendationEngine.EventDetail>) {
        binding.layoutEventsLoading.visibility = View.GONE

        if (events.isNotEmpty()) {
            binding.recyclerEvents.visibility = View.VISIBLE
            binding.txtEventsEmpty.visibility = View.GONE
            binding.recyclerEvents.adapter = TMEventAdapter(events)
        } else {
            binding.recyclerEvents.visibility = View.GONE
            binding.txtEventsEmpty.visibility = View.VISIBLE
        }
    }

    private fun displayAmenities(amenities: List<RecommendationEngine.ResourceDetail>) {
        binding.layoutAmenitiesLoading.visibility = View.GONE

        if (amenities.isNotEmpty()) {
            binding.recyclerAmenities.visibility = View.VISIBLE
            binding.layoutAmenitiesEmpty.visibility = View.GONE
            binding.recyclerAmenities.adapter = AmenityAdapter(amenities)
        } else {
            binding.recyclerAmenities.visibility = View.GONE
            binding.layoutAmenitiesEmpty.visibility = View.VISIBLE
        }
    }

    private fun hideAllLoading() {
        binding.layoutEventsLoading.visibility = View.GONE
        binding.layoutAmenitiesLoading.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false
    }

    // -------------------------------------------------
    // LOCATION & DATA LOADING
    // -------------------------------------------------

    private fun refreshData() {
        loadWithProfileLocation(forceRefresh = true)
    }

    private fun checkLocationPermissionAndLoad(forceRefresh: Boolean = false) {
        // Use profile location instead of GPS
        loadWithProfileLocation(forceRefresh)
    }

    /**
     * Gets the user's location from their profile, defaulting to Madison, WI if not set
     */
    private fun getProfileLocation(): String {
        val profileLocation = UserStore.location
        return if (!profileLocation.isNullOrBlank()) {
            profileLocation
        } else {
            "Madison, WI" // Default location
        }
    }

    private fun loadWithProfileLocation(forceRefresh: Boolean = false) {
        if (forceRefresh) {
            binding.swipeRefresh.isRefreshing = true
        } else {
            // Show loading only if no cached data
            if (cachedEvents == null) {
                binding.layoutEventsLoading.visibility = View.VISIBLE
                binding.recyclerEvents.visibility = View.GONE
                binding.txtEventsEmpty.visibility = View.GONE
            }
            if (cachedAmenities == null) {
                binding.layoutAmenitiesLoading.visibility = View.VISIBLE
                binding.recyclerAmenities.visibility = View.GONE
                binding.layoutAmenitiesEmpty.visibility = View.GONE
            }
        }

        val locationString = getProfileLocation()
        loadDataWithLocation(locationString)
    }

    private fun loadDataWithLocation(locationString: String) {
        lifecycleScope.launch {
            try {

                val emotionData = RecommendationEngine.createEmotionData(
                    emotion = "neutral",
                    emotionScore = 0.5f,
                    sentiment = "neutral",
                    sentimentScore = 0.5f
                )

                // Load events and amenities in parallel
                val eventsDeferred = async {
                    recommendationEngine.getEventRecommendations(
                        location = locationString,
                        emotionData = emotionData
                    )
                }

                val amenitiesDeferred = async {
                    recommendationEngine.getNearbyHelp(
                        journalText = "Looking for nearby mental health resources and wellness amenities",
                        emotionData = emotionData,
                        location = locationString
                    )
                }

                val eventRecs = eventsDeferred.await()
                val nearbyHelp = amenitiesDeferred.await()

                // Update events
                if (eventRecs != null && eventRecs.events.isNotEmpty()) {
                    cachedEvents = eventRecs.events
                    displayEvents(eventRecs.events)
                } else {
                    binding.layoutEventsLoading.visibility = View.GONE
                    if (cachedEvents == null) {
                        binding.txtEventsEmpty.visibility = View.VISIBLE
                    }
                }

                // Update amenities
                if (nearbyHelp != null && nearbyHelp.resources.isNotEmpty()) {
                    cachedAmenities = nearbyHelp.resources
                    displayAmenities(nearbyHelp.resources)
                } else {
                    binding.layoutAmenitiesLoading.visibility = View.GONE
                    if (cachedAmenities == null) {
                        binding.layoutAmenitiesEmpty.visibility = View.VISIBLE
                    }
                }

                // Save to cache
                saveCache(cachedEvents, cachedAmenities, locationString)

                binding.swipeRefresh.isRefreshing = false

            } catch (e: Exception) {
                hideAllLoading()

                if (cachedEvents != null || cachedAmenities != null) {
                    Toast.makeText(requireContext(), "Could not refresh. Showing cached data.", Toast.LENGTH_SHORT).show()
                } else {
                    binding.txtEventsEmpty.visibility = View.VISIBLE
                    binding.layoutAmenitiesEmpty.visibility = View.VISIBLE
                }

                android.util.Log.e("ActivitiesFragment", "Error loading data", e)
            }
        }
    }

    // -------------------------------------------------
    // STEP COUNTER
    // -------------------------------------------------

    private fun checkActivityPermissionAndSetupSensor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED -> {
                    setupStepSensor()
                }
                else -> {
                    activityPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                }
            }
        } else {
            setupStepSensor()
        }
    }

    private fun loadStepGoal() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        stepGoal = prefs.getInt(KEY_STEP_GOAL, DEFAULT_STEP_GOAL)

        val lastSaveDate = prefs.getString(KEY_LAST_SAVE_DATE, "") ?: ""
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(java.util.Date())

        if (lastSaveDate != today) {
            initialSteps = -1f
            prefs.edit()
                .putFloat(KEY_INITIAL_STEPS, -1f)
                .putString(KEY_LAST_SAVE_DATE, today)
                .apply()
        } else {
            initialSteps = prefs.getFloat(KEY_INITIAL_STEPS, -1f)
        }
    }

    private fun saveInitialSteps(steps: Float) {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(java.util.Date())
        prefs.edit()
            .putFloat(KEY_INITIAL_STEPS, steps)
            .putString(KEY_LAST_SAVE_DATE, today)
            .apply()
    }

    private fun setupStepSensor() {
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        if (stepSensor == null) {
            binding.txtStepCount.text = "—"
            binding.txtProgressPercent.text = "Step counter not available"
        }
    }

    private fun setupEditGoalButton() {
        binding.btnEditGoal.setOnClickListener {
            showEditGoalDialog()
        }
    }

    private fun showEditGoalDialog() {
        val editText = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.enter_step_goal)
            setText(stepGoal.toString())
            selectAll()
        }

        val padding = (24 * resources.displayMetrics.density).toInt()
        editText.setPadding(padding, padding, padding, padding)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.set_step_goal)
            .setView(editText)
            .setPositiveButton(R.string.save) { _, _ ->
                val newGoal = editText.text.toString().toIntOrNull()
                if (newGoal != null && newGoal > 0) {
                    stepGoal = newGoal
                    saveStepGoal(newGoal)
                    updateStepUI()
                } else {
                    Toast.makeText(requireContext(), "Please enter a valid number", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun saveStepGoal(goal: Int) {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_STEP_GOAL, goal).apply()
    }

    private fun updateStepUI() {
        val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())

        binding.txtStepCount.text = numberFormat.format(currentSteps)
        binding.txtStepGoal.text = "/ ${numberFormat.format(stepGoal)} steps"

        val progressPercent = if (stepGoal > 0) {
            ((currentSteps.toFloat() / stepGoal) * 100).coerceAtMost(100f).toInt()
        } else {
            0
        }

        binding.progressSteps.progress = progressPercent
        binding.txtProgressPercent.text = "$progressPercent% of daily goal"
    }

    override fun onResume() {
        super.onResume()
        stepSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        // Start activity tracker
        activityTracker?.start()
        lastCalorieUpdateTime = System.currentTimeMillis()

        // Refresh daily data
        loadDailyData()
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
        // Stop activity tracker
        activityTracker?.stop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activityTracker?.stop()
        activityTracker = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSteps = event.values[0]

            if (initialSteps < 0) {
                initialSteps = totalSteps
                saveInitialSteps(totalSteps)
            }

            currentSteps = (totalSteps - initialSteps).toInt().coerceAtLeast(0)
            updateStepUI()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for step counter
    }
}
