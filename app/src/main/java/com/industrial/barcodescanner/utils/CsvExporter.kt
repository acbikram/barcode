package com.industrial.barcodescanner.utils

import com.industrial.barcodescanner.domain.model.ScannedItem
import com.opencsv.CSVWriter
import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * Writes scanned items to the CSV format expected by the price-tag printing
 * tool:
 *
 *  A: pos_code     — item code resolved from the catalog (falls back to the
 *                     raw barcode if no catalog match), preserving leading
 *                     zeros since it's written as plain text.
 *  B: price        — always empty (not yet supported).
 *  C: tag_type     — A4 / 4PCS / 4PCS_DATE / 4PCS_SAME / VEG
 *  D: unit_type    — PCS / PKT / CTN / KGS
 *  E: copies       — number of tags to print
 *  F: custom_eng   — always empty
 *  G: custom_ara   — always empty
 *
 * No product names/descriptions are exported.
 */
object CsvExporter {

    private const val HEADER_POS_CODE = "pos_code"
    private const val HEADER_PRICE = "price"
    private const val HEADER_TAG_TYPE = "tag_type"
    private const val HEADER_UNIT_TYPE = "unit_type"
    private const val HEADER_COPIES = "copies"
    private const val HEADER_CUSTOM_ENG = "custom_eng"
    private const val HEADER_CUSTOM_ARA = "custom_ara"

    fun writeCsv(outputStream: OutputStream, items: List<ScannedItem>) {
        // UTF-8 BOM so Excel opens the file with correct encoding/leading zeros intact.
        outputStream.write(0xEF)
        outputStream.write(0xBB)
        outputStream.write(0xBF)

        CSVWriter(OutputStreamWriter(outputStream)).use { writer ->
            writer.writeNext(
                arrayOf(
                    HEADER_POS_CODE,
                    HEADER_PRICE,
                    HEADER_TAG_TYPE,
                    HEADER_UNIT_TYPE,
                    HEADER_COPIES,
                    HEADER_CUSTOM_ENG,
                    HEADER_CUSTOM_ARA
                )
            )
            items.forEach { item ->
                val posCode = item.itemCode?.takeIf { it.isNotBlank() } ?: item.barcode
                writer.writeNext(
                    arrayOf(
                        posCode,
                        "",
                        item.tagType,
                        item.unitType,
                        item.copies.toString(),
                        "",
                        ""
                    )
                )
            }
        }
    }
}
