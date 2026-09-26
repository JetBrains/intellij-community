// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.editor.livepreview

import com.intellij.openapi.util.text.StringUtil
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.entities.EntityConverter
import org.intellij.markdown.parser.LinkMap
import org.intellij.plugins.markdown.lang.parser.MarkdownParserManager
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeaderContent

/** The leaves that render as plain text. Each one becomes a span with its source range. */
private val PlainTextLeafTypes: List<IElementType> = listOf(
  MarkdownTokenTypes.TEXT,
  MarkdownTokenTypes.WHITE_SPACE,
  MarkdownTokenTypes.COLON,
  MarkdownTokenTypes.SINGLE_QUOTE,
  MarkdownTokenTypes.DOUBLE_QUOTE,
  MarkdownTokenTypes.LPAREN,
  MarkdownTokenTypes.RPAREN,
  MarkdownTokenTypes.LBRACKET,
  MarkdownTokenTypes.RBRACKET,
  MarkdownTokenTypes.LT,
  MarkdownTokenTypes.GT,
  MarkdownTokenTypes.EXCLAMATION_MARK,
  MarkdownTokenTypes.EMPH,
  MarkdownTokenTypes.ESCAPED_BACKTICKS,
)

private val HeadingProviders: Map<IElementType, GeneratingProvider> =
  PlainTextLeafTypes.associateWith { SourceSpanProvider } + mapOf(
    MarkdownTokenTypes.HTML_TAG to LiteralTextProvider,
    GFMElementTypes.INLINE_MATH to LiteralTextProvider,
    MarkdownElementTypes.IMAGE to SkippedProvider,
  )

/**
 * Generates the inline HTML of the headings. Only the heading lines are parsed.
 * Raw HTML and math stay literal text. Images are left out, because an inlay below the line paints them.
 */
internal object HeadingHtmlGenerator {
  private val providers by lazy {
    MarkdownParserManager.FLAVOUR.createHtmlGeneratingProviders(LinkMap(emptyMap()), null) + HeadingProviders
  }

  /**
   * The HTML of [content]. Its source ranges are relative to [lineStart].
   * An edit before the heading then keeps the HTML, so the frontend keeps the heading fold.
   */
  fun generate(content: MarkdownHeaderContent, lineStart: Int): String {
    val range = content.textRange
    val line = content.parent.text
    val lineEnd = range.endOffset - lineStart
    val root = MarkdownParserManager.createMarkdownParser(MarkdownParserManager.FLAVOUR)
      .parseInline(MarkdownTokenTypes.ATX_CONTENT, line, range.startOffset - lineStart, lineEnd)
    return HtmlGenerator(line, root, providers, includeSrcPositions = true).generateHtml()
  }
}

/** Wraps a leaf in a span with its source range. The text is escaped like the default leaf text. */
private object SourceSpanProvider : GeneratingProvider {
  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    visitor.consumeHtml(sourceSpan(node, EntityConverter.replaceEntities(node.getTextInNode(text), true, true)))
  }
}

/** Shows the source of a node as text in a span with its source range. */
private object LiteralTextProvider : GeneratingProvider {
  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    visitor.consumeHtml(sourceSpan(node, StringUtil.escapeXmlEntities(node.getTextInNode(text).toString())))
  }
}

private fun sourceSpan(node: ASTNode, html: String): String =
  "<span ${HtmlGenerator.SRC_ATTRIBUTE_NAME}='${node.startOffset}..${node.endOffset}'>$html</span>"

private object SkippedProvider : GeneratingProvider {
  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) = Unit
}
