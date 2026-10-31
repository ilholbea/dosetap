package com.dosetap.billing

import android.content.Context
import android.provider.Settings
import android.util.Log
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val TAG = "SponsorCodeManager"

/**
 * Generates and validates Sponsor-a-Friend codes (recipient gets the 14-day Pro trial).
 *
 * Design: no backend required, and a code made on the sender's phone validates on **any** install.
 * - Format: `DTP-NNNNNN-TTTTTT` — N = a per-device, per-slot nonce, T = a tag over that nonce.
 * - The nonce comes from a device-bound key, so each sender gets their own stable code per slot.
 * - The tag is an HMAC under an app-wide key, so the recipient's device can verify it offline.
 *
 * (v1 derived the whole code from the sender's ANDROID_ID, so it only ever validated on the
 * sender's own phone — friends could never redeem it, and no redeem path even called it.)
 *
 * Security note: the app key ships in the APK, so a determined reverser could mint codes. That's
 * the same trust level as the bundled gift-code hashes and acceptable for a free 14-day trial; a
 * blind guess has a 1 in 16.7M chance per attempt.
 */
class SponsorCodeManager(private val context: Context) {

    private val deviceKey: ByteArray by lazy {
        val androidId = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        val raw = "${context.packageName}:sponsor_key_v1:$androidId"
        MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
    }

    /** Generate this device's sponsor code for the given slot index (0-based). Stable per device + slot. */
    fun generateCode(slotIndex: Int): String {
        val nonce = hex(hmac(deviceKey, "slot:$slotIndex:v2").copyOf(3))
        return "DTP-$nonce-${tagFor(nonce)}"
    }

    /** True if [code] is a genuine sponsor code from any DoseTap install. */
    fun isValidSponsorCode(code: String): Boolean {
        val match = CODE_REGEX.matchEntire(code.trim().uppercase()) ?: return false
        val (nonce, tag) = match.destructured
        val valid = MessageDigest.isEqual(tagFor(nonce).toByteArray(), tag.toByteArray())
        if (valid) Log.d(TAG, "Valid sponsor code")
        return valid
    }

    private fun tagFor(nonce: String): String =
        hex(hmac(APP_KEY.toByteArray(), "sponsor:v2:$nonce").copyOf(3))

    private fun hmac(key: ByteArray, input: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(input.toByteArray())
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it) }

    companion object {
        /** Maximum gift slots per Pro device. */
        const val MAX_SLOTS = 1

        private const val APP_KEY = "dosetap-sponsor-v2:4ef903a165494f4a4fe3e28cc5c83da6dc1c9b369da3d532"
        private val CODE_REGEX = Regex("""DTP-([0-9A-F]{6})-([0-9A-F]{6})""")
    }
}
