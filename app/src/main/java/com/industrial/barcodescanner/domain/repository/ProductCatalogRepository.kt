package com.industrial.barcodescanner.domain.repository

import com.industrial.barcodescanner.domain.model.ProductInfo
import java.io.File

interface ProductCatalogRepository {
    suspend fun lookup(barcode: String): ProductInfo?
    /** Imports a new products.db; returns item count immediately without restart. */
    suspend fun importFromFile(file: File): Int
    fun catalogLastModified(): Long
    suspend fun countProducts(): Int
}
