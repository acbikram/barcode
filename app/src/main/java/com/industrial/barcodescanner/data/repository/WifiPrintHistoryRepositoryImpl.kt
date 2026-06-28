package com.industrial.barcodescanner.data.repository

import com.industrial.barcodescanner.data.local.database.BarcodeDatabase
import com.industrial.barcodescanner.data.local.entity.WifiPrintHistoryEntity
import com.industrial.barcodescanner.domain.repository.WifiPrintHistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiPrintHistoryRepositoryImpl @Inject constructor(
    database: BarcodeDatabase
) : WifiPrintHistoryRepository {

    private val dao = database.wifiPrintHistoryDao()

    override fun getAll(): Flow<List<WifiPrintHistoryEntity>> = dao.getAll()
    override suspend fun insertAll(items: List<WifiPrintHistoryEntity>) = dao.insertAll(items)
    override suspend fun deleteJob(jobId: Long) = dao.deleteJob(jobId)
    override suspend fun deleteAll() = dao.deleteAll()
    override suspend fun trimOlderThan(cutoff: Long) = dao.trimOlderThan(cutoff)
    override suspend fun trimToCount(keep: Int) = dao.trimToCount(keep)
}
