package com.industrial.barcodescanner.domain.repository

import com.industrial.barcodescanner.data.local.entity.WifiPrintHistoryEntity
import kotlinx.coroutines.flow.Flow

interface WifiPrintHistoryRepository {
    fun getAll(): Flow<List<WifiPrintHistoryEntity>>
    suspend fun insertAll(items: List<WifiPrintHistoryEntity>)
    suspend fun deleteJob(jobId: Long)
    suspend fun deleteAll()
    suspend fun trimOlderThan(cutoff: Long)
    suspend fun trimToCount(keep: Int)
}
