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

    /**
     * Discovers Price Tag PCs on the local network.
     *
     * Sends the PTAGWHO1 discovery packet to:
     *   1. The subnet broadcast address (e.g. 192.168.1.255) — most reliable on Android
     *   2. 255.255.255.255 — global broadcast (blocked by some routers/Android versions)
     *   3. The PC's direct IP if [knownIp] is provided — works even when broadcast is blocked
     *
     * Android often blocks 255.255.255.255 broadcasts; the subnet broadcast
     * address obtained from WifiManager's DHCP info is far more reliable.
     */
    fun discover(
        context: android.content.Context? = null,
        timeoutMs: Int = 2500,
        knownIp: String? = null,
        knownPort: Int = DISCOVERY_PORT
    ): List<WifiPc> {
        val found = LinkedHashMap<String, WifiPc>()
        var sock: DatagramSocket? = null
        try {
            sock = DatagramSocket().apply {
                broadcast = true
                soTimeout = 500
            }

            // Build the list of addresses to probe
            val targets = mutableListOf<InetAddress>()

            // 1. Subnet broadcast (most reliable on Android)
            val subnetBroadcast = getSubnetBroadcast(context)
            if (subnetBroadcast != null) targets.add(subnetBroadcast)

            // 2. Global broadcast
            targets.add(InetAddress.getByName("255.255.255.255"))

            // 3. Direct IP — works even when broadcast is fully blocked
            if (!knownIp.isNullOrBlank()) {
                runCatching { targets.add(InetAddress.getByName(knownIp)) }
            }

            // Send to all targets
            for (target in targets) {
                runCatching {
                    sock.send(DatagramPacket(REQUEST, REQUEST.size, target, DISCOVERY_PORT))
                }
            }

            // Collect replies until the deadline
            val deadline = System.currentTimeMillis() + timeoutMs
            val buf = ByteArray(2048)
            while (System.currentTimeMillis() < deadline) {
                try {
                    val resp = DatagramPacket(buf, buf.size)
                    sock.receive(resp)
                    val o = JSONObject(String(resp.data, 0, resp.length, Charsets.UTF_8))
                    val ip = o.optString("ip", "").ifBlank { resp.address?.hostAddress ?: "" }
                    val pc = WifiPc(
                        name = o.optString("name", "PC"),
                        ip   = ip,
                        port = o.optInt("port", DISCOVERY_PORT)
                    )
                    if (pc.ip.isNotBlank()) found["${pc.ip}:${pc.port}"] = pc
                } catch (_: SocketTimeoutException) {
                    // keep polling until the overall deadline
                }
            }

            // If broadcast found nothing but we have a known IP, ping it directly
            if (found.isEmpty() && !knownIp.isNullOrBlank()) {
                runCatching {
                    val direct = pingDirect(knownIp, knownPort, sock)
                    if (direct != null) found["${direct.ip}:${direct.port}"] = direct
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { sock?.close() }
        }
        return found.values.toList()
    }

    /**
     * Returns the subnet broadcast address derived from the device's current
     * WiFi DHCP info (e.g. for 192.168.1.100/24 returns 192.168.1.255).
     * Returns null if unavailable or not on WiFi.
     */
    private fun getSubnetBroadcast(context: android.content.Context?): InetAddress? {
        context ?: return null
        return try {
            val wm = context.applicationContext
                .getSystemService(android.content.Context.WIFI_SERVICE)
                    as? android.net.wifi.WifiManager ?: return null
            val dhcp = wm.dhcpInfo ?: return null
            if (dhcp.ipAddress == 0) return null
            // broadcast = (ip & netmask) | (~netmask)
            val broadcast = (dhcp.ipAddress and dhcp.netmask) or dhcp.netmask.inv()
            InetAddress.getByName(
                "${broadcast and 0xFF}." +
                "${(broadcast shr 8) and 0xFF}." +
                "${(broadcast shr 16) and 0xFF}." +
                "${(broadcast shr 24) and 0xFF}"
            )
        } catch (_: Exception) { null }
    }

    /**
     * Sends a discovery packet directly to [ip]:[port] and waits up to 1 second
     * for a PTAGWHO1 reply. Used as a fallback when broadcast is blocked.
     */
    private fun pingDirect(ip: String, port: Int, sock: DatagramSocket): WifiPc? {
        return try {
            val addr = InetAddress.getByName(ip)
            sock.soTimeout = 1000
            sock.send(DatagramPacket(REQUEST, REQUEST.size, addr, port))
            val buf = ByteArray(2048)
            val resp = DatagramPacket(buf, buf.size)
            sock.receive(resp)
            val o = JSONObject(String(resp.data, 0, resp.length, Charsets.UTF_8))
            WifiPc(
                name = o.optString("name", ip),
                ip   = o.optString("ip", ip),
                port = o.optInt("port", port)
            )
        } catch (_: Exception) { null }
    }
}
