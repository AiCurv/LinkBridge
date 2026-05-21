package com.linkbridge.tv.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.linkbridge.tv.server.LinkBridgeServerService

/**
 * Auto-starts the LinkBridge server service when the TV boots.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, LinkBridgeServerService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
