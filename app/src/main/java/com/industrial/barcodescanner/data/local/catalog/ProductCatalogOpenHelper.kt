package com.industrial.barcodescanner.data.local.catalog

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductCatalogOpenHelper @Inject constructor(
    @ApplicationContext private val context: Context
) : SQLiteOpenHelper(context, DB_NAME, null, SQLITE_VERSION) {

    companion object {
        private const val DB_NAME        = "products.db"
        private const val ASSET_PATH     = "products.db"
        private const val SQLITE_VERSION = 1
        private const val CATALOG_VERSION = 1
    }

    private val dbFile:      File by lazy { context.getDatabasePath(DB_NAME) }
    private val versionFile: File by lazy { File(dbFile.parentFile, "$DB_NAME.catalog_version") }

    /** The live readable handle — replaced atomically on catalog import. */
    @Volatile private var _db: SQLiteDatabase? = null

    init { copyDatabaseIfNeeded() }

    @Synchronized
    private fun copyDatabaseIfNeeded() {
        val currentVersion = versionFile.takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()
        if (dbFile.exists() && currentVersion == CATALOG_VERSION) return

        dbFile.parentFile?.mkdirs()
        context.assets.open(ASSET_PATH).use { input ->
            dbFile.outputStream().use { output -> input.copyTo(output) }
        }
        versionFile.writeText(CATALOG_VERSION.toString())
    }

    /** Returns an open readable handle. Thread-safe. */
    fun openReadable(): SQLiteDatabase {
        _db?.takeIf { it.isOpen }?.let { return it }
        return synchronized(this) {
            _db?.takeIf { it.isOpen } ?: SQLiteDatabase.openDatabase(
                dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            ).also { _db = it }
        }
    }

    /**
     * Imports a new [products.db] file instantly — no restart required.
     *
     * 1. Validates the file is a real SQLite database.
     * 2. Closes the current handle.
     * 3. Replaces the database file.
     * 4. Reopens the handle.
     * 5. Returns the number of products loaded.
     */
    @Synchronized
    fun importCatalogFromFile(sourceFile: File): Int {
        // Validate SQLite magic bytes
        val header = sourceFile.inputStream().use { it.readNBytes(16) }
        val magic = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)
        if (!header.contentEquals(magic)) {
            throw IllegalArgumentException("File is not a valid SQLite database")
        }

        // Close current handle
        _db?.takeIf { it.isOpen }?.close()
        _db = null

        // Replace file
        dbFile.parentFile?.mkdirs()
        sourceFile.copyTo(dbFile, overwrite = true)
        versionFile.writeText(CATALOG_VERSION.toString())

        // Reopen and count items
        val db = SQLiteDatabase.openDatabase(
            dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
        )
        _db = db
        return db.rawQuery("SELECT COUNT(*) FROM products", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    fun catalogLastModified(): Long = dbFile.lastModified()

    override fun onCreate(db: SQLiteDatabase) {}
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
}
