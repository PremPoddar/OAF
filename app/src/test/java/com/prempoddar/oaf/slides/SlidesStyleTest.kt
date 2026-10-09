package com.prempoddar.oaf.slides

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class SlidesStyleTest {
    private fun deck(name: String): Deck {
        val f = File.createTempFile("deck", ".pptx")
        javaClass.classLoader!!.getResourceAsStream("decks/$name")!!.use { i -> f.outputStream().use { i.copyTo(it) } }
        return Slides.pptx(f)
    }

    @Test fun widescreenSizeAndInheritedLooks() {
        val d = deck("styled.pptx")
        println("size ${d.width} x ${d.height}")
        d.slides.forEachIndexed { i, s -> println("slide $i bg=${s.background} img=${s.backgroundImage} shapes=${s.shapes.map { it.javaClass.simpleName + (if (it is SlideShape.Text) ":" + it.paragraphs.flatMap { p -> p.runs }.map { r -> r.text + "/" + r.color?.toString(16) + "/" + r.bold } else "") }}") }
        assertThat(d.width / d.height).isWithin(0.01f).of(16f / 9f)
        val first = d.slides[0]
        assertThat(first.backgroundImage).isNotNull()
        val title = first.shapes.filterIsInstance<SlideShape.Text>().first { it.isTitle }
        assertThat(title.paragraphs.first().runs.first().color).isEqualTo(0xFFFFFF)
        assertThat(title.paragraphs.first().runs.first().bold).isTrue()
        assertThat(d.slides[1].shapes.filterIsInstance<SlideShape.Picture>()).isNotEmpty()
    }
}
