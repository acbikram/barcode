package com.industrial.barcodescanner.utils

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** A validated, short-lived QR pairing invitation. Never persist [code]. */
data class PriceTagPairingInvitation(
    val host: String,
    val port: Int,
    val code: String,
    val expiresAtEpochSeconds: Long,
    val pcName: String
)

/** The encrypted record retained after a successful pairing. */
data class PairedPriceTagPc(
    val host: String,
    val port: Int,
    val deviceToken: String,
    val pcName: String,
    val protocol: Int,
    val pairedAtEpochMillis: Long
)

sealed interface PairingQrValidation {
    data class Valid(val invitation: PriceTagPairingInvitation) : PairingQrValidation
    data class Invalid(val reason: Reason) : PairingQrValidation

    enum class Reason {
        MALFORMED,
        UNSUPPORTED,
        EXPIRED,
        INVALID_CONNECTION
    }
}

/** Strict parser for the v3 one-time pairing QR payload. */
object PriceTagPairingQr {
    private const val PROTOCOL = 3
    private const val TYPE = "price_tag_pair"

    fun validate(raw: String, nowEpochSeconds: Long): PairingQrValidation {
        val json = try {
            Json.parseToJsonElement(raw).jsonObject
        } catch (_: Exception) {
            return PairingQrValidation.Invalid(PairingQrValidation.Reason.MALFORMED)
        }
        if (json["v"]?.jsonPrimitive?.intOrNull != PROTOCOL ||
            json["type"]?.jsonPrimitive?.contentOrNull != TYPE
        ) {
            return PairingQrValidation.Invalid(PairingQrValidation.Reason.UNSUPPORTED)
        }
        val host = json["host"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val port = json["port"]?.jsonPrimitive?.intOrNull ?: -1
        val code = json["code"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val expiresAt = json["expiresAt"]?.jsonPrimitive?.longOrNull ?: -1L
        val pcName = json["pcName"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (host.isEmpty() || code.isEmpty() || port !in 1024..65535) {
            return PairingQrValidation.Invalid(PairingQrValidation.Reason.INVALID_CONNECTION)
        }
        if (expiresAt <= nowEpochSeconds) {
            return PairingQrValidation.Invalid(PairingQrValidation.Reason.EXPIRED)
        }
        return PairingQrValidation.Valid(
            PriceTagPairingInvitation(host, port, code, expiresAt, pcName.ifBlank { host })
        )
    }
}

/** Exact binary framing helpers shared by pairing and authenticated PC requests. */
object PriceTagPairingProtocol {
    private val PAIR_MAGIC = "PTAGPAIR".toByteArray(StandardCharsets.US_ASCII)
    private val AUTH_MAGIC = "PTAGAUTH".toByteArray(StandardCharsets.US_ASCII)
    const val APP_NAME = "Barcode To CSV"
    const val MAX_CODE_BYTES = 512
    const val MAX_DEVICE_ID_BYTES = 120
    const val MAX_APP_NAME_BYTES = 80
    const val MAX_DEVICE_TOKEN_BYTES = 4096

    fun pairRequestFrame(code: String, installationId: String, appName: String = APP_NAME): ByteArray {
        val codeBytes = code.toByteArray(StandardCharsets.UTF_8)
        val idBytes = installationId.toByteArray(StandardCharsets.UTF_8)
        val appBytes = appName.toByteArray(StandardCharsets.UTF_8)
        requireField(codeBytes, MAX_CODE_BYTES, "code")
        requireField(idBytes, MAX_DEVICE_ID_BYTES, "installation ID")
        requireField(appBytes, MAX_APP_NAME_BYTES, "app name")
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(PAIR_MAGIC)
                output.writeShort(codeBytes.size)
                output.write(codeBytes)
                output.writeShort(idBytes.size)
                output.write(idBytes)
                output.writeShort(appBytes.size)
                output.write(appBytes)
                output.flush()
            }
            bytes.toByteArray()
        }
    }

    /** Parses exactly one successful pairing response line without retaining QR data. */
    fun parsePairedResponse(responseLine: String, invitation: PriceTagPairingInvitation): PairedPriceTagPc {
        val response = try {
            Json.parseToJsonElement(responseLine).jsonObject
        } catch (_: Exception) {
            throw IllegalStateException("Pairing response is invalid")
        }
        val token = response["deviceToken"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val protocol = response["protocol"]?.jsonPrimitive?.intOrNull ?: -1
        if (response["type"]?.jsonPrimitive?.contentOrNull != "paired" || protocol != 3 || token.isEmpty()) {
            throw IllegalStateException("Pairing was rejected")
        }
        requireField(token.toByteArray(StandardCharsets.UTF_8), MAX_DEVICE_TOKEN_BYTES, "device token")
        return PairedPriceTagPc(
            host = invitation.host,
            port = invitation.port,
            deviceToken = token,
            pcName = response["pcName"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { invitation.pcName },
            protocol = protocol,
            pairedAtEpochMillis = 0L
        )
    }

    fun authenticatedPrefix(deviceToken: String): ByteArray {
        val tokenBytes = deviceToken.toByteArray(StandardCharsets.UTF_8)
        requireField(tokenBytes, MAX_DEVICE_TOKEN_BYTES, "device token")
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(AUTH_MAGIC)
                output.writeShort(tokenBytes.size)
                output.write(tokenBytes)
                output.flush()
            }
            bytes.toByteArray()
        }
    }

    private fun requireField(value: ByteArray, maxBytes: Int, label: String) {
        require(value.isNotEmpty()) { "$label is required" }
        require(value.size <= maxBytes) { "$label is too long" }
    }
}

interface PairingStore {
    fun pairedPc(): PairedPriceTagPc?
    fun stableInstallationId(): String
    fun savePairedPc(pairedPc: PairedPriceTagPc)
    fun forgetPairedPc()
}

interface PriceTagPairingClient {
    fun pair(invitation: PriceTagPairingInvitation, installationId: String): PairedPriceTagPc
}

/** Coordinates a socket pairing attempt and commits encrypted storage only on success. */
class PriceTagPairingService(
    private val store: PairingStore,
    private val client: PriceTagPairingClient,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) {
    fun pair(invitation: PriceTagPairingInvitation): PairedPriceTagPc {
        val paired = client.pair(invitation, store.stableInstallationId())
        val saved = paired.copy(pairedAtEpochMillis = nowEpochMillis())
        store.savePairedPc(saved)
        return saved
    }
}
