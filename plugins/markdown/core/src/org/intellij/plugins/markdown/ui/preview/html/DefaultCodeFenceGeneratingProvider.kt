// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.html

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.entities.EntityConverter
import org.intellij.plugins.markdown.extensions.CodeFenceGeneratingProvider
import org.intellij.plugins.markdown.extensions.common.highlighter.MarkdownCodeFencePreviewHighlighter
import org.intellij.plugins.markdown.extensions.common.highlighter.buildHighlightedFenceContent
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.CommandRunnerExtension
import org.intellij.plugins.markdown.lang.psi.util.hasType

internal class DefaultCodeFenceGeneratingProvider(
  private val cacheProviders: Array<CodeFenceGeneratingProvider>,
  private val project: Project? = null,
  private val file: VirtualFile? = null
): GeneratingProvider {
  private fun pluginGeneratedHtml(language: String?, codeFenceContent: String, codeFenceRawContent: String, node: ASTNode): String {
    if (language == null) {
      return buildHighlightedFenceContent(
        codeFenceContent,
        highlightedRanges = emptyList(),
        node,
        useAbsoluteOffsets = true,
        additionalLineProcessor = ::processCodeLine
      )
    }
    val html = when (val provider = cacheProviders.firstOrNull { it.isApplicable(language) }) {
      null -> buildHighlightedFenceContent(
        codeFenceContent,
        highlightedRanges = emptyList(),
        node,
        useAbsoluteOffsets = true,
        additionalLineProcessor = ::processCodeLine
      )
      else -> provider.generateHtmlWithFile(language, codeFenceRawContent, node, file)
    }
    return processCodeBlock(codeFenceRawContent, language) + html
  }

  private fun CodeFenceGeneratingProvider.generateHtmlWithFile(
    language: String,
    raw: String,
    node: ASTNode,
    file: VirtualFile?
  ): String {
    return when {
      this is MarkdownCodeFencePreviewHighlighter && file != null -> generateHtmlForFile(language, raw, node, file)
      else -> generateHtml(language, raw, node)
    }
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
    addCopyButton(visitor, collectFenceText(node, text).trim())

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

  private fun collectFenceText(node: ASTNode, allText: String): String {
    return buildString {
      collectFenceText(this, node, allText)
    }
  }

  private fun collectFenceText(builder: StringBuilder, node: ASTNode, allText: String) {
    if (node.type == MarkdownTokenTypes.CODE_FENCE_CONTENT || node.type == MarkdownTokenTypes.EOL) {
      builder.append(codeFenceRawText(allText, node))
    }
    for (child in node.children) {
      collectFenceText(builder, child, allText)
    }
  }

  private fun addCopyButton(visitor: HtmlGenerator.HtmlGeneratingVisitor, content: String) {
    val encodedContent = PreviewEncodingUtil.encodeContent(content)
    // language=HTML
    val html = """
    <div class="code-fence-highlighter-copy-button" data-fence-content="$encodedContent">
      <img class="code-fence-highlighter-copy-button-icon">
      <span class="tooltiptext">Copy to clipboard</span>
    </div>
    """.trimIndent()
    visitor.consumeHtml(html)
  }

  private fun codeFenceRawText(text: String, node: ASTNode): CharSequence {
    return when (node.type) {
      MarkdownTokenTypes.BLOCK_QUOTE -> ""
      else -> node.getTextInNode(text)
    }
  }

  private fun codeFenceText(text: String, node: ASTNode): CharSequence =
    if (node.type != MarkdownTokenTypes.BLOCK_QUOTE) HtmlGenerator.leafText(text, node, false) else ""

  companion object {
    private fun findFenceContentStart(fence: ASTNode): ASTNode? {
      val children = fence.children
      val language = children.find { it.hasType(MarkdownTokenTypes.FENCE_LANG) }
      if (language != null) {
        return language
      }
      return children.find { it.hasType(MarkdownTokenTypes.CODE_FENCE_START) }
    }

    internal fun calculateCodeFenceContentBaseOffset(fence: ASTNode): Int {
      return when (val start = findFenceContentStart(fence)) {
        null -> fence.startOffset
        else -> start.endOffset + 1
      }
    }

    internal fun escape(html: String) = EntityConverter.replaceEntities(
      html,
      processEntities = true,
      processEscapes = false
    )
  }
}
