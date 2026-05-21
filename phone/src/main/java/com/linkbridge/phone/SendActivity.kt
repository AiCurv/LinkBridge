package com.linkbridge.phone

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.google.gson.Gson
import com.linkbridge.common.model.LinkPayload
import com.linkbridge.common.network.Protocol
import com.linkbridge.phone.discovery.DeviceDiscovery
import com.linkbridge.phone.network.Sender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SendActivity : Activity() {

    private lateinit var tvPreview: TextView
    private lateinit var tvStatus: TextView
    private lateinit var spinnerTvs: Spinner
    private lateinit var etManualIp: EditText
    private lateinit var etManualPort: EditText
    private lateinit var btnSend: Button
    private lateinit var btnRetry: Button
    private lateinit var progressSending: ProgressBar
    private lateinit var tvRecentLabel: TextView
    private lateinit var recentLayout: LinearLayout

    private val sender = Sender()
    private val discovery = DeviceDiscovery()
    private val gson = Gson()

    private var discoveredTvs = mutableListOf<DeviceDiscovery.DiscoveredTV>()
    private var sharedText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make it look like a dialog, not full-screen
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        setContentView(R.layout.activity_send)

        tvPreview = findViewById(R.id.tvPreview)
        tvStatus = findViewById(R.id.tvStatus)
        spinnerTvs = findViewById(R.id.spinnerTvs)
        etManualIp = findViewById(R.id.etManualIp)
        etManualPort = findViewById(R.id.etManualPort)
        btnSend = findViewById(R.id.btnSend)
        btnRetry = findViewById(R.id.btnRetry)
        progressSending = findViewById(R.id.progressSending)
        tvRecentLabel = findViewById(R.id.tvRecentLabel)
        recentLayout = findViewById(R.id.recentLayout)

        // Get shared text
        sharedText = intent?.getStringExtra(android.content.Intent.EXTRA_TEXT) ?: ""
        if (sharedText.isEmpty()) {
            // Try clipboard fallback
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            sharedText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        }

        if (sharedText.isEmpty()) {
            tvPreview.text = "(No text received)"
            btnSend.isEnabled = false
            return
        }

        // Show preview (first 3 lines)
        val previewLines = sharedText.lines().take(3).joinToString("\n")
        tvPreview.text = if (sharedText.lines().size > 3) "$previewLines…" else previewLines

        // Pre-fill last known TV
        val prefs = getSharedPreferences("linkbridge_phone", MODE_PRIVATE)
        val lastIp = prefs.getString(LinkBridgePhoneApp.KEY_LAST_TV_IP, "")
        val lastPort = prefs.getInt(LinkBridgePhoneApp.KEY_LAST_TV_PORT, Protocol.DEFAULT_PORT)
        if (!lastIp.isNullOrEmpty()) {
            etManualIp.setText(lastIp)
            etManualPort.setText(lastPort.toString())
        }

        // Show recent sends
        showRecentSends()

        // Discover TVs
        discoverTvs()

        // Send button
        btnSend.setOnClickListener { sendToTv() }
        btnRetry.setOnClickListener { sendToTv() }
    }

    private fun discoverTvs() {
        tvStatus.text = "Scanning for TVs…"
        CoroutineScope(Dispatchers.IO).launch {
            val tvs = discovery.discover()
            withContext(Dispatchers.Main) {
                discoveredTvs.clear()
                discoveredTvs.addAll(tvs)

                if (tvs.isNotEmpty()) {
                    val tvNames = tvs.map { "${it.deviceName} (${it.ip}:${it.port})" }.toMutableList()
                    tvNames.add("Enter manually…")
                    val adapter = ArrayAdapter(this@SendActivity, android.R.layout.simple_spinner_item, tvNames)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerTvs.adapter = adapter

                    // Auto-select first
                    spinnerTvs.setSelection(0)
                    etManualIp.setText(tvs[0].ip)
                    etManualPort.setText(tvs[0].port.toString())

                    tvStatus.text = "Found ${tvs.size} TV(s)"
                } else {
                    val adapter = ArrayAdapter(this@SendActivity, android.R.layout.simple_spinner_item, listOf("No TVs found — enter manually"))
                    spinnerTvs.adapter = adapter
                    tvStatus.text = "No TVs found. Enter IP manually."
                }

                spinnerTvs.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        if (position < discoveredTvs.size) {
                            etManualIp.setText(discoveredTvs[position].ip)
                            etManualPort.setText(discoveredTvs[position].port.toString())
                        }
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }
        }
    }

    private fun sendToTv() {
        val ip = etManualIp.text.toString().trim()
        val port = etManualPort.text.toString().trim().toIntOrNull() ?: Protocol.DEFAULT_PORT

        if (ip.isEmpty()) {
            tvStatus.text = "Please enter a TV IP address"
            return
        }

        // UI state: sending
        btnSend.visibility = View.GONE
        btnRetry.visibility = View.GONE
        progressSending.visibility = View.VISIBLE
        tvStatus.text = "Sending…"

        // Save as last-used TV
        val prefs = getSharedPreferences("linkbridge_phone", MODE_PRIVATE)
        prefs.edit()
            .putString(LinkBridgePhoneApp.KEY_LAST_TV_IP, ip)
            .putInt(LinkBridgePhoneApp.KEY_LAST_TV_PORT, port)
            .apply()

        CoroutineScope(Dispatchers.IO).launch {
            val payload = LinkPayload(
                text = sharedText,
                senderDevice = Build.MODEL ?: "Unknown"
            )

            val result = sender.send(ip, port, payload)

            withContext(Dispatchers.Main) {
                progressSending.visibility = View.GONE
                when (result) {
                    Sender.SendResult.SUCCESS -> {
                        tvStatus.text = "Sent!"
                        saveToHistory(sharedText)
                        // Auto-close after a short delay
                        btnSend.visibility = View.GONE
                        btnRetry.visibility = View.GONE
                        CoroutineScope(Dispatchers.Main).launch {
                            kotlinx.coroutines.delay(1500)
                            finish()
                        }
                    }
                    Sender.SendResult.UNREACHABLE -> {
                        tvStatus.text = "Failed (TV unreachable)"
                        btnRetry.visibility = View.VISIBLE
                    }
                    Sender.SendResult.ERROR -> {
                        tvStatus.text = "Failed. Check IP and try again."
                        btnRetry.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun saveToHistory(text: String) {
        val prefs = getSharedPreferences("linkbridge_phone", MODE_PRIVATE)
        val historyJson = prefs.getString(LinkBridgePhoneApp.KEY_SEND_HISTORY, "[]") ?: "[]"
        val history = try {
            gson.fromJson(historyJson, Array<HistoryItem>::class.java).toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }

        history.add(0, HistoryItem(text, System.currentTimeMillis()))
        while (history.size > 5) history.removeAt(history.lastIndex)

        prefs.edit()
            .putString(LinkBridgePhoneApp.KEY_SEND_HISTORY, gson.toJson(history))
            .apply()
    }

    private fun showRecentSends() {
        val prefs = getSharedPreferences("linkbridge_phone", MODE_PRIVATE)
        val historyJson = prefs.getString(LinkBridgePhoneApp.KEY_SEND_HISTORY, "[]") ?: "[]"
        val history = try {
            gson.fromJson(historyJson, Array<HistoryItem>::class.java).toList()
        } catch (_: Exception) {
            emptyList()
        }

        if (history.isEmpty()) {
            tvRecentLabel.visibility = View.GONE
            return
        }

        tvRecentLabel.visibility = View.VISIBLE
        recentLayout.removeAllViews()

        history.take(5).forEach { item ->
            val btn = Button(this).apply {
                text = item.text.take(40) + if (item.text.length > 40) "…" else ""
                textSize = 12f
                setOnClickListener {
                    sharedText = item.text
                    val previewLines = sharedText.lines().take(3).joinToString("\n")
                    tvPreview.text = if (sharedText.lines().size > 3) "$previewLines…" else previewLines
                    btnSend.isEnabled = true
                }
            }
            recentLayout.addView(btn)
        }
    }

    data class HistoryItem(val text: String, val timestamp: Long)
}
