package com.industrial.barcodescanner.utils

import android.content.Context
import com.industrial.barcodescanner.domain.model.PrintJob
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists print jobs to a JSON file in the app's private files directory.
 * Keeps the 200 most recent jobs (same cap as the .py app's 5,000 log lines;
 * each job typically represents a batch of 1-10 sheets).
 */
@Singleton
class PrintHistoryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val historyFile = File(context.filesDir, "print_history.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val MAX_JOBS = 200

    suspend fun saveJob(job: PrintJob) = withContext(Dispatchers.IO) {
        val jobs = loadJobs().toMutableList()
        jobs.add(0, job)  // newest first
        if (jobs.size > MAX_JOBS) jobs.subList(MAX_JOBS, jobs.size).clear()
        historyFile.writeText(json.encodeToString(jobs))
    }

    suspend fun loadJobs(): List<PrintJob> = withContext(Dispatchers.IO) {
        if (!historyFile.exists()) return@withContext emptyList()
        try {
            json.decodeFromString<List<PrintJob>>(historyFile.readText())
        } catch (_: Exception) { emptyList() }
    }

    suspend fun deleteJob(id: Long) = withContext(Dispatchers.IO) {
        val jobs = loadJobs().filter { it.id != id }
        historyFile.writeText(json.encodeToString(jobs))
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        historyFile.delete()
    }
}
