package com.industrial.barcodescanner.utils

import com.industrial.barcodescanner.domain.model.ScannedItem
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {

    @Test
    fun writeCsv_writesBomHeaderAndCatalogItemCode() {
        val output = ByteArrayOutputStream()
        CsvExporter.writeCsv(
            output,
            listOf(
                ScannedItem(
                    id = 1,
                    barcode = "0012345678905",
                    itemCode = "00042",
                    tagType = "A4",
                    unitType = "PCS",
                    copies = 3,
                    createdAt = 1L,
                    updatedAt = 1L
                )
            )
        )

        val bytes = output.toByteArray()
        assertArrayEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), bytes.copyOfRange(0, 3))
        val csv = bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
        assertTrue(csv.contains("pos_code"))
        assertTrue(csv.contains("00042"))
        assertTrue(csv.contains("A4"))
        assertTrue(csv.contains("PCS"))
        assertTrue(csv.contains("3"))
    }

    @Test
    fun writeCsv_usesBarcodeWhenCatalogItemCodeIsMissing() {
        val output = ByteArrayOutputStream()
        CsvExporter.writeCsv(
            output,
            listOf(
                ScannedItem(
                    id = 2,
                    barcode = "00999",
                    itemCode = "",
                    tagType = "VEG",
                    unitType = "KGS",
                    copies = 1,
                    createdAt = 1L,
                    updatedAt = 1L
                )
            )
        )

        val csv = output.toByteArray().copyOfRange(3, output.size()).toString(Charsets.UTF_8)
        assertTrue(csv.contains("00999"))
        assertTrue(csv.contains("VEG"))
        assertTrue(csv.contains("KGS"))
    }
}
