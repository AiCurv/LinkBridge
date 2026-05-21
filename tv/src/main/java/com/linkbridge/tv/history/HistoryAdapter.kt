package com.linkbridge.tv.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.linkbridge.common.model.LinkCategory
import com.linkbridge.tv.R
import com.linkbridge.tv.database.LinkEntity

class HistoryAdapter(
    private val onCopyClick: (LinkEntity) -> Unit,
    private val onOpenClick: (LinkEntity) -> Unit,
    private val onFavoriteClick: (LinkEntity) -> Unit,
    private val onDeleteClick: (LinkEntity) -> Unit,
    private val onTextClick: (LinkEntity) -> Unit,
    private val onSelectChanged: (LinkEntity, Boolean) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private var items: List<LinkEntity> = emptyList()
    var bulkMode: Boolean = false

    fun submitList(newItems: List<LinkEntity>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, bulkMode)
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cbSelect: CheckBox = view.findViewById(R.id.cbSelect)
        private val btnCopy: ImageButton = view.findViewById(R.id.btnCopy)
        private val tvCategoryChip: TextView = view.findViewById(R.id.tvCategoryChip)
        private val tvLinkText: TextView = view.findViewById(R.id.tvLinkText)
        private val btnFavorite: ImageButton = view.findViewById(R.id.btnFavorite)
        private val btnOpen: ImageButton = view.findViewById(R.id.btnOpen)
        private val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)

        fun bind(item: LinkEntity, bulkMode: Boolean) {
            tvLinkText.text = item.text

            // Category chip
            val category = try { LinkCategory.valueOf(item.category) } catch (_: Exception) { LinkCategory.TEXT }
            tvCategoryChip.text = category.label
            tvCategoryChip.setBackgroundColor(getChipColor(category))

            // Favorite
            btnFavorite.setImageResource(
                if (item.isFavorite) R.drawable.ic_star_on else R.drawable.ic_star_off
            )

            // Bulk select mode
            cbSelect.visibility = if (bulkMode) View.VISIBLE else View.GONE

            // Click listeners
            btnCopy.setOnClickListener { onCopyClick(item) }
            btnOpen.setOnClickListener { onOpenClick(item) }
            btnFavorite.setOnClickListener { onFavoriteClick(item) }
            btnDelete.setOnClickListener { onDeleteClick(item) }
            tvLinkText.setOnClickListener { onTextClick(item) }
            cbSelect.setOnCheckedChangeListener(null)
            cbSelect.isChecked = false
            cbSelect.setOnCheckedChangeListener { _, isChecked -> onSelectChanged(item, isChecked) }
        }

        private fun getChipColor(category: LinkCategory): Int {
            return when (category) {
                LinkCategory.WEB -> 0xFF4CAF50.toInt()
                LinkCategory.MAGNET -> 0xFFFF9800.toInt()
                LinkCategory.VIDEO -> 0xFFF44336.toInt()
                LinkCategory.TORRENT -> 0xFFFF5722.toInt()
                LinkCategory.TEXT -> 0xFF607D8B.toInt()
            }
        }
    }
}
