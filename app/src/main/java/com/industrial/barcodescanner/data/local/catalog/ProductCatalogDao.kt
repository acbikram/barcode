package com.industrial.barcodescanner.data.local.catalog

import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductCatalogDao @Inject constructor(
    private val openHelper: ProductCatalogOpenHelper
) {
    private val columns = arrayOf("barcode", "pos_code", "name_en", "name_ar", "uom", "barcode_type")

    /**
     * Looks up a scanned barcode in the catalog.
     *
     * Strategy (mirrors Price_Tag_Final.py normalize_barcode logic):
     *   1. Exact match — fastest, covers most cases.
     *   2. Left-strip leading zeros then match — handles scanners that strip zeros.
     *   3. Zero-pad to 13 digits then match — handles scanners that pad to EAN-13.
     *
     * When duplicates exist we prefer "EA" then "POS" entries (single-item
     * barcodes) over carton/offer entries.
     */
    suspend fun findByBarcode(barcode: String): ProductCatalogEntry? = withContext(Dispatchers.IO) {
        if (barcode.isBlank()) return@withContext null
        if (openHelper.isEmpty()) return@withContext null

        val db = openHelper.openReadable()
        val order = "CASE barcode_type WHEN 'EA' THEN 0 WHEN 'POS' THEN 1 WHEN 'CTN' THEN 2 ELSE 3 END"

        // 1. Exact match
        queryOne(db, barcode, order)?.let { return@withContext it }

        // 2. Strip leading zeros (e.g. "0072714834561" → "72714834561")
        val stripped = barcode.trimStart('0')
        if (stripped.isNotEmpty() && stripped != barcode) {
            queryOne(db, stripped, order)?.let { return@withContext it }
        }

        // 3. Zero-pad to 13 digits (EAN-13 normalization)
        if (barcode.length < 13 && barcode.all { it.isDigit() }) {
            val padded = barcode.padStart(13, '0')
            queryOne(db, padded, order)?.let { return@withContext it }
        }

        // 4. If the barcode has no leading zeros but the DB might store it padded
        if (barcode.length in 1..12 && barcode.all { it.isDigit() }) {
            for (len in 13 downTo barcode.length + 1) {
                val padded = barcode.padStart(len, '0')
                queryOne(db, padded, order)?.let { return@withContext it }
            }
        }

        null
    }

    private fun queryOne(
        db: android.database.sqlite.SQLiteDatabase,
        barcode: String,
        order: String
    ): ProductCatalogEntry? {
        return db.query("products", columns, "barcode = ?", arrayOf(barcode), null, null, order, "1")
            .use { cursor -> if (cursor.moveToFirst()) cursor.toEntry() else null }
    }

    suspend fun countProducts(): Int = withContext(Dispatchers.IO) {
        openHelper.countProducts()
    }

    private fun Cursor.toEntry(): ProductCatalogEntry = ProductCatalogEntry(
        barcode      = getString(0),
        posCode      = getStringOrNull(1),
        nameEn       = getStringOrNull(2),
        nameAr       = getStringOrNull(3),
        uom          = getStringOrNull(4),
        barcodeType  = getStringOrNull(5)
    )

    private fun Cursor.getStringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)
}
