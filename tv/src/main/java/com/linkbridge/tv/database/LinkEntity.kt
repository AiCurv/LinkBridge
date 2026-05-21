package com.linkbridge.tv.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.linkbridge.common.model.LinkCategory

@Entity(
    tableName = "link_history",
    indices = [Index(value = ["timestamp"], orders = [androidx.room.Index.Order.DESC])]
)
data class LinkEntity(
    @PrimaryKey
    val id: String,
    val text: String,
    val timestamp: Long,
    val category: String,     // LinkCategory name
    val isFavorite: Boolean = false,
    val senderDevice: String = ""
)
