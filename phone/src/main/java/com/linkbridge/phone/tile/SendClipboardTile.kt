package com.linkbridge.phone.tile

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.google.gson.Gson
import com.linkbridge.common.model.LinkPayload
import com.linkbridge.phone.LinkBridgePhoneApp
import com.linkbridge.phone.R
import com.linkbridge.phone.network.Sender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick Settings Tile: "Send Clipboard to TV"
 * User copies any link anywhere, pulls down QS, taps tile →
 * instantly sends clipboard text to the last known TV IP.
 */
class SendClipboardTile : TileService() {

    private val sender = Sender()
    private val gson = Gson()

    override fun onStartListening() {
        super.onStartListening()
        val hasLastTv = getPrefs().contains(LinkBridgePhoneApp.KEY_LAST_TV_IP)
        qsTile.state = if (hasLastTv) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile.updateTile()
    }

    override fun onClick() {
        super.onClick()

        val prefs = getPrefs()
        val lastIp = prefs.getString(LinkBridgePhoneApp.KEY_LAST_TV_IP, null)
        val lastPort = prefs.getInt(LinkBridgePhoneApp.KEY_LAST_TV_PORT, 9090)

        if (lastIp == null) {
            showToast("No TV configured yet. Use Share first.")
            return
        }

        // Get clipboard text
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0) {
            showToast("Clipboard is empty")
            return
        }

        val text = clip.getItemAt(0)?.text?.toString()?.trim()
        if (text.isNullOrEmpty()) {
            showToast("Clipboard is empty")
            return
        }

        qsTile.state = Tile.STATE_ACTIVE
        qsTile.updateTile()

        CoroutineScope(Dispatchers.IO).launch {
            val payload = LinkPayload(
                text = text,
                senderDevice = Build.MODEL ?: "Unknown"
            )

            val result = sender.send(lastIp, lastPort, payload)

            withContext(Dispatchers.Main) {
                when (result) {
                    Sender.SendResult.SUCCESS -> {
                        showToast("Sent to TV!")
                        saveToHistory(text)
                    }
                    Sender.SendResult.UNREACHABLE -> showToast("TV unreachable")
                    Sender.SendResult.ERROR -> showToast("Send failed")
                }
                qsTile.state = Tile.STATE_ACTIVE
                qsTile.updateTile()
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun getPrefs() = getSharedPreferences("linkbridge_phone", MODE_PRIVATE)

    private fun saveToHistory(text: String) {
        val prefs = getPrefs()
        val historyJson = prefs.getString(LinkBridgePhoneApp.KEY_SEND_HISTORY, "[]") ?: "[]"
        val history = try {
            gson.fromJson(historyJson, Array<HistoryItem>::class.java).toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }

        history.add(0, HistoryItem(text, System.currentTimeMillis()))
        // Keep only last 5
        while (history.size > 5) history.removeAt(history.lastIndex)

        prefs.edit()
            .putString(LinkBridgePhoneApp.KEY_SEND_HISTORY, gson.toJson(history))
            .apply()
    }

    data class HistoryItem(val text: String, val timestamp: Long)
}
