package com.prempoddar.oaf.slides

import com.prempoddar.oaf.xml.Xml
import com.prempoddar.oaf.xml.descendants
import com.prempoddar.oaf.xml.kid
import com.prempoddar.oaf.xml.kids
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.hslf.usermodel.HSLFTextParagraph
import org.w3c.dom.Element
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.roundToInt

/** A slide deck. Sizes are in points; [image] gives the bytes of a picture by its path in the file. */
class Deck(val width: Float, val height: Float, val slides: List<Slide>, val image: (String) -> ByteArray?, private val onClose: () -> Unit = {}) : java.io.Closeable {
    override fun close() = onClose()
}

class Slide(val background: Int?, val shapes: List<SlideShape>, val notes: String?, val backgroundImage: String? = null) {
    val title: String? get() = shapes.filterIsInstance<SlideShape.Text>().firstOrNull { it.isTitle }?.plainText?.takeIf { it.isNotBlank() }
}

data class SRun(val text: String, val size: Float? = null, val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false, val color: Int? = null)
data class SPara(val runs: List<SRun>, val align: String = "l", val level: Int = 0, val bullet: String? = null)

sealed interface SlideShape {
    val x: Float; val y: Float; val w: Float; val h: Float

    data class Text(
        override val x: Float, override val y: Float, override val w: Float, override val h: Float,
        val paragraphs: List<SPara>, val fill: Int? = null, val line: Int? = null, val lineWidth: Float = 0f,
        /** "rect", "ellipse", "roundRect" and so on. */
        val geometry: String = "rect",
        /** "t", "ctr" or "b". */
        val anchor: String = "t",
        val isTitle: Boolean = false,
        val rotation: Float = 0f,
    ) : SlideShape {
        val plainText get() = paragraphs.joinToString("\n") { p -> p.runs.joinToString("") { it.text } }
    }

    data class Picture(
        override val x: Float, override val y: Float, override val w: Float, override val h: Float, val path: String,
        /** The part of the picture shown, as fractions cut from each side (left, top, right, bottom). */
        val crop: FloatArray? = null,
    ) : SlideShape
    data class Table(override val x: Float, override val y: Float, override val w: Float, override val h: Float, val columns: List<Float>, val rows: List<List<List<SPara>>>) : SlideShape
}

private const val EMU = 12700f

object Slides {
    // ---------------------------------------------------------------- PowerPoint (.pptx)

    private class Part(val path: String, val root: Element, val rels: Map<String, String>)

    private class Theme(val colors: Map<String, Int>)

    private class Context(
        val theme: Theme,
        /** Placeholder positions and default text sizes from the layout and master, by "type:idx". */
        val placeholders: Map<String, Pair<FloatArray?, Float?>>,
        val titleSize: Float, val bodySizes: List<Float>, val otherSize: Float,
        /** The master's text styles: "title", "body" and "other", nine levels each. */
        val masterStyles: Map<String, List<LStyle>> = emptyMap(),
        /** Placeholder text styles from the master and layout, by "type:idx". */
        val phStyles: Map<String, List<LStyle>> = emptyMap(),
    )

    /** Text formatting for one outline level; null means "not set here". A bullet of "" means none. */
    private data class LStyle(
        val size: Float? = null, val bold: Boolean? = null, val italic: Boolean? = null, val color: Int? = null,
        val align: String? = null, val bullet: String? = null,
    ) {
        infix fun over(base: LStyle) = LStyle(size ?: base.size, bold ?: base.bold, italic ?: base.italic, color ?: base.color, align ?: base.align, bullet ?: base.bullet)
    }

    private fun stack(top: List<LStyle>?, base: List<LStyle>?): List<LStyle> =
        (0 until 9).map { i -> (top?.getOrNull(i) ?: LStyle()) over (base?.getOrNull(i) ?: LStyle()) }

    private fun flag(e: Element?, name: String): Boolean? = e?.getAttribute(name)?.takeIf { it.isNotEmpty() }?.let { it == "1" || it == "true" }

    /** Symbol-font bullets live in the private-use area; they're shown as a plain dot. */
    private fun bulletChar(c: String) = if (c.isEmpty() || c[0].code in 0xF000..0xF0FF) "\u2022" else c

    private fun lvl(p: Element, theme: Theme): LStyle {
        val d = p.kid("defRPr")
        return LStyle(
            d?.getAttribute("sz")?.toFloatOrNull()?.div(100), flag(d, "b"), flag(d, "i"),
            d?.kid("solidFill")?.let { colorOf(it, theme) },
            p.getAttribute("algn").ifEmpty { null },
            when {
                p.kid("buNone") != null -> ""
                p.kid("buChar") != null -> bulletChar(p.kid("buChar")!!.getAttribute("char"))
                p.kid("buAutoNum") != null -> "#"
                else -> null
            },
        )
    }

    /** Nine levels from a list-style element (lstStyle, titleStyle, bodyStyle…). */
    private fun levels(list: Element?, theme: Theme): List<LStyle>? = list?.let { l -> (1..9).map { n -> l.kid("lvl${n}pPr")?.let { lvl(it, theme) } ?: LStyle() } }

    fun pptx(file: File): Deck {
        val zip = ZipFile(file)
        fun bytes(path: String) = zip.getEntry(path)?.let { e -> zip.getInputStream(e).use { it.readBytes() } }
        fun part(path: String): Part? {
            val data = bytes(path) ?: return null
            val dir = path.substringBeforeLast('/', "")
            val relsPath = (if (dir.isEmpty()) "" else "$dir/") + "_rels/" + path.substringAfterLast('/') + ".rels"
            val rels = bytes(relsPath)?.let { Xml.parse(it).documentElement.kids("Relationship") }.orEmpty()
                .filter { it.getAttribute("TargetMode") != "External" }
                .associate { it.getAttribute("Id") to resolve(dir, it.getAttribute("Target")) }
            return Part(path, Xml.parse(data).documentElement, rels)
        }
        val pres = part("ppt/presentation.xml") ?: error("Not a PowerPoint file")
        val size = pres.root.kid("sldSz")
        val width = (size?.getAttribute("cx")?.toFloatOrNull() ?: 9144000f) / EMU
        val height = (size?.getAttribute("cy")?.toFloatOrNull() ?: 6858000f) / EMU
        val slidePaths = pres.root.kid("sldIdLst")?.kids("sldId").orEmpty().mapNotNull { pres.rels[it.getAttributeNS(R, "id")] }

        val masters = HashMap<String, Pair<Part, Theme>>()
        val slides = slidePaths.mapNotNull { path ->
            val slide = part(path) ?: return@mapNotNull null
            val layout = slide.rels.values.firstOrNull { it.contains("slideLayout") }?.let { part(it) }
            val masterPath = layout?.rels?.values?.firstOrNull { it.contains("slideMaster") }
            val (master, theme) = masterPath?.let { mp ->
                masters.getOrPut(mp) {
                    val m = part(mp)!!
                    val t = m.rels.values.firstOrNull { it.contains("theme") }?.let { part(it) }
                    m to Theme(themeColors(t?.root, m.root.kid("clrMap")))
                }
            } ?: (null to Theme(emptyMap()))
            val ph = HashMap<String, Pair<FloatArray?, Float?>>()
            // The layout refines the master; where it leaves position or size out, the master's still apply.
            listOfNotNull(master, layout).forEach { p ->
                p.root.descendants("sp").forEach { sp ->
                    placeholderKey(sp)?.let { k ->
                        val before = ph[k] ?: ph.entries.firstOrNull { it.key.substringBefore(':') == k.substringBefore(':') }?.value
                        ph[k] = (xfrm(sp.kid("spPr")) ?: before?.first) to (firstSize(sp) ?: before?.second)
                    }
                }
            }
            val tx = master?.root?.kid("txStyles")
            fun lvl(style: String, n: Int) = tx?.kid(style)?.kid("lvl${n}pPr")?.kid("defRPr")?.getAttribute("sz")?.toFloatOrNull()?.div(100)
            val masterStyles = mapOf(
                "title" to (levels(tx?.kid("titleStyle"), theme) ?: emptyList()),
                "body" to (levels(tx?.kid("bodyStyle"), theme) ?: emptyList()),
                "other" to (levels(tx?.kid("otherStyle"), theme) ?: emptyList()),
            )
            val phStyles = HashMap<String, List<LStyle>>()
            listOfNotNull(master, layout).forEach { p ->
                p.root.descendants("sp").forEach { sp ->
                    val k = placeholderKey(sp) ?: return@forEach
                    val before = phStyles[k] ?: phStyles.entries.firstOrNull { it.key.substringBefore(':') == k.substringBefore(':') }?.value
                    phStyles[k] = stack(levels(sp.kid("txBody")?.kid("lstStyle"), theme), before)
                }
            }
            val ctx = Context(theme, ph, lvl("titleStyle", 1) ?: 44f, (1..5).map { lvl("bodyStyle", it) ?: (28f - (it - 1) * 4f).coerceAtLeast(14f) }, lvl("otherStyle", 1) ?: 18f, masterStyles, phStyles)

            val shapes = ArrayList<SlideShape>()
            // Decorations from the master and layout sit underneath, unless the slide hides them.
            if (slide.root.getAttribute("showMasterSp") != "0") {
                listOfNotNull(master, layout).forEach { p -> p.root.kid("cSld")?.kid("spTree")?.let { tree(it, p, ctx, shapes, skipPlaceholders = true) } }
            }
            slide.root.kid("cSld")?.kid("spTree")?.let { tree(it, slide, ctx, shapes, skipPlaceholders = false) }
            val bgOwner = listOfNotNull(slide, layout, master).firstOrNull { it.root.kid("cSld")?.kid("bg") != null }
            val bgEl = bgOwner?.root?.kid("cSld")?.kid("bg")
            val bg = bgEl?.let { b -> fillOf(b.kid("bgPr"), theme) ?: b.kid("bgRef")?.let { colorOf(it, theme) } }
            val bgImage = bgEl?.kid("bgPr")?.kid("blipFill")?.descendants("blip")?.firstOrNull()?.getAttributeNS(R, "embed")?.let { bgOwner.rels[it] }
            val notes = slide.rels.values.firstOrNull { it.contains("notesSlide") }?.let { part(it) }?.root?.descendants("sp")
                ?.firstOrNull { placeholderKey(it)?.startsWith("body") == true }?.let { sp -> paragraphs(sp, ctx, 12f).joinToString("\n") { p -> p.runs.joinToString("") { it.text } } }
            Slide(bg, shapes, notes?.takeIf { it.isNotBlank() }, bgImage)
        }
        return Deck(width, height, slides, { path -> bytes(path) }, zip::close)
    }

    private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private fun resolve(dir: String, target: String): String {
        if (target.startsWith("/")) return target.substring(1)
        val stack = ArrayList(if (dir.isEmpty()) emptyList() else dir.split('/'))
        target.split('/').forEach { seg -> when (seg) { ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.size - 1); ".", "" -> Unit; else -> stack += seg } }
        return stack.joinToString("/")
    }

    /** Theme colours, with the master's map of text and background names (dark designs swap them). */
    private fun themeColors(theme: Element?, clrMap: Element?): Map<String, Int> {
        val base = themeColors(theme).toMutableMap()
        clrMap?.let { m ->
            listOf("tx1", "tx2", "bg1", "bg2").forEach { k -> m.getAttribute(k).takeIf { it.isNotEmpty() }?.let { target -> base[target]?.let { base[k] = it } } }
        }
        return base
    }

    private fun themeColors(theme: Element?): Map<String, Int> {
        val scheme = theme?.descendants("clrScheme")?.firstOrNull() ?: return emptyMap()
        val out = HashMap<String, Int>()
        scheme.kids().forEach { c ->
            val v = c.kid("srgbClr")?.getAttribute("val") ?: c.kid("sysClr")?.getAttribute("lastClr")
            v?.toIntOrNull(16)?.let { out[c.localName] = it }
        }
        // The usual mapping of text and background names onto the scheme.
        out["tx1"] = out["dk1"] ?: 0x000000; out["bg1"] = out["lt1"] ?: 0xFFFFFF
        out["tx2"] = out["dk2"] ?: 0x44546A; out["bg2"] = out["lt2"] ?: 0xE7E6E6
        return out
    }

    /** A colour element (srgbClr, schemeClr, sysClr, prstClr) with brightness changes applied. */
    private fun colorOf(parent: Element?, theme: Theme): Int? {
        val c = parent?.kids()?.firstOrNull { it.localName in setOf("srgbClr", "schemeClr", "sysClr", "prstClr") } ?: return null
        var rgb = when (c.localName) {
            "srgbClr" -> c.getAttribute("val").toIntOrNull(16)
            "schemeClr" -> theme.colors[c.getAttribute("val")] ?: if (c.getAttribute("val").startsWith("bg")) 0xFFFFFF else 0x000000
            "sysClr" -> c.getAttribute("lastClr").toIntOrNull(16) ?: if (c.getAttribute("val") == "window") 0xFFFFFF else 0x000000
            else -> when (c.getAttribute("val")) { "white" -> 0xFFFFFF; "black" -> 0; "red" -> 0xFF0000; "blue" -> 0x0000FF; "green" -> 0x008000; else -> 0x808080 }
        } ?: return null
        val mod = c.kid("lumMod")?.getAttribute("val")?.toFloatOrNull()?.div(100000f)
        val off = c.kid("lumOff")?.getAttribute("val")?.toFloatOrNull()?.div(100000f)
        val tint = c.kid("tint")?.getAttribute("val")?.toFloatOrNull()?.div(100000f)
        val shade = c.kid("shade")?.getAttribute("val")?.toFloatOrNull()?.div(100000f)
        if (mod != null || off != null) rgb = lum(rgb, mod ?: 1f, off ?: 0f)
        if (tint != null) rgb = mix(rgb, 0xFFFFFF, 1 - tint)
        if (shade != null) rgb = mix(rgb, 0x000000, 1 - shade)
        return rgb
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) * (1 - t) + ((b shr s) and 0xFF) * t).roundToInt().coerceIn(0, 255)
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Scales lightness in HSL, as PowerPoint's luminance modifiers do. */
    private fun lum(rgb: Int, mod: Float, off: Float): Int {
        val r = (rgb shr 16 and 0xFF) / 255f; val g = (rgb shr 8 and 0xFF) / 255f; val b = (rgb and 0xFF) / 255f
        val max = maxOf(r, g, b); val min = minOf(r, g, b)
        var l = (max + min) / 2
        val d = max - min
        val s = if (d == 0f) 0f else d / (1 - kotlin.math.abs(2 * l - 1))
        val h = when { d == 0f -> 0f; max == r -> ((g - b) / d).mod(6f); max == g -> (b - r) / d + 2; else -> (r - g) / d + 4 } * 60
        l = (l * mod + off).coerceIn(0f, 1f)
        val c = (1 - kotlin.math.abs(2 * l - 1)) * s
        val x = c * (1 - kotlin.math.abs((h / 60).mod(2f) - 1))
        val m = l - c / 2
        val (rr, gg, bb) = when ((h / 60).toInt()) { 0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x); 3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x) }
        return (((rr + m) * 255).roundToInt().coerceIn(0, 255) shl 16) or (((gg + m) * 255).roundToInt().coerceIn(0, 255) shl 8) or ((bb + m) * 255).roundToInt().coerceIn(0, 255)
    }

    private fun fillOf(spPr: Element?, theme: Theme): Int? = spPr?.kid("solidFill")?.let { colorOf(it, theme) }
        ?: spPr?.kid("gradFill")?.descendants("gs")?.firstOrNull()?.let { colorOf(it, theme) }

    private fun placeholderKey(sp: Element): String? {
        val ph = sp.kid("nvSpPr")?.kid("nvPr")?.kid("ph") ?: sp.kid("nvPicPr")?.kid("nvPr")?.kid("ph") ?: return null
        val type = ph.getAttribute("type").ifEmpty { "body" }.let { if (it == "ctrTitle") "title" else if (it == "subTitle") "body" else it }
        return "$type:${ph.getAttribute("idx").ifEmpty { "0" }}"
    }

    private fun xfrm(spPr: Element?): FloatArray? {
        val x = spPr?.kid("xfrm") ?: return null
        val off = x.kid("off") ?: return null; val ext = x.kid("ext") ?: return null
        fun n(e: Element, a: String) = e.getAttribute(a).toFloatOrNull() ?: 0f
        return floatArrayOf(n(off, "x") / EMU, n(off, "y") / EMU, n(ext, "cx") / EMU, n(ext, "cy") / EMU)
    }

    private fun firstSize(sp: Element): Float? = sp.descendants("lvl1pPr").firstOrNull()?.kid("defRPr")?.getAttribute("sz")?.toFloatOrNull()?.div(100)

    /** Walks a shape tree. [map] places a group's children (child space → slide points). */
    private fun tree(t: Element, part: Part, ctx: Context, out: MutableList<SlideShape>, skipPlaceholders: Boolean, map: (FloatArray) -> FloatArray = { it }) {
        for (el in t.kids()) when (el.localName) {
            "sp" -> {
                val key = placeholderKey(el)
                if (key != null && skipPlaceholders) continue
                val own = xfrm(el.kid("spPr"))
                val inherited = key?.let { k -> ctx.placeholders[k] ?: ctx.placeholders.entries.firstOrNull { it.key.substringBefore(':') == k.substringBefore(':') }?.value }
                val box = map(own ?: inherited?.first ?: continue)
                val isTitle = key?.startsWith("title") == true
                val defaultSize = inherited?.second ?: when { isTitle -> ctx.titleSize; key != null -> ctx.bodySizes[0]; else -> ctx.otherSize }
                val spPr = el.kid("spPr")
                val style = el.kid("style")
                val fill = if (spPr?.kid("noFill") != null) null else fillOf(spPr, ctx.theme) ?: style?.kid("fillRef")?.takeIf { (it.getAttribute("idx").toIntOrNull() ?: 0) > 0 }?.let { colorOf(it, ctx.theme) }
                val ln = spPr?.kid("ln")
                val line = if (ln?.kid("noFill") != null) null else ln?.kid("solidFill")?.let { colorOf(it, ctx.theme) } ?: style?.kid("lnRef")?.takeIf { (it.getAttribute("idx").toIntOrNull() ?: 0) > 0 }?.let { colorOf(it, ctx.theme) }
                // A shape filled with a picture: the picture first, any text on top.
                spPr?.kid("blipFill")?.descendants("blip")?.firstOrNull()?.getAttributeNS(R, "embed")?.let { part.rels[it] }?.let {
                    out += SlideShape.Picture(box[0], box[1], box[2], box[3], it)
                }
                val base = when { isTitle -> ctx.masterStyles["title"]; key != null -> ctx.masterStyles["body"]; else -> ctx.masterStyles["other"] }
                val fontRef = style?.kid("fontRef")?.let { colorOf(it, ctx.theme) }?.let { c -> List(9) { LStyle(color = c) } }
                val inheritedStyles = key?.let { k -> ctx.phStyles[k] ?: ctx.phStyles.entries.firstOrNull { it.key.substringBefore(':') == k.substringBefore(':') }?.value }
                val styles = stack(levels(el.kid("txBody")?.kid("lstStyle"), ctx.theme), stack(inheritedStyles, stack(fontRef, base)))
                val paras = paragraphs(el, ctx, defaultSize, if (key == null || isTitle || inherited?.second != null) null else ctx.bodySizes, null, styles)
                out += SlideShape.Text(
                    box[0], box[1], box[2], box[3], paras, fill, line, (ln?.getAttribute("w")?.toFloatOrNull() ?: 9525f) / EMU,
                    spPr?.kid("prstGeom")?.getAttribute("prst")?.ifEmpty { null } ?: "rect",
                    el.kid("txBody")?.kid("bodyPr")?.getAttribute("anchor")?.ifEmpty { null } ?: if (isTitle) "ctr" else "t",
                    isTitle, (spPr?.kid("xfrm")?.getAttribute("rot")?.toFloatOrNull() ?: 0f) / 60000f,
                )
            }
            "pic" -> {
                // Pictures in placeholders take their place from the layout.
                val key = placeholderKey(el)
                if (key != null && skipPlaceholders) continue
                val inheritedBox = key?.let { k -> ctx.placeholders[k]?.first ?: ctx.placeholders.entries.firstOrNull { it.key.substringBefore(':') == k.substringBefore(':') }?.value?.first }
                val box = map(xfrm(el.kid("spPr")) ?: inheritedBox ?: continue)
                val embed = el.descendants("blip").firstOrNull()?.getAttributeNS(R, "embed") ?: continue
                val src = el.descendants("srcRect").firstOrNull()
                val crop = src?.let { r -> arrayOf("l", "t", "r", "b").let { k -> FloatArray(4) { i -> (r.getAttribute(k[i]).toFloatOrNull() ?: 0f) / 100000f } } }?.takeIf { c -> c.any { it != 0f } }
                part.rels[embed]?.let { out += SlideShape.Picture(box[0], box[1], box[2], box[3], it, crop) }
            }
            "grpSp" -> {
                val x = el.kid("grpSpPr")?.kid("xfrm")
                val off = x?.kid("off"); val ext = x?.kid("ext"); val chOff = x?.kid("chOff"); val chExt = x?.kid("chExt")
                if (off == null || ext == null || chOff == null || chExt == null) { tree(el, part, ctx, out, skipPlaceholders, map); continue }
                val ox = off.getAttribute("x").toFloat() / EMU; val oy = off.getAttribute("y").toFloat() / EMU
                val cx = chOff.getAttribute("x").toFloat() / EMU; val cy = chOff.getAttribute("y").toFloat() / EMU
                val sx = ext.getAttribute("cx").toFloat() / chExt.getAttribute("cx").toFloat().coerceAtLeast(1f)
                val sy = ext.getAttribute("cy").toFloat() / chExt.getAttribute("cy").toFloat().coerceAtLeast(1f)
                tree(el, part, ctx, out, skipPlaceholders) { b -> map(floatArrayOf(ox + (b[0] - cx) * sx, oy + (b[1] - cy) * sy, b[2] * sx, b[3] * sy)) }
            }
            "graphicFrame" -> {
                val xf = el.kid("xfrm") ?: continue
                val off = xf.kid("off") ?: continue; val ext = xf.kid("ext") ?: continue
                val box = map(floatArrayOf(off.getAttribute("x").toFloat() / EMU, off.getAttribute("y").toFloat() / EMU, ext.getAttribute("cx").toFloat() / EMU, ext.getAttribute("cy").toFloat() / EMU))
                val tbl = el.descendants("tbl").firstOrNull()
                if (tbl != null) {
                    val cols = tbl.kid("tblGrid")?.kids("gridCol").orEmpty().map { it.getAttribute("w").toFloat() / EMU * (box[2] / ((xf.kid("ext")!!.getAttribute("cx").toFloat() / EMU).coerceAtLeast(1f))) }
                    val rows = tbl.kids("tr").map { tr -> tr.kids("tc").map { tc -> paragraphs(tc, ctx, ctx.otherSize) } }
                    out += SlideShape.Table(box[0], box[1], box[2], box[3], cols, rows)
                } else {
                    // Charts and other embedded objects: a labelled frame.
                    out += SlideShape.Text(box[0], box[1], box[2], box[3], listOf(SPara(listOf(SRun("Chart", 14f, color = 0x888888)), "ctr")), line = 0xBBBBBB, lineWidth = 0.75f, anchor = "ctr")
                }
            }
        }
    }

    private fun paragraphs(holder: Element, ctx: Context, defaultSize: Float, levelSizes: List<Float>? = null, fontColor: Int? = null, styles: List<LStyle>? = null): List<SPara> {
        val body = holder.kid("txBody") ?: holder.kid("txBody") ?: holder.descendants("txBody").firstOrNull() ?: return emptyList()
        var number = 0
        return body.kids("p").map { p ->
            val pPr = p.kid("pPr")
            val level = pPr?.getAttribute("lvl")?.toIntOrNull() ?: 0
            val st = (pPr?.let { lvl(it, ctx.theme) } ?: LStyle()) over (styles?.getOrNull(level) ?: LStyle())
            val size = st.size ?: levelSizes?.getOrNull(level) ?: defaultSize
            val baseColor = st.color ?: fontColor
            val runs = p.kids().mapNotNull { r ->
                when (r.localName) {
                    "r", "fld" -> {
                        val rPr = r.kid("rPr")
                        SRun(
                            r.kid("t")?.textContent.orEmpty(),
                            rPr?.getAttribute("sz")?.toFloatOrNull()?.div(100) ?: size,
                            flag(rPr, "b") ?: st.bold ?: false, flag(rPr, "i") ?: st.italic ?: false,
                            rPr?.getAttribute("u")?.let { it.isNotEmpty() && it != "none" } == true,
                            rPr?.kid("solidFill")?.let { colorOf(it, ctx.theme) } ?: baseColor,
                        )
                    }
                    "br" -> SRun("\n", size)
                    else -> null
                }
            }
            val hasText = runs.any { it.text.isNotBlank() }
            val bullet = when (val bu = st.bullet) {
                null, "" -> null
                "#" -> if (hasText) "${++number}." else null
                else -> if (hasText) bu else null
            }
            SPara(runs.ifEmpty { listOf(SRun("", size)) }, st.align ?: "l", level, bullet)
        }
    }

    // ---------------------------------------------------------------- OpenDocument (.odp)

    private const val DRAW = "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
    private const val SVG = "urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0"
    private const val PRESENTATION = "urn:oasis:names:tc:opendocument:xmlns:presentation:1.0"
    private const val FO = "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
    private const val STYLE = "urn:oasis:names:tc:opendocument:xmlns:style:1.0"
    private const val TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
    private const val XLINK = "http://www.w3.org/1999/xlink"

    /** "2.5cm", "1in", "72pt", "10mm" → points. */
    fun length(v: String?): Float? {
        val m = Regex("^(-?[0-9.]+)\\s*(cm|mm|in|pt|pc|px)?$").find(v?.trim() ?: return null) ?: return null
        val n = m.groupValues[1].toFloatOrNull() ?: return null
        return n * when (m.groupValues[2]) { "cm" -> 28.3465f; "mm" -> 2.83465f; "in" -> 72f; "pc" -> 12f; "px" -> 0.75f; else -> 1f }
    }

    fun odp(file: File): Deck {
        val zip = ZipFile(file)
        fun bytes(path: String) = zip.getEntry(path)?.let { e -> zip.getInputStream(e).use { it.readBytes() } }
        val content = Xml.parse(bytes("content.xml") ?: error("Not an OpenDocument presentation")).documentElement
        val styles = bytes("styles.xml")?.let { Xml.parse(it).documentElement }
        val layout = styles?.getElementsByTagNameNS(STYLE, "page-layout-properties")?.item(0) as? Element
        val width = length(layout?.getAttributeNS(FO, "page-width")) ?: 792f
        val height = length(layout?.getAttributeNS(FO, "page-height")) ?: 612f
        val textSizes = HashMap<String, Pair<Float?, Boolean>>()
        val fills = HashMap<String, Int?>()
        listOfNotNull(styles, content).forEach { root ->
            val nl = root.getElementsByTagNameNS(STYLE, "style")
            for (i in 0 until nl.length) {
                val s = nl.item(i) as Element
                val name = s.getAttributeNS(STYLE, "name")
                (s.kids("text-properties").firstOrNull())?.let { tp -> textSizes[name] = length(tp.getAttributeNS(FO, "font-size")) to (tp.getAttributeNS(FO, "font-weight") == "bold") }
                s.kids("graphic-properties").firstOrNull()?.let { gp ->
                    if (gp.getAttributeNS(DRAW, "fill") == "solid") fills[name] = gp.getAttributeNS(DRAW, "fill-color").removePrefix("#").toIntOrNull(16)
                }
            }
        }
        val pages = content.getElementsByTagNameNS(DRAW, "page")
        val slides = (0 until pages.length).map { i ->
            val page = pages.item(i) as Element
            val shapes = ArrayList<SlideShape>()
            var notes: String? = null
            fun walk(parent: Element) {
                for (el in parent.kids()) {
                    if (el.localName == "notes") { notes = el.descendants("p").joinToString("\n") { it.textContent }.takeIf { it.isNotBlank() }; continue }
                    if (el.namespaceURI != DRAW) continue
                    if (el.localName == "g") { walk(el); continue }
                    val x = length(el.getAttributeNS(SVG, "x")) ?: 0f; val y = length(el.getAttributeNS(SVG, "y")) ?: 0f
                    val w = length(el.getAttributeNS(SVG, "width")) ?: continue; val h = length(el.getAttributeNS(SVG, "height")) ?: continue
                    val image = el.kids("image").firstOrNull()?.getAttributeNS(XLINK, "href")
                    if (image != null && !image.startsWith("http")) { shapes += SlideShape.Picture(x, y, w, h, image.removePrefix("./")); continue }
                    val cls = el.getAttributeNS(PRESENTATION, "class")
                    val isTitle = cls == "title"
                    val base = if (isTitle) 40f else if (cls == "outline" || cls == "subtitle") 24f else 18f
                    var number = 0
                    val paras = ArrayList<SPara>()
                    fun para(p: Element, level: Int, bullet: String?) {
                        val runs = ArrayList<SRun>()
                        fun inline(e: Element, size: Float, bold: Boolean) {
                            var n = e.firstChild
                            while (n != null) {
                                if (n.nodeType == org.w3c.dom.Node.TEXT_NODE) runs += SRun(n.nodeValue, size, bold)
                                else if (n is Element) when (n.localName) {
                                    "span" -> (textSizes[n.getAttributeNS(TEXT, "style-name")] ?: (null to false)).let { (s, b) -> inline(n, s ?: size, b || bold) }
                                    "s" -> runs += SRun(" ", size)
                                    "tab" -> runs += SRun("\t", size)
                                    "line-break" -> runs += SRun("\n", size)
                                    else -> inline(n, size, bold)
                                }
                                n = n.nextSibling
                            }
                        }
                        val (s, b) = textSizes[p.getAttributeNS(TEXT, "style-name")] ?: (null to false)
                        inline(p, s ?: base, b)
                        paras += SPara(runs.ifEmpty { listOf(SRun("", base)) }, if (isTitle) "ctr" else "l", level, bullet)
                    }
                    fun list(l: Element, level: Int) {
                        l.kids("list-item").forEach { item ->
                            item.kids().forEach { k -> when (k.localName) { "p", "h" -> para(k, level, if (cls == "outline" || l.hasAttribute("text:style-name")) "•" else "${++number}."); "list" -> list(k, level + 1) } }
                        }
                    }
                    el.descendants("text-box").firstOrNull()?.kids()?.forEach { k -> when (k.localName) { "p", "h" -> para(k, 0, null); "list" -> list(k, 0) } }
                    if (el.localName != "frame") el.kids().forEach { k -> when (k.localName) { "p", "h" -> para(k, 0, null); "list" -> list(k, 0) } }
                    val geometry = when (el.localName) { "ellipse", "circle" -> "ellipse"; else -> el.descendants("enhanced-geometry").firstOrNull()?.getAttributeNS(DRAW, "type")?.takeIf { it == "ellipse" } ?: "rect" }
                    val fill = fills[el.getAttributeNS(DRAW, "style-name")]
                    if (paras.isNotEmpty() || fill != null) shapes += SlideShape.Text(x, y, w, h, paras, fill, geometry = geometry, anchor = if (isTitle) "ctr" else "t", isTitle = isTitle)
                }
            }
            walk(page)
            Slide(fills[page.getAttributeNS(DRAW, "style-name")], shapes, notes)
        }
        return Deck(width, height, slides, { path -> bytes(path) }, zip::close)
    }

    // ---------------------------------------------------------------- PowerPoint 97–2003 (.ppt)

    /** Text only: each slide's title and body laid out simply. */
    fun ppt(file: File): Deck = file.inputStream().use { input ->
        HSLFSlideShow(input).use { show ->
            val w = 720f; val h = 540f
            val slides = show.slides.map { slide ->
                val blocks = slide.textParagraphs
                val title = blocks.firstOrNull { b -> b.firstOrNull()?.runType.let { it == 0 || it == 6 } }
                val body = blocks.filter { it !== title }
                fun paras(list: List<HSLFTextParagraph>, size: Float, bullet: Boolean) = list.map { p ->
                    SPara(listOf(SRun(HSLFTextParagraph.getRawText(listOf(p)).trim(), size)), if (bullet) "l" else "ctr", p.indentLevel, if (bullet && p.isBullet) "•" else null)
                }
                val shapes = ArrayList<SlideShape>()
                title?.let { shapes += SlideShape.Text(36f, 24f, w - 72f, 90f, paras(it, 36f, false), anchor = "ctr", isTitle = true) }
                if (body.isNotEmpty()) shapes += SlideShape.Text(48f, 130f, w - 96f, h - 160f, body.flatMap { paras(it, 22f, true) })
                val notes = slide.notes?.textParagraphs?.joinToString("\n") { HSLFTextParagraph.getRawText(it) }?.trim()?.takeIf { it.isNotEmpty() }
                Slide(null, shapes, notes)
            }
            Deck(w, h, slides, { null })
        }
    }
}
