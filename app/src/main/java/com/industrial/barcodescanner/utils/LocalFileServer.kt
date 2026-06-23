package com.industrial.barcodescanner.utils

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
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
 * Implements the exact same wire-protocol the Price_Tag_Final.py PC app expects:
 *
 *   DISCOVERY (UDP):
 *     Phone broadcasts  "PTAGWHO1" on UDP port 8765
 *     PC replies with   JSON: {"name":"PCName","ip":"x.x.x.x","port":8765}
 *
 *   CSV PUSH (TCP):
 *     Phone → PC : MAGIC(8) "PTAGCSV1" + LEN(4 big-endian) + CSV bytes (UTF-8/BOM ok)
 *     PC   → Phone: newline-terminated JSON messages:
 *       {"type":"busy"}
 *       {"type":"result","ready":N,"failed":[...],"retry_csv":"..."}
 *       {"type":"done","printed":N}
 *       {"type":"printed","printed":N,"failed":[...],"retry_csv":"..."}
 *       {"type":"error","message":"..."}
 *       {"type":"cancelled"}
 *     Phone → PC (only when result has failures AND ready>0):
 *       {"decision":"print"} or {"decision":"cancel"}
 */
object LocalFileServer {

    const val DISCOVERY_PORT  = 8765
    const val TCP_PORT        = 8765
    private val MAGIC_CSV     = "PTAGCSV1".toByteArray(Charsets.US_ASCII)
    private val DISCOVERY_REQ = "PTAGWHO1".toByteArray(Charsets.US_ASCII)

    /** Returns the device's current WiFi IP address, or null if not connected. */
    fun getWifiIpAddress(context: Context): String? {
        val wm = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val ip = wm.connectionInfo?.ipAddress ?: return null
        if (ip == 0) return null
        return Formatter.formatIpAddress(ip)
    }

    /**
     * Discovers all PCs on the LAN that are running Price_Tag_Final.py with
     * the WiFi receiver enabled. Sends a single UDP broadcast on [DISCOVERY_PORT]
     * and collects replies for [timeoutMs] ms.
     *
     * Returns a list of [PcInfo] objects — one per responding PC.
     */
    suspend fun discoverPcs(timeoutMs: Int = 2000): List<PcInfo> = withContext(Dispatchers.IO) {
        val results = mutableListOf<PcInfo>()
        try {
            DatagramSocket().use { sock ->
                sock.broadcast = true
                sock.soTimeout = timeoutMs
                val req = DatagramPacket(
                    DISCOVERY_REQ, DISCOVERY_REQ.size,
                    InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT
                )
                sock.send(req)
                val buf = ByteArray(2048)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val reply = DatagramPacket(buf, buf.size)
                        sock.receive(reply)
                        val text = String(buf, 0, reply.length, Charsets.UTF_8)
                        val json = JSONObject(text)
                        results.add(
                            PcInfo(
                                name = json.optString("name", "PC"),
                                ip   = json.optString("ip", reply.address.hostAddress ?: ""),
                                port = json.optInt("port", TCP_PORT)
                            )
                        )
                    } catch (e: IOException) {
                        break  // timeout — no more replies
                    }
                }
            }
        } catch (_: Exception) {}
        results
    }

    /**
     * Pushes the CSV (already sorted oldest-first) to [pc] via the TCP protocol.
     * Returns a [PushResult] describing what happened.
     */
    suspend fun pushCsv(
        pc: PcInfo,
        csvBytes: ByteArray,
        onStatus: (String) -> Unit = {},
        onDecisionNeeded: suspend (ready: Int, failed: List<FailedItem>, retryCsv: String) -> Boolean = { _, _, _ -> false }
    ): PushResult = withContext(Dispatchers.IO) {
        try {
            Socket(pc.ip, pc.port).use { sock ->
                sock.soTimeout = 30_000
                val out = DataOutputStream(sock.getOutputStream())
                val reader = sock.getInputStream().bufferedReader(Charsets.UTF_8)

                // 1. Send magic + length + csv
                out.write(MAGIC_CSV)
                out.writeInt(csvBytes.size)   // 4 bytes big-endian
                out.write(csvBytes)
                out.flush()
                onStatus("Sent ${csvBytes.size} bytes to ${pc.name}")

                // 2. Read response from PC
                sock.soTimeout = 60_000
                val line = reader.readLine() ?: return@withContext PushResult.Error("No response from PC")
                val msg = JSONObject(line)

                when (val type = msg.optString("type")) {
                    "busy" -> return@withContext PushResult.Busy

                    "done" -> {
                        val printed = msg.optInt("printed", 0)
                        onStatus("Printed $printed item(s)")
                        return@withContext PushResult.Done(printed)
                    }

                    "result" -> {
                        val ready  = msg.optInt("ready", 0)
                        val failed = parseFailedItems(msg)
                        val retry  = msg.optString("retry_csv", "")

                        if (failed.isEmpty()) {
                            // Wait for "printed" or "done"
                            sock.soTimeout = 120_000
                            val line2 = reader.readLine()
                            val msg2  = if (line2 != null) JSONObject(line2) else null
                            val printed = msg2?.optInt("printed", ready) ?: ready
                            return@withContext PushResult.Done(printed)
                        }

                        // Some failed — ask the user
                        val shouldPrint = onDecisionNeeded(ready, failed, retry)
                        if (ready > 0) {
                            val decision = if (shouldPrint) "print" else "cancel"
                            out.write(("{\"decision\":\"$decision\"}\n").toByteArray(Charsets.UTF_8))
                            out.flush()
                            if (shouldPrint) {
                                sock.soTimeout = 120_000
                                val line2 = reader.readLine()
                                val msg2  = if (line2 != null) JSONObject(line2) else null
                                val printed = msg2?.optInt("printed", ready) ?: ready
                                return@withContext PushResult.PartialDone(printed, failed, retry)
                            }
                        }
                        return@withContext PushResult.PartialDone(0, failed, retry)
                    }

                    "error" -> return@withContext PushResult.Error(msg.optString("message", "Unknown error"))

                    else -> return@withContext PushResult.Error("Unexpected response: $type")
                }
            }
        } catch (e: Exception) {
            PushResult.Error(e.message ?: "Connection failed")
        }
    }

    /** Builds the framed CSV bytes (MAGIC + LEN + CSV) ready to send. */
    fun buildCsvBytes(items: List<com.industrial.barcodescanner.domain.model.ScannedItem>): ByteArray {
        val csvStream = ByteArrayOutputStream()
        CsvExporter.writeCsv(csvStream, items)
        return csvStream.toByteArray()
    }

    private fun parseFailedItems(msg: JSONObject): List<FailedItem> {
        val arr = msg.optJSONArray("failed") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            FailedItem(
                row    = obj.optInt("row", 0),
                pos    = obj.optString("pos", ""),
                reason = obj.optString("reason", "")
            )
        }
    }

    data class PcInfo(val name: String, val ip: String, val port: Int) {
        override fun toString() = "$name ($ip:$port)"
    }

    data class FailedItem(val row: Int, val pos: String, val reason: String)

    sealed class PushResult {
        data class Done(val printed: Int) : PushResult()
        data class PartialDone(val printed: Int, val failed: List<FailedItem>, val retryCsv: String) : PushResult()
        object Busy : PushResult()
        data class Error(val message: String) : PushResult()
    }
}
