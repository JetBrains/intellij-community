// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.jcef

import org.jsoup.Jsoup
import org.jsoup.nodes.Comment
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal object IncrementalDOM {
  private fun ensureCorrectTag(name: String): String {
    return if (name == "body") {
      "div"
    }
    else name
  }

  private fun openTag(node: Node): String =
    buildString {
      append("o('${ensureCorrectTag(node.nodeName())}'")
      for (attribute in node.attributes()) {
        append(",'${attribute.key}','${attribute.value.replace("'", "\\'")}'")
      }
      append(");")
    }

  private fun closeTag(node: Node): String = "c('${ensureCorrectTag(node.nodeName())}');"

  private fun textElement(node: TextNode): String = "t(`${node.wholeText.replace("`", "\\`")}`);"

  private fun traverse(node: Node, result: StringBuilder = StringBuilder()): StringBuilder {
    when (node) {
      is TextNode -> result.append(textElement(node))
      is Comment -> Unit
      else -> {
        result.append(openTag(node))
        for (child in node.childNodes()) {
          traverse(child, result)
        }
        result.append(closeTag(node))
      }
    }
    return result
  }

  fun generateRenderClosure(html: String): String {
    val document = Jsoup.parse(html)
    // language=JavaScript
    return """
      () => {
        const o = (tag, ...attrs) => IncrementalDOM.elementOpen(tag, null, null, ...attrs);
        const t = IncrementalDOM.text;
        const c = IncrementalDOM.elementClose;
        ${traverse(document.body())}
      }
    """
  }
}
