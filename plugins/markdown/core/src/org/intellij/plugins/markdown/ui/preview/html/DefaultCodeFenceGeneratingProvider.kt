// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.html

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Base64
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.entities.EntityConverter
import org.intellij.plugins.markdown.extensions.CodeFenceGeneratingProvider
import org.intellij.plugins.markdown.extensions.common.highlighter.MarkdownCodeFencePreviewHighlighter
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.CommandRunnerExtension

internal class DefaultCodeFenceGeneratingProvider(
  private val cacheProviders: Array<CodeFenceGeneratingProvider>,
  private val project: Project? = null,
  private val file: VirtualFile? = null
): GeneratingProvider {
  private fun pluginGeneratedHtml(language: String?, codeFenceContent: String, codeFenceRawContent: String, node: ASTNode): String {
    if (language == null) {
      return insertCodeOffsets(codeFenceContent, node)
    }
    val html = cacheProviders
      .filter { it.isApplicable(language) }.stream()
      .findFirst()
      .map {
        if (it is MarkdownCodeFencePreviewHighlighter && file != null) {
          it.generateHtmlForFile(language, codeFenceRawContent, node, file)
        } else {
          it.generateHtml(language, codeFenceRawContent, node)
        }
      }
      .orElse(insertCodeOffsets(codeFenceContent, node))

    return processCodeBlock(codeFenceRawContent, language) + html
  }

  private fun processCodeBlock(codeFenceRawContent: String, language: String): String {
    return file?.let(CommandRunnerExtension::getRunnerByFile)?.processCodeBlock(codeFenceRawContent, language) ?: ""
  }

  private fun processCodeLine(rawCodeLine: String): String {
    return file?.let(CommandRunnerExtension::getRunnerByFile)?.processCodeLine(rawCodeLine, true) ?: ""
  }

  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    val indentBefore = node.getTextInNode(text).commonPrefixWith(" ".repeat(10)).length

    visitor.consumeHtml("<pre class=\"code-fence\" ${HtmlGenerator.getSrcPosAttribute(node)}>")

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

    val rawContentResult = codeFenceRawContent.toString()
    addCopyButton(visitor, rawContentResult)
    if (state == 1) {
      visitor.consumeHtml(
        pluginGeneratedHtml(language, codeFenceContent.toString(), rawContentResult, node)
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

  private fun addCopyButton(visitor: HtmlGenerator.HtmlGeneratingVisitor, content: String) {
    val encodedContent = Base64.encode(content.toByteArray())
    // language=HTML
    val html = """
    <div class="code-fence-highlighter-copy-button" data-fence-content="$encodedContent">
        <img class="code-fence-highlighter-copy-button-icon">
    </div>
    """.trimIndent()
    visitor.consumeHtml(html)
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
      lines.add("<span ${HtmlGenerator.SRC_ATTRIBUTE_NAME}='$left..${left + line.length}'>${processCodeLine(line) + escape(line)}</span>")
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
