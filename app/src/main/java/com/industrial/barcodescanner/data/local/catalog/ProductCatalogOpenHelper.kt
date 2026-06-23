package com.industrial.barcodescanner.data.local.catalog

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the product catalog SQLite database.
 *
 * The catalog is no longer bundled in the APK — the app starts with an
 * empty database and the user pulls the latest catalog from their PC over
 * WiFi using the "Get catalog from PC" button in Settings.
 *
 * Schema (matches what Price_Tag_Final.py writes via export_master_to_mobile_db):
 *   TABLE products (
 *       barcode      TEXT NOT NULL,
 *       pos_code     TEXT,
 *       name_en      TEXT,
 *       name_ar      TEXT,
 *       uom          TEXT,
 *       barcode_type TEXT
 *   )
 *   INDEX idx_products_barcode ON products(barcode)
 */
@Singleton
class ProductCatalogOpenHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val DB_NAME = "products.db"
    }

    private val dbFile: File by lazy { context.getDatabasePath(DB_NAME) }

    /** Live readable handle; replaced atomically on catalog import. */
    @Volatile private var _db: SQLiteDatabase? = null

    init { ensureDatabase() }

    /**
     * On first launch (no .db yet) creates an empty database with the
     * correct schema so lookups return null gracefully instead of crashing.
     */
    @Synchronized
    private fun ensureDatabase() {
        if (dbFile.exists()) return
        dbFile.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS products (" +
                "barcode TEXT NOT NULL, pos_code TEXT, name_en TEXT, " +
                "name_ar TEXT, uom TEXT, barcode_type TEXT)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_products_barcode ON products(barcode)")
        db.close()
    }

    /** Returns an open readable handle. Thread-safe, fast after first call. */
    fun openReadable(): SQLiteDatabase {
        _db?.takeIf { it.isOpen }?.let { return it }
        return synchronized(this) {
            _db?.takeIf { it.isOpen } ?: SQLiteDatabase.openDatabase(
                dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            ).also { _db = it }
        }
    }

    /**
     * Replaces the working catalog instantly — no restart required.
     * 1. Validates the file is a real SQLite database.
     * 2. Closes the current handle.
     * 3. Replaces the database file.
     * 4. Reopens the handle.
     * 5. Returns the number of products loaded.
     */
    @Synchronized
    fun importCatalogFromFile(sourceFile: File): Int {
        val header = sourceFile.inputStream().use { it.readNBytes(16) }
        val magic = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)
        if (!header.contentEquals(magic)) {
            throw IllegalArgumentException("File is not a valid SQLite database")
        }
        _db?.takeIf { it.isOpen }?.close()
        _db = null
        dbFile.parentFile?.mkdirs()
        sourceFile.copyTo(dbFile, overwrite = true)
        val db = SQLiteDatabase.openDatabase(
            dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
        )
        _db = db
        return db.rawQuery("SELECT COUNT(*) FROM products", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun catalogLastModified(): Long = dbFile.lastModified()

    fun countProducts(): Int {
        return try {
            openReadable().rawQuery("SELECT COUNT(*) FROM products", null).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        } catch (_: Exception) { 0 }
    }

    fun isEmpty(): Boolean = countProducts() == 0
}
