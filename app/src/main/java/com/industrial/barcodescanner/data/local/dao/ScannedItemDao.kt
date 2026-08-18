package com.industrial.barcodescanner.data.local.dao

import androidx.room.*
import com.industrial.barcodescanner.data.local.entity.ScannedItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScannedItemDao {
    @Query("SELECT * FROM scanned_items WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun getAllItems(): Flow<List<ScannedItemEntity>>

    @Query("SELECT * FROM scanned_items WHERE deletedAt IS NULL ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentItems(limit: Int): Flow<List<ScannedItemEntity>>

    @Query("SELECT * FROM scanned_items WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getDeletedItems(): Flow<List<ScannedItemEntity>>

    @Query("SELECT * FROM scanned_items WHERE id = :id AND deletedAt IS NULL")
    suspend fun getItemById(id: Long): ScannedItemEntity?

    /**
     * Looks for an existing row with the same barcode, tag type, and unit
     * type — used to decide whether a new scan should be merged (copies
     * added together) into an existing row instead of creating a new one.
     */
    @Query("SELECT * FROM scanned_items WHERE barcode = :barcode AND tagType = :tagType AND unitType = :unitType AND deletedAt IS NULL LIMIT 1")
    suspend fun findByBarcodeTagUnit(barcode: String, tagType: String, unitType: String): ScannedItemEntity?

    @Insert
    suspend fun insert(item: ScannedItemEntity): Long

    @Update
    suspend fun update(item: ScannedItemEntity)

    @Query("UPDATE scanned_items SET deletedAt = :deletedAt WHERE id = :id AND deletedAt IS NULL")
    suspend fun moveToRecycleBin(id: Long, deletedAt: Long)

    @Query("UPDATE scanned_items SET deletedAt = :deletedAt WHERE deletedAt IS NULL")
    suspend fun moveAllToRecycleBin(deletedAt: Long)

    /** Automatic history retention affects active records only; recycled records have their own retention. */
    @Query("DELETE FROM scanned_items WHERE createdAt < :cutoff AND deletedAt IS NULL")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM scanned_items WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun deleteRecycledOlderThan(cutoff: Long): Int

    @Query("UPDATE scanned_items SET deletedAt = :deletedAt WHERE id IN (:ids) AND deletedAt IS NULL")
    suspend fun moveToRecycleBinByIds(ids: List<Long>, deletedAt: Long)

    @Query("UPDATE scanned_items SET deletedAt = :deletedAt WHERE tagType = :tagType AND deletedAt IS NULL")
    suspend fun moveToRecycleBinByTagType(tagType: String, deletedAt: Long)

    @Query("UPDATE scanned_items SET deletedAt = :deletedAt WHERE unitType = :unitType AND deletedAt IS NULL")
    suspend fun moveToRecycleBinByUnitType(unitType: String, deletedAt: Long)

    @Query("UPDATE scanned_items SET deletedAt = NULL WHERE id IN (:ids) AND deletedAt IS NOT NULL")
    suspend fun restoreFromRecycleBin(ids: List<Long>)

    @Query("DELETE FROM scanned_items WHERE id IN (:ids) AND deletedAt IS NOT NULL")
    suspend fun permanentlyDelete(ids: List<Long>)

    @Query("DELETE FROM scanned_items WHERE deletedAt IS NOT NULL")
    suspend fun emptyRecycleBin()

    @Query("SELECT COUNT(*) FROM scanned_items WHERE deletedAt IS NULL")
    suspend fun getCount(): Int

    // ── Dashboard aggregates ────────────────────────────────────────────────

    @Query("SELECT COUNT(*) AS totalRecords, COALESCE(SUM(copies), 0) AS totalCopies FROM scanned_items WHERE deletedAt IS NULL")
    fun getDashboardSummary(): Flow<DashboardSummary>

    @Query("SELECT tagType, COUNT(*) as count FROM scanned_items WHERE deletedAt IS NULL GROUP BY tagType")
    fun getTagTypeCounts(): Flow<List<TagTypeCount>>

    @Query("SELECT unitType, COUNT(*) as count FROM scanned_items WHERE deletedAt IS NULL GROUP BY unitType")
    fun getUnitTypeCounts(): Flow<List<UnitTypeCount>>
}

data class DashboardSummary(val totalRecords: Int, val totalCopies: Int)

data class TagTypeCount(val tagType: String, val count: Int)
data class UnitTypeCount(val unitType: String, val count: Int)
