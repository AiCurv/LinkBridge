package com.linkbridge.tv.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface LinkDao {

    @Query("SELECT * FROM link_history ORDER BY timestamp DESC")
    fun getAll(): LiveData<List<LinkEntity>>

    @Query("SELECT * FROM link_history ORDER BY timestamp DESC")
    suspend fun getAllSync(): List<LinkEntity>

    @Query("SELECT * FROM link_history WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavorites(): LiveData<List<LinkEntity>>

    @Query("SELECT * FROM link_history WHERE text LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun search(query: String): LiveData<List<LinkEntity>>

    @Query("SELECT * FROM link_history WHERE id = :id")
    suspend fun getById(id: String): LinkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(link: LinkEntity)

    @Delete
    suspend fun delete(link: LinkEntity)

    @Query("DELETE FROM link_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM link_history")
    suspend fun deleteAll()

    @Query("UPDATE link_history SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: String, fav: Boolean)

    @Query("DELETE FROM link_history WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}
