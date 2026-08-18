package com.industrial.barcodescanner.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.industrial.barcodescanner.data.local.dao.ScannedItemDao
import com.industrial.barcodescanner.data.local.dao.WifiPrintHistoryDao
import com.industrial.barcodescanner.data.local.entity.ScannedItemEntity
import com.industrial.barcodescanner.data.local.entity.WifiPrintHistoryEntity

@Database(
    entities = [ScannedItemEntity::class, WifiPrintHistoryEntity::class],
    version = 5,
    exportSchema = false
)
abstract class BarcodeDatabase : RoomDatabase() {
    abstract fun scannedItemDao(): ScannedItemDao
    abstract fun wifiPrintHistoryDao(): WifiPrintHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: BarcodeDatabase? = null

        // v1 -> v2: add the Wi-Fi print history table WITHOUT touching the
        // existing scanned_items data (so a destructive wipe is avoided).
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `wifi_print_history` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`posCode` TEXT NOT NULL, " +
                        "`englishDesc` TEXT NOT NULL, " +
                        "`unitType` TEXT NOT NULL, " +
                        "`copies` INTEGER NOT NULL, " +
                        "`tagType` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`reason` TEXT NOT NULL, " +
                        "`jobId` INTEGER NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wifi_print_history_jobId` ON `wifi_print_history` (`jobId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wifi_print_history_timestamp` ON `wifi_print_history` (`timestamp`)")
            }
        }

        // v2 -> v3: the Wi-Fi history changed to a per-physical-page model
        // (one row per printed sheet + failed items). The table is recreated
        // with the new schema. Only the (new, rarely populated) Wi-Fi history
        // is reset; scanned_items is untouched.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `wifi_print_history`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `wifi_print_history` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`jobId` INTEGER NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`tagType` TEXT NOT NULL, " +
                        "`unitType` TEXT NOT NULL, " +
                        "`copies` INTEGER NOT NULL, " +
                        "`nTags` INTEGER NOT NULL, " +
                        "`summary` TEXT NOT NULL, " +
                        "`posCode` TEXT NOT NULL, " +
                        "`price` TEXT NOT NULL, " +
                        "`reason` TEXT NOT NULL, " +
                        "`itemsJson` TEXT NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wifi_print_history_jobId` ON `wifi_print_history` (`jobId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wifi_print_history_timestamp` ON `wifi_print_history` (`timestamp`)")
            }
        }

        // v3 -> v4: add an index for the ORDER BY createdAt DESC queries used
        // by dashboard and scanner recent-item feeds.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_scanned_items_createdAt` " +
                        "ON `scanned_items` (`createdAt`)"
                )
            }
        }

        // v4 -> v5: keep user records recoverable after deletion.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `scanned_items` ADD COLUMN `deletedAt` INTEGER")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_scanned_items_deletedAt` " +
                        "ON `scanned_items` (`deletedAt`)"
                )
            }
        }

        fun getInstance(context: Context): BarcodeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BarcodeDatabase::class.java,
                    "barcode_to_csv_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
