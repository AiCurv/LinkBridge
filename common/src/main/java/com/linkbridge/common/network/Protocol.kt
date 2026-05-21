package com.linkbridge.common.network

/**
 * Network protocol constants shared between Phone and TV modules.
 */
object Protocol {
    const val DEFAULT_PORT = 9090
    const val DISCOVERY_PORT = 9091
    const val DISCOVERY_TIMEOUT_MS = 5000L
    const val DISCOVERY_MAGIC = "LINKBRIDGE_DISCOVER"
    const val DISCOVERY_RESPONSE_PREFIX = "LINKBRIDGE_TV:"
    const val PATH_RECEIVE = "/receive"
    const val PATH_PING = "/ping"
    const val CONNECT_TIMEOUT_MS = 5000
    const val READ_TIMEOUT_MS = 10000
}
