package com.dosetap

import com.dosetap.domain.model.MedicationPreset
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MedicationPresetTest {

    @Test
    fun `ALL contains the expected number of presets`() {
        assertThat(MedicationPreset.ALL).hasSize(9)
    }

    @Test
    fun `every preset has a non-blank label`() {
        MedicationPreset.ALL.forEach { preset ->
            assertThat(preset.label.isBlank()).isFalse()
        }
    }

    @Test
    fun `stimulant presets have a positive duration`() {
        MedicationPreset.ALL
            .filter { it.hasEffectWindow }
            .forEach { preset ->
                assertThat(preset.durationMinutes).isNotNull()
                assertThat(preset.durationMinutes!!).isGreaterThan(0)
                // Realistic upper bound: no oral stimulant exceeds 16h.
                assertThat(preset.durationMinutes).isLessThan(16 * 60 + 1)
            }
    }

    @Test
    fun `non-stimulant presets have null duration`() {
        MedicationPreset.ALL
            .filter { !it.hasEffectWindow }
            .forEach { preset ->
                assertThat(preset.durationMinutes).isNull()
            }
    }

    @Test
    fun `every preset has at least one follow-up reminder offset`() {
        MedicationPreset.ALL.forEach { preset ->
            assertThat(preset.followUps).isNotEmpty()
            preset.followUps.forEach { offset ->
                assertThat(offset).isGreaterThan(0)
            }
        }
    }
}
