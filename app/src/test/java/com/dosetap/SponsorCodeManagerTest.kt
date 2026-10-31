package com.dosetap

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.dosetap.billing.SponsorCodeManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SponsorCodeManagerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun setAndroidId(id: String) {
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ANDROID_ID, id)
    }

    @Test
    fun `code generated on one device validates on another`() {
        setAndroidId("sender-device")
        val code = SponsorCodeManager(context).generateCode(0)

        setAndroidId("friend-device")
        assertThat(SponsorCodeManager(context).isValidSponsorCode(code)).isTrue()
    }

    @Test
    fun `code is stable per device and slot, and accepts lowercase input`() {
        setAndroidId("sender-device")
        val manager = SponsorCodeManager(context)
        val code = manager.generateCode(0)
        assertThat(code).matches("DTP-[0-9A-F]{6}-[0-9A-F]{6}")
        assertThat(manager.generateCode(0)).isEqualTo(code)
        assertThat(manager.isValidSponsorCode(" ${code.lowercase()} ")).isTrue()
    }

    @Test
    fun `tampered or malformed codes are rejected`() {
        val manager = SponsorCodeManager(context)
        val code = manager.generateCode(0)
        val tampered = code.dropLast(1) + if (code.last() == '0') "1" else "0"
        assertThat(manager.isValidSponsorCode(tampered)).isFalse()
        assertThat(manager.isValidSponsorCode("DTP-123")).isFalse()
        assertThat(manager.isValidSponsorCode("")).isFalse()
    }
}
