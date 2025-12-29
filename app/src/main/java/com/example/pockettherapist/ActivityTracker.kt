package com.example.pockettherapist

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * ActivityTracker uses sensor fusion with Kalman filtering to estimate
 * user activity level from accelerometer, gyroscope, and light sensor data.
 */
class ActivityTracker(private val context: Context) : SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var lightSensor: Sensor? = null

    // Kalman filter states for each sensor dimension
    private val accelKalman = KalmanFilter()
    private val gyroKalman = KalmanFilter()
    private val lightKalman = KalmanFilter()

    // Raw sensor values
    private var accelMagnitude = 0f
    private var gyroMagnitude = 0f
    private var lightLevel = 0f

    // Filtered values
    private var filteredAccel = 0f
    private var filteredGyro = 0f
    private var filteredLight = 0f

    // Activity metrics
    private var activityLevel = 0f // 0.0 to 1.0
    private var activityState = ActivityState.RESTING
    private var caloriesPerMinute = 0f

    // Calorie calculation parameters
    private var userWeightKg = 70f // Default weight, can be set
    private var userAge = 30 // Default age
    private var userIsMale = true // Default gender

    // Activity history for smoothing
    private val activityHistory = mutableListOf<Float>()
    private val maxHistorySize = 30 // ~3 seconds at 10Hz

    // Callback for UI updates
    var onActivityUpdate: ((ActivityData) -> Unit)? = null

    enum class ActivityState(val displayName: String, val metValue: Float) {
        RESTING("Resting", 1.0f),
        LIGHT("Light", 2.0f),
        MODERATE("Moderate", 4.0f),
        VIGOROUS("Vigorous", 8.0f)
    }

    data class ActivityData(
        val activityLevel: Float,      // 0.0 to 1.0
        val activityState: ActivityState,
        val caloriesPerMinute: Float,
        val movementIntensity: Float,  // From accelerometer
        val rotationRate: Float,       // From gyroscope
        val ambientLight: Float,       // From light sensor
        val isInPocket: Boolean        // Inferred from light
    )

    /**
     * Simple 1D Kalman Filter for sensor data smoothing
     */
    inner class KalmanFilter {
        private var estimate = 0f
        private var errorEstimate = 1f
        private val errorMeasure = 0.5f  // Measurement noise
        private val processNoise = 0.1f  // Process noise

        fun update(measurement: Float): Float {
            // Prediction
            errorEstimate += processNoise

            // Update
            val kalmanGain = errorEstimate / (errorEstimate + errorMeasure)
            estimate += kalmanGain * (measurement - estimate)
            errorEstimate *= (1 - kalmanGain)

            return estimate
        }

        fun reset() {
            estimate = 0f
            errorEstimate = 1f
        }
    }

    fun setUserProfile(weightKg: Float, age: Int, isMale: Boolean) {
        userWeightKg = weightKg
        userAge = age
        userIsMale = isMale
    }

    fun start(): Boolean {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

        val hasAccelerometer = accelerometer != null
        val hasGyroscope = gyroscope != null
        val hasLight = lightSensor != null

        if (!hasAccelerometer) {
            return false // Accelerometer is required
        }

        // Register listeners
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        lightSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        return true
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> processAccelerometer(event)
            Sensor.TYPE_GYROSCOPE -> processGyroscope(event)
            Sensor.TYPE_LIGHT -> processLight(event)
        }

        // Update activity level after each accelerometer reading (primary sensor)
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            calculateActivityLevel()
        }
    }

    private fun processAccelerometer(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Calculate magnitude minus gravity (~9.81)
        val magnitude = sqrt(x * x + y * y + z * z) - 9.81f
        accelMagnitude = magnitude.coerceAtLeast(0f)

        // Apply Kalman filter
        filteredAccel = accelKalman.update(accelMagnitude)
    }

    private fun processGyroscope(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Calculate rotation rate magnitude (rad/s)
        gyroMagnitude = sqrt(x * x + y * y + z * z)

        // Apply Kalman filter
        filteredGyro = gyroKalman.update(gyroMagnitude)
    }

    private fun processLight(event: SensorEvent) {
        lightLevel = event.values[0]

        // Apply Kalman filter
        filteredLight = lightKalman.update(lightLevel)
    }

    private fun calculateActivityLevel() {
        // Normalize accelerometer (0-20 m/s² range typical for activities)
        val accelNorm = (filteredAccel / 15f).coerceIn(0f, 1f)

        // Normalize gyroscope (0-5 rad/s range typical)
        val gyroNorm = (filteredGyro / 4f).coerceIn(0f, 1f)

        // Determine if phone is in pocket (low light = likely in pocket)
        val isInPocket = filteredLight < 10f

        // Weights for sensor fusion
        // Accelerometer is primary indicator of movement
        // Gyroscope helps distinguish rotation-based activities
        // Light provides context
        val accelWeight = 0.7f
        val gyroWeight = 0.3f

        // Fused activity level
        var rawActivity = (accelNorm * accelWeight + gyroNorm * gyroWeight)

        // Adjust based on context
        if (isInPocket && rawActivity > 0.1f) {
            // If in pocket with movement, likely walking/running
            rawActivity *= 1.1f
        }

        // Add to history for temporal smoothing
        activityHistory.add(rawActivity)
        if (activityHistory.size > maxHistorySize) {
            activityHistory.removeAt(0)
        }

        // Calculate smoothed activity level
        activityLevel = activityHistory.average().toFloat().coerceIn(0f, 1f)

        // Determine activity state
        activityState = when {
            activityLevel < 0.1f -> ActivityState.RESTING
            activityLevel < 0.3f -> ActivityState.LIGHT
            activityLevel < 0.6f -> ActivityState.MODERATE
            else -> ActivityState.VIGOROUS
        }

        // Calculate calories per minute using MET formula
        // Calories/min = (MET × weight in kg × 3.5) / 200
        val met = activityState.metValue * (1 + activityLevel) // Scale MET with intensity
        caloriesPerMinute = (met * userWeightKg * 3.5f) / 200f

        // Notify listener
        onActivityUpdate?.invoke(
            ActivityData(
                activityLevel = activityLevel,
                activityState = activityState,
                caloriesPerMinute = caloriesPerMinute,
                movementIntensity = filteredAccel,
                rotationRate = filteredGyro,
                ambientLight = filteredLight,
                isInPocket = filteredLight < 10f
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }

    fun getActivityLevel(): Float = activityLevel

    fun getActivityState(): ActivityState = activityState

    fun getCaloriesPerMinute(): Float = caloriesPerMinute

    fun reset() {
        accelKalman.reset()
        gyroKalman.reset()
        lightKalman.reset()
        activityHistory.clear()
        activityLevel = 0f
        activityState = ActivityState.RESTING
        caloriesPerMinute = 0f
    }
}
