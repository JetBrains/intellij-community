package com.github.firsttimeinforever.mermaid.lang.highlighting

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidLexer
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens

class MermaidHighlighter: SyntaxHighlighterBase() {
  override fun getHighlightingLexer(): Lexer {
    return MermaidLexer()
  }

  private fun getPieHighlights(tokenType: IElementType): Array<TextAttributesKey>? {
    return when (tokenType) {
      MermaidTokens.Pie.PIE, MermaidTokens.Pie.TITLE, MermaidTokens.Pie.SHOW_DATA -> arrayOf(MermaidTextAttributes.keyword)
      MermaidTokens.Pie.TITLE_VALUE -> arrayOf(MermaidTextAttributes.string)
      else -> null
    }
  }

  override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
    val pieHighlights = getPieHighlights(tokenType)
    return pieHighlights ?: when (tokenType) {
      MermaidTokens.DOUBLE_QUOTE, MermaidTokens.STRING_VALUE -> arrayOf(MermaidTextAttributes.string)
      MermaidTokens.LINE_COMMENT, MermaidTokens.COMMENT_TEXT -> arrayOf(MermaidTextAttributes.comment)
      else -> arrayOf(HighlighterColors.TEXT)
    }
  }
}
