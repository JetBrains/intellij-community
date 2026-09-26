package com.intellij.ide.starter.utils

import com.intellij.openapi.components.impl.stores.ComponentStorageUtil
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.xmlb.Constants
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Applies [edit] to the document of [file] and writes the result back. A file that does not exist yet starts from
 * an empty [rootTag] element, and [orCreateChildElement] then adds what [edit] needs.
 *
 * @param logContent whether the result goes to the log. Turn it off for a config a project can make large.
 */
fun editXmlConfig(file: Path, rootTag: String, logContent: Boolean = true, edit: (Document) -> Unit) {
  val xmlDoc = if (file.exists()) XmlBuilder.parse(file) else XmlBuilder.parse("<$rootTag/>".byteInputStream())
  xmlDoc.documentElement.removeIndentation()
  edit(xmlDoc)
  file.parent.createDirectories()
  XmlBuilder.writeDocument(xmlDoc, file)
  if (logContent) logOutput("Content of $file: ${file.readText()}")
}

/**
 * The [containerTag] element of the `option` [option] of the `component` [component]. A config file of the IDE keeps
 * a setting in that chain. Gives `null` when the document holds no such chain.
 */
fun Document.componentOption(component: String, option: String, containerTag: String): Element? =
  documentElement.childElement(ComponentStorageUtil.COMPONENT, component)
    ?.childElement(Constants.OPTION, option)
    ?.childElement(containerTag)

/** The same element as [componentOption], with every element it needs created on the way. */
fun Document.orCreateComponentOption(component: String, option: String, containerTag: String): Element =
  documentElement.orCreateChildElement(ComponentStorageUtil.COMPONENT, component)
    .orCreateChildElement(Constants.OPTION, option)
    .orCreateChildElement(containerTag)

/** The first child [tag] element whose `name` attribute is [name]. A [name] of `null` matches the tag alone. */
fun Element.childElement(tag: String, name: String? = null): Element? = childElements().firstOrNull {
  it.tagName == tag && (name == null || it.getAttribute(ComponentStorageUtil.NAME) == name)
}

/** The same child as [childElement], created and appended when it does not exist yet. */
fun Element.orCreateChildElement(tag: String, name: String? = null): Element =
  childElement(tag, name) ?: ownerDocument.createElement(tag).also { created ->
    if (name != null) created.setAttribute(ComponentStorageUtil.NAME, name)
    appendChild(created)
  }

/** Drops every child element that [drop] accepts. */
fun Element.removeChildElements(drop: (Element) -> Boolean) {
  childElements().filter(drop).forEach { removeChild(it) }
}

/**
 * Drops every text node that holds whitespace only. [XmlBuilder.writeDocument] indents the result itself, so the
 * indentation of the file it read has to go. Otherwise the two add up, and every further edit indents the file again.
 */
fun Node.removeIndentation() {
  for (child in childNodes.snapshot()) {
    if (child.nodeType == Node.TEXT_NODE && child.textContent.isBlank()) removeChild(child)
    else child.removeIndentation()
  }
}

private fun Element.childElements(): List<Element> = childNodes.snapshot().filterIsInstance<Element>()

/** [NodeList] is live, so a caller that removes a node needs a snapshot of it. */
private fun NodeList.snapshot(): List<Node> = (0 until length).map(::item)
