package com.linkbridge.tv

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.linkbridge.common.model.LinkCategory
import com.linkbridge.common.network.Protocol
import com.linkbridge.common.util.NetworkUtil
import com.linkbridge.tv.database.AppDatabase
import com.linkbridge.tv.database.LinkEntity
import com.linkbridge.tv.history.HistoryAdapter
import com.linkbridge.tv.server.LinkBridgeServerService
import com.linkbridge.tv.util.IntentHelper
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var tvIpAddress: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvReceivedOverlay: TextView
    private lateinit var etSearch: EditText
    private lateinit var rvHistory: RecyclerView
    private lateinit var emptyState: View
    private lateinit var btnFavoritesOnly: Button
    private lateinit var btnBulkSelect: Button
    private lateinit var btnBulkDelete: Button
    private lateinit var btnClearAll: Button
    private lateinit var btnExport: Button

    private lateinit var adapter: HistoryAdapter
    private lateinit var db: AppDatabase

    private var showingFavoritesOnly = false
    private var bulkMode = false
    private val selectedIds = mutableSetOf<String>()
    private var lastReceivedTimestamp = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val gson = Gson()

    private val statusUpdateRunnable = object : Runnable {
        override fun run() {
            if (lastReceivedTimestamp > 0) {
                val secondsAgo = (System.currentTimeMillis() - lastReceivedTimestamp) / 1000
                tvStatus.text = getString(R.string.last_received, secondsAgo)
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = (application as LinkBridgeTvApp).database

        // Bind views
        tvIpAddress = findViewById(R.id.tvIpAddress)
        tvStatus = findViewById(R.id.tvStatus)
        tvReceivedOverlay = findViewById(R.id.tvReceivedOverlay)
        etSearch = findViewById(R.id.etSearch)
        rvHistory = findViewById(R.id.rvHistory)
        emptyState = findViewById(R.id.emptyState)
        btnFavoritesOnly = findViewById(R.id.btnFavoritesOnly)
        btnBulkSelect = findViewById(R.id.btnBulkSelect)
        btnBulkDelete = findViewById(R.id.btnBulkDelete)
        btnClearAll = findViewById(R.id.btnClearAll)
        btnExport = findViewById(R.id.btnExport)

        // Show IP
        displayIpAddress()

        // Setup RecyclerView
        adapter = HistoryAdapter(
            onCopyClick = { item -> copyItem(item) },
            onOpenClick = { item -> openItem(item) },
            onFavoriteClick = { item -> toggleFavorite(item) },
            onDeleteClick = { item -> deleteItem(item) },
            onTextClick = { item -> openItem(item) },
            onSelectChanged = { item, selected ->
                if (selected) selectedIds.add(item.id) else selectedIds.remove(item.id)
                btnBulkDelete.visibility = if (selectedIds.isNotEmpty()) View.VISIBLE else View.GONE
            }
        )
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = adapter

        // Observe all items initially
        observeHistory()

        // Start server service
        startServerService()

        // Search/filter
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isEmpty()) {
                    observeHistory()
                } else {
                    observeSearch(query)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Buttons
        btnFavoritesOnly.setOnClickListener { toggleFavoritesFilter() }
        btnBulkSelect.setOnClickListener { toggleBulkMode() }
        btnBulkDelete.setOnClickListener { bulkDelete() }
        btnClearAll.setOnClickListener { confirmClearAll() }
        btnExport.setOnClickListener { exportHistory() }

        // Start status updater
        handler.post(statusUpdateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(statusUpdateRunnable)
    }

    override fun onResume() {
        super.onResume()
        displayIpAddress()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Handle any intent while the activity is already open
    }

    private fun displayIpAddress() {
        val ip = NetworkUtil.getLocalIpAddress()
        tvIpAddress.text = if (ip != null) "$ip:${Protocol.DEFAULT_PORT}" else getString(R.string.no_ip)
    }

    private fun startServerService() {
        val serviceIntent = Intent(this, LinkBridgeServerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private var currentObserver: androidx.lifecycle.Observer<List<LinkEntity>>? = null
    private var currentLiveData: androidx.lifecycle.LiveData<List<LinkEntity>>? = null

    private fun observeHistory() {
        removeCurrentObserver()
        showingFavoritesOnly = false
        btnFavoritesOnly.text = "★ Favorites"
        val liveData = db.linkDao().getAll()
        currentLiveData = liveData
        val observer = androidx.lifecycle.Observer<List<LinkEntity>> { items ->
            updateList(items)
        }
        currentObserver = observer
        liveData.observe(this, observer)
    }

    private fun observeFavorites() {
        removeCurrentObserver()
        showingFavoritesOnly = true
        btnFavoritesOnly.text = "★ All"
        val liveData = db.linkDao().getFavorites()
        currentLiveData = liveData
        val observer = androidx.lifecycle.Observer<List<LinkEntity>> { items ->
            updateList(items)
        }
        currentObserver = observer
        liveData.observe(this, observer)
    }

    private fun observeSearch(query: String) {
        removeCurrentObserver()
        val liveData = db.linkDao().search(query)
        currentLiveData = liveData
        val observer = androidx.lifecycle.Observer<List<LinkEntity>> { items ->
            updateList(items)
        }
        currentObserver = observer
        liveData.observe(this, observer)
    }

    private fun removeCurrentObserver() {
        currentObserver?.let { obs ->
            currentLiveData?.removeObserver(obs)
        }
        currentObserver = null
        currentLiveData = null
    }

    private fun updateList(items: List<LinkEntity>) {
        adapter.submitList(items)
        emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        rvHistory.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE

        // Track last received timestamp
        if (items.isNotEmpty()) {
            lastReceivedTimestamp = items.first().timestamp
        }
    }

    private fun copyItem(item: LinkEntity) {
        IntentHelper.copyToClipboard(this, item.text)
        Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show()
    }

    private fun openItem(item: LinkEntity) {
        IntentHelper.openWith(this, item.text)
    }

    private fun toggleFavorite(item: LinkEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            db.linkDao().setFavorite(item.id, !item.isFavorite)
        }
    }

    private fun deleteItem(item: LinkEntity) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_delete))
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.linkDao().deleteById(item.id)
                }
                Toast.makeText(this, getString(R.string.deleted), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toggleFavoritesFilter() {
        if (showingFavoritesOnly) {
            observeHistory()
        } else {
            observeFavorites()
        }
    }

    private fun toggleBulkMode() {
        bulkMode = !bulkMode
        adapter.bulkMode = bulkMode
        selectedIds.clear()
        btnBulkDelete.visibility = View.GONE
        btnBulkSelect.text = if (bulkMode) "Cancel" else "Select"
        adapter.notifyDataSetChanged()
    }

    private fun bulkDelete() {
        if (selectedIds.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Delete ${selectedIds.size} items?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.linkDao().deleteByIds(selectedIds.toList())
                }
                selectedIds.clear()
                btnBulkDelete.visibility = View.GONE
                bulkMode = false
                adapter.bulkMode = false
                btnBulkSelect.text = "Select"
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmClearAll() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_clear))
            .setPositiveButton("Clear All") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.linkDao().deleteAll()
                }
                Toast.makeText(this, getString(R.string.cleared), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportHistory() {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                db.linkDao().getAllSync()
            }
            withContext(Dispatchers.IO) {
                try {
                    val dir = File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "LinkBridge")
                    dir.mkdirs()
                    val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    val fileName = "linkbridge_history_${sdf.format(Date())}.json"
                    val file = File(dir, fileName)
                    file.writeText(gson.toJson(items))

                    // Also copy to public Downloads if possible
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        @Suppress("DEPRECATION")
                        val publicDir = File(android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DOWNLOADS), "LinkBridge")
                        publicDir.mkdirs()
                        val publicFile = File(publicDir, fileName)
                        file.copyTo(publicFile, overwrite = true)
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, getString(R.string.exported), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /**
     * Called from the HTTP server when a new link is received while the activity is open.
     * Shows a brief overlay notification.
     */
    fun onLinkReceived(entity: LinkEntity) {
        lastReceivedTimestamp = entity.timestamp
        tvReceivedOverlay.visibility = View.VISIBLE
        tvReceivedOverlay.text = "Received! Opening..."
        handler.postDelayed({
            tvReceivedOverlay.visibility = View.GONE
        }, 3000)
    }
}
