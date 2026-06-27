package com.industrial.barcodescanner.utils

import com.industrial.barcodescanner.domain.model.ScannedItem
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/** A price-tag PC found on the network (via auto-discovery). */
data class WifiPc(val name: String, val ip: String, val port: Int)

/**
 * Phone half of "Share WiFi". See WifiReceiver in the Python app for the
 * matching protocol. Adds: a connection test (ping/pong) and LAN auto-discovery.
 */
object WifiSender {

    val MAGIC: ByteArray = "PTAGCSV1".toByteArray(Charsets.US_ASCII)
    val PING: ByteArray = "PTAGPNG1".toByteArray(Charsets.US_ASCII)
    val GET_DB: ByteArray = "PTAGGDB1".toByteArray(Charsets.US_ASCII)
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 60_000   // PC heartbeats every ~20s keep this alive
    private const val DB_READ_TIMEOUT_MS = 180_000 // catalog can be ~20 MB over WiFi

    fun csvBytes(items: List<ScannedItem>): ByteArray =
        ByteArrayOutputStream().use { baos ->
            CsvExporter.writeCsv(baos, items)
            baos.toByteArray()
        }

    fun csvBytesFromText(text: String): ByteArray = text.toByteArray(Charsets.UTF_8)

    /**
     * Pull the catalog .db from the PC (the reverse of sendCsv). Sends the
     * GET_DB request, then reads a 4-byte big-endian length followed by that
     * many raw bytes, streaming them into [sink]. Returns the number of bytes
     * received (0 means the PC had no catalog ready).
     */
    fun pullCatalog(host: String, port: Int, sink: java.io.OutputStream): Long {
        Socket().use { s ->
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            s.soTimeout = DB_READ_TIMEOUT_MS
            s.getOutputStream().apply { write(GET_DB); flush() }
            val ins = s.getInputStream()
            val lenBuf = readN(ins, 4) ?: throw IOException("No reply from PC")
            val n = ((lenBuf[0].toInt() and 0xFF) shl 24) or
                    ((lenBuf[1].toInt() and 0xFF) shl 16) or
                    ((lenBuf[2].toInt() and 0xFF) shl 8) or
                    (lenBuf[3].toInt() and 0xFF)
            if (n <= 0) return 0L
            var remaining = n.toLong()
            val buf = ByteArray(65536)
            var total = 0L
            while (remaining > 0) {
                val toRead = if (remaining < buf.size) remaining.toInt() else buf.size
                val r = ins.read(buf, 0, toRead)
                if (r < 0) throw IOException("Connection closed during catalog transfer")
                sink.write(buf, 0, r)
                remaining -= r
                total += r
            }
            return total
        }
    }

    private fun readN(ins: java.io.InputStream, n: Int): ByteArray? {
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = ins.read(buf, off, n - off)
            if (r < 0) return null
            off += r
        }
        return buf
    }

    /** Connection test — returns the PC's name, or throws on failure. */
    fun ping(host: String, port: Int): String {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), 5_000)
            s.soTimeout = 5_000
            s.getOutputStream().apply { write(PING); flush() }
            val line = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8)).readLine()
                ?: throw IOException("No reply from PC")
            val o = JSONObject(line)
            if (o.optString("type") == "pong") return o.optString("name", "PC")
            throw IOException(o.optString("message", "Unexpected reply"))
        }
    }

    /** One TCP conversation for sending a job. */
    class Session(host: String, port: Int) : Closeable {
        private val socket = Socket()
        private val reader: BufferedReader

        init {
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS
            reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        }

        fun sendCsv(payload: ByteArray) {
            val out = socket.getOutputStream()
            out.write(MAGIC)
            val n = payload.size
            out.write(
                byteArrayOf(
                    (n ushr 24 and 0xFF).toByte(),
                    (n ushr 16 and 0xFF).toByte(),
                    (n ushr 8 and 0xFF).toByte(),
                    (n and 0xFF).toByte()
                )
            )
            out.write(payload)
            out.flush()
        }

        fun readMessage(): JSONObject? {
            val line = reader.readLine() ?: return null
            if (line.isBlank()) return JSONObject()
            return JSONObject(line)
        }

        fun sendDecision(decision: String) {
            val out = socket.getOutputStream()
            out.write((JSONObject().put("decision", decision).toString() + "\n").toByteArray(Charsets.UTF_8))
            out.flush()
        }

        override fun close() {
            try { socket.close() } catch (_: Exception) {}
        }
    }
}

/** LAN auto-discovery: broadcast a request, collect replies from listening PCs. */
object WifiDiscovery {
    private const val DISCOVERY_PORT = 8765
    private val REQUEST = "PTAGWHO1".toByteArray(Charsets.US_ASCII)

    fun discover(timeoutMs: Int = 1500): List<WifiPc> {
        val found = LinkedHashMap<String, WifiPc>()
        var sock: DatagramSocket? = null
        try {
            sock = DatagramSocket().apply {
                broadcast = true
                soTimeout = 400
            }
            sock.send(
                DatagramPacket(
                    REQUEST, REQUEST.size,
                    InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT
                )
            )
            val deadline = System.currentTimeMillis() + timeoutMs
            val buf = ByteArray(2048)
            while (System.currentTimeMillis() < deadline) {
                try {
                    val resp = DatagramPacket(buf, buf.size)
                    sock.receive(resp)
                    val o = JSONObject(String(resp.data, 0, resp.length, Charsets.UTF_8))
                    val pc = WifiPc(
                        o.optString("name", "PC"),
                        o.optString("ip", ""),
                        o.optInt("port", 8765)
                    )
                    if (pc.ip.isNotBlank()) found["${pc.ip}:${pc.port}"] = pc
                } catch (_: SocketTimeoutException) {
                    // keep polling until the overall deadline
                }
            }
        } catch (_: Exception) {
            // return whatever we found
        } finally {
            try { sock?.close() } catch (_: Exception) {}
        }
        return found.values.toList()
    }
}
