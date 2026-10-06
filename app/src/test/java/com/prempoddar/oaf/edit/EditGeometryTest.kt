package com.prempoddar.oaf.edit

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EditGeometryTest {
    @Test fun fitsAnImageInsideABoxCentred() {
        val r = EditGeometry.fit(2000, 1000, 400f, 400f)
        assertThat(r).isEqualTo(Rect(0f, 100f, 400f, 300f))
    }

    @Test fun mapsScreenPointsBackToImagePixels() {
        val shown = Rect(0f, 100f, 400f, 300f)
        assertThat(EditGeometry.toImage(shown, 2000, 1000, 200f, 200f)).isEqualTo(Pair(1000f, 500f))
        // Outside the picture clamps to its edge.
        assertThat(EditGeometry.toImage(shown, 2000, 1000, -50f, 999f)).isEqualTo(Pair(0f, 1000f))
    }

    @Test fun largestCentredCropForAnAspect() {
        assertThat(EditGeometry.aspectCrop(4000, 3000, 1f)).isEqualTo(Rect(500f, 0f, 3500f, 3000f))
        assertThat(EditGeometry.aspectCrop(1920, 1080, 9f / 16f)).isEqualTo(Rect(656.25f, 0f, 1263.75f, 1080f))
    }

    @Test fun videoCropInNormalisedCoordinates() {
        // 16:9 video cut to a square keeps the middle 9/16 of the width.
        val c = EditGeometry.ndcCrop(1920, 1080, 1f)
        assertThat(c.left).isWithin(1e-4f).of(-0.5625f)
        assertThat(c.right).isWithin(1e-4f).of(0.5625f)
        assertThat(c.bottom).isEqualTo(-1f)
        assertThat(c.top).isEqualTo(1f)
        // Portrait video cut to 16:9 keeps a horizontal band.
        val p = EditGeometry.ndcCrop(1080, 1920, 16f / 9f)
        assertThat(p.left).isEqualTo(-1f)
        assertThat(p.top).isWithin(1e-4f).of(0.31640625f)
    }

    @Test fun keepsAspectWhenResizing() {
        assertThat(EditGeometry.scaledTo(4000, 3000, width = 1000)).isEqualTo(Pair(1000, 750))
        assertThat(EditGeometry.scaledTo(4000, 3000, height = 600)).isEqualTo(Pair(800, 600))
    }

    @Test fun editedNameKeepsTheExtensionChoice() {
        assertThat(EditGeometry.editedName("Holiday.jpeg", "png")).isEqualTo("Holiday (edited).png")
        assertThat(EditGeometry.editedName("clip", "mp4")).isEqualTo("clip (edited).mp4")
    }
}
