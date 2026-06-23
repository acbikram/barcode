package com.industrial.barcodescanner.domain.repository

import com.industrial.barcodescanner.domain.model.ProductInfo
import java.io.File

interface ProductCatalogRepository {
    /** Returns product info for [barcode], or null if it's not in the catalog. */
    suspend fun lookup(barcode: String): ProductInfo?

    /** Replaces the working catalog with the supplied .db file. */
    suspend fun importFromFile(file: File)

    /** Timestamp (epoch ms) of the currently active catalog file. */
    fun catalogLastModified(): Long
}
