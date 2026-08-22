package com.industrial.barcodescanner.utils

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceTagPairingProtocolTest {

    @Test
    fun `valid v3 QR produces confirmation invitation`() {
        val raw = """{"v":3,"type":"price_tag_pair","host":"192.168.10.12","port":8765,"code":"one-time","expiresAt":2000,"pcName":"Office PC"}"""

        val result = PriceTagPairingQr.validate(raw, nowEpochSeconds = 1000)

        assertTrue(result is PairingQrValidation.Valid)
        val invitation = (result as PairingQrValidation.Valid).invitation
        assertEquals("192.168.10.12", invitation.host)
        assertEquals(8765, invitation.port)
        assertEquals("one-time", invitation.code)
        assertEquals("Office PC", invitation.pcName)
    }

    @Test
    fun `expired QR is rejected`() {
        val raw = """{"v":3,"type":"price_tag_pair","host":"192.168.10.12","port":8765,"code":"one-time","expiresAt":1000,"pcName":"Office PC"}"""

        val result = PriceTagPairingQr.validate(raw, nowEpochSeconds = 1000)

        assertEquals(
            PairingQrValidation.Invalid(PairingQrValidation.Reason.EXPIRED),
            result
        )
    }

    @Test
    fun `pair request uses exact PTAGPAIR big endian framing`() {
        val actual = PriceTagPairingProtocol.pairRequestFrame("abc", "device-1")
        val expected = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { out ->
                out.write("PTAGPAIR".toByteArray(Charsets.US_ASCII))
                out.writeShort(3)
                out.write("abc".toByteArray(Charsets.UTF_8))
                out.writeShort(8)
                out.write("device-1".toByteArray(Charsets.UTF_8))
                out.writeShort("Barcode To CSV".toByteArray(Charsets.UTF_8).size)
                out.write("Barcode To CSV".toByteArray(Charsets.UTF_8))
            }
            bytes.toByteArray()
        }

        assertArrayEquals(expected, actual)
    }

    @Test
    fun `successful paired response supplies durable token to persistence flow`() {
        val response = """{"type":"paired","protocol":3,"deviceToken":"durable-token","pcName":"Office PC"}"""

        val paired = PriceTagPairingProtocol.parsePairedResponse(response, invitation())

        assertEquals("durable-token", paired.deviceToken)
        assertEquals("Office PC", paired.pcName)
        assertEquals(3, paired.protocol)
    }

    @Test
    fun `rejected pairing response does not expose sensitive QR content`() {
        val invitation = invitation()
        val failure = runCatching {
            PriceTagPairingProtocol.parsePairedResponse(
                """{"type":"rejected","protocol":3,"deviceToken":"should-not-be-used"}""",
                invitation
            )
        }.exceptionOrNull()

        assertTrue(failure != null)
        assertFalse(failure?.message.orEmpty().contains(invitation.code))
        assertFalse(failure?.message.orEmpty().contains("should-not-be-used"))
    }

    @Test
    fun `successful pairing persists only durable paired record`() {
        val store = FakePairingStore()
        val invitation = invitation()
        val service = PriceTagPairingService(
            store = store,
            client = object : PriceTagPairingClient {
                override fun pair(invitation: PriceTagPairingInvitation, installationId: String) = PairedPriceTagPc(
                    host = invitation.host,
                    port = invitation.port,
                    deviceToken = "durable-token",
                    pcName = "Office PC",
                    protocol = 3,
                    pairedAtEpochMillis = 0
                )
            },
            nowEpochMillis = { 1234L }
        )

        val saved = service.pair(invitation)

        assertEquals("durable-token", saved.deviceToken)
        assertEquals(1234L, saved.pairedAtEpochMillis)
        assertEquals(saved, store.pairedPc())
        assertFalse(store.persistedValues.containsValue(invitation.code))
    }

    @Test
    fun `pairing rejection leaves existing record unchanged without persisting QR code`() {
        val existing = PairedPriceTagPc("10.0.0.2", 8765, "old-token", "Existing", 3, 99L)
        val store = FakePairingStore(existing)
        val invitation = invitation()
        val service = PriceTagPairingService(
            store,
            object : PriceTagPairingClient {
                override fun pair(invitation: PriceTagPairingInvitation, installationId: String): PairedPriceTagPc {
                    throw IllegalStateException("rejected")
                }
            }
        )

        runCatching { service.pair(invitation) }

        assertEquals(existing, store.pairedPc())
        assertFalse(store.persistedValues.containsValue(invitation.code))
    }

    @Test
    fun `authenticated CSV writes PTAGAUTH before unchanged PTAGCSV1 length and payload`() {
        val payload = "a,b\n1,2\n".toByteArray(Charsets.UTF_8)
        val actual = WifiSender.authenticatedCsvRequestFrame("token", payload)
        val expected = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { out ->
                out.write("PTAGAUTH".toByteArray(Charsets.US_ASCII))
                out.writeShort(5)
                out.write("token".toByteArray(Charsets.UTF_8))
                out.write("PTAGCSV1".toByteArray(Charsets.US_ASCII))
                out.writeInt(payload.size)
                out.write(payload)
            }
            bytes.toByteArray()
        }

        assertArrayEquals(expected, actual)
    }

    @Test
    fun `authenticated ping writes PTAGAUTH before PTAGPNG1`() {
        val actual = WifiSender.authenticatedPingRequestFrame("token")
        val expected = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { out ->
                out.write("PTAGAUTH".toByteArray(Charsets.US_ASCII))
                out.writeShort(5)
                out.write("token".toByteArray(Charsets.UTF_8))
                out.write("PTAGPNG1".toByteArray(Charsets.US_ASCII))
            }
            bytes.toByteArray()
        }

        assertArrayEquals(expected, actual)
    }

    @Test
    fun `legacy CSV framing remains unchanged before pairing`() {
        val payload = byteArrayOf(1, 2, 3)
        val actual = WifiSender.legacyCsvRequestFrame(payload)
        val expected = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { out ->
                out.write("PTAGCSV1".toByteArray(Charsets.US_ASCII))
                out.writeInt(3)
                out.write(payload)
            }
            bytes.toByteArray()
        }

        assertArrayEquals(expected, actual)
    }

    private fun invitation() = PriceTagPairingInvitation(
        host = "192.168.10.12",
        port = 8765,
        code = "one-time-secret",
        expiresAtEpochSeconds = 9999L,
        pcName = "Office PC"
    )

    private class FakePairingStore(initial: PairedPriceTagPc? = null) : PairingStore {
        private var record = initial
        val persistedValues = linkedMapOf<String, String>()

        override fun pairedPc(): PairedPriceTagPc? = record
        override fun stableInstallationId(): String = "device-1"
        override fun savePairedPc(pairedPc: PairedPriceTagPc) {
            record = pairedPc
            persistedValues["token"] = pairedPc.deviceToken
            persistedValues["host"] = pairedPc.host
        }
        override fun forgetPairedPc() {
            record = null
            persistedValues.clear()
        }
    }
}
