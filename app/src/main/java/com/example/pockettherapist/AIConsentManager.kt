package com.example.pockettherapist

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.widget.Button

/**
 * AIConsentManager - Manages user consent for AI-powered features.
 *
 * This class handles:
 * 1. Storing and retrieving user's AI consent preference
 * 2. Showing the detailed consent dialog
 * 3. Providing callbacks for consent decisions
 *
 * Privacy is important - we never send data to external AI services
 * without explicit user permission.
 */
object AIConsentManager {

    private const val PREFS_NAME = "ai_consent_prefs"
    private const val KEY_AI_CONSENT_GIVEN = "ai_consent_given"
    private const val KEY_AI_CONSENT_TIMESTAMP = "ai_consent_timestamp"
    private const val KEY_AI_FEATURES_ENABLED = "ai_features_enabled"
    private const val KEY_CONSENT_DIALOG_SHOWN = "consent_dialog_shown"

    private var prefs: SharedPreferences? = null

    /**
     * Initialize the consent manager with context.
     * Call this early in your app lifecycle.
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Check if the user has already made a consent decision (either way).
     * Returns true if they've seen the dialog and made a choice.
     */
    fun hasUserMadeConsentDecision(): Boolean {
        return prefs?.getBoolean(KEY_CONSENT_DIALOG_SHOWN, false) ?: false
    }

    /**
     * Check if AI features are currently enabled.
     * Returns true only if user has explicitly consented.
     */
    fun isAIEnabled(): Boolean {
        return prefs?.getBoolean(KEY_AI_FEATURES_ENABLED, false) ?: false
    }

    /**
     * Check if user gave consent (for logging/analytics purposes).
     */
    fun hasUserConsented(): Boolean {
        return prefs?.getBoolean(KEY_AI_CONSENT_GIVEN, false) ?: false
    }

    /**
     * Get the timestamp when consent was given (if any).
     * Returns 0 if no consent was given.
     */
    fun getConsentTimestamp(): Long {
        return prefs?.getLong(KEY_AI_CONSENT_TIMESTAMP, 0L) ?: 0L
    }

    /**
     * Enable AI features (called when user consents).
     */
    fun enableAIFeatures() {
        prefs?.edit()?.apply {
            putBoolean(KEY_AI_CONSENT_GIVEN, true)
            putBoolean(KEY_AI_FEATURES_ENABLED, true)
            putBoolean(KEY_CONSENT_DIALOG_SHOWN, true)
            putLong(KEY_AI_CONSENT_TIMESTAMP, System.currentTimeMillis())
            apply()
        }
    }

    /**
     * Disable AI features (called when user declines or disables in settings).
     */
    fun disableAIFeatures() {
        prefs?.edit()?.apply {
            putBoolean(KEY_AI_FEATURES_ENABLED, false)
            putBoolean(KEY_CONSENT_DIALOG_SHOWN, true)
            apply()
        }
    }

    /**
     * Toggle AI features on/off (for settings screen).
     */
    fun setAIFeaturesEnabled(enabled: Boolean) {
        if (enabled) {
            enableAIFeatures()
        } else {
            disableAIFeatures()
        }
    }

    /**
     * Reset consent (for testing or if user wants to see dialog again).
     */
    fun resetConsent() {
        prefs?.edit()?.clear()?.apply()
    }

    /**
     * Show the AI consent dialog.
     *
     * @param context The activity context
     * @param onConsent Callback when user enables AI features
     * @param onDecline Callback when user declines AI features
     */
    fun showConsentDialog(
        context: Context,
        onConsent: () -> Unit,
        onDecline: () -> Unit
    ) {
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_ai_consent, null)

        val dialog = AlertDialog.Builder(context, R.style.Theme_PocketTherapist_AlertDialog)
            .setView(dialogView)
            .setCancelable(false) // User must make a choice
            .create()

        val btnEnable = dialogView.findViewById<Button>(R.id.btnEnableAI)
        val btnDecline = dialogView.findViewById<Button>(R.id.btnDeclineAI)

        btnEnable.setOnClickListener {
            enableAIFeatures()
            dialog.dismiss()
            onConsent()
        }

        btnDecline.setOnClickListener {
            disableAIFeatures()
            dialog.dismiss()
            onDecline()
        }

        dialog.show()
    }

    /**
     * Show consent dialog only if user hasn't made a decision yet.
     * If they've already decided, calls the appropriate callback immediately.
     *
     * @param context The activity context
     * @param onConsent Callback when AI is enabled (either now or previously)
     * @param onDecline Callback when AI is disabled (either now or previously)
     */
    fun checkAndShowConsentIfNeeded(
        context: Context,
        onConsent: () -> Unit,
        onDecline: () -> Unit
    ) {
        init(context)

        if (hasUserMadeConsentDecision()) {
            // User already made a choice
            if (isAIEnabled()) {
                onConsent()
            } else {
                onDecline()
            }
        } else {
            // First time - show the dialog
            showConsentDialog(context, onConsent, onDecline)
        }
    }

    /**
     * Require consent before performing an AI operation.
     * If consent is given, executes the action. Otherwise shows dialog.
     *
     * @param context The activity context
     * @param onAllowed Called when AI operation can proceed
     * @param onDenied Called when AI operation should not proceed
     */
    fun requireConsentForAI(
        context: Context,
        onAllowed: () -> Unit,
        onDenied: (() -> Unit)? = null
    ) {
        init(context)

        when {
            isAIEnabled() -> {
                // User has consented, proceed
                onAllowed()
            }
            hasUserMadeConsentDecision() -> {
                // User explicitly declined before
                onDenied?.invoke()
            }
            else -> {
                // Haven't asked yet, show dialog
                showConsentDialog(
                    context,
                    onConsent = { onAllowed() },
                    onDecline = { onDenied?.invoke() }
                )
            }
        }
    }
}
