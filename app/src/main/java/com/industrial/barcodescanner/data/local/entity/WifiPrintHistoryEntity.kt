package com.industrial.barcodescanner.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One ROW of the Wi-Fi print history — the phone-side mirror of the PC
 * Dashboard's "Recent Print Jobs". A row is either:
 *
 *  • kind = "sheet"  → one physical page that printed (1–4 items on it), with
 *                      one Reprint button, exactly like a Dashboard row.
 *  • kind = "failed" → one item that could NOT be printed (no price / no
 *                      description), shown in the job's "Failed" section.
 *
 * Items on a sheet are stored as JSON in [itemsJson] as
 * [{pos, eng, unit, copies, price}], so reprinting can rebuild the same page.
 * English descriptions and prices come from the PC's master file.
 */
@Entity(
    tableName = "wifi_print_history",
    indices = [Index(value = ["jobId"]), Index(value = ["timestamp"])]
)
data class WifiPrintHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Groups every sheet + failed item from one "Share Wi-Fi" send. */
    val jobId: Long,
    val timestamp: Long,
    /** "sheet" or "failed". */
    val kind: String,
    val tagType: String,
    val unitType: String,
    /** Physical copies for a single-item page; 1 for a grouped (4-up) page. */
    val copies: Int,
    /** Number of real items on the page (1–4); 1 for a failed item. */
    val nTags: Int,
    /** English description of the first item — the row title. */
    val summary: String,
    /** First/only item's POS code (display + search). */
    val posCode: String,
    /** Resolved price of the first item (display). */
    val price: String,
    /** Failure reason when kind == "failed" (empty otherwise). */
    val reason: String,
    /** JSON array of the items on this page: [{pos,eng,unit,copies,price}]. */
    val itemsJson: String
)
