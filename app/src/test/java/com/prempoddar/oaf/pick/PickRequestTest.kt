package com.prempoddar.oaf.pick

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PickRequestTest {
    private fun req(vararg mimes: String, multiple: Boolean = false) = PickRequest(mimes.toList(), multiple, "WhatsApp", {}, {})

    @Test fun picturesOnlyWhenPicturesAreWanted() {
        val r = req("image/*")
        assertThat(r.accepts("image/jpeg")).isTrue()
        assertThat(r.accepts("video/mp4")).isFalse()
        assertThat(r.accepts(null as String?)).isFalse()
        assertThat(r.what).isEqualTo("a picture")
        assertThat(r.startRoute).isEqualTo("category/IMAGES")
    }

    @Test fun anythingWhenAnythingIsWanted() {
        val r = req("*/*", multiple = true)
        assertThat(r.accepts("application/zip")).isTrue()
        assertThat(r.what).isEqualTo("files")
        assertThat(r.startRoute).isEqualTo("browser")
    }

    @Test fun exactTypesAndMixes() {
        assertThat(req("application/pdf").what).isEqualTo("a PDF")
        assertThat(req("image/*", "video/*").what).isEqualTo("a photo or video")
        assertThat(req("application/pdf").accepts("application/PDF")).isTrue()
    }
}
