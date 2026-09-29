package com.industrial.barcodescanner.utils

import com.industrial.barcodescanner.presentation.screens.shelftag.ShelfTagItem
import com.industrial.barcodescanner.presentation.screens.shelftag.ShelfTagLookupException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL

class ShelfTagClient {
    suspend fun lookup(
        barcode: String,
        deviceId: String,
        host: String,
        port: Int,
        mode: String
    ): ShelfTagItem = withContext(Dispatchers.IO) {
        val raw = if (mode == "Socket") {
            Socket(host, port).use { socket ->
                PrintWriter(socket.getOutputStream()).use { writer ->
                    writer.println("<START><DEVICEID>$deviceId</DEVICEID><BARCODE>$barcode</BARCODE><END>")
                    writer.flush()
                    BufferedReader(InputStreamReader(socket.getInputStream())).readLine()
                }
            }
        } else {
            val connection = (URL("http://$host:$port/BarcodeApi/api/Item").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            try {
                connection.outputStream.use { it.write(JSONObject().apply {
                    put("DeviceId", deviceId)
                    put("Barcode", barcode)
                }.toString().toByteArray()) }
                val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            } finally { connection.disconnect() }
        }
        if (raw.isNullOrBlank()) throw ShelfTagLookupException("No response from Price Tag server")
        val json = runCatching { JSONObject(raw) }.getOrElse {
            throw ShelfTagLookupException(raw.replace("\"", ""))
        }
        if (!json.has("POS_CODE")) throw ShelfTagLookupException(raw.replace("\"", ""))
        ShelfTagItem.fromJson(json)
    }
}
