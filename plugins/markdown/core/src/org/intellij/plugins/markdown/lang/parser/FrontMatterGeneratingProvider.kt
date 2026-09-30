// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.parser

import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.entities.EntityConverter
import org.intellij.plugins.markdown.lang.parser.blocks.frontmatter.FrontMatterHeaderMarkerProvider
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.nodes.CollectionNode
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode
import java.io.StringReader

internal class FrontMatterGeneratingProvider: GeneratingProvider {
  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    val content = node.children.find { it.type == FrontMatterHeaderMarkerProvider.FRONT_MATTER_HEADER_CONTENT } ?: return
    val contentText = content.getTextInNode(text).toString()
    val table = if (isYamlHeader(node, text)) renderYamlTable(contentText) else null
    if (table != null) {
      visitor.consumeTagOpen(node, "table", """class="$CSS_CLASS"""")
      visitor.consumeHtml(table)
      visitor.consumeTagClose("table")
    }
    else {
      visitor.consumeTagOpen(node, "pre", """class="$CSS_CLASS"""")
      visitor.consumeHtml("<code>${escape(contentText.trimEnd())}</code>")
      visitor.consumeTagClose("pre")
    }
  }

  private fun isYamlHeader(node: ASTNode, text: String): Boolean {
    val openingDelimiter = node.children.firstOrNull { it.type == FrontMatterHeaderMarkerProvider.FRONT_MATTER_HEADER_DELIMITER }
    return openingDelimiter?.getTextInNode(text)?.startsWith("-") == true
  }

  private fun renderYamlTable(yaml: String): String? {
    val root = try {
      Yaml(LoaderOptions()).compose(StringReader(yaml))
    }
    catch (_: YAMLException) {
      return null
    }
    if (root !is CollectionNode<*> || root.value.isEmpty()) {
      return null
    }
    return try {
      TableRenderer().apply { appendTableContent(root, depth = 0) }.result()
    }
    catch (_: TooLargeException) {
      null
    }
  }

  private class TableRenderer {
    private val builder = StringBuilder()
    private var renderedNodes = 0

    fun result(): String = builder.toString()

    fun appendTableContent(node: CollectionNode<*>, depth: Int) {
      when (node) {
        is MappingNode if depth == 0 -> {
          builder.append("<tbody>")
          for (tuple in node.value) {
            builder.append("<tr><th>")
            appendValue(tuple.keyNode, depth = 1)
            builder.append("</th><td>")
            appendValue(tuple.valueNode, depth = 1)
            builder.append("</td></tr>")
          }
          builder.append("</tbody>")
        }
        is MappingNode -> {
          builder.append("<thead><tr>")
          for (tuple in node.value) {
            builder.append("<th>")
            appendValue(tuple.keyNode, depth + 1)
            builder.append("</th>")
          }
          builder.append("</tr></thead><tbody><tr>")
          for (tuple in node.value) {
            builder.append("<td>")
            appendValue(tuple.valueNode, depth + 1)
            builder.append("</td>")
          }
          builder.append("</tr></tbody>")
        }
        is SequenceNode -> {
          builder.append("<tbody><tr>")
          for (item in node.value) {
            builder.append("<td>")
            appendValue(item, depth + 1)
            builder.append("</td>")
          }
          builder.append("</tr></tbody>")
        }
      }
    }

    private fun appendValue(node: Node, depth: Int) {
      renderedNodes++
      if (depth > MAX_DEPTH || renderedNodes > MAX_NODES) {
        throw TooLargeException()
      }
      when (node) {
        is ScalarNode -> builder.append(escape(node.value))
        is CollectionNode<*> -> {
          if (node.value.isEmpty()) {
            return
          }
          builder.append("<table>")
          appendTableContent(node, depth)
          builder.append("</table>")
        }
      }
    }
  }

  private class TooLargeException: RuntimeException(null, null, false, false)

  companion object {
    private const val CSS_CLASS = "frontmatter-header"
    private const val MAX_DEPTH = 16
    private const val MAX_NODES = 10_000

    private fun escape(text: String): String {
      return EntityConverter.replaceEntities(text, processEntities = false, processEscapes = false)
    }
  }
}
