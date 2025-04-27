package com.wyldsoft.notes.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wyldsoft.notes.database.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the settings table
 */
@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = :id")
    fun getSettings(id: String = SettingsEntity.DEFAULT_SETTINGS_ID): Flow<SettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: SettingsEntity)

    @Update
    suspend fun updateSettings(settings: SettingsEntity)
}