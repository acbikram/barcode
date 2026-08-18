package com.industrial.barcodescanner.domain.repository

import com.industrial.barcodescanner.data.local.dao.DashboardSummary
import com.industrial.barcodescanner.data.local.dao.TagTypeCount
import com.industrial.barcodescanner.data.local.dao.UnitTypeCount
import com.industrial.barcodescanner.data.local.entity.ScannedItemEntity
import com.industrial.barcodescanner.domain.model.ScannedItem
import kotlinx.coroutines.flow.Flow

interface ScannedItemRepository {
    fun getAllItems(): Flow<List<ScannedItem>>
    fun getRecentItems(limit: Int): Flow<List<ScannedItem>>
    fun getDeletedItems(): Flow<List<ScannedItem>>
    suspend fun getItemById(id: Long): ScannedItem?
    suspend fun findByBarcodeTagUnit(barcode: String, tagType: String, unitType: String): ScannedItem?
    suspend fun insertItem(item: ScannedItemEntity): Long
    suspend fun updateItem(item: ScannedItemEntity)
    suspend fun deleteItem(item: ScannedItem)
    suspend fun deleteAllItems()
    suspend fun deleteItemsOlderThan(cutoff: Long): Int
    suspend fun deleteItemsByIds(ids: List<Long>)
    suspend fun deleteItemsByTagType(tagType: String)
    suspend fun deleteItemsByUnitType(unitType: String)
    suspend fun restoreItems(ids: List<Long>)
    suspend fun permanentlyDeleteItems(ids: List<Long>)
    suspend fun emptyRecycleBin()

    fun getDashboardSummary(): Flow<DashboardSummary>
    fun getTagTypeCounts(): Flow<List<TagTypeCount>>
    fun getUnitTypeCounts(): Flow<List<UnitTypeCount>>
}
