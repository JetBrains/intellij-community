package com.github.firsttimeinforever.mermaid.lang.lexer

import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COLON
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.DOUBLE_QUOTE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.EOL
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Pie
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.STRING_VALUE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.TITLE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.TITLE_VALUE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.WHITE_SPACE

class PieTest: MermaidTestCase() {
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
    pie
      title Pets adopted by volunteers
      "Dogs" : 386
      "Cats" : 85
      "Rats" : 15
    """.trimIndent()
    val expected = listOf(
      Token(Pie.PIE, 0, 3, "pie"),
      Token(EOL, 3, 4, "\n"),
      Token(WHITE_SPACE, 4, 6, "  "),
      Token(TITLE, 6, 11, "title"),
      Token(WHITE_SPACE, 11, 12, " "),
      Token(TITLE_VALUE, 12, 38, "Pets adopted by volunteers"),
      Token(EOL, 38, 39, "\n"),
      Token(WHITE_SPACE, 39, 41, "  "),
      Token(DOUBLE_QUOTE, 41, 42, "\""),
      Token(STRING_VALUE, 42, 46, "Dogs"),
      Token(DOUBLE_QUOTE, 46, 47, "\""),
      Token(WHITE_SPACE, 47, 48, " "),
      Token(COLON, 48, 49, ":"),
      Token(WHITE_SPACE, 49, 50, " "),
      Token(Pie.VALUE, 50, 53, "386"),
      Token(EOL, 53, 54, "\n"),
      Token(WHITE_SPACE, 54, 56, "  "),
      Token(DOUBLE_QUOTE, 56, 57, "\""),
      Token(STRING_VALUE, 57, 61, "Cats"),
      Token(DOUBLE_QUOTE, 61, 62, "\""),
      Token(WHITE_SPACE, 62, 63, " "),
      Token(COLON, 63, 64, ":"),
      Token(WHITE_SPACE, 64, 65, " "),
      Token(Pie.VALUE, 65, 67, "85"),
      Token(EOL, 67, 68, "\n"),
      Token(WHITE_SPACE, 68, 70, "  "),
      Token(DOUBLE_QUOTE, 70, 71, "\""),
      Token(STRING_VALUE, 71, 75, "Rats"),
      Token(DOUBLE_QUOTE, 75, 76, "\""),
      Token(WHITE_SPACE, 76, 77, " "),
      Token(COLON, 77, 78, ":"),
      Token(WHITE_SPACE, 78, 79, " "),
      Token(Pie.VALUE, 79, 81, "15")
    )
    doTest(content, expected)
  }
}
