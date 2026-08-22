package com.industrial.barcodescanner.utils

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keystore-backed persistence for one paired Price Tag PC. QR one-time codes are
 * deliberately never accepted by this class and therefore can never be written.
 */
@Singleton
class SecurePairingStore @Inject constructor(
    @ApplicationContext private val context: Context
) : PairingStore {

    @Suppress("DEPRECATION")
    private val preferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun pairedPc(): PairedPriceTagPc? {
        val token = preferences.getString(KEY_TOKEN, null)?.trim().orEmpty()
        val host = preferences.getString(KEY_HOST, null)?.trim().orEmpty()
        val port = preferences.getInt(KEY_PORT, -1)
        val pcName = preferences.getString(KEY_PC_NAME, null)?.trim().orEmpty()
        val protocol = preferences.getInt(KEY_PROTOCOL, -1)
        val pairedAt = preferences.getLong(KEY_PAIRED_AT, 0L)
        if (token.isEmpty() || host.isEmpty() || port !in 1024..65535 || protocol != PROTOCOL) return null
        return PairedPriceTagPc(
            host = host,
            port = port,
            deviceToken = token,
            pcName = pcName.ifBlank { host },
            protocol = protocol,
            pairedAtEpochMillis = pairedAt
        )
    }

    override fun stableInstallationId(): String {
        val existing = preferences.getString(KEY_INSTALLATION_ID, null)?.trim().orEmpty()
        if (existing.isNotEmpty()) return existing
        val created = UUID.randomUUID().toString()
        preferences.edit().putString(KEY_INSTALLATION_ID, created).commit()
        return created
    }

    override fun savePairedPc(pairedPc: PairedPriceTagPc) {
        require(pairedPc.deviceToken.isNotBlank()) { "device token is required" }
        require(pairedPc.host.isNotBlank()) { "host is required" }
        require(pairedPc.port in 1024..65535) { "invalid port" }
        require(pairedPc.protocol == PROTOCOL) { "unsupported protocol" }
        preferences.edit()
            .putString(KEY_TOKEN, pairedPc.deviceToken)
            .putString(KEY_HOST, pairedPc.host)
            .putInt(KEY_PORT, pairedPc.port)
            .putString(KEY_PC_NAME, pairedPc.pcName)
            .putInt(KEY_PROTOCOL, pairedPc.protocol)
            .putLong(KEY_PAIRED_AT, pairedPc.pairedAtEpochMillis)
            .commit()
    }

    override fun forgetPairedPc() {
        preferences.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_HOST)
            .remove(KEY_PORT)
            .remove(KEY_PC_NAME)
            .remove(KEY_PROTOCOL)
            .remove(KEY_PAIRED_AT)
            .commit()
    }

    private companion object {
        const val FILE_NAME = "price_tag_pairing_preferences"
        const val PROTOCOL = 3
        const val KEY_TOKEN = "device_token"
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_PC_NAME = "pc_name"
        const val KEY_PROTOCOL = "protocol"
        const val KEY_PAIRED_AT = "paired_at"
        const val KEY_INSTALLATION_ID = "installation_id"
    }
}
