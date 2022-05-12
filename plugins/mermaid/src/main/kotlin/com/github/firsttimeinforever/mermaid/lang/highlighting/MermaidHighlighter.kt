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

  private fun getFlowchartHighlights(tokenType: IElementType): Array<TextAttributesKey>? {
    return when (tokenType) {
      MermaidTokens.Flowchart.FLOWCHART,
      MermaidTokens.Flowchart.SUBGRAPH,
      MermaidTokens.Flowchart.END,
      MermaidTokens.Flowchart.DIRECTION,
      MermaidTokens.Flowchart.STYLE,
      MermaidTokens.Flowchart.STYLE_OPT,
      MermaidTokens.Flowchart.CLASS_DEF -> arrayOf(MermaidTextAttributes.keyword)

      MermaidTokens.Flowchart.LINK,
      MermaidTokens.Flowchart.START_LINK,
      MermaidTokens.Flowchart.STYLE_SEPARATOR -> arrayOf(MermaidTextAttributes.operationSign)


      MermaidTokens.Flowchart.NODE_TEXT,
      MermaidTokens.Flowchart.STYLE_VAL -> arrayOf(MermaidTextAttributes.string)

      MermaidTokens.Flowchart.DIR  -> arrayOf(MermaidTextAttributes.constant)

      MermaidTokens.Flowchart.NODE_ID,
      MermaidTokens.Flowchart.STYLE_TARGET,
      MermaidTokens.Flowchart.CLASS -> arrayOf(MermaidTextAttributes.identifier)

      else -> null
    }
  }

  override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
    val pieHighlights = getPieHighlights(tokenType)
    val journeyHighlighter = getJourneyHighlights(tokenType)
    val flowchartHighlighter = getFlowchartHighlights(tokenType)
    return pieHighlights
      ?: journeyHighlighter
      ?: flowchartHighlighter
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
