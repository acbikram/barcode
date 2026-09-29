package com.industrial.barcodescanner.utils

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import java.io.OutputStream
import java.util.UUID

@SuppressLint("MissingPermission")
class ShelfTagPrinter {
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun pairedPrinters(model: String): List<BluetoothDevice> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        return adapter.bondedDevices.filter { device ->
            val name = device.name.orEmpty().uppercase()
            if (model == "Zebra") name.contains("ZQ") || name.contains("ZEBRA") else name.contains("RP") || name.contains("HONEYWELL")
        }.sortedBy { it.name }
    }

    fun print(model: String, address: String, item: com.industrial.barcodescanner.presentation.screens.shelftag.ShelfTagItem, copies: Int, arabic: Boolean) {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: error("Bluetooth is not supported")
        val device = adapter.getRemoteDevice(address)
        device.createRfcommSocketToServiceRecord(sppUuid).use { socket ->
            socket.connect()
            socket.outputStream.use { output ->
                val payload = if (model == "Zebra") zpl(item, copies, arabic) else dpl(item, copies)
                output.write(payload.toByteArray(Charsets.UTF_8))
                output.flush()
            }
        }
    }

    private fun zpl(item: com.industrial.barcodescanner.presentation.screens.shelftag.ShelfTagItem, copies: Int, arabic: Boolean): String {
        val each = item.eachPrice.ifBlank { "*****" }
        val offer = if (item.hasOffer) item.offerPrice else item.cartonPrice
        return "^XA^PW812^LL285^LH0,0" +
            "^FO14,31^GB782,0,3^FS^FO213,169^GB386,0,3^FS^FO16,241^GB781,0,3^FS" +
            "^FO13,31^GB0,213,3^FS^FO793,32^GB0,212,3^FS" +
            "^FO217,111^A0N,24,24^FB385,3,1,C^FD${item.englishDescription.sanitizeZpl()}^FS" +
            "^FO18,97^A0N,51,51^FB192,1,13,C^FD${each.sanitizeZpl()}^FS" +
            "^FO18,169^A0N,25,25^FD${item.currency.sanitizeZpl()}^FS" +
            "^FO600,95^A0N,51,51^FB197,1,13,C^FD${offer.sanitizeZpl()}^FS" +
            "^FO600,178^A0N,25,25^FD${item.currency.sanitizeZpl()}^FS" +
            "^BY1,3,59^FT463,237^BCN,,N,N,,A^FD${item.idItmPos.sanitizeZpl()}^FS" +
            "^FO215,36^A0N,29,29^FB385,1,1,C^FD${item.arabicDescription.sanitizeZpl()}^FS" +
            "^FO227,201^A0N,25,25^FD${item.posCode.sanitizeZpl()}^FS" +
            "^PQ${copies.coerceIn(1, 99)},,,Y^XZ\r\n"
    }

    private fun dpl(item: com.industrial.barcodescanner.presentation.screens.shelftag.ShelfTagItem, copies: Int): String =
        "\u0002L\r\nD11\r\nA2\r\n191100100010${item.englishDescription.take(28)}\r\nA2\r\n191100100060${item.eachPrice}\r\nB2\r\n191100100120${item.idItmPos}\r\nQ${copies.coerceIn(1, 99)}\r\nE\r\n"

    private fun String.sanitizeZpl(): String = replace("^", " ").replace("~", " ").replace("\\", " ")
}
