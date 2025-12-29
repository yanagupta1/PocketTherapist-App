package com.example.pockettherapist

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView

/**
 * CrisisDetector - Detects crisis/extreme language in journal entries
 * and shows emergency resources alert dialog
 */
object CrisisDetector {

    // Crisis keywords and phrases to detect
    private val crisisKeywords = listOf(
        // Suicidal ideation
        "kill myself", "end my life", "want to die", "wish i was dead",
        "better off dead", "suicide", "suicidal", "end it all",
        "no reason to live", "can't go on", "don't want to live",
        "take my own life", "not worth living", "goodbye forever",

        // Self-harm
        "hurt myself", "cutting myself", "self harm", "self-harm",
        "harm myself", "cut myself", "burn myself",

        // Severe distress
        "can't take it anymore", "no way out", "hopeless",
        "give up on life", "nothing matters anymore", "no point in living",
        "everyone would be better without me", "nobody cares if i die",
        "planning to end", "going to kill", "want to disappear forever"
    )

    // Emergency resources
    data class EmergencyResource(
        val name: String,
        val number: String,
        val description: String,
        val type: String // "call", "text", "both"
    )

    private val emergencyResources = listOf(
        EmergencyResource(
            "988 Suicide & Crisis Lifeline",
            "988",
            "Free, confidential 24/7 support",
            "both"
        ),
        EmergencyResource(
            "Crisis Text Line",
            "741741",
            "Text HOME to connect with a counselor",
            "text"
        ),
        EmergencyResource(
            "National Suicide Prevention",
            "1-800-273-8255",
            "24/7 crisis support line",
            "call"
        ),
        EmergencyResource(
            "Emergency Services",
            "911",
            "Immediate emergency help",
            "call"
        )
    )

    /**
     * Check if text contains crisis language
     * @param text The journal text to analyze
     * @return true if crisis language detected
     */
    fun containsCrisisLanguage(text: String): Boolean {
        val lowerText = text.lowercase()
        return crisisKeywords.any { keyword ->
            lowerText.contains(keyword)
        }
    }

    /**
     * Show crisis alert dialog with emergency resources
     * @param context The context to show dialog in
     * @param onDismiss Optional callback when dialog is dismissed
     */
    fun showCrisisAlert(context: Context, onDismiss: (() -> Unit)? = null) {
        val builder = AlertDialog.Builder(context, R.style.Theme_PocketTherapist_AlertDialog)

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_crisis_alert, null)
        builder.setView(dialogView)
        builder.setCancelable(true)

        val dialog = builder.create()

        // Setup buttons
        dialogView.findViewById<Button>(R.id.btnCall988)?.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:988"))
            context.startActivity(intent)
        }

        dialogView.findViewById<Button>(R.id.btnTextCrisisLine)?.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:741741"))
            intent.putExtra("sms_body", "HOME")
            context.startActivity(intent)
        }

        dialogView.findViewById<Button>(R.id.btnCall911)?.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:911"))
            context.startActivity(intent)
        }

        dialogView.findViewById<Button>(R.id.btnDismiss)?.setOnClickListener {
            dialog.dismiss()
            onDismiss?.invoke()
        }

        dialog.setOnDismissListener {
            onDismiss?.invoke()
        }

        dialog.show()
    }

    /**
     * Check text and show alert if crisis detected
     * @param context The context
     * @param text The text to check
     * @param onCrisisDetected Callback if crisis is detected (called before showing dialog)
     * @param onNoCrisis Callback if no crisis detected
     */
    fun checkAndAlert(
        context: Context,
        text: String,
        onCrisisDetected: (() -> Unit)? = null,
        onNoCrisis: (() -> Unit)? = null
    ) {
        if (containsCrisisLanguage(text)) {
            onCrisisDetected?.invoke()
            showCrisisAlert(context)
        } else {
            onNoCrisis?.invoke()
        }
    }
}
