package com.industrial.barcodescanner.data.local.catalog

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opens the product catalog database.
 *
 * The app no longer ships a bundled catalog. It starts with an EMPTY `products`
 * table and the catalog is loaded later by the user — either by importing a file
 * or by pulling the latest `products.db` from the PC over WiFi
 * ("Update catalog from PC"). This keeps the APK small and the catalog always
 * up to date with the PC's master.
 */
@Singleton
class ProductCatalogOpenHelper @Inject constructor(
    @ApplicationContext private val context: Context
) : SQLiteOpenHelper(context, DB_NAME, null, SQLITE_VERSION) {

    companion object {
        private const val DB_NAME = "products.db"
        private const val SQLITE_VERSION = 1
    }

    private val dbFile: File by lazy { context.getDatabasePath(DB_NAME) }

    /**
     * Returns a readable handle to the catalog.
     * Synchronized on [this] so callers block while a catalog import is
     * swapping the underlying file inside [replaceWithSqlite].
     */
    fun openReadable(): SQLiteDatabase = synchronized(this) { readableDatabase }

    /** Number of products currently loaded (0 means the catalog is empty). */
    fun productCount(): Int = try {
        openReadable().rawQuery("SELECT COUNT(*) FROM products", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    } catch (_: Exception) { 0 }

    /**
     * Replace the catalog contents from a provided stream. Accepts either:
     *   • a prebuilt products.db SQLite file (swapped in directly), or
     *   • a CSV exported from the master (columns matched by header name).
     * Returns the number of products afterwards. Throws on an Excel/.xlsx file.
     */
    @Synchronized
    fun importCatalog(rawInput: java.io.InputStream): Int {
        val input = java.io.BufferedInputStream(rawInput)
        input.mark(64)
        val prefix = ByteArray(16)
        val read = input.read(prefix).coerceAtLeast(0)
        input.reset()
        val head = String(prefix, 0, read, Charsets.ISO_8859_1)
        return when {
            head.startsWith("SQLite format 3") -> replaceWithSqlite(input)
            read >= 2 && prefix[0] == 'P'.code.toByte() && prefix[1] == 'K'.code.toByte() ->
                throw IllegalArgumentException(
                    "This looks like an Excel (.xlsx) file. Please save it as CSV (UTF-8) and import that.")
            else -> importCsv(input)
        }
    }

    private fun replaceWithSqlite(input: java.io.InputStream): Int {
        // Close WAL/SHM journal files and the underlying database connection,
        // but do NOT call the outer SQLiteOpenHelper.close() since we are a
        // @Singleton and other coroutines may be mid-read. We take the
        // synchronized lock so no new reads start while we swap the file.
        synchronized(this) {
            // Force-close only the internal database handle.
            try { super.getWritableDatabase()?.close() } catch (_: Exception) {}
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            dbFile.parentFile?.mkdirs()
            dbFile.outputStream().use { input.copyTo(it) }
        }
        return readableDatabase.rawQuery("SELECT COUNT(*) FROM products", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    private fun importCsv(input: java.io.InputStream): Int {
        val reader = com.opencsv.CSVReader(java.io.InputStreamReader(input, Charsets.UTF_8))
        val header = reader.readNext() ?: throw IllegalArgumentException("The CSV file is empty.")
        if (header.isNotEmpty()) header[0] = header[0].removePrefix("\uFEFF")  // strip BOM
        val hl = header.map { it.trim().lowercase() }
        val iType = hl.indexOfFirst { it.contains("barcode") && it.contains("type") }
        val iBarcode = hl.indexOfFirst { it.contains("barcode") && !it.contains("type") }
        val iPos = hl.indexOfFirst { it.contains("pos") }
        val iEng = hl.indexOfFirst { it.contains("eng") || it.contains("english") || (it.contains("desc") && !it.contains("ara")) }
        val iAra = hl.indexOfFirst { it.contains("ara") }
        val iUom = hl.indexOfFirst { it.contains("uom") || it.contains("prm") }
        if (iBarcode < 0 || iPos < 0) {
            reader.close()
            throw IllegalArgumentException("CSV needs at least 'Barcode' and 'Pos Code' columns.")
        }
        fun cell(row: Array<String>, idx: Int) = if (idx in row.indices) row[idx].trim() else ""
        val db = writableDatabase
        var count = 0
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM products")
            val stmt = db.compileStatement(
                "INSERT INTO products (barcode,pos_code,name_en,name_ar,uom,barcode_type) VALUES (?,?,?,?,?,?)")
            var row = reader.readNext()
            while (row != null) {
                val bc = cell(row, iBarcode)
                if (bc.isNotEmpty()) {
                    stmt.clearBindings()
                    stmt.bindString(1, bc)
                    stmt.bindString(2, cell(row, iPos))
                    stmt.bindString(3, cell(row, iEng))
                    stmt.bindString(4, cell(row, iAra))
                    stmt.bindString(5, cell(row, iUom))
                    stmt.bindString(6, cell(row, iType))
                    stmt.executeInsert()
                    count++
                }
                row = reader.readNext()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            reader.close()
        }
        return count
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Start with an empty catalog; the user loads data via import / WiFi pull.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS products (" +
                "barcode TEXT NOT NULL, pos_code TEXT, name_en TEXT, " +
                "name_ar TEXT, uom TEXT, barcode_type TEXT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_products_barcode ON products(barcode)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onCreate(db)
    }
}
