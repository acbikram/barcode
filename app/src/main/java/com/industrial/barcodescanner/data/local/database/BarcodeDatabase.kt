package com.industrial.barcodescanner.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.industrial.barcodescanner.data.local.dao.ScannedItemDao
import com.industrial.barcodescanner.data.local.entity.ScannedItemEntity

@Database(
    entities = [ScannedItemEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BarcodeDatabase : RoomDatabase() {
    abstract fun scannedItemDao(): ScannedItemDao

    companion object {
        @Volatile
        private var INSTANCE: BarcodeDatabase? = null

        fun getInstance(context: Context): BarcodeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BarcodeDatabase::class.java,
                    "barcode_to_csv_database"
                )
                    // Fresh app — no prior schema versions to migrate from.
                    // Future schema bumps should add explicit Migration objects
                    // here (see the Near Expiry project for the pattern).
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
