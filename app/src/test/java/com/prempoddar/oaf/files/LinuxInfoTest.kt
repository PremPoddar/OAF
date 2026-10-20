package com.prempoddar.oaf.files

import com.google.common.truth.Truth.assertThat
import com.prempoddar.oaf.ui.browser.LinuxInfo
import org.junit.Test

class LinuxInfoTest {
    @Test fun modeStringsReadLikeLs() {
        assertThat(LinuxInfo.modeString(0x4000 or "755".toInt(8))).isEqualTo("drwxr-xr-x")
        assertThat(LinuxInfo.modeString(0x8000 or "640".toInt(8))).isEqualTo("-rw-r-----")
        assertThat(LinuxInfo.modeString(0xA000 or "777".toInt(8))).isEqualTo("lrwxrwxrwx")
        assertThat(LinuxInfo.modeString(0x4000 or "1777".toInt(8))).isEqualTo("drwxrwxrwt")
        assertThat(LinuxInfo.modeString(0x8000 or "4644".toInt(8))).isEqualTo("-rwSr--r--")
    }
}
