package com.dosetap

import com.dosetap.domain.DoseTiming
import com.dosetap.domain.model.Medication
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar

class DoseTimingTest {

    private val hour = 60 * 60_000L

    private fun at(day: Int, h: Int, m: Int = 0): Long = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, day, h, m, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val med = Medication(name = "Med", followUps = listOf(240, 480), durationMinutes = 480)

    @Test
    fun `night shift dose before midnight is still current after midnight`() {
        assertThat(DoseTiming.isCurrent(at(20, 20), med, at(21, 3))).isTrue()
    }

    @Test
    fun `yesterday morning dose is not current next morning`() {
        assertThat(DoseTiming.isCurrent(at(20, 8), med, at(21, 7))).isFalse()
    }

    @Test
    fun `early-hours dose is not current at the next evening shift`() {
        // Dosed 00:30, next shift dose 23:30 the same calendar day — must read as a new cycle.
        assertThat(DoseTiming.isCurrent(at(20, 0, 30), med, at(20, 23, 30))).isFalse()
    }

    @Test
    fun `day dose stays current through the evening`() {
        assertThat(DoseTiming.isCurrent(at(20, 8), med, at(20, 22))).isTrue()
    }

    @Test
    fun `double dose window uses duration and works in both directions`() {
        val last = at(20, 23)
        assertThat(DoseTiming.isPossibleDoubleDose(med, last, last + 7 * hour)).isTrue()
        assertThat(DoseTiming.isPossibleDoubleDose(med, last, last + 9 * hour)).isFalse()
        assertThat(DoseTiming.isPossibleDoubleDose(med, last, last - 1 * hour)).isTrue()
        assertThat(DoseTiming.isPossibleDoubleDose(med, null, last)).isFalse()
    }

    @Test
    fun `double dose window falls back to 3h without a duration`() {
        val noDuration = med.copy(durationMinutes = null)
        val last = at(20, 23)
        assertThat(DoseTiming.isPossibleDoubleDose(noDuration, last, last + 2 * hour)).isTrue()
        assertThat(DoseTiming.isPossibleDoubleDose(noDuration, last, last + 4 * hour)).isFalse()
    }
}
