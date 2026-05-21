package com.dosetap

import com.dosetap.util.Constants
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Verifies the PendingIntent request code formula produces unique codes per alarm.
 * Formula: (tapId.toInt() shl REQUEST_CODE_SHIFT) or followUpIndex
 */
class AlarmRequestCodeTest {

    private fun buildRequestCode(tapId: Long, followUpIndex: Int): Int =
        (tapId.toInt() shl Constants.REQUEST_CODE_SHIFT) or followUpIndex

    @Test
    fun `request codes are unique for different follow-up indices on same tap`() {
        val tapId = 42L
        val codes = (0..3).map { buildRequestCode(tapId, it) }.toSet()
        assertThat(codes).hasSize(4)
    }

    @Test
    fun `request codes are unique for different tap IDs with same follow-up index`() {
        val codes = (1L..10L).map { buildRequestCode(it, 0) }.toSet()
        assertThat(codes).hasSize(10)
    }

    @Test
    fun `follow-up index 0 produces lowest code for a tap`() {
        val tapId = 1L
        val code0 = buildRequestCode(tapId, 0)
        val code1 = buildRequestCode(tapId, 1)
        assertThat(code0).isLessThan(code1)
    }

    @Test
    fun `request code shift constant is 4`() {
        assertThat(Constants.REQUEST_CODE_SHIFT).isEqualTo(4)
    }

    @Test
    fun `snooze duration is 15 minutes`() {
        assertThat(Constants.SNOOZE_DURATION_MINUTES).isEqualTo(15)
        assertThat(Constants.SNOOZE_DURATION_MILLIS).isEqualTo(15 * 60 * 1000L)
    }

    @Test
    fun `default follow-ups are 4h and 8h`() {
        assertThat(Constants.DEFAULT_FOLLOWUPS).containsExactly(240, 480).inOrder()
    }
}
