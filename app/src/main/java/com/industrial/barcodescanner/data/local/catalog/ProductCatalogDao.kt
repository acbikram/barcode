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
     * Looks up a scanned barcode in the bundled catalog.
     *
     * The source data contains a handful of duplicate barcodes (e.g. the
     * same product listed once as "EA" - each/unit - and once as "CTN" -
     * carton). When duplicates exist we prefer the "EA" entry since that's
     * what a shopper actually scans off a single item.
     */
    suspend fun findByBarcode(barcode: String): ProductCatalogEntry? = withContext(Dispatchers.IO) {
        val db = openHelper.openReadable()
        val order =
            "CASE barcode_type WHEN 'EA' THEN 0 WHEN 'POS' THEN 1 WHEN 'CTN' THEN 2 ELSE 3 END"

        fun queryOne(where: String, arg: String): ProductCatalogEntry? =
            db.query("products", columns, where, arrayOf(arg), null, null, order, "1")
                .use { cursor -> if (cursor.moveToFirst()) cursor.toProductCatalogEntry() else null }

        // 1) Exact match — uses the barcode index, covers the common case.
        queryOne("barcode = ?", barcode)?.let { return@withContext it }

        // 2) Leading-zero tolerant match. The master stores barcodes like
        //    "072714834561" / "070136" with their leading zeros, but a scanner
        //    may return them stripped ("72714834561") or zero-padded
        //    ("0072714834561", UPC-A vs EAN-13). Compare both sides with leading
        //    zeros removed so they still resolve. Only runs when the exact match
        //    misses, so normal scans stay fast.
        val stripped = barcode.trimStart('0')
        if (stripped.isNotEmpty()) {
            queryOne("ltrim(barcode, '0') = ?", stripped)?.let { return@withContext it }
        }
        null
    }

    private fun Cursor.toProductCatalogEntry(): ProductCatalogEntry = ProductCatalogEntry(
        barcode = getString(0),
        posCode = getStringOrNull(1),
        nameEn = getStringOrNull(2),
        nameAr = getStringOrNull(3),
        uom = getStringOrNull(4),
        barcodeType = getStringOrNull(5)
    )

    private fun Cursor.getStringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)
}
