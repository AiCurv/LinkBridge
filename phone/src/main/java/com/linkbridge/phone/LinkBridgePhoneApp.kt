package com.linkbridge.phone

import android.app.Application
import android.content.SharedPreferences

class LinkBridgePhoneApp : Application() {
    lateinit var prefs: SharedPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("linkbridge_phone", MODE_PRIVATE)
    }

    companion object {
        const val KEY_LAST_TV_IP = "last_tv_ip"
        const val KEY_LAST_TV_PORT = "last_tv_port"
        const val KEY_LAST_TV_NAME = "last_tv_name"
        const val KEY_SEND_HISTORY = "send_history"
    }
}
