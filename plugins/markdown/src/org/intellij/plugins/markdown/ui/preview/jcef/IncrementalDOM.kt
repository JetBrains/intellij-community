// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.jcef

import com.intellij.openapi.util.text.StringUtil
import org.jsoup.Jsoup
import org.jsoup.nodes.Comment
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.net.URLEncoder

internal object IncrementalDOM {
  private fun ensureCorrectTag(name: String): String {
    return if (name == "body") {
      "div"
    }
    else name
  }

  private fun encodeArgument(argument: String): String {
    return URLEncoder.encode(argument, Charsets.UTF_8).replace("+", "%20")
  }

  private fun openTag(node: Node, builder: StringBuilder) {
    with(builder) {
      append("o('")
      append(ensureCorrectTag(node.nodeName()))
      append("'")
      for (attribute in node.attributes()) {
        append(",'")
        append(attribute.key)
        append("','")
        append(escapeAttributeContent(attribute.value))
        append("'")
      }
      append(");")
    }
  }

  private fun closeTag(node: Node, builder: StringBuilder) {
    with(builder) {
      append("c('")
      append(ensureCorrectTag(node.nodeName()))
      append("');")
    }
  }

  private fun textElement(node: TextNode, builder: StringBuilder) {
    with(builder) {
      // It seems like CefBrowser::executeJavaScript() is not supporting a lot of unicode
      // symbols (like emojis) in the code string (probably a limitation of CefString).
      // To preserve this symbols we are encoding our strings before sending them to JCEF,
      // and decoding them before executing the code. For our use case it's enough to encode
      // just the actual text content that will be displayed (only IncrementalDOM.text() calls).
      append("t(`")
      append(encodeArgument(node.wholeText))
      append("`);")
    }
  }

  private fun traverse(node: Node, result: StringBuilder = StringBuilder()): StringBuilder {
    when (node) {
      is TextNode -> textElement(node, result)
      is Comment -> Unit
      else -> {
        openTag(node, result)
        for (child in node.childNodes()) {
          traverse(child, result)
        }
        closeTag(node, result)
      }
    }
    return result
  }

  fun generateRenderClosure(html: String): String {
    // language=JavaScript
    return """
      () => {
        const o = (tag, ...attrs) => IncrementalDOM.elementOpen(tag, null, null, ...attrs);
        const t = content => IncrementalDOM.text(decodeURIComponent(content));
        const c = IncrementalDOM.elementClose;
        ${generateDomBuildCalls(html)}
      }
    """
  }

  fun generateDomBuildCalls(html: String): String {
    val document = Jsoup.parse(html)
    return traverse(document.body()).toString()
  }

  private fun escapeTagContent(string: String): String =
    StringUtil.escapeChars(string, '\\', '`', '$')

  private fun escapeAttributeContent(string: String): String = StringUtil.escapeChar(string, '\'')
}
