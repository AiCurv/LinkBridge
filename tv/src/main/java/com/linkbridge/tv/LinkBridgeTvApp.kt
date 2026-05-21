package com.linkbridge.tv

import android.app.Application
import com.linkbridge.tv.database.AppDatabase

class LinkBridgeTvApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
}
