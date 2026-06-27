package com.industrial.barcodescanner.data.repository

import com.industrial.barcodescanner.data.local.catalog.ProductCatalogDao
import com.industrial.barcodescanner.domain.model.ProductInfo
import com.industrial.barcodescanner.domain.repository.ProductCatalogRepository
import javax.inject.Inject

class ProductCatalogRepositoryImpl @Inject constructor(
    private val dao: ProductCatalogDao
) : ProductCatalogRepository {

    override suspend fun lookup(barcode: String): ProductInfo? {
        val entry = dao.findByBarcode(barcode) ?: return null
        val name = entry.nameEn?.takeIf { it.isNotBlank() }
        val nameArabic = entry.nameAr?.takeIf { it.isNotBlank() }
        if (name == null && nameArabic == null) return null

        // Unit logic (from the master's Barcode Type, column F):
        //  • EA or POS   → use Prm Uom (column E)            e.g. "PCS", "KGS"
        //  • PKT or OFR  → PKT (offer barcodes print as PKT)
        //  • CTN / else  → use the barcode type itself        e.g. "CTN"
        val unit = when (entry.barcodeType?.uppercase()) {
            "EA", "POS"  -> entry.uom?.takeIf { it.isNotBlank() } ?: entry.barcodeType
            "PKT", "OFR" -> "PKT"
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
}
