package com.industrial.barcodescanner.utils

import com.industrial.barcodescanner.data.local.entity.ScannedItemEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

@Serializable
data class BackupData(
    val version: Int = 1,
    val items: List<ScannedItemEntity>
)

object JsonBackup {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun exportToJson(outputStream: OutputStream, items: List<ScannedItemEntity>) {
        val backup = BackupData(items = items)
        outputStream.bufferedWriter().use {
            it.write(json.encodeToString(backup))
        }
    }

    fun importFromJson(inputStream: InputStream): List<ScannedItemEntity> {
        val backup = inputStream.bufferedReader().use {
            json.decodeFromString<BackupData>(it.readText())
        }
        return backup.items
    }
}
