package com.industrial.barcodescanner.utils

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.industrial.barcodescanner.data.local.database.BarcodeDatabase
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Writes rolling, portable JSON backups to Documents/Barcode To CSV Backups.
 *
 * The payload deliberately uses the same format as the manual Backup & Restore
 * screen, so a backup can be recovered through the existing restore workflow.
 * Public Documents storage keeps these files available after an app reinstall.
 */
object AutoBackup {
    private const val FOLDER = "Barcode To CSV Backups"
    private const val PREFIX = "BarcodeToCsv_auto_backup_"
    private const val KEEP_COUNT = 7

    /** Writes or replaces today's backup and returns its visible file name. */
    suspend fun run(context: Context): String {
        val items = BarcodeDatabase.getInstance(context)
            .scannedItemDao()
            .getAllItems()
            .first()

        val name = PREFIX + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".json"
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath = Environment.DIRECTORY_DOCUMENTS + "/" + FOLDER

        // A retry on the same day replaces the prior copy rather than creating
        // an unbounded sequence of equivalent backup files.
        deleteByName(context, name)

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Could not create the backup file.")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                JsonBackup.exportToJson(output, items)
            } ?: throw IllegalStateException("Could not write the backup file.")
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }

        pruneOld(context)
        return name
    }

    private fun pruneOld(context: Context) {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val backups = mutableListOf<Pair<Long, String>>()
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
            MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? AND " +
                MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?",
            arrayOf("%$FOLDER%", "$PREFIX%"),
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                backups += cursor.getLong(idColumn) to cursor.getString(nameColumn)
            }
        }

        backups.sortByDescending { it.second }
        backups.drop(KEEP_COUNT).forEach { (id, _) ->
            try {
                resolver.delete(ContentUris.withAppendedId(collection, id), null, null)
            } catch (_: Exception) {
                // Files created by a prior install may not be deletable; keeping
                // them is safe and preserves the user's recovery options.
            }
        }
    }

    private fun deleteByName(context: Context, name: String) {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? AND " +
                MediaStore.MediaColumns.DISPLAY_NAME + " = ?",
            arrayOf("%$FOLDER%", name),
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            while (cursor.moveToNext()) {
                try {
                    resolver.delete(ContentUris.withAppendedId(collection, cursor.getLong(idColumn)), null, null)
                } catch (_: Exception) {
                    // See pruneOld: ownership can differ after reinstall.
                }
            }
        }
    }
}
