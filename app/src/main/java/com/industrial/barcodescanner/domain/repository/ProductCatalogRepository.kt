package com.industrial.barcodescanner.domain.repository

import com.industrial.barcodescanner.domain.model.ProductInfo

interface ProductCatalogRepository {
    /** Returns product info for [barcode], or null if it's not in the catalog. */
    suspend fun lookup(barcode: String): ProductInfo?
}
