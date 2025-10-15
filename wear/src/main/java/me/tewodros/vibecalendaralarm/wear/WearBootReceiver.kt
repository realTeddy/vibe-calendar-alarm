package me.tewodros.vibecalendaralarm.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receiver that handles device boot to restore scheduled alarms
 */
class WearBootReceiver : BroadcastReceiver() {

    private val TAG = "WearBootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "📱 Boot completed or app updated: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d(TAG, "🔄 Requesting re-sync from phone after boot")
                // The WearDataLayerListenerService will automatically reconnect
                // and receive any pending data from the phone
            }
        }
    }
}
