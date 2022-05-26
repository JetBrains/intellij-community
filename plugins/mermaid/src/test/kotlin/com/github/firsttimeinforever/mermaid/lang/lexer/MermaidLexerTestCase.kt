package com.github.firsttimeinforever.mermaid.lang.lexer

import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.LightPlatformCodeInsightTestCase

abstract class MermaidLexerTestCase: LightPlatformCodeInsightTestCase() {
  data class Token(
    val type: IElementType,
    val start: Int,
    val end: Int,
    val text: String
  ) {
    override fun toString(): String {
      return when (type) {
        MermaidTokens.EOL -> "Token($type, $start, $end, \"\\n\"),"
        MermaidTokens.DOUBLE_QUOTE -> "Token($type, $start, $end, \"\\\"\"),"
        else -> "Token($type, $start, $end, \"$text\"),"
      }
    }
  }

  protected fun doTest(content: String, expectedTokens: List<Token>) {
    val tokens = runLexer(content)
    assertEquals(tokensToString(expectedTokens), tokensToString(tokens))
  }

  companion object {
    fun tokensToString(tokens: Iterable<Token>): String {
      return buildString {
        for (token in tokens) {
          append("$token\n")
        }
      }
    }

    fun runLexer(content: String): List<Token> {
      val lexer = MermaidLexer()
      lexer.start(content)
      val tokens = mutableListOf<Token>()
      while (lexer.tokenType != null) {
        with(lexer) {
          tokens.add(Token(tokenType!!, tokenStart, tokenEnd, tokenText))
        }
        lexer.advance()
      }
      return tokens
    }
  }
}
