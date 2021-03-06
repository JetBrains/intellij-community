// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.html

import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.entities.EntityConverter
import org.intellij.plugins.markdown.extensions.MarkdownCodeFencePluginGeneratingProvider
import java.util.*

internal class MarkdownCodeFenceGeneratingProvider(private val pluginCacheProviders: Array<MarkdownCodeFencePluginGeneratingProvider>)
  : GeneratingProvider {

  private fun pluginGeneratedHtml(language: String, codeFenceContent: String, codeFenceRawContent: String, node: ASTNode): String {
    return pluginCacheProviders
      .filter { it.isApplicable(language) }.stream()
      .findFirst()
      .map { it.generateHtml(language, codeFenceRawContent, node) }
      .orElse(insertCodeOffsets(codeFenceContent, node))
  }

  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    val indentBefore = node.getTextInNode(text).commonPrefixWith(" ".repeat(10)).length

    visitor.consumeHtml("<pre ${HtmlGenerator.getSrcPosAttribute(node)}>")

    var state = 0

    var childrenToConsider = node.children
    if (childrenToConsider.last().type == MarkdownTokenTypes.CODE_FENCE_END) {
      childrenToConsider = childrenToConsider.subList(0, childrenToConsider.size - 1)
    }

    var lastChildWasContent = false

    val attributes = ArrayList<String>()
    var language: String? = null
    val codeFenceRawContent = StringBuilder()
    val codeFenceContent = StringBuilder()
    for (child in childrenToConsider) {
      if (state == 1 && child.type in listOf(MarkdownTokenTypes.CODE_FENCE_CONTENT, MarkdownTokenTypes.EOL)) {
        codeFenceRawContent.append(HtmlGenerator.trimIndents(codeFenceRawText(text, child), indentBefore))
        codeFenceContent.append(HtmlGenerator.trimIndents(codeFenceText(text, child), indentBefore))
        lastChildWasContent = child.type == MarkdownTokenTypes.CODE_FENCE_CONTENT
      }
      if (state == 0 && child.type == MarkdownTokenTypes.FENCE_LANG) {
        language = HtmlGenerator.leafText(text, child).toString().trim()
        attributes.add("class=\"language-${language.split(" ").joinToString(separator = "-")}\"")
      }
      if (state == 0 && child.type == MarkdownTokenTypes.EOL) {
        visitor.consumeTagOpen(node, "code", *attributes.toTypedArray())
        state = 1
      }
    }

    if (state == 1) {
      visitor.consumeHtml(
        if (language != null) {
          pluginGeneratedHtml(language, codeFenceContent.toString(), codeFenceRawContent.toString(), node)
        }
        else insertCodeOffsets(codeFenceContent.toString(), node)
      )
    }

    if (state == 0) {
      visitor.consumeTagOpen(node, "code", *attributes.toTypedArray())
    }
    if (lastChildWasContent) {
      visitor.consumeHtml("\n")
    }
    visitor.consumeHtml("</code></pre>")
  }

  private fun codeFenceRawText(text: String, node: ASTNode): CharSequence =
    if (node.type != MarkdownTokenTypes.BLOCK_QUOTE) node.getTextInNode(text) else ""

  private fun codeFenceText(text: String, node: ASTNode): CharSequence =
    if (node.type != MarkdownTokenTypes.BLOCK_QUOTE) HtmlGenerator.leafText(text, node, false) else ""

  private fun insertCodeOffsets(content: String, node: ASTNode): String {
    val lines = ArrayList<String>()
    val baseOffset = calcCodeFenceContentBaseOffset(node)
    var left = baseOffset
    for (line in content.lines()) {
      val right = left + line.length
      lines.add("<span ${HtmlGenerator.SRC_ATTRIBUTE_NAME}='$left..${left + line.length}'>${escape(line)}</span>")
      left = right + 1
    }
    return lines.joinToString(
      separator = "\n",
      prefix = "<span ${HtmlGenerator.SRC_ATTRIBUTE_NAME}='${node.startOffset}..$baseOffset'/>",
      postfix = node.children.find { it.type == MarkdownTokenTypes.CODE_FENCE_END }?.let {
        "<span ${HtmlGenerator.SRC_ATTRIBUTE_NAME}='${it.startOffset}..${it.endOffset}'/>"
      } ?: ""
    )
  }

  companion object {
    internal fun calcCodeFenceContentBaseOffset(node: ASTNode): Int {
      val baseNode = node.children.find { it.type == MarkdownTokenTypes.FENCE_LANG }
                     ?: node.children.find { it.type == MarkdownTokenTypes.CODE_FENCE_START }
      return baseNode?.let { it.endOffset + 1 } ?: node.startOffset
    }

    internal fun escape(html: String) = EntityConverter.replaceEntities(
      html,
      processEntities = true,
      processEscapes = false
    )
  }
}
