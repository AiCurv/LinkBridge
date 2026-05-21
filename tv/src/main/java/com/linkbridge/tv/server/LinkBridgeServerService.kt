package com.linkbridge.tv.server

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.linkbridge.common.network.Protocol
import com.linkbridge.tv.MainActivity
import com.linkbridge.tv.R

/**
 * Foreground service that keeps the HTTP server and discovery receiver alive.
 */
class LinkBridgeServerService : Service() {

    private val tag = "LinkBridgeServerService"
    private val NOTIFICATION_CHANNEL_ID = "linkbridge_server"
    private val NOTIFICATION_ID = 1

    private var httpServer: LinkBridgeHttpServer? = null
    private var discoveryReceiver: DiscoveryReceiver? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Waiting for links..."))

        httpServer = LinkBridgeHttpServer(this, Protocol.DEFAULT_PORT).also { server ->
            server.onLinkReceived = { entity ->
                updateNotification("Last received: ${entity.text.take(30)}")
            }
            try {
                server.start()
            } catch (e: Exception) {
                android.util.Log.e(tag, "Failed to start HTTP server", e)
            }
        }

        discoveryReceiver = DiscoveryReceiver().also { receiver ->
            receiver.serverPort = Protocol.DEFAULT_PORT
            receiver.start()
        }

        android.util.Log.d(tag, "LinkBridge server service started")
    }

    override fun onDestroy() {
        super.onDestroy()
        httpServer?.stop()
        httpServer = null
        discoveryReceiver?.stop()
        discoveryReceiver = null
        android.util.Log.d(tag, "LinkBridge server service stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "LinkBridge Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "LinkBridge is listening for incoming links"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("LinkBridge TV")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_linkbridge_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
}
