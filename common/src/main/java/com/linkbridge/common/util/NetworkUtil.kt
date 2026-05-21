package com.linkbridge.common.util

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Utility to find the device's own local IPv4 address.
 */
object NetworkUtil {

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                // Skip loopback and non-up interfaces
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    /**
     * Returns the subnet prefix for UDP broadcast.
     * E.g., "192.168.1" from "192.168.1.100"
     */
    fun getSubnetPrefix(ip: String): String {
        val parts = ip.split(".")
        if (parts.size >= 3) {
            return "${parts[0]}.${parts[1]}.${parts[2]}"
        }
        return ip
    }
}
