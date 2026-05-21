package com.linkbridge.tv.server

import android.util.Log
import com.linkbridge.common.network.Protocol
import com.linkbridge.common.util.NetworkUtil
import java.net.DatagramPacket
import java.net.DatagramSocket

/**
 * Listens for UDP discovery broadcasts from the phone app
 * and responds with the TV's IP and port.
 */
class DiscoveryReceiver(
    private val port: Int = Protocol.DISCOVERY_PORT
) {
    private val tag = "DiscoveryReceiver"
    private var socket: DatagramSocket? = null
    private var running = false
    private var thread: Thread? = null
    var serverPort: Int = Protocol.DEFAULT_PORT

    fun start() {
        if (running) return
        running = true
        thread = Thread({ listenLoop() }, "DiscoveryReceiver")
        thread?.isDaemon = true
        thread?.start()
        Log.d(tag, "Discovery receiver started on UDP port $port")
    }

    fun stop() {
        running = false
        socket?.close()
        socket = null
        thread?.interrupt()
        thread = null
        Log.d(tag, "Discovery receiver stopped")
    }

    private fun listenLoop() {
        try {
            socket = DatagramSocket(port).apply {
                reuseAddress = true
                broadcast = true
                soTimeout = 2000
            }

            val buf = ByteArray(1024)
            while (running) {
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    socket?.receive(packet) ?: break

                    val msg = String(packet.data, packet.offset, packet.length, Charsets.UTF_8).trim()
                    if (msg == Protocol.DISCOVERY_MAGIC) {
                        Log.d(tag, "Discovery request from ${packet.address.hostAddress}")
                        respond(packet.address.hostAddress, packet.port)
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    // Normal timeout, continue loop
                }
            }
        } catch (e: Exception) {
            if (running) Log.e(tag, "Discovery listener error", e)
        }
    }

    private fun respond(senderIp: String, senderPort: Int) {
        try {
            val myIp = NetworkUtil.getLocalIpAddress() ?: return
            val deviceName = android.os.Build.MODEL ?: "AndroidTV"
            val responseMsg = "${Protocol.DISCOVERY_RESPONSE_PREFIX}$myIp:$serverPort:$deviceName"
            val data = responseMsg.toByteArray(Charsets.UTF_8)

            val socket = DatagramSocket()
            socket.use {
                val packet = DatagramPacket(data, data.size, java.net.InetAddress.getByName(senderIp), senderPort)
                it.send(packet)
            }
            Log.d(tag, "Sent discovery response to $senderIp:$senderPort")
        } catch (e: Exception) {
            Log.e(tag, "Error sending discovery response", e)
        }
    }
}
