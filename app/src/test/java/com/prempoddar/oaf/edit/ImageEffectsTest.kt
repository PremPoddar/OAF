package com.prempoddar.oaf.edit

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageEffectsTest {
    /** A busy picture (a gradient with noise) so compression has something to do. */
    private fun picture(w: Int = 400, h: Int = 300): Bitmap {
        val px = IntArray(w * h) { i -> val x = i % w; val y = i / w; Color.rgb((x * 255 / w + (i * 31) % 40) % 256, (y * 255 / h + (i * 17) % 50) % 256, (i * 13) % 256) }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).also { it.setHasAlpha(false) }
    }

    @Test fun framesFitTheWholePictureIntoTheRatio() {
        val b = picture(400, 300)
        val square = ImageEffects.frame(b, 1f, Color.WHITE)
        assertThat(square.width).isEqualTo(400); assertThat(square.height).isEqualTo(400)
        val story = ImageEffects.frame(b, 9f / 16, null)
        assertThat(story.width).isEqualTo(400); assertThat(story.height).isEqualTo(711)
        // The space above the picture is the fill colour.
        assertThat(square.getPixel(200, 5)).isEqualTo(Color.WHITE)
    }

    @Test fun shapesLeaveTheCornersSeeThrough() {
        val cut = ImageEffects.cutToShape(picture(), ImageEffects.Mask.CIRCLE)
        assertThat(cut.width).isEqualTo(300); assertThat(cut.height).isEqualTo(300)
        assertThat(Color.alpha(cut.getPixel(2, 2))).isEqualTo(0)
        assertThat(Color.alpha(cut.getPixel(150, 150))).isEqualTo(255)
    }

    @Test fun encodingStaysUnderTheSizeLimit() {
        val (bytes, q) = ImageEffects.encodeWithin(picture(800, 600), ImageOps.Format.JPEG, 30 * 1024)
        assertThat(bytes.size).isAtMost(30 * 1024)
        assertThat(q).isAtLeast(10)
    }

    @Test fun stitchingMatchesHeightsSideBySide() {
        val out = ImageEffects.stitch(listOf(picture(400, 300), picture(200, 200)), across = true, gap = 10)
        assertThat(out.height).isEqualTo(200)
        assertThat(out.width).isEqualTo(266 + 10 + 200)
    }

    @Test fun noHueTurnLeavesColoursAlone() {
        val m = ImageEffects.hueMatrix(0f).array
        assertThat(m[0]).isWithin(1e-4f).of(1f); assertThat(m[6]).isWithin(1e-4f).of(1f); assertThat(m[12]).isWithin(1e-4f).of(1f)
        assertThat(m[1]).isWithin(1e-4f).of(0f)
    }

    @Test fun drawingAndWatermarksChangeThePicture() {
        val b = picture()
        val drawn = ImageEffects.draw(b, listOf(ImageEffects.Mark(ImageEffects.Pen.BOX, listOf(10f to 10f, 100f to 100f), Color.RED, 6f)))
        assertThat(drawn.getPixel(10, 50)).isEqualTo(Color.RED)
        val marked = ImageEffects.watermark(b, "OAF", 0.5f)
        assertThat(marked.sameAs(b)).isFalse()
    }
}
