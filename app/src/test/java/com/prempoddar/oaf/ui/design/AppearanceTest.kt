package com.prempoddar.oaf.ui.design

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppearanceTest {
    @Test fun lookSettingsRoundTrip() {
        val a = Appearance(
            dusk = true, font = Appearance.Font.GROTESK, palette = Appearance.Palette.VIBRANT, contrast = Appearance.Contrast.HIGH,
            corners = Appearance.Corners.SHARP, compact = true, textSize = Appearance.TextSize.LARGE, haptics = false,
        )
        assertThat(Appearance().withExtras(a.extras())).isEqualTo(a)
    }

    @Test fun missingOrUnknownValuesKeepDefaults() {
        assertThat(Appearance().withExtras(null)).isEqualTo(Appearance())
        assertThat(Appearance().withExtras("font=COMIC;haptics=0;junk")).isEqualTo(Appearance(haptics = false))
    }
}
