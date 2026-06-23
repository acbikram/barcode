package com.industrial.barcodescanner.domain.model

import kotlinx.serialization.Serializable

/**
 * Mirrors the "sheets" list that Price_Tag_Final.py sends back after
 * analysing the CSV — one [PrintSheet] = one physical page printed.
 *
 * Stored in [PrintHistory] so the user can reprint a job later.
 */
@Serializable
data class PrintItem(
    val pos: String,
    val eng: String,
    val unit: String,
    val copies: Int,
    val price: String
)

@Serializable
data class PrintSheet(
    val tag: String,
    val unit: String,
    val copies: Int,
    val nTags: Int,
    val items: List<PrintItem>
)

@Serializable
data class PrintJob(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val sheets: List<PrintSheet>,
    /** Total physical pages printed. */
    val totalSheets: Int = sheets.size,
    val totalItems: Int = sheets.sumOf { it.nTags }
)

/** A resolved item from the PC — may be ready or failed. */
data class ResolvedItem(
    val pos: String,
    val eng: String,
    val unit: String,
    val copies: Int,
    val tag: String,
    val status: String,   // "ready" or "failed"
    val reason: String    // error reason if failed
)
