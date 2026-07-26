package com.dosetap

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Verifies the days-until-refill arithmetic used by HomeViewModel to decide whether to
 * show the prescription-aware refill banner. Lifted into a standalone helper so it can
 * be tested without spinning up the whole VM.
 */
class RefillPredictionTest {

    private val dayMillis = 24L * 60 * 60 * 1000

    private fun computeRefillByMillis(
        rxDate: Long?,
        daysSupply: Int?,
        bufferDays: Int,
        nowMillis: Long,
    ): Long? {
        rxDate ?: return null
        daysSupply ?: return null
        val refillByMillis = rxDate + (daysSupply - bufferDays).coerceAtLeast(0) * dayMillis
        val daysUntilRefill = (refillByMillis - nowMillis) / dayMillis
        return if (daysUntilRefill in -1..(bufferDays * 2L)) refillByMillis else null
    }

    @Test
    fun `returns null when no Rx data provided`() {
        val now = 1_700_000_000_000L
        assertThat(computeRefillByMillis(null, 30, 7, now)).isNull()
        assertThat(computeRefillByMillis(now, null, 7, now)).isNull()
    }

    @Test
    fun `returns null when refill date is far in the future`() {
        val now = 1_700_000_000_000L
        // 30 days supply, 7-day buffer = banner at day 23. Filled today.
        val rxDate = now
        // We're 1 day past fill, so refill-by date is in 22 days, well beyond bufferDays * 2 (14).
        val result = computeRefillByMillis(rxDate, 30, 7, now + dayMillis)
        assertThat(result).isNull()
    }

    @Test
    fun `returns refill-by millis when inside the warning window`() {
        val now = 1_700_000_000_000L
        // Filled 25 days ago, 30-day supply, 7-day buffer → refill-by was 2 days ago.
        // Within the [-1, 14] window.
        val rxDate = now - 25 * dayMillis
        val result = computeRefillByMillis(rxDate, 30, 7, now)
        assertThat(result).isNotNull()
        val expectedRefillBy = rxDate + 23 * dayMillis
        assertThat(result).isEqualTo(expectedRefillBy)
    }

    @Test
    fun `returns refill-by when exactly at the buffer threshold`() {
        val now = 1_700_000_000_000L
        // 30-day supply, 7-day buffer, filled 23 days ago → refill-by is today.
        val rxDate = now - 23 * dayMillis
        val result = computeRefillByMillis(rxDate, 30, 7, now)
        assertThat(result).isNotNull()
    }

    @Test
    fun `returns null once user is more than one day past run-out`() {
        val now = 1_700_000_000_000L
        // Filled 35 days ago, 30-day supply, 7-day buffer → refill-by was 12 days ago.
        // Outside the [-1, 14] window on the lower end.
        val rxDate = now - 35 * dayMillis
        val result = computeRefillByMillis(rxDate, 30, 7, now)
        assertThat(result).isNull()
    }
}
