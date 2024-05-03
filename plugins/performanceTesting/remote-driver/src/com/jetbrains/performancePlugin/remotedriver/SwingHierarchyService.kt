package com.jetbrains.performancePlugin.remotedriver

import com.intellij.driver.model.TextDataList
import com.intellij.openapi.components.Service
import com.jetbrains.performancePlugin.remotedriver.dataextractor.TextParser
import com.jetbrains.performancePlugin.remotedriver.xpath.XpathDataModelCreator
import org.jsoup.helper.W3CDom
import org.w3c.dom.Node
import org.w3c.dom.Text
import java.awt.Component

@Suppress("unused")
@Service(Service.Level.APP)
class SwingHierarchyService {
  fun getSwingHierarchyAsDOM(component: Component?, onlyFrontend: Boolean): String {
    val creator = XpathDataModelCreator()
    if (onlyFrontend) {
      creator.elementProcessors.removeIf { it.isRemDevExtension }
    }

    val doc = creator.create(component)

    sanitizeXmlContent(doc.documentElement)

    return W3CDom().asString(doc)
  }

  fun sanitizeXmlContent(node: Node) {
    node.attributes?.let { attrs ->
      (0..attrs.length).mapNotNull { attrs.item(it) }
        .forEach {
          it.textContent = sanitizeXmlChars(it.textContent)
        }
    }
    if (node is Text) {
      node.textContent = sanitizeXmlChars(node.textContent)
    }
    (0..node.childNodes.length).mapNotNull { node.childNodes.item(it) }
      .forEach { sanitizeXmlContent(it) }
  }

  fun sanitizeXmlChars(xml: String): String {
    if (xml.isEmpty()) return ""
    // ref : http://www.w3.org/TR/REC-xml/#charsets
    val xmlInvalidChars =
      "[^\\u0009\\u000A\\u000D\\u0020-\\uD7FF\\uE000-\\uFFFD\\x{10000}-\\x{10FFFF}]".toRegex()
    return xmlInvalidChars.replace(xml, "")
  }

  fun findAllText(component: Component): TextDataList {
    return TextParser.parseComponent(component).let { TextDataList().apply { addAll(it) } }
  }
}