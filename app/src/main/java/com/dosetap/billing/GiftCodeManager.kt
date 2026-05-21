package com.dosetap.billing

import android.content.Context
import com.dosetap.R
import java.security.MessageDigest

/**
 * Validates one-time gift codes against SHA-256 hashes bundled in res/raw/gift_codes.txt.
 *
 * Codes are stored as hashes so the plain-text codes are not in the APK.
 * Used codes are tracked per-device in DataStore via [com.dosetap.data.prefs.SettingsRepository].
 *
 * To generate a new code:
 *   echo -n "YOUR-CODE-HERE" | sha256sum
 * then paste the hash as a new line in res/raw/gift_codes.txt.
 *
 * Default codes: DOSETAP-GIFT-0001 through DOSETAP-GIFT-0020
 */
class GiftCodeManager(private val context: Context) {

    private val validHashes: Set<String> by lazy {
        context.resources.openRawResource(R.raw.gift_codes)
            .bufferedReader()
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /** Returns true if [code] is a valid gift code (case-insensitive). */
    fun isValid(code: String): Boolean {
        val hash = sha256(code.trim().uppercase())
        return hash in validHashes
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
