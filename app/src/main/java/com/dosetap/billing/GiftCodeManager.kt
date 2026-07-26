package com.dosetap.billing

import android.content.Context
import com.dosetap.R
import java.security.MessageDigest

/**
 * Validates gift codes and developer grant codes.
 *
 * Two code types:
 *  - [CodeResult.DevGrant]   — grants permanent Pro. Codes: DOSETAP-DEV-01 … DOSETAP-DEV-20.
 *                              **Debug builds only** — the hash list lives in the debug source
 *                              set (`src/debug/res/raw/dev_grant_codes.txt`) and is therefore
 *                              absent from the release APK, so the permanent-Pro backdoor cannot
 *                              be exercised in production (E-C3). The list is resolved at runtime
 *                              by name; when the resource is missing (release) the set is empty.
 *  - [CodeResult.SponsorGift] — starts a 14-day Pro trial. Hashes in res/raw/gift_codes.txt.
 *
 * To generate a new dev grant code hash:
 *   echo -n "DOSETAP-DEV-21" | sha256sum
 * Paste the hash into src/debug/res/raw/dev_grant_codes.txt and rebuild a debug variant.
 */
class GiftCodeManager(private val context: Context) {

    enum class CodeResult { DevGrant, SponsorGift, Invalid }

    private val devGrantHashes: Set<String> by lazy {
        // Resolved by name so this compiles in the release variant, where the resource does not
        // exist. Missing resource (getIdentifier == 0) => empty set => dev grants never validate.
        val resId = context.resources.getIdentifier("dev_grant_codes", "raw", context.packageName)
        if (resId == 0) {
            emptySet()
        } else {
            context.resources.openRawResource(resId)
                .bufferedReader().readLines()
                .map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }
    }

    private val giftHashes: Set<String> by lazy {
        context.resources.openRawResource(R.raw.gift_codes)
            .bufferedReader().readLines()
            .map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    /** Check what type of code was entered. Dev grants are checked first. */
    fun validate(code: String): CodeResult {
        val hash = sha256(code.trim().uppercase())
        return when {
            hash in devGrantHashes -> CodeResult.DevGrant
            hash in giftHashes -> CodeResult.SponsorGift
            else -> CodeResult.Invalid
        }
    }

    /** Legacy single-bool check used by old call sites. */
    fun isValid(code: String): Boolean = validate(code) != CodeResult.Invalid

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
