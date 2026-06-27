package com.industrial.barcodescanner.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.industrial.barcodescanner.data.local.entity.WifiPrintHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WifiPrintHistoryDao {

    @Query("SELECT * FROM wifi_print_history ORDER BY timestamp DESC, id DESC")
    fun getAll(): Flow<List<WifiPrintHistoryEntity>>

    @Insert
    suspend fun insertAll(items: List<WifiPrintHistoryEntity>)

    @Query("DELETE FROM wifi_print_history WHERE jobId = :jobId")
    suspend fun deleteJob(jobId: Long)

    @Query("DELETE FROM wifi_print_history")
    suspend fun deleteAll()

    /** Auto-trim: drop entries older than the cutoff (epoch millis). */
    @Query("DELETE FROM wifi_print_history WHERE timestamp < :cutoff")
    suspend fun trimOlderThan(cutoff: Long)

    /** Auto-trim: keep only the newest [keep] rows. */
    @Query(
        "DELETE FROM wifi_print_history WHERE id NOT IN " +
            "(SELECT id FROM wifi_print_history ORDER BY timestamp DESC, id DESC LIMIT :keep)"
    )
    suspend fun trimToCount(keep: Int)
}
