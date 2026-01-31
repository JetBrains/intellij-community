package com.intellij.ide.starter.utils

import com.intellij.util.createDocumentBuilder
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Path
import java.util.Optional
import java.util.stream.IntStream
import javax.xml.parsers.DocumentBuilder
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.io.path.inputStream
import kotlin.io.path.notExists
import kotlin.io.path.outputStream

object XmlBuilder {

  fun parse(inputStream: InputStream): Document {
    val documentBuilder = createDocumentBuilder()
    val xmlDoc = documentBuilder.parse(inputStream)
    xmlDoc.documentElement.normalize()
    return xmlDoc
  }

  fun parse(path: Path): Document {
    val documentBuilder = createDocumentBuilder()
    if (path.notExists()) throw FileNotFoundException(path.toString())

    val xmlDoc = documentBuilder.parse(path)
    xmlDoc.documentElement.normalize()
    return xmlDoc
  }

  /**
   * Parses the XML document located at the given [path] with this [DocumentBuilder].
   *
   * We intentionally bypass `DocumentBuilder.parse(File)` because that overload
   * requires a `java.io.File` and therefore the default file-system.
   * Instead, we open a stream from the NIO [Path] and feed it through an
   * [InputSource].
   *
   * The Xerces implementation bundled with the JDK resolves relative references
   * (e.g., XInclude, external entities) using `InputSource.systemId`.
   * Supplying the URI of [path] ensures those references are resolved correctly.
   */
  private fun DocumentBuilder.parse(path: Path): Document =
    path.inputStream().use { stream ->
      val inputSource = InputSource(stream).apply {
        systemId = path.toUri().toASCIIString()
      }
      parse(inputSource)
    }

  fun writeDocument(xmlDoc: Document, outputPath: Path) {
    val source = DOMSource(xmlDoc)

    val transformerFactory = TransformerFactory.newDefaultInstance()
    val transformer = transformerFactory.newTransformer()
    transformer.setOutputProperty(OutputKeys.INDENT, "yes")
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")

    outputPath.outputStream().use {
      transformer.transform(source, StreamResult(it))
    }
  }

  fun findNode(nodes: NodeList, filter: (Element) -> Boolean): Optional<Element> {
    return IntStream
      .range(0, nodes.length)
      .mapToObj { i -> nodes.item(i) as Element }
      .filter(filter)
      .findAny()
  }
}