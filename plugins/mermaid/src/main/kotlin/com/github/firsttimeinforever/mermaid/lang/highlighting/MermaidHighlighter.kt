package com.github.firsttimeinforever.mermaid.lang.highlighting

import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidLexer
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

class MermaidHighlighter : SyntaxHighlighterBase() {
  override fun getHighlightingLexer(): Lexer {
    return MermaidLexer()
  }

  private fun getPieHighlights(tokenType: IElementType): Array<TextAttributesKey>? {
    return when (tokenType) {
      MermaidTokens.Pie.PIE,
      MermaidTokens.Pie.SHOW_DATA -> arrayOf(MermaidTextAttributes.keyword)
      else -> null
    }
  }

  private fun getJourneyHighlights(tokenType: IElementType): Array<TextAttributesKey>? {
    return when (tokenType) {
      MermaidTokens.Journey.JOURNEY,
      MermaidTokens.Journey.SECTION -> arrayOf(MermaidTextAttributes.keyword)

      MermaidTokens.Journey.TASK_NAME,
      MermaidTokens.Journey.SECTION_TITLE -> arrayOf(MermaidTextAttributes.string)
      else -> null
    }
  }

  override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
    val pieHighlights = getPieHighlights(tokenType)
    val journeyHighlighter = getJourneyHighlights(tokenType)
    return pieHighlights
      ?: journeyHighlighter
      ?: when (tokenType) {
        MermaidTokens.TITLE -> arrayOf(MermaidTextAttributes.keyword)

        MermaidTokens.TITLE_VALUE,
        MermaidTokens.DOUBLE_QUOTE,
        MermaidTokens.STRING_VALUE -> arrayOf(MermaidTextAttributes.string)

        MermaidTokens.LINE_COMMENT,
        MermaidTokens.COMMENT_TEXT,
        MermaidTokens.IGNORED -> arrayOf(MermaidTextAttributes.comment)

        else -> arrayOf(HighlighterColors.TEXT)
      }
  }
}
