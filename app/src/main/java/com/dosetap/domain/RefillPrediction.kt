package com.dosetap.domain

/**
 * "Book your psych appointment by X" prediction (Pro) for the Home refill banner.
 */
object RefillPrediction {

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /**
     * Returns the refill-by date (prescription date + supply − buffer) when the banner should show,
     * or null. It shows from `bufferDays * 2` days before the refill-by date until one day past
     * actual run-out — the days between refill-by and run-out are the most urgent, so the banner
     * must not disappear there (it previously vanished one day after the refill-by date).
     */
    fun refillByMillis(rxDateMillis: Long?, daysSupply: Int?, bufferDays: Int, nowMillis: Long): Long? {
        rxDateMillis ?: return null
        daysSupply ?: return null
        val refillBy = rxDateMillis + (daysSupply - bufferDays).coerceAtLeast(0) * DAY_MILLIS
        val runOut = rxDateMillis + daysSupply * DAY_MILLIS
        val showFrom = refillBy - bufferDays * 2L * DAY_MILLIS
        return if (nowMillis >= showFrom && nowMillis <= runOut + DAY_MILLIS) refillBy else null
    }
}
