package com.example.pockettherapist

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

/**
 * Foreground service that tracks activity and steps even when the app is closed.
 * Uses Kalman filter sensor fusion for activity level estimation.
 */
class ActivityTrackingService : Service(), SensorEventListener {

    companion object {
        const val CHANNEL_ID = "activity_tracking_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.pockettherapist.START_TRACKING"
        const val ACTION_STOP = "com.example.pockettherapist.STOP_TRACKING"

        private const val PREFS_NAME = "activity_service_prefs"
        private const val KEY_IS_RUNNING = "is_running"

        fun isRunning(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getBoolean(KEY_IS_RUNNING, false)
        }

        fun start(context: Context) {
            val intent = Intent(context, ActivityTrackingService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ActivityTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var lightSensor: Sensor? = null

    private var wakeLock: PowerManager.WakeLock? = null

    // Kalman filters
    private val accelKalman = KalmanFilter()
    private val gyroKalman = KalmanFilter()
    private val lightKalman = KalmanFilter()

    // Sensor values
    private var filteredAccel = 0f
    private var filteredGyro = 0f
    private var filteredLight = 0f

    // Activity tracking
    private var activityLevel = 0f
    private var activityState = ActivityTracker.ActivityState.RESTING
    private var caloriesPerMinute = 0f
    private val activityHistory = mutableListOf<Float>()

    // Step tracking
    private var initialSteps = -1f
    private var currentSteps = 0

    // Timing
    private var lastSampleTime = 0L
    private var lastNotificationUpdate = 0L

    // User profile (defaults)
    private var userWeightKg = 70f

    inner class KalmanFilter {
        private var estimate = 0f
        private var errorEstimate = 1f
        private val errorMeasure = 0.5f
        private val processNoise = 0.1f

        fun update(measurement: Float): Float {
            errorEstimate += processNoise
            val kalmanGain = errorEstimate / (errorEstimate + errorMeasure)
            estimate += kalmanGain * (measurement - estimate)
            errorEstimate *= (1 - kalmanGain)
            return estimate
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        loadStepData()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification())

        // Acquire wake lock to keep sensors active
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PocketTherapist:ActivityTracking"
        ).apply {
            acquire(10 * 60 * 60 * 1000L) // 10 hours max
        }

        // Setup sensors
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

        // Register listeners
        stepSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gyroscope?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        lightSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        lastSampleTime = System.currentTimeMillis()

        // Save running state
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(KEY_IS_RUNNING, true)
            .apply()
    }

    private fun stopTracking() {
        // Unregister sensors
        sensorManager?.unregisterListener(this)

        // Release wake lock
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null

        // Save running state
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(KEY_IS_RUNNING, false)
            .apply()

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun loadStepData() {
        val prefs = getSharedPreferences("step_counter_prefs", MODE_PRIVATE)
        val lastSaveDate = prefs.getString("last_save_date", "") ?: ""
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())

        if (lastSaveDate == today) {
            initialSteps = prefs.getFloat("initial_steps", -1f)
        } else {
            initialSteps = -1f
        }
    }

    private fun saveStepData(steps: Float) {
        val prefs = getSharedPreferences("step_counter_prefs", MODE_PRIVATE)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        prefs.edit()
            .putFloat("initial_steps", steps)
            .putString("last_save_date", today)
            .apply()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> processSteps(event)
            Sensor.TYPE_ACCELEROMETER -> processAccelerometer(event)
            Sensor.TYPE_GYROSCOPE -> processGyroscope(event)
            Sensor.TYPE_LIGHT -> processLight(event)
        }

        // Calculate activity on accelerometer updates
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            calculateAndSaveActivity()
        }
    }

    private fun processSteps(event: SensorEvent) {
        val totalSteps = event.values[0]

        if (initialSteps < 0) {
            initialSteps = totalSteps
            saveStepData(totalSteps)
        }

        currentSteps = (totalSteps - initialSteps).toInt().coerceAtLeast(0)

        // Update daily store
        DailyActivityStore.updateSteps(this, currentSteps)
    }

    private fun processAccelerometer(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = (sqrt(x * x + y * y + z * z) - 9.81f).coerceAtLeast(0f)
        filteredAccel = accelKalman.update(magnitude)
    }

    private fun processGyroscope(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        filteredGyro = gyroKalman.update(magnitude)
    }

    private fun processLight(event: SensorEvent) {
        filteredLight = lightKalman.update(event.values[0])
    }

    private fun calculateAndSaveActivity() {
        val currentTime = System.currentTimeMillis()
        val elapsedSeconds = (currentTime - lastSampleTime) / 1000f

        // Only process every ~1 second
        if (elapsedSeconds < 1f) return

        // Normalize sensors
        val accelNorm = (filteredAccel / 15f).coerceIn(0f, 1f)
        val gyroNorm = (filteredGyro / 4f).coerceIn(0f, 1f)

        // Sensor fusion
        var rawActivity = (accelNorm * 0.7f + gyroNorm * 0.3f)

        // Context adjustment
        if (filteredLight < 10f && rawActivity > 0.1f) {
            rawActivity *= 1.1f
        }

        // Temporal smoothing
        activityHistory.add(rawActivity)
        if (activityHistory.size > 30) {
            activityHistory.removeAt(0)
        }

        activityLevel = activityHistory.average().toFloat().coerceIn(0f, 1f)

        // Determine state
        activityState = when {
            activityLevel < 0.1f -> ActivityTracker.ActivityState.RESTING
            activityLevel < 0.3f -> ActivityTracker.ActivityState.LIGHT
            activityLevel < 0.6f -> ActivityTracker.ActivityState.MODERATE
            else -> ActivityTracker.ActivityState.VIGOROUS
        }

        // Only record calories and activity when there's actual movement (not resting)
        if (activityLevel > 0.1f) {
            // Calculate calories only for active movement
            val met = activityState.metValue * (1 + activityLevel)
            caloriesPerMinute = (met * userWeightKg * 3.5f) / 200f

            // Record to daily store
            DailyActivityStore.recordActivitySample(
                context = this,
                activityLevel = activityLevel,
                activityState = activityState,
                caloriesPerMinute = caloriesPerMinute,
                durationSeconds = elapsedSeconds
            )
        }

        lastSampleTime = currentTime

        // Update notification periodically (every 30 seconds)
        if (currentTime - lastNotificationUpdate > 30000) {
            updateNotification()
            lastNotificationUpdate = currentTime
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Activity Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tracks your activity and steps in the background"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val todayData = DailyActivityStore.getTodayData(this)
        val activeMinutes = todayData.getTotalActiveMinutes()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tracking Activity")
            .setContentText("Steps: ${todayData.totalSteps} | Active: ${activeMinutes}min | Cal: ${todayData.totalCalories.toInt()}")
            .setSmallIcon(R.drawable.ic_directions_walk)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        sensorManager?.unregisterListener(this)
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }
}
