package com.linkbridge.phone.network

import android.util.Log
import com.google.gson.Gson
import com.linkbridge.common.model.LinkPayload
import com.linkbridge.common.network.Protocol
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends a LinkPayload to a TV via HTTP POST.
 */
class Sender {

    private val tag = "Sender"
    private val gson = Gson()

    enum class SendResult {
        SUCCESS,
        UNREACHABLE,
        ERROR
    }

    fun send(tvIp: String, tvPort: Int, payload: LinkPayload): SendResult {
        return try {
            val url = URL("http://$tvIp:$tvPort${Protocol.PATH_RECEIVE}")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = Protocol.CONNECT_TIMEOUT_MS
            conn.readTimeout = Protocol.READ_TIMEOUT_MS

            val json = gson.toJson(payload)
            conn.outputStream.use { os ->
                val input = json.toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
                os.flush()
            }

            val responseCode = conn.responseCode
            conn.disconnect()

            if (responseCode == 200) {
                Log.d(tag, "Successfully sent to $tvIp:$tvPort")
                SendResult.SUCCESS
            } else {
                Log.e(tag, "Server returned $responseCode")
                SendResult.ERROR
            }
        } catch (e: java.net.ConnectException) {
            Log.e(tag, "TV unreachable at $tvIp:$tvPort", e)
            SendResult.UNREACHABLE
        } catch (e: Exception) {
            Log.e(tag, "Send error", e)
            SendResult.ERROR
        }
    }
}
