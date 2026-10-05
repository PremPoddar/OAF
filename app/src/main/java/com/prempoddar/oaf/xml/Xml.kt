package com.prempoddar.oaf.xml

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/** Small DOM helpers shared by the Office formats. DTDs are refused, so files can't pull in outside entities. */
object Xml {
    fun parse(data: ByteArray): Document {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = true
        f.isExpandEntityReferences = false
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        return f.newDocumentBuilder().parse(InputSource(ByteArrayInputStream(data)))
    }

    fun parse(text: String) = parse(text.toByteArray())

    fun bytes(doc: Document): ByteArray {
        val t = TransformerFactory.newInstance().newTransformer()
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        t.setOutputProperty(OutputKeys.STANDALONE, "yes")
        doc.xmlStandalone = true
        return ByteArrayOutputStream().also { t.transform(DOMSource(doc), StreamResult(it)) }.toByteArray()
    }
}

fun Element.kids(local: String? = null): List<Element> {
    val out = ArrayList<Element>()
    var n = firstChild
    while (n != null) { if (n.nodeType == Node.ELEMENT_NODE && (local == null || n.localName == local)) out += n as Element; n = n.nextSibling }
    return out
}

fun Element.kid(local: String): Element? {
    var n = firstChild
    while (n != null) { if (n.nodeType == Node.ELEMENT_NODE && n.localName == local) return n as Element; n = n.nextSibling }
    return null
}

/** Every descendant with this local name, in document order. */
fun Element.descendants(local: String): List<Element> {
    val nl = getElementsByTagNameNS("*", local)
    return (0 until nl.length).map { nl.item(it) as Element }
}

fun Document.byTag(local: String): List<Element> = documentElement.descendants(local).let { if (documentElement.localName == local) listOf(documentElement) + it else it }
