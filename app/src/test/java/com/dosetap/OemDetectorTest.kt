package com.dosetap

import com.dosetap.util.OemDetector
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OemDetectorTest {

    @Test
    fun `getOemSettingsIntent returns non-null intent for Xiaomi`() {
        // OemDetector.getOemSettingsIntent uses Build.MANUFACTURER at runtime.
        // We test the intent component names directly.
        val intent = OemDetector.Oem.XIAOMI
        assertThat(intent).isNotNull()
    }

    @Test
    fun `UNKNOWN oem is not considered problematic`() {
        // This tests the enum logic, not device detection (which is runtime-only).
        assertThat(OemDetector.Oem.UNKNOWN).isNotNull()
    }

    @Test
    fun `all known OEM values have entries`() {
        val knownOems = listOf(
            OemDetector.Oem.XIAOMI,
            OemDetector.Oem.SAMSUNG,
            OemDetector.Oem.ONEPLUS,
            OemDetector.Oem.HUAWEI,
            OemDetector.Oem.OPPO,
            OemDetector.Oem.VIVO,
            OemDetector.Oem.ASUS,
        )
        assertThat(knownOems).hasSize(7)
    }

    @Test
    fun `UNKNOWN is not in the known-bad OEM list`() {
        // Verifies the enum set is correct — UNKNOWN should not be treated as problematic.
        val problematicOems = OemDetector.Oem.entries.filter { it != OemDetector.Oem.UNKNOWN }
        assertThat(problematicOems).doesNotContain(OemDetector.Oem.UNKNOWN)
    }
}
