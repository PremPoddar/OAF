package com.prempoddar.oaf.pdf

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PickTest {
    private fun el(kind: PdfElement.Kind, l: Float, t: Float, r: Float, b: Float, text: String = "") = PdfElement(kind, emptyList(), Box(l, t, r, b), text)

    private val background = el(PdfElement.Kind.IMAGE, 0f, 0f, 1f, 1f)
    private val heading = el(PdfElement.Kind.TEXT, 0.1f, 0.1f, 0.9f, 0.15f, "Title")
    private val logo = el(PdfElement.Kind.IMAGE, 0.7f, 0.11f, 0.85f, 0.14f)
    private val body = el(PdfElement.Kind.TEXT, 0.1f, 0.3f, 0.9f, 0.32f, "Body")
    private val all = listOf(background, heading, logo, body)

    @Test fun picksTheSmallestBoxUnderTheFinger() {
        assertThat(PdfContent.pick(all, 0.75f, 0.12f).first()).isSameInstanceAs(logo)
        assertThat(PdfContent.pick(all, 0.3f, 0.12f).first()).isSameInstanceAs(heading)
    }

    @Test fun theBackgroundComesLast() {
        assertThat(PdfContent.pick(all, 0.3f, 0.12f).last()).isSameInstanceAs(background)
        assertThat(PdfContent.pick(all, 0.5f, 0.6f).single()).isSameInstanceAs(background)
    }

    @Test fun aNearMissStillFindsTheClosestLine() {
        val noBackground = listOf(heading, body)
        assertThat(PdfContent.pick(noBackground, 0.5f, 0.335f).first()).isSameInstanceAs(body)
        assertThat(PdfContent.pick(noBackground, 0.5f, 0.6f)).isEmpty()
    }
}
