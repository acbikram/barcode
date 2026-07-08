package com.industrial.barcodescanner.data.local.dao

import androidx.room.*
import com.industrial.barcodescanner.data.local.entity.ScannedItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScannedItemDao {
    @Query("SELECT * FROM scanned_items ORDER BY createdAt DESC")
    fun getAllItems(): Flow<List<ScannedItemEntity>>

    @Query("SELECT * FROM scanned_items WHERE id = :id")
    suspend fun getItemById(id: Long): ScannedItemEntity?

    /**
     * Looks for an existing row with the same barcode, tag type, and unit
     * type — used to decide whether a new scan should be merged (copies
     * added together) into an existing row instead of creating a new one.
     */
    @Query("SELECT * FROM scanned_items WHERE barcode = :barcode AND tagType = :tagType AND unitType = :unitType LIMIT 1")
    suspend fun findByBarcodeTagUnit(barcode: String, tagType: String, unitType: String): ScannedItemEntity?

    @Insert
    suspend fun insert(item: ScannedItemEntity): Long

    @Update
    suspend fun update(item: ScannedItemEntity)

    @Delete
    suspend fun delete(item: ScannedItemEntity)

    @Query("DELETE FROM scanned_items")
    suspend fun deleteAll()

    @Query("DELETE FROM scanned_items WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM scanned_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM scanned_items WHERE tagType = :tagType")
    suspend fun deleteByTagType(tagType: String)

    @Query("DELETE FROM scanned_items WHERE unitType = :unitType")
    suspend fun deleteByUnitType(unitType: String)

    @Query("SELECT COUNT(*) FROM scanned_items")
    suspend fun getCount(): Int

    // ── Dashboard aggregates ────────────────────────────────────────────────

    @Query("SELECT tagType, COUNT(*) as count FROM scanned_items GROUP BY tagType")
    fun getTagTypeCounts(): Flow<List<TagTypeCount>>

    @Query("SELECT unitType, COUNT(*) as count FROM scanned_items GROUP BY unitType")
    fun getUnitTypeCounts(): Flow<List<UnitTypeCount>>
}

data class TagTypeCount(val tagType: String, val count: Int)
data class UnitTypeCount(val unitType: String, val count: Int)
