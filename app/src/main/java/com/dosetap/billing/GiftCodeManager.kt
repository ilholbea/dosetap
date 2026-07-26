package com.dosetap.billing

import android.content.Context
import com.dosetap.R
import java.security.MessageDigest

/**
 * Validates gift codes and developer grant codes.
 *
 * Code types:
 *  - [CodeResult.DevGrant]      — grants permanent Pro. Codes: DOSETAP-DEV-01 … DOSETAP-DEV-20.
 *                                 **Debug builds only** — the hash list lives in the debug source
 *                                 set (`src/debug/res/raw/dev_grant_codes.txt`), so the guessable
 *                                 backdoor is absent from release (E-C3). Resolved at runtime by
 *                                 name; when the resource is missing (release) the set is empty.
 *  - [CodeResult.PermanentGift] — grants permanent Pro in **any** build (Play Store included).
 *                                 High-entropy random codes; only their SHA-256 hashes ship, in
 *                                 res/raw/premium_codes.txt. This is the "gift Pro to a friend"
 *                                 flow. Because the codes are random, they can't be brute-forced
 *                                 from the hashes; but a leaked code can't be revoked without an
 *                                 app update (no backend — offline app). Keep the plaintext list
 *                                 private.
 *  - [CodeResult.SponsorGift]   — starts a 14-day Pro trial. Hashes in res/raw/gift_codes.txt.
 *
 * To mint a new permanent gift code:
 *   printf '%s' "DOSETAP-XXXXX-XXXXX" | sha256sum
 * Paste the hash into res/raw/premium_codes.txt and rebuild. (Uppercase; validate() uppercases.)
 */
class GiftCodeManager(private val context: Context) {

    enum class CodeResult { DevGrant, PermanentGift, SponsorGift, Invalid }

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

    private val premiumHashes: Set<String> by lazy {
        context.resources.openRawResource(R.raw.premium_codes)
            .bufferedReader().readLines()
            .map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    private val giftHashes: Set<String> by lazy {
        context.resources.openRawResource(R.raw.gift_codes)
            .bufferedReader().readLines()
            .map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    /** Check what type of code was entered. Permanent grants are checked before the trial. */
    fun validate(code: String): CodeResult {
        val hash = sha256(code.trim().uppercase())
        return when {
            hash in devGrantHashes -> CodeResult.DevGrant
            hash in premiumHashes -> CodeResult.PermanentGift
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
