package com.industrial.barcodescanner.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.industrial.barcodescanner.domain.model.ScannedItem
import kotlinx.serialization.Serializable

/**
 * A scanned barcode queued up for printing on a price tag.
 *
 * There is no expiry-date concept in this app — the purpose is purely to
 * capture barcode -> (item code, tag type, unit type, copies) so it can be
 * exported as a CSV and printed.
 */
@Serializable
@Entity(
    tableName = "scanned_items",
    indices = [Index(value = ["barcode", "tagType", "unitType"])]
)
data class ScannedItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val barcode: String,
    /** POS/Item Code resolved from the local product catalog (column B / pos_code). */
    val itemCode: String? = null,
    /** Display-only product name resolved from the catalog. Never exported to CSV. */
    val productName: String? = null,
    /** Display-only Arabic product name resolved from the catalog. Never exported to CSV. */
    val productNameArabic: String? = null,
    /** A4, 4PCS, 4PCS_DATE, 4PCS_SAME, or VEG. */
    val tagType: String = "A4",
    /** PCS, PKT, CTN, or KGS. */
    val unitType: String = "PCS",
    /** Number of copies of this price tag to print. */
    val copies: Int = 1,
    val createdAt: Long,
    val updatedAt: Long
)

fun ScannedItemEntity.toDomain() = ScannedItem(
    id = id,
    barcode = barcode,
    itemCode = itemCode,
    productName = productName,
    productNameArabic = productNameArabic,
    tagType = tagType,
    unitType = unitType,
    copies = copies,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ScannedItem.toEntity() = ScannedItemEntity(
    id = id,
    barcode = barcode,
    itemCode = itemCode,
    productName = productName,
    productNameArabic = productNameArabic,
    tagType = tagType,
    unitType = unitType,
    copies = copies,
    createdAt = createdAt,
    updatedAt = updatedAt
)
