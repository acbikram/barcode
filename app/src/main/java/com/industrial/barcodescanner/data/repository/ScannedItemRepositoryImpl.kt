package com.industrial.barcodescanner.data.repository

import com.industrial.barcodescanner.data.local.dao.DashboardSummary
import com.industrial.barcodescanner.data.local.dao.ScannedItemDao
import com.industrial.barcodescanner.data.local.dao.TagTypeCount
import com.industrial.barcodescanner.data.local.dao.UnitTypeCount
import com.industrial.barcodescanner.data.local.database.BarcodeDatabase
import com.industrial.barcodescanner.data.local.entity.ScannedItemEntity
import com.industrial.barcodescanner.data.local.entity.toDomain
import com.industrial.barcodescanner.data.local.entity.toEntity
import com.industrial.barcodescanner.domain.model.ScannedItem
import com.industrial.barcodescanner.domain.repository.ScannedItemRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScannedItemRepositoryImpl @Inject constructor(
    private val database: BarcodeDatabase
) : ScannedItemRepository {

    private val dao = database.scannedItemDao()

    override fun getAllItems(): Flow<List<ScannedItem>> {
        return dao.getAllItems().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getRecentItems(limit: Int): Flow<List<ScannedItem>> {
        return dao.getRecentItems(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getDeletedItems(): Flow<List<ScannedItem>> {
        return dao.getDeletedItems().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getItemById(id: Long): ScannedItem? {
        return dao.getItemById(id)?.toDomain()
    }

    override suspend fun findByBarcodeTagUnit(barcode: String, tagType: String, unitType: String): ScannedItem? {
        return dao.findByBarcodeTagUnit(barcode, tagType, unitType)?.toDomain()
    }

    override suspend fun insertItem(item: ScannedItemEntity): Long {
        return dao.insert(item)
    }

    override suspend fun updateItem(item: ScannedItemEntity) {
        dao.update(item)
    }

    override suspend fun deleteItem(item: ScannedItem) {
        dao.moveToRecycleBin(item.id, System.currentTimeMillis())
    }

    override suspend fun deleteAllItems() {
        dao.moveAllToRecycleBin(System.currentTimeMillis())
    }

    override suspend fun deleteItemsOlderThan(cutoff: Long): Int = dao.deleteOlderThan(cutoff)

    override suspend fun deleteItemsByIds(ids: List<Long>) {
        dao.moveToRecycleBinByIds(ids, System.currentTimeMillis())
    }

    override suspend fun deleteItemsByTagType(tagType: String) {
        dao.moveToRecycleBinByTagType(tagType, System.currentTimeMillis())
    }

    override suspend fun deleteItemsByUnitType(unitType: String) {
        dao.moveToRecycleBinByUnitType(unitType, System.currentTimeMillis())
    }

    override suspend fun restoreItems(ids: List<Long>) {
        dao.restoreFromRecycleBin(ids)
    }

    override suspend fun permanentlyDeleteItems(ids: List<Long>) {
        dao.permanentlyDelete(ids)
    }

    override suspend fun emptyRecycleBin() {
        dao.emptyRecycleBin()
    }

    override fun getDashboardSummary(): Flow<DashboardSummary> = dao.getDashboardSummary()

    override fun getTagTypeCounts(): Flow<List<TagTypeCount>> = dao.getTagTypeCounts()

    override fun getUnitTypeCounts(): Flow<List<UnitTypeCount>> = dao.getUnitTypeCounts()
}
