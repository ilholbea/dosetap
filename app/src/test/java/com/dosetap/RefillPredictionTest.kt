package com.dosetap

import com.dosetap.domain.RefillPrediction
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Verifies the refill-by prediction behind HomeViewModel's prescription-aware refill banner.
 * Tests the production [RefillPrediction] directly (this used to test a private copy of the logic).
 */
class RefillPredictionTest {

    private val dayMillis = 24L * 60 * 60 * 1000
    private val now = 1_700_000_000_000L

    private fun compute(rxDate: Long?, daysSupply: Int?, bufferDays: Int = 7, at: Long = now) =
        RefillPrediction.refillByMillis(rxDate, daysSupply, bufferDays, at)

    @Test
    fun `returns null when no Rx data provided`() {
        assertThat(compute(null, 30)).isNull()
        assertThat(compute(now, null)).isNull()
    }

    @Test
    fun `returns null when refill date is far in the future`() {
        // 30 days supply, 7-day buffer: refill-by is day 23. One day after fill it's 22 days
        // away — beyond the 14-day (bufferDays * 2) lead.
        assertThat(compute(rxDate = now, daysSupply = 30, at = now + dayMillis)).isNull()
    }

    @Test
    fun `returns refill-by millis between refill-by and run-out`() {
        // Filled 25 days ago: refill-by passed 2 days ago, but the pills last 5 more days — the
        // most urgent window, so the banner must still show.
        val rxDate = now - 25 * dayMillis
        assertThat(compute(rxDate, 30)).isEqualTo(rxDate + 23 * dayMillis)
    }

    @Test
    fun `returns refill-by when exactly at the buffer threshold`() {
        // Filled 23 days ago → refill-by is today.
        assertThat(compute(now - 23 * dayMillis, 30)).isNotNull()
    }

    @Test
    fun `still shows one day after run-out`() {
        assertThat(compute(now - 31 * dayMillis, 30)).isNotNull()
    }

    @Test
    fun `returns null once user is more than one day past run-out`() {
        // Filled 35 days ago, 30-day supply → ran out 5 days ago.
        assertThat(compute(now - 35 * dayMillis, 30)).isNull()
    }
}
