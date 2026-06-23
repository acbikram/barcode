package com.industrial.barcodescanner.utils

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import com.industrial.barcodescanner.domain.model.PrintItem
import com.industrial.barcodescanner.domain.model.PrintSheet
import com.industrial.barcodescanner.domain.model.ResolvedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket

/**
 * Implements the exact wire-protocol used by Price_Tag_Final.py:
 *
 *  DISCOVERY (UDP port 8765):
 *    Phone broadcasts  "PTAGWHO1"
 *    PC replies        JSON {"name":"…","ip":"…","port":N}
 *
 *  CSV PUSH (TCP port from discovery reply):
 *    Phone → PC : "PTAGCSV1" (8) + LEN (4 big-endian) + CSV bytes
 *    PC → Phone : JSON lines (result/done/printed/error/cancelled)
 *    Phone → PC : {"decision":"print"} or {"decision":"cancel"} (if partial)
 *
 *  CATALOG PULL (TCP port from discovery reply):
 *    Phone → PC : "PTAGGDB1" (8 bytes)
 *    PC → Phone : LEN (4 big-endian) + products.db bytes
 */      {"type":"result","ready":N,"failed":[{row,pos,reason}…],
 *       "items":[{pos,eng,unit,copies,tag,status,reason}…],
 *       "sheets":[{tag,unit,copies,n_tags,items:[{pos,eng,unit,copies,price}…]}…],
 *       "retry_csv":"…"}
 *      {"type":"done","printed":N}
 *      {"type":"printed","printed":N,"failed_sheets":N}
 *      {"type":"error","message":"…"}
 *      {"type":"cancelled"}
 *    Phone → PC (only when result has ready>0 AND failures):
 *      {"decision":"print"} or {"decision":"cancel"}
 */
object LocalFileServer {

    const val DISCOVERY_PORT  = 8765
    const val TCP_PORT        = 8765
    private val MAGIC_CSV     = "PTAGCSV1".toByteArray(Charsets.US_ASCII)
    private val MAGIC_GDB     = "PTAGGDB1".toByteArray(Charsets.US_ASCII)
    private val DISCOVERY_REQ = "PTAGWHO1".toByteArray(Charsets.US_ASCII)

    fun getWifiIpAddress(context: Context): String? {
        val wm = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val ip = wm.connectionInfo?.ipAddress ?: return null
        if (ip == 0) return null
        return Formatter.formatIpAddress(ip)
    }

    suspend fun discoverPcs(timeoutMs: Int = 2500): List<PcInfo> = withContext(Dispatchers.IO) {
        val results = mutableListOf<PcInfo>()
        try {
            DatagramSocket().use { sock ->
                sock.broadcast = true
                sock.soTimeout = timeoutMs
                sock.send(DatagramPacket(
                    DISCOVERY_REQ, DISCOVERY_REQ.size,
                    InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT
                ))
                val buf = ByteArray(2048)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val reply = DatagramPacket(buf, buf.size)
                        sock.receive(reply)
                        val text = String(buf, 0, reply.length, Charsets.UTF_8)
                        val json = JSONObject(text)
                        results.add(PcInfo(
                            name = json.optString("name", "PC"),
                            ip   = json.optString("ip", reply.address.hostAddress ?: ""),
                            port = json.optInt("port", TCP_PORT)
                        ))
                    } catch (_: IOException) { break }
                }
            }
        } catch (_: Exception) {}
        results
    }

    /**
     * Pushes the CSV to [pc].
     *
     * Returns one of:
     * - [PushResult.Preview]         — PC resolved everything; show preview before deciding
     * - [PushResult.NeedsDecision]   — some items failed; show errors + ask Cancel/Print
     * - [PushResult.Done]            — printed successfully (after user approved)
     * - [PushResult.Busy]            — PC is handling another job
     * - [PushResult.Error]           — connection or protocol error
     *
     * The caller must call [sendDecision] on the same [socket handle] returned in
     * [PushResult.NeedsDecision] / [PushResult.Preview] to continue the conversation.
     */
    suspend fun pushCsvAndGetPreview(
        pc: PcInfo,
        csvBytes: ByteArray,
        onStatus: (String) -> Unit = {}
    ): PushResult = withContext(Dispatchers.IO) {
        try {
            val sock = Socket(pc.ip, pc.port)
            sock.soTimeout = 30_000
            val out = DataOutputStream(sock.getOutputStream())
            val reader = sock.getInputStream().bufferedReader(Charsets.UTF_8)

            // 1. Send MAGIC + LEN + CSV
            out.write(MAGIC_CSV)
            out.writeInt(csvBytes.size)
            out.write(csvBytes)
            out.flush()
            onStatus("Sent to ${pc.name}…")

            // 2. Read first response (PC analyses the CSV)
            sock.soTimeout = 300_000   // 5 min — large batches take time
            val line = reader.readLine()
                ?: return@withContext PushResult.Error("No response from PC")
            val msg = JSONObject(line)

            when (val type = msg.optString("type")) {
                "busy" -> {
                    sock.close()
                    return@withContext PushResult.Busy
                }
                "result" -> {
                    val ready      = msg.optInt("ready", 0)
                    val failed     = parseFailedItems(msg)
                    val retryCsv   = msg.optString("retry_csv", "")
                    val allItems   = parseResolvedItems(msg)
                    val sheets     = parseSheets(msg)

                    return@withContext if (failed.isEmpty()) {
                        // Everything resolved — show preview, then user taps Print
                        PushResult.Preview(
                            readyItems = allItems.filter { it.status == "ready" },
                            sheets = sheets,
                            retryCsv = retryCsv,
                            sock = sock, out = out, reader = reader
                        )
                    } else {
                        // Some items failed — show errors and ask
                        PushResult.NeedsDecision(
                            readyItems  = allItems.filter { it.status == "ready" },
                            failedItems = allItems.filter { it.status == "failed" },
                            sheets = sheets,
                            retryCsv = retryCsv,
                            sock = sock, out = out, reader = reader,
                            ready = ready
                        )
                    }
                }
                "error" -> {
                    sock.close()
                    return@withContext PushResult.Error(msg.optString("message", "Unknown error"))
                }
                else -> {
                    sock.close()
                    return@withContext PushResult.Error("Unexpected response: $type")
                }
            }
        } catch (e: Exception) {
            PushResult.Error(e.message ?: "Connection failed")
        }
    }

    /**
     * After the user decides (Print / Cancel), finishes the TCP conversation.
     * Returns the sheets that were actually printed.
     */
    suspend fun sendDecision(
        decision: Boolean,
        result: PushResult.NeedsDecision,
        onStatus: (String) -> Unit = {}
    ): FinalResult = withContext(Dispatchers.IO) {
        try {
            if (result.ready <= 0 || !decision) {
                result.sock.close()
                return@withContext FinalResult(printed = 0, sheets = emptyList(), cancelled = !decision)
            }
            val decisionStr = if (decision) "print" else "cancel"
            result.out.write(("{\"decision\":\"$decisionStr\"}\n").toByteArray(Charsets.UTF_8))
            result.out.flush()
            onStatus("Printing…")
            // Wait up to 10 minutes — large batches take time
            result.sock.soTimeout = 600_000
            val line2 = result.reader.readLine()
            val msg2 = if (line2 != null) JSONObject(line2) else null
            val printed = msg2?.optInt("printed", result.ready) ?: result.ready
            result.sock.close()
            FinalResult(printed = printed, sheets = result.sheets)
        } catch (e: Exception) {
            runCatching { result.sock.close() }
            FinalResult(printed = 0, sheets = emptyList(), error = e.message)
        }
    }

    /**
     * For the all-clear case (no failures): user taps Print after preview.
     */
    suspend fun confirmPrint(
        result: PushResult.Preview,
        onStatus: (String) -> Unit = {}
    ): FinalResult = withContext(Dispatchers.IO) {
        try {
            onStatus("Printing…")
            // Wait up to 10 minutes for the PC to finish printing
            result.sock.soTimeout = 600_000
            val line2 = result.reader.readLine()
            val msg2 = if (line2 != null) JSONObject(line2) else null
            val printed = msg2?.optInt("printed", result.readyItems.size) ?: result.readyItems.size
            result.sock.close()
            FinalResult(printed = printed, sheets = result.sheets)
        } catch (e: Exception) {
            runCatching { result.sock.close() }
            FinalResult(printed = 0, sheets = emptyList(), error = e.message)
        }
    }

    fun cancelPreview(result: PushResult.Preview) = runCatching { result.sock.close() }
    fun cancelDecision(result: PushResult.NeedsDecision) = runCatching { result.sock.close() }

    /**
     * Pulls the product catalog (.db) from [pc] over the PTAGGDB1 protocol:
     *
     *   Phone → PC  : "PTAGGDB1" (8 bytes)
     *   PC → Phone  : LEN (4 bytes big-endian) + .db bytes (LEN bytes)
     *
     * The PC generates the .db from the current master Excel file (cached;
     * regenerated only when the master changes) and streams it back.
     *
     * Returns the raw bytes of the .db file, or throws on error.
     */
    suspend fun pullCatalogDb(
        pc: PcInfo,
        onProgress: (bytesReceived: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): ByteArray = withContext(Dispatchers.IO) {
        Socket(pc.ip, pc.port).use { sock ->
            sock.soTimeout = 300_000   // 5 min — PC may need to regenerate the DB

            val out = sock.getOutputStream()
            val inp = sock.getInputStream()

            // 1. Send magic
            out.write(MAGIC_GDB)
            out.flush()

            // 2. Receive LEN (4 bytes big-endian)
            val lenBytes = ByteArray(4)
            var read = 0
            while (read < 4) {
                val n = inp.read(lenBytes, read, 4 - read)
                if (n < 0) throw IOException("Connection closed before length received")
                read += n
            }
            val totalLen = ((lenBytes[0].toInt() and 0xFF) shl 24) or
                           ((lenBytes[1].toInt() and 0xFF) shl 16) or
                           ((lenBytes[2].toInt() and 0xFF) shl 8)  or
                            (lenBytes[3].toInt() and 0xFF)

            if (totalLen <= 0 || totalLen > 100 * 1024 * 1024) {
                throw IOException("Invalid catalog size from PC: $totalLen bytes")
            }

            // 3. Receive .db bytes
            val buf = ByteArray(65536)
            val baos = ByteArrayOutputStream(totalLen)
            var received = 0L
            while (received < totalLen) {
                val toRead = minOf(buf.size.toLong(), totalLen - received).toInt()
                val n = inp.read(buf, 0, toRead)
                if (n < 0) throw IOException("Connection dropped after $received / $totalLen bytes")
                baos.write(buf, 0, n)
                received += n
                onProgress(received, totalLen.toLong())
            }
            baos.toByteArray()
        }
    }

    fun buildCsvBytes(items: List<com.industrial.barcodescanner.domain.model.ScannedItem>): ByteArray {
        val csvStream = ByteArrayOutputStream()
        CsvExporter.writeCsv(csvStream, items)
        return csvStream.toByteArray()
    }

    // ── Parsing helpers ──────────────────────────────────────────────────────

    private fun parseFailedItems(msg: JSONObject): List<FailedItem> {
        val arr = msg.optJSONArray("failed") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            FailedItem(row = obj.optInt("row", 0), pos = obj.optString("pos", ""), reason = obj.optString("reason", ""))
        }
    }

    private fun parseResolvedItems(msg: JSONObject): List<ResolvedItem> {
        val arr = msg.optJSONArray("items") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            ResolvedItem(
                pos    = obj.optString("pos", ""),
                eng    = obj.optString("eng", ""),
                unit   = obj.optString("unit", ""),
                copies = obj.optInt("copies", 1),
                tag    = obj.optString("tag", ""),
                status = obj.optString("status", "ready"),
                reason = obj.optString("reason", "")
            )
        }
    }

    private fun parseSheets(msg: JSONObject): List<PrintSheet> {
        val arr = msg.optJSONArray("sheets") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val itemsArr = obj.optJSONArray("items")
            val items = if (itemsArr != null) {
                (0 until itemsArr.length()).mapNotNull { j ->
                    val it = itemsArr.optJSONObject(j) ?: return@mapNotNull null
                    PrintItem(
                        pos    = it.optString("pos", ""),
                        eng    = it.optString("eng", ""),
                        unit   = it.optString("unit", ""),
                        copies = it.optInt("copies", 1),
                        price  = it.optString("price", "")
                    )
                }
            } else emptyList()
            PrintSheet(
                tag    = obj.optString("tag", ""),
                unit   = obj.optString("unit", ""),
                copies = obj.optInt("copies", 1),
                nTags  = obj.optInt("n_tags", items.size),
                items  = items
            )
        }
    }

    // ── Data classes ─────────────────────────────────────────────────────────

    data class PcInfo(val name: String, val ip: String, val port: Int) {
        override fun toString() = "$name ($ip:$port)"
    }

    data class FailedItem(val row: Int, val pos: String, val reason: String)

    data class FinalResult(
        val printed: Int,
        val sheets: List<PrintSheet>,
        val cancelled: Boolean = false,
        val error: String? = null
    )

    sealed class PushResult {
        /** All items resolved — show preview, wait for user to tap Print. */
        data class Preview(
            val readyItems: List<ResolvedItem>,
            val sheets: List<PrintSheet>,
            val retryCsv: String,
            internal val sock: Socket,
            internal val out: DataOutputStream,
            internal val reader: java.io.BufferedReader
        ) : PushResult()

        /** Some items failed — show errors, ask Cancel / Print ready. */
        data class NeedsDecision(
            val readyItems: List<ResolvedItem>,
            val failedItems: List<ResolvedItem>,
            val sheets: List<PrintSheet>,
            val retryCsv: String,
            val ready: Int,
            internal val sock: Socket,
            internal val out: DataOutputStream,
            internal val reader: java.io.BufferedReader
        ) : PushResult()

        object Busy : PushResult()
        data class Error(val message: String) : PushResult()
    }
}
