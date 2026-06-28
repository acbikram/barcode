package com.industrial.barcodescanner.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Queries the GitHub Releases API for the latest release of this app.
 *
 * Release tags are expected in the format:  v<versionName>+<versionCode>
 * e.g.  v1.4+5
 *
 * The signed APK must be attached as a release asset (done automatically
 * by the updated GitHub Actions workflow).
 */
object UpdateChecker {

    private const val RELEASES_URL =
        "https://api.github.com/repos/acbikram/barcode/releases/latest"

    data class UpdateInfo(
        val latestVersionCode: Int,
        val latestVersionName: String,
        val apkDownloadUrl: String,
        val releaseNotes: String
    )

    sealed class CheckResult {
        data class UpdateAvailable(val info: UpdateInfo) : CheckResult()
        object UpToDate : CheckResult()
        data class Error(val message: String) : CheckResult()
    }

    suspend fun check(currentVersionCode: Int): CheckResult = withContext(Dispatchers.IO) {
        try {
            val url = URL(RELEASES_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = 10_000
                readTimeout    = 10_000
            }

            if (conn.responseCode != 200) {
                return@withContext CheckResult.Error("Server returned ${conn.responseCode}")
            }

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val json = JSONObject(body)
            val tag  = json.optString("tag_name", "") // e.g. "v1.4+5"

            // Parse versionCode from tag: everything after '+'
            val latestCode = tag.substringAfter("+", "").toIntOrNull()
                ?: return@withContext CheckResult.Error("Unrecognised release tag: $tag")
            val latestName = tag.substringAfter("v").substringBefore("+")

            // Find the APK asset URL
            val assets = json.optJSONArray("assets")
            var apkUrl = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name", "").endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }
            if (apkUrl.isBlank()) {
                return@withContext CheckResult.Error("No APK found in the latest release")
            }

            val notes = json.optString("body", "").take(300).trim()

            if (latestCode > currentVersionCode) {
                CheckResult.UpdateAvailable(
                    UpdateInfo(latestCode, latestName, apkUrl, notes)
                )
            } else {
                CheckResult.UpToDate
            }
        } catch (e: Exception) {
            CheckResult.Error(e.message ?: "Network error")
        }
    }
}
