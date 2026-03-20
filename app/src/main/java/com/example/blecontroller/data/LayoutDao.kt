package com.example.blecontroller.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LayoutDao {
    @Transaction
    @Query("SELECT * FROM layouts ORDER BY updatedAt DESC, id DESC")
    fun observeLayouts(): Flow<List<LayoutWithButtons>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLayout(entity: LayoutPresetEntity): Long

    @Update
    suspend fun updateLayout(entity: LayoutPresetEntity)

    @Delete
    suspend fun deleteLayout(entity: LayoutPresetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertButton(entity: VirtualButtonEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertButtons(entities: List<VirtualButtonEntity>)

    @Update
    suspend fun updateButton(entity: VirtualButtonEntity)

    @Delete
    suspend fun deleteButton(entity: VirtualButtonEntity)

    @Query("SELECT COUNT(*) FROM layouts")
    suspend fun layoutCount(): Int
}
