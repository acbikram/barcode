package com.industrial.barcodescanner.domain.model

import com.industrial.barcodescanner.utils.LanguageManager

data class ScannedItem(
    val id: Long,
    val barcode: String,
    val itemCode: String? = null,
    val productName: String? = null,
    val productNameArabic: String? = null,
    val tagType: String = "A4",
    val unitType: String = "PCS",
    val copies: Int = 1,
    val createdAt: Long,
    val updatedAt: Long
) {
    /**
     * The product description shown in the UI, in the user's selected
     * app language: Arabic name first when the app is in Arabic (falling
     * back to the English name if no Arabic name is available), otherwise
     * the English name. Falls back further to [itemCode] then [barcode]
     * if no product name was resolved at all.
     *
     * This is purely for on-screen display — the CSV export never includes
     * product names, only [itemCode] (or [barcode] as a fallback).
     */
    val displayName: String
        get() = if (LanguageManager.isArabic()) {
            productNameArabic?.takeIf { it.isNotBlank() }
                ?: productName?.takeIf { it.isNotBlank() }
                ?: itemCode
                ?: barcode
        } else {
            productName?.takeIf { it.isNotBlank() }
                ?: itemCode
                ?: barcode
        }
}
