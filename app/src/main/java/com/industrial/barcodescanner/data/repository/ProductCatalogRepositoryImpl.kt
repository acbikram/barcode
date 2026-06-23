package com.industrial.barcodescanner.data.repository

import com.industrial.barcodescanner.data.local.catalog.ProductCatalogDao
import com.industrial.barcodescanner.data.local.catalog.ProductCatalogOpenHelper
import com.industrial.barcodescanner.domain.model.ProductInfo
import com.industrial.barcodescanner.domain.repository.ProductCatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class ProductCatalogRepositoryImpl @Inject constructor(
    private val dao: ProductCatalogDao,
    private val openHelper: ProductCatalogOpenHelper
) : ProductCatalogRepository {

    override suspend fun lookup(barcode: String): ProductInfo? {
        val entry = dao.findByBarcode(barcode) ?: return null
        val name = entry.nameEn?.takeIf { it.isNotBlank() }
        val nameArabic = entry.nameAr?.takeIf { it.isNotBlank() }
        if (name == null && nameArabic == null) return null

        val unit = when (entry.barcodeType?.uppercase()) {
            "EA", "POS" -> entry.uom?.takeIf { it.isNotBlank() } ?: entry.barcodeType
            else         -> entry.barcodeType?.takeIf { it.isNotBlank() } ?: entry.uom
        }

        return ProductInfo(
            barcode = entry.barcode,
            name = name,
            nameArabic = nameArabic,
            unit = unit,
            itemCode = entry.posCode?.takeIf { it.isNotBlank() }
        )
    }

    override suspend fun importFromFile(file: File): Int = withContext(Dispatchers.IO) {
        openHelper.importCatalogFromFile(file)
    }

    override fun catalogLastModified(): Long = openHelper.catalogLastModified()

    override suspend fun countProducts(): Int = dao.countProducts()
}
