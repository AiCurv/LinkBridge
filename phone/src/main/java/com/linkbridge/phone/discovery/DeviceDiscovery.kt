package com.linkbridge.phone.discovery

import android.util.Log
import com.linkbridge.common.network.Protocol
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Discovers LinkBridge TV instances on the local network via UDP broadcast.
 */
class DeviceDiscovery {

    private val tag = "DeviceDiscovery"

    data class DiscoveredTV(
        val ip: String,
        val port: Int,
        val deviceName: String
    )

    /**
     * Scan the local subnet for LinkBridge TV instances.
     * Sends a UDP broadcast and waits for responses.
     * Returns a list of discovered TVs.
     */
    fun discover(timeoutMs: Long = Protocol.DISCOVERY_TIMEOUT_MS): List<DiscoveredTV> {
        val results = mutableListOf<DiscoveredTV>()
        var socket: DatagramSocket? = null

        try {
            socket = DatagramSocket().apply {
                reuseAddress = true
                broadcast = true
                soTimeout = 1000 // 1-second read timeout per round
            }

            // Send discovery broadcast
            val discoverMsg = Protocol.DISCOVERY_MAGIC.toByteArray(Charsets.UTF_8)
            val broadcastAddr = InetAddress.getByName("255.255.255.255")
            val sendPacket = DatagramPacket(
                discoverMsg, discoverMsg.size,
                broadcastAddr, Protocol.DISCOVERY_PORT
            )

            // Send a few times for reliability
            repeat(3) {
                socket.send(sendPacket)
                Thread.sleep(200)
            }

            // Listen for responses
            val startTime = System.currentTimeMillis()
            val buf = ByteArray(1024)

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    val receivePacket = DatagramPacket(buf, buf.size)
                    socket.receive(receivePacket)

                    val response = String(receivePacket.data, receivePacket.offset, receivePacket.length, Charsets.UTF_8).trim()

                    if (response.startsWith(Protocol.DISCOVERY_RESPONSE_PREFIX)) {
                        val parts = response.removePrefix(Protocol.DISCOVERY_RESPONSE_PREFIX).split(":")
                        if (parts.size >= 3) {
                            val ip = parts[0]
                            val port = parts[1].toIntOrNull() ?: Protocol.DEFAULT_PORT
                            val name = parts.drop(2).joinToString(":")
                            results.add(DiscoveredTV(ip, port, name))
                            Log.d(tag, "Discovered TV: $name at $ip:$port")
                        }
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    // Continue waiting
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Discovery error", e)
        } finally {
            socket?.close()
        }

        return results.distinctBy { it.ip }
    }

    /**
     * Simple HTTP ping to check if a TV is reachable at the given address.
     */
    fun ping(ip: String, port: Int = Protocol.DEFAULT_PORT): Boolean {
        return try {
            val url = java.net.URL("http://$ip:$port${Protocol.PATH_PING}")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = Protocol.CONNECT_TIMEOUT_MS
            conn.readTimeout = Protocol.READ_TIMEOUT_MS
            val response = conn.responseCode
            conn.disconnect()
            response == 200
        } catch (_: Exception) {
            false
        }
    }
}
