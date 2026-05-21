package com.dosetap

import com.dosetap.util.TimeFormatter
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar

class TimeFormatterTest {

    @Test
    fun `formatDuration returns less than 1 min for very short durations`() {
        assertThat(TimeFormatter.formatDuration(30_000L)).isEqualTo("<1 min")
    }

    @Test
    fun `formatDuration returns minutes for durations under an hour`() {
        val thirtyMinutes = 30 * 60 * 1000L
        assertThat(TimeFormatter.formatDuration(thirtyMinutes)).isEqualTo("30 min")
    }

    @Test
    fun `formatDuration returns hours only when no remainder`() {
        val twoHours = 2 * 60 * 60 * 1000L
        assertThat(TimeFormatter.formatDuration(twoHours)).isEqualTo("2h")
    }

    @Test
    fun `formatDuration returns hours and minutes`() {
        val oneHourThirty = (90 * 60 * 1000L)
        assertThat(TimeFormatter.formatDuration(oneHourThirty)).isEqualTo("1h 30m")
    }

    @Test
    fun `formatDuration handles negative (absolute value)`() {
        val thirtyMinutes = -(30 * 60 * 1000L)
        assertThat(TimeFormatter.formatDuration(thirtyMinutes)).isEqualTo("30 min")
    }

    @Test
    fun `formatInterval returns minutes string for under an hour`() {
        assertThat(TimeFormatter.formatInterval(30)).isEqualTo("30 minutes")
    }

    @Test
    fun `formatInterval returns hours string for exact hours`() {
        assertThat(TimeFormatter.formatInterval(120)).isEqualTo("2 hours")
    }

    @Test
    fun `formatInterval returns hours and minutes`() {
        assertThat(TimeFormatter.formatInterval(90)).isEqualTo("1 hours 30 min")
    }

    @Test
    fun `startOfDay returns midnight of the given day`() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 15)
        cal.set(Calendar.MINUTE, 30)
        val millis = cal.timeInMillis

        val result = TimeFormatter.startOfDay(millis)
        val resultCal = Calendar.getInstance().also { it.timeInMillis = result }

        assertThat(resultCal.get(Calendar.HOUR_OF_DAY)).isEqualTo(0)
        assertThat(resultCal.get(Calendar.MINUTE)).isEqualTo(0)
        assertThat(resultCal.get(Calendar.SECOND)).isEqualTo(0)
    }

    @Test
    fun `isSameDay returns true for two timestamps on the same day`() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 10)
        val morning = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 22)
        val evening = cal.timeInMillis

        assertThat(TimeFormatter.isSameDay(morning, evening)).isTrue()
    }

    @Test
    fun `isSameDay returns false for timestamps on different days`() {
        val cal = Calendar.getInstance()
        val today = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrow = cal.timeInMillis

        assertThat(TimeFormatter.isSameDay(today, tomorrow)).isFalse()
    }

    @Test
    fun `isToday returns true for current time`() {
        assertThat(TimeFormatter.isToday(System.currentTimeMillis())).isTrue()
    }

    @Test
    fun `isToday returns false for yesterday`() {
        val yesterday = Calendar.getInstance().also {
            it.add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis
        assertThat(TimeFormatter.isToday(yesterday)).isFalse()
    }
}
