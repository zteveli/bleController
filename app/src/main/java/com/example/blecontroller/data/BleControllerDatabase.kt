package com.example.blecontroller.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [LayoutPresetEntity::class, VirtualButtonEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class BleControllerDatabase : RoomDatabase() {
    abstract fun layoutDao(): LayoutDao

    companion object {
        @Volatile
        private var INSTANCE: BleControllerDatabase? = null

        fun getInstance(context: Context): BleControllerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BleControllerDatabase::class.java,
                    "ble_controller.db",
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
