package com.linkbridge.tv.server

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.gson.Gson
import com.linkbridge.common.model.LinkCategory
import com.linkbridge.common.model.LinkPayload
import com.linkbridge.common.network.Protocol
import com.linkbridge.tv.database.AppDatabase
import com.linkbridge.tv.database.LinkEntity
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Embedded HTTP server that runs on the TV to receive links from the phone.
 */
class LinkBridgeHttpServer(
    private val context: Context,
    port: Int = Protocol.DEFAULT_PORT
) : NanoHTTPD(port) {

    private val gson = Gson()
    private val tag = "LinkBridgeHttpServer"
    var onLinkReceived: ((LinkEntity) -> Unit)? = null

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")

        return when {
            uri == Protocol.PATH_RECEIVE && session.method == Method.POST -> handleReceive(session)
            uri == Protocol.PING -> newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "pong")
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        }
    }

    private fun handleReceive(session: IHTTPSession): Response {
        return try {
            val files = HashMap<String, String>()
            session.parseBody(files)
            val postData = files["postData"] ?: return newFixedLengthResponse(
                Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "No data"
            )

            val payload = gson.fromJson(postData, LinkPayload::class.java)
            if (payload.text.isEmpty()) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Empty text")
            }

            val category = LinkCategory.classify(payload.text)
            val entity = LinkEntity(
                id = payload.uuid,
                text = payload.text,
                timestamp = payload.timestamp,
                category = category.name,
                isFavorite = false,
                senderDevice = payload.senderDevice
            )

            // Persist to database
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getInstance(context)
                db.linkDao().insert(entity)
            }

            // Notify UI
            onLinkReceived?.invoke(entity)

            // Fire "Open With" intent
            fireOpenWithIntent(payload.text)

            Log.d(tag, "Received and stored: ${payload.text.take(50)}")
            newFixedLengthResponse(Response.Status.OK, "application/json", """{"status":"ok","id":"${payload.uuid}"}""")
        } catch (e: Exception) {
            Log.e(tag, "Error handling receive", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error: ${e.message}")
        }
    }

    /**
     * Force the Android "Open With" dialog for any received text.
     * Strategy:
     * 1) Try ACTION_VIEW with Uri.parse (works for http/https/magnet/etc.)
     * 2) Fall back to ACTION_SEND as text/plain with chooser
     */
    private fun fireOpenWithIntent(text: String) {
        try {
            // Strategy 1: ACTION_VIEW
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                val uri = android.net.Uri.parse(text)
                setDataAndNormalize(uri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Check if any app can resolve this intent
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

        try {
            // Strategy 2: ACTION_SEND as text/plain
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
            Log.e(tag, "Could not open any intent for text: ${text.take(30)}", e)
        }
    }
}
