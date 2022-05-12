package com.github.firsttimeinforever.mermaid.lang.lexer

import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COLON
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COMMENT_TEXT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.DOUBLE_QUOTE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.EOL
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.LINE_COMMENT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Pie
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.STRING_VALUE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.TITLE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.TITLE_VALUE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.WHITE_SPACE

class PieTest: MermaidLexerTestCase() {
  fun `test pie with title with newline at the end`() {
    val content = """
    pie
      title Pets will be available
    
    """.trimIndent()
    val expected = listOf(
      Token(Pie.PIE, 0, 3, "pie"),
      Token(EOL, 3, 4, "\n"),
      Token(WHITE_SPACE, 4, 6, "  "),
      Token(TITLE, 6, 11, "title"),
      Token(WHITE_SPACE, 11, 12, " "),
      Token(TITLE_VALUE, 12, 34, "Pets will be available"),
      Token(EOL, 34, 35, "\n")
    )
    doTest(content, expected)
  }

  fun `test with value and newlines`() {
    val content = """
    pie
      title Pets adopted by volunteers
    
    
      "Dogs" : 386
    
    """.trimIndent()
    val expected = listOf(
      Token(Pie.PIE, 0, 3, "pie"),
      Token(EOL, 3, 4, "\n"),
      Token(WHITE_SPACE, 4, 6, "  "),
      Token(TITLE, 6, 11, "title"),
      Token(WHITE_SPACE, 11, 12, " "),
      Token(TITLE_VALUE, 12, 38, "Pets adopted by volunteers"),
      Token(EOL, 38, 39, "\n"),
      Token(EOL, 39, 41, "\n"),
      Token(WHITE_SPACE, 41, 43, "  "),
      Token(DOUBLE_QUOTE, 43, 44, "\""),
      Token(STRING_VALUE, 44, 48, "Dogs"),
      Token(DOUBLE_QUOTE, 48, 49, "\""),
      Token(WHITE_SPACE, 49, 50, " "),
      Token(COLON, 50, 51, ":"),
      Token(WHITE_SPACE, 51, 52, " "),
      Token(Pie.VALUE, 52, 55, "386"),
      Token(EOL, 55, 56, "\n")
    )
    doTest(content, expected)
  }

  fun `test full complex`() {
    val content = """
    pie %% This is comment
      title Pets adopted by volunteers %% This is not comment
      "Dogs" : 386 %% This is comment
      %% This is comment
    """.trimIndent()
    val expected = listOf(
      Token(Pie.PIE, 0, 3, "pie"),
      Token(WHITE_SPACE, 3, 4, " "),
      Token(LINE_COMMENT, 4, 6, "%%"),
      Token(COMMENT_TEXT, 6, 22, " This is comment"),
      Token(EOL, 22, 23, "\n"),
      Token(WHITE_SPACE, 23, 25, "  "),
      Token(TITLE, 25, 30, "title"),
      Token(WHITE_SPACE, 30, 31, " "),
      Token(TITLE_VALUE, 31, 80, "Pets adopted by volunteers %% This is not comment"),
      Token(EOL, 80, 81, "\n"),
      Token(WHITE_SPACE, 81, 83, "  "),
      Token(DOUBLE_QUOTE, 83, 84, "\""),
      Token(STRING_VALUE, 84, 88, "Dogs"),
      Token(DOUBLE_QUOTE, 88, 89, "\""),
      Token(WHITE_SPACE, 89, 90, " "),
      Token(COLON, 90, 91, ":"),
      Token(WHITE_SPACE, 91, 92, " "),
      Token(Pie.VALUE, 92, 95, "386"),
      Token(WHITE_SPACE, 95, 96, " "),
      Token(LINE_COMMENT, 96, 98, "%%"),
      Token(COMMENT_TEXT, 98, 114, " This is comment"),
      Token(EOL, 114, 115, "\n"),
      Token(WHITE_SPACE, 115, 117, "  "),
      Token(LINE_COMMENT, 117, 119, "%%"),
      Token(COMMENT_TEXT, 119, 135, " This is comment")
    )
    doTest(content, expected)
  }
}
