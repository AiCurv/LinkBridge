package com.linkbridge.common.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * Payload sent from Phone to TV over the local network.
 */
data class LinkPayload(
    @SerializedName("text")
    val text: String,
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    @SerializedName("uuid")
    val uuid: String = UUID.randomUUID().toString(),
    @SerializedName("senderDevice")
    val senderDevice: String = ""
)

/**
 * Category tag for a received link/text.
 */
enum class LinkCategory(val label: String) {
    WEB("WEB"),
    MAGNET("MAGNET"),
    VIDEO("VIDEO"),
    TORRENT("TORRENT"),
    TEXT("TEXT");

    companion object {
        fun classify(text: String): LinkCategory {
            val t = text.trim().lowercase()
            return when {
                t.startsWith("magnet:") -> MAGNET
                t.endsWith(".torrent") || t.contains(".torrent?") -> TORRENT
                t.startsWith("http://") || t.startsWith("https://") -> {
                    if (t.contains(".mp4") || t.contains(".mkv") || t.contains(".avi") ||
                        t.contains(".m3u8") || t.contains(".webm") || t.contains("/video/")
                    ) VIDEO else WEB
                }
                t.startsWith("ftp://") || t.startsWith("rtsp://") -> VIDEO
                else -> TEXT
            }
        }
    }
}

/**
 * Discovery response broadcast by the TV.
 */
data class DiscoveryResponse(
    @SerializedName("ip")
    val ip: String,
    @SerializedName("port")
    val port: Int,
    @SerializedName("deviceName")
    val deviceName: String,
    @SerializedName("type")
    val type: String = "LinkBridgeTV"
)
