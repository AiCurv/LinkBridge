package com.linkbridge.tv.util

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Utility to fire the "Open With" intent for any text.
 * Shared between the server (auto-trigger on receive) and the UI (re-trigger from history).
 */
object IntentHelper {

    private val tag = "IntentHelper"

    /**
     * Attempt to open the given text with any capable app.
     * Strategy:
     * 1) Try ACTION_VIEW (for URIs like http/https/magnet/ftp)
     * 2) Fall back to ACTION_SEND as text/plain
     */
    fun openWith(context: Context, text: String) {
        // Strategy 1: ACTION_VIEW
        try {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                val uri = android.net.Uri.parse(text)
                setDataAndNormalize(uri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val resolveInfo = context.packageManager.queryIntentActivities(viewIntent, 0)
            if (resolveInfo.isNotEmpty()) {
                val chooser = Intent.createChooser(viewIntent, "Open with").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                return
            }
        } catch (_: Exception) {
            // Uri.parse may fail for non-URI text; fall through
        }

        // Strategy 2: ACTION_SEND as text/plain
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(sendIntent, "Open with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(tag, "Could not open any intent for text", e)
        }
    }

    /**
     * Copy text to the system clipboard.
     */
    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("LinkBridge", text)
        clipboard.setPrimaryClip(clip)
    }
}
