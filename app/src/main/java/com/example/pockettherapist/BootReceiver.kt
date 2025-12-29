package com.example.pockettherapist

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts the activity tracking service when the device boots up,
 * if it was running before the device was turned off.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            // Check if service was running before reboot
            if (ActivityTrackingService.isRunning(context)) {
                ActivityTrackingService.start(context)
            }
        }
    }
}
