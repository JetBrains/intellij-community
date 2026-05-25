// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.highlighting

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import org.intellij.plugins.markdown.highlighting.MarkdownHighlightingLexer.getHtmlSyntaxHighlighter
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes


open class MarkdownSyntaxHighlighter: SyntaxHighlighterBase() {
  private val htmlSyntaxHighlighter by lazy { getHtmlSyntaxHighlighter() }
  protected val lexer: Lexer by lazy { MarkdownHighlightingLexer(htmlSyntaxHighlighter) }

  override fun getHighlightingLexer(): Lexer = lexer

  override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> {
    val attributes = ATTRIBUTES[tokenType]
    if (attributes != null) return pack(attributes)
    return pack(htmlSyntaxHighlighter?.getTokenHighlights(tokenType)?.lastOrNull())
  }

  fun getMarkdownTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> = pack(ATTRIBUTES[tokenType])

  @Suppress("CompanionObjectInExtension")
  companion object {
    protected val ATTRIBUTES: Map<IElementType, TextAttributesKey> = HashMap<IElementType, TextAttributesKey>().also {
      safeMap(it, MarkdownTokenTypes.TEXT, MarkdownHighlighterColors.TEXT)
      safeMap(it, MarkdownElementTypes.STRONG, MarkdownHighlighterColors.BOLD)
      safeMap(it, MarkdownElementTypes.EMPH, MarkdownHighlighterColors.ITALIC)
      safeMap(it, MarkdownElementTypes.STRIKETHROUGH, MarkdownHighlighterColors.STRIKE_THROUGH)

      safeMap(it, MarkdownTokenTypes.HORIZONTAL_RULE, MarkdownHighlighterColors.HRULE)
      safeMap(it, MarkdownTokenTypes.TABLE_SEPARATOR, MarkdownHighlighterColors.TABLE_SEPARATOR)
      safeMap(it, MarkdownTokenTypes.BLOCK_QUOTE, MarkdownHighlighterColors.BLOCK_QUOTE_MARKER)
      safeMap(it, MarkdownTokenTypeSets.LIST_MARKERS, MarkdownHighlighterColors.LIST_MARKER)
      safeMap(it, MarkdownTokenTypeSets.HEADER_MARKERS, MarkdownHighlighterColors.HEADER_MARKER)

      safeMap(it, MarkdownTokenTypeSets.HEADER_LEVEL_1_SET, MarkdownHighlighterColors.HEADER_LEVEL_1)
      safeMap(it, MarkdownTokenTypeSets.HEADER_LEVEL_2_SET, MarkdownHighlighterColors.HEADER_LEVEL_2)
      safeMap(it, MarkdownTokenTypeSets.HEADER_LEVEL_3_SET, MarkdownHighlighterColors.HEADER_LEVEL_3)
      safeMap(it, MarkdownTokenTypeSets.HEADER_LEVEL_4_SET, MarkdownHighlighterColors.HEADER_LEVEL_4)
      safeMap(it, MarkdownTokenTypeSets.HEADER_LEVEL_5_SET, MarkdownHighlighterColors.HEADER_LEVEL_5)
      safeMap(it, MarkdownTokenTypeSets.HEADER_LEVEL_6_SET, MarkdownHighlighterColors.HEADER_LEVEL_6)

      safeMap(it, MarkdownElementTypes.INLINE_LINK, MarkdownHighlighterColors.EXPLICIT_LINK)
      safeMap(it, MarkdownTokenTypeSets.REFERENCE_LINK_SET, MarkdownHighlighterColors.REFERENCE_LINK)
      safeMap(it, MarkdownElementTypes.IMAGE, MarkdownHighlighterColors.IMAGE)
      safeMap(it, MarkdownTokenTypes.AUTOLINK, MarkdownHighlighterColors.AUTO_LINK)
      safeMap(it, MarkdownTokenTypes.GFM_AUTOLINK, MarkdownHighlighterColors.AUTO_LINK)
      safeMap(it, MarkdownTokenTypes.EMAIL_AUTOLINK, MarkdownHighlighterColors.AUTO_LINK)
      safeMap(it, MarkdownElementTypes.LINK_DEFINITION, MarkdownHighlighterColors.LINK_DEFINITION)
      safeMap(it, MarkdownElementTypes.LINK_TEXT, MarkdownHighlighterColors.LINK_TEXT)
      safeMap(it, MarkdownElementTypes.LINK_LABEL, MarkdownHighlighterColors.LINK_LABEL)
      safeMap(it, MarkdownElementTypes.LINK_DESTINATION, MarkdownHighlighterColors.LINK_DESTINATION)
      safeMap(it, MarkdownElementTypes.LINK_TITLE, MarkdownHighlighterColors.LINK_TITLE)
      safeMap(it, MarkdownElementTypes.LINK_COMMENT, MarkdownHighlighterColors.COMMENT)

      safeMap(it, MarkdownElementTypes.BLOCK_QUOTE, MarkdownHighlighterColors.BLOCK_QUOTE)
      safeMap(it, MarkdownElementTypes.UNORDERED_LIST, MarkdownHighlighterColors.UNORDERED_LIST)
      safeMap(it, MarkdownElementTypes.ORDERED_LIST, MarkdownHighlighterColors.ORDERED_LIST)
      safeMap(it, MarkdownElementTypes.LIST_ITEM, MarkdownHighlighterColors.LIST_ITEM)

      safeMap(it, MarkdownTokenTypes.BACKTICK, MarkdownHighlighterColors.TEXT)
      safeMap(it, MarkdownElementTypes.CODE_SPAN, MarkdownHighlighterColors.CODE_SPAN)
      safeMap(it, MarkdownTokenTypes.CODE_LINE, MarkdownHighlighterColors.CODE_BLOCK)

      safeMap(it, MarkdownTokenTypes.CODE_FENCE_CONTENT, MarkdownHighlighterColors.CODE_FENCE)
      safeMap(it, MarkdownTokenTypes.CODE_FENCE_START, MarkdownHighlighterColors.CODE_FENCE_MARKER)
      safeMap(it, MarkdownTokenTypes.CODE_FENCE_END, MarkdownHighlighterColors.CODE_FENCE_MARKER)
      safeMap(it, MarkdownTokenTypes.FENCE_LANG, MarkdownHighlighterColors.CODE_FENCE_LANGUAGE)

      safeMap(it, MarkdownElementTypes.HTML_BLOCK, MarkdownHighlighterColors.HTML_BLOCK)
      safeMap(it, MarkdownTokenTypes.HTML_TAG, MarkdownHighlighterColors.INLINE_HTML)

      safeMap(it, MarkdownElementTypes.DEFINITION_LIST, MarkdownHighlighterColors.DEFINITION_LIST)
      safeMap(it, MarkdownElementTypes.DEFINITION_MARKER, MarkdownHighlighterColors.DEFINITION_LIST_MARKER)
      safeMap(it, MarkdownElementTypes.DEFINITION, MarkdownHighlighterColors.DEFINITION)
      safeMap(it, MarkdownElementTypes.DEFINITION_TERM, MarkdownHighlighterColors.TERM)

      safeMap(it, MarkdownElementTypes.FRONT_MATTER_HEADER_DELIMITER, MarkdownHighlighterColors.FRONT_MATTER_HEADER_DELIMITER)
    }
  }
}
