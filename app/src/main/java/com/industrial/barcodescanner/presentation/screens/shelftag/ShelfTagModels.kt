package com.industrial.barcodescanner.presentation.screens.shelftag

import org.json.JSONObject

data class ShelfTagItem(
    val posCode: String,
    val idItmPos: String,
    val englishDescription: String,
    val arabicDescription: String,
    val eachUomEng: String,
    val eachUomArb: String,
    val eachPrice: String,
    val offerUomEng: String,
    val offerUomArb: String,
    val offerPrice: String,
    val cartonUomEng: String,
    val cartonUomArb: String,
    val cartonPrice: String,
    val country: String,
    val vatPercentage: Double
) {
    val currency: String get() = when (country) { "BH" -> "BHD"; "KSA" -> "SAR"; else -> "--" }
    val currencyArabic: String get() = when (country) { "BH" -> " دينار بحريني"; "KSA" -> "ريال سعودي"; else -> "--" }
    val hasOffer: Boolean get() = offerPrice.toDoubleOrNull()?.let { it > 0.0 && it <= 9999.0 } == true && cartonPrice.toDoubleOrNull()?.let { it > 0.0 && it <= 9999.0 } == true

    companion object {
        fun fromJson(json: JSONObject): ShelfTagItem = ShelfTagItem(
            posCode = json.optString("POS_CODE"),
            idItmPos = json.optString("ID_ITM_POS"),
            englishDescription = json.optString("ENGLISH_DESCRIPTION"),
            arabicDescription = json.optString("ARABIC_DESCRIPTION"),
            eachUomEng = json.optString("EACH_UOM_ENG"),
            eachUomArb = json.optString("EACH_UOM_ARB"),
            eachPrice = json.optString("EACHPRICE"),
            offerUomEng = json.optString("OFR_UOM_ENG"),
            offerUomArb = json.optString("OFR_UOM_ARB"),
            offerPrice = json.optString("OFFERPRICE"),
            cartonUomEng = json.optString("CTN_UOM_ENG"),
            cartonUomArb = json.optString("CTN_UOM_ARB"),
            cartonPrice = json.optString("CASEPRICE"),
            country = json.optString("OPR_COUNTRY"),
            vatPercentage = json.optDouble("VAT_PERCENTAGE", 0.0)
        )
    }
}

class ShelfTagLookupException(message: String) : Exception(message)
