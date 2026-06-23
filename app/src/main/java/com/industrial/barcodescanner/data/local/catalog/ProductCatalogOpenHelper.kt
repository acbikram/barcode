package com.industrial.barcodescanner.data.local.catalog

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opens the read-only product catalog database that ships as an asset
 * (`app/src/main/assets/products.db`, built from Price_Tag_Master_CTN.xlsx,
 * ~135,725 products keyed by barcode).
 *
 * SQLite can't query a database directly inside the APK's assets, so on
 * first use we copy it into the app's databases directory. If you replace
 * `products.db` with an updated export later, bump [CATALOG_VERSION] so the
 * new copy gets picked up on the next app start.
 */
@Singleton
class ProductCatalogOpenHelper @Inject constructor(
    @ApplicationContext private val context: Context
) : SQLiteOpenHelper(context, DB_NAME, null, SQLITE_VERSION) {

    companion object {
        private const val DB_NAME = "products.db"
        private const val ASSET_PATH = "products.db"

        // Internal SQLiteOpenHelper version - unrelated to CATALOG_VERSION below.
        private const val SQLITE_VERSION = 1

        // Bump this whenever you ship a new products.db asset so the copy
        // in app storage gets refreshed.
        private const val CATALOG_VERSION = 1
    }

    private val dbFile: File by lazy { context.getDatabasePath(DB_NAME) }
    private val versionFile: File by lazy { File(dbFile.parentFile, "$DB_NAME.catalog_version") }

    init {
        copyDatabaseIfNeeded()
    }

    @Synchronized
    private fun copyDatabaseIfNeeded() {
        val currentVersion = versionFile.takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()
        if (dbFile.exists() && currentVersion == CATALOG_VERSION) return

        dbFile.parentFile?.mkdirs()
        context.assets.open(ASSET_PATH).use { input ->
            dbFile.outputStream().use { output -> input.copyTo(output) }
        }
        versionFile.writeText(CATALOG_VERSION.toString())
    }

    /** Read-only handle to the product catalog. Safe to call from a background thread. */
    fun openReadable(): SQLiteDatabase = readableDatabase

    /**
     * Replaces the working catalog with a new [products.db] file the user
     * has placed on their phone (e.g. copied via USB from the PC).
     *
     * The caller should close any open [readableDatabase] before calling this,
     * then re-open the database afterwards (e.g. by recreating the app process
     * or restarting the relevant feature).
     *
     * @param sourceFile  A `products.db` file the user picked from their storage.
     * @throws Exception  If the file can't be validated as a SQLite database or
     *                    the copy fails.
     */
    @Synchronized
    fun importCatalogFromFile(sourceFile: File) {
        // Basic SQLite magic-byte check: first 16 bytes of a valid SQLite db
        // start with "SQLite format 3\000".
        val header = sourceFile.inputStream().use { it.readNBytes(16) }
        val magic = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)
        if (!header.contentEquals(magic)) {
            throw IllegalArgumentException("File does not appear to be a valid SQLite database")
        }
        dbFile.parentFile?.mkdirs()
        sourceFile.copyTo(dbFile, overwrite = true)
        // Write a version number higher than CATALOG_VERSION so the asset
        // copy never overwrites the imported file.
        versionFile.writeText(CATALOG_VERSION.toString())
    }

    /** Returns the last-modified timestamp of the working catalog, for display. */
    fun catalogLastModified(): Long = dbFile.lastModified()

    override fun onCreate(db: SQLiteDatabase) {
        // No-op: the database is fully pre-populated and copied from assets above.
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No-op: catalog refreshes are handled via copyDatabaseIfNeeded()/CATALOG_VERSION.
    }
}
