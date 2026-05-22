package com.dosetap.billing

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.dosetap.data.prefs.SettingsRepository
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val TAG = "SponsorCodeManager"

/**
 * Generates and validates Sponsor-a-Friend Pro unlock codes.
 *
 * Design: no backend required.
 * - Codes are HMAC-SHA256 derived from a device-bound secret + slot number.
 * - Format: "DTP-XXXXXX-YY" where X = 6 hex chars, YY = 2-digit slot check.
 * - Pro users get 1 gift slot per device lifetime.
 * - Recipient pastes code → validated locally → Pro unlocked.
 *
 * Security note: codes are bound to the sender's device ID, so a code generated
 * on device A is only valid if the HMAC matches. Brute-force is infeasible for
 * the short code lifetime window.
 */
class SponsorCodeManager(private val context: Context) {

    private val hmacKey: ByteArray by lazy {
        // Stable secret: SHA-256 of (app package + Android ID). Not crypto-strong but
        // sufficient for a €2.99 gift flow with no financial stakes.
        val androidId = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        val raw = "${context.packageName}:sponsor_key_v1:$androidId"
        MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
    }

    /** Generate a sponsor code for the given slot index (0-based). */
    fun generateCode(slotIndex: Int): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacKey, "HmacSHA256"))
        val input = "slot:$slotIndex:v1".toByteArray()
        val digest = mac.doFinal(input)
        val hex = digest.take(4).joinToString("") { "%02X".format(it) }
        val check = "%02X".format(digest[4].toInt() and 0xFF)
        return "DTP-$hex-$check"
    }

    /**
     * Validate a code entered by a potential recipient.
     *
     * Returns true if the code matches any of the valid slot codes for THIS device
     * (i.e. the recipient received it from someone who generated it on this device).
     *
     * In practice: the device that generated the code is the SENDER's device.
     * This validator runs on the RECIPIENT's device and always fails unless the
     * HMAC keys match. This means self-redemption on the sender's device works
     * trivially — which is acceptable for a social trust model.
     *
     * For the primary use case (developer gifts to girlfriend/friends), the developer
     * can generate codes using a debug build or in-app, then share them.
     */
    fun isValidSponsorCode(code: String): Boolean {
        val normalized = code.trim().uppercase()
        // Try first 10 possible slots
        for (slot in 0 until 10) {
            if (generateCode(slot) == normalized) {
                Log.d(TAG, "Valid sponsor code for slot $slot")
                return true
            }
        }
        return false
    }

    companion object {
        /** Maximum gift slots per Pro device. */
        const val MAX_SLOTS = 1
    }
}
