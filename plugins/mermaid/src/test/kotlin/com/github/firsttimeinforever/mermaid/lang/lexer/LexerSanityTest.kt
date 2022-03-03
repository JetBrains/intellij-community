package com.github.firsttimeinforever.mermaid.lang.lexer

import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.CLOSE_DIRECTIVE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COLON
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COMMA
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COMMENT_TEXT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.DIRECTIVE_TEXT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.DOUBLE_QUOTE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.EOL
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.LINE_COMMENT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.OPEN_DIRECTIVE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Pie
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.STRING_VALUE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.WHITE_SPACE

class LexerSanityTest: LightPlatformCodeInsightTestCase() {
  fun `test title only single word`() {
    val content = """
    title Pets
    """.trimIndent()
    val expected = listOf(
      Token(Pie.TITLE, 0, 5, "title"),
      Token(WHITE_SPACE, 5, 6, " "),
      Token(Pie.TITLE_VALUE, 6, 10, "Pets"),
    )
    doTest(content, expected)
  }

  fun `test title multiple words`() {
    val content = """
    title Pets will be available
    """.trimIndent()
    val expected = listOf(
      Token(Pie.TITLE, 0, 5, "title"),
      Token(WHITE_SPACE, 5, 6, " "),
      Token(Pie.TITLE_VALUE, 6, 28, "Pets will be available"),
    )
    doTest(content, expected)
  }

  fun `test title with newline at the end`() {
    val content = """
    title Pets
    
    """.trimIndent()
    val expected = listOf(
      Token(Pie.TITLE, 0, 5, "title"),
      Token(WHITE_SPACE, 5, 6, " "),
      Token(Pie.TITLE_VALUE, 6, 10, "Pets"),
      Token(EOL, 10, 11, "\n"),
    )
    doTest(content, expected)
  }

  fun `test pie with title`() {
    val content = """
    pie
      title Pets will be available
    """.trimIndent()
    val expected = listOf(
      Token(Pie.PIE, 0, 3, "pie"),
      Token(EOL, 3, 4, "\n"),
      Token(WHITE_SPACE, 4, 6, "  "),
      Token(Pie.TITLE, 6, 11, "title"),
      Token(WHITE_SPACE, 11, 12, " "),
      Token(Pie.TITLE_VALUE, 12, 34, "Pets will be available"),
    )
    doTest(content, expected)
  }

  fun `test line comment`() {
    val content = """
    %% This is comment
    """.trimIndent()
    val expected = listOf(
      Token(LINE_COMMENT, 0, 2, "%%"),
      Token(WHITE_SPACE, 2, 3, " "),
      Token(COMMENT_TEXT, 3, 18, "This is comment")
    )
    doTest(content, expected)
  }

  fun `test line comment not eating next newline`() {
    val content = """
    %% This is comment
    
    """.trimIndent()
    val expected = listOf(
      Token(LINE_COMMENT, 0, 2, "%%"),
      Token(WHITE_SPACE, 2, 3, " "),
      Token(COMMENT_TEXT, 3, 18, "This is comment"),
      Token(EOL, 18, 19, "\n")
    )
    doTest(content, expected)
  }

  fun `test empty directive`() {
    val content = """
    %%{}%%
    """.trimIndent()
    val expected = listOf(
      Token(OPEN_DIRECTIVE, 0, 3, "%%{"),
      Token(CLOSE_DIRECTIVE, 3, 6, "}%%")
    )
    doTest(content, expected)
  }

  fun `test empty directive with whitespaces`() {
    val content = """
    %%{    }%%
    """.trimIndent()
    val expected = listOf(
      Token(OPEN_DIRECTIVE, 0, 3, "%%{"),
      Token(WHITE_SPACE, 3, 7, "    "),
      Token(CLOSE_DIRECTIVE, 7, 10, "}%%")
    )
    doTest(content, expected)
  }

  fun `test directive with single simple numeric property`() {
    val content = """
    %%{ some: 42 }%%
    """.trimIndent()
    val expected = listOf(
      Token(OPEN_DIRECTIVE, 0, 3, "%%{"),
      Token(WHITE_SPACE, 3, 4, " "),
      Token(DIRECTIVE_TEXT, 4, 8, "some"),
      Token(COLON, 8, 9, ":"),
      Token(WHITE_SPACE, 9, 10, " "),
      Token(DIRECTIVE_TEXT, 10, 12, "42"),
      Token(WHITE_SPACE, 12, 13, " "),
      Token(CLOSE_DIRECTIVE, 13, 16, "}%%")
    )
    doTest(content, expected)
  }

  fun `test directive with single simple quoted property`() {
    val content = """
    %%{ some: "42" }%%
    """.trimIndent()
    val expected = listOf(
      Token(OPEN_DIRECTIVE, 0, 3, "%%{"),
      Token(WHITE_SPACE, 3, 4, " "),
      Token(DIRECTIVE_TEXT, 4, 8, "some"),
      Token(COLON, 8, 9, ":"),
      Token(WHITE_SPACE, 9, 10, " "),
      Token(DOUBLE_QUOTE, 10, 11, "\""),
      Token(STRING_VALUE, 11, 13, "42"),
      Token(DOUBLE_QUOTE, 13, 14, "\""),
      Token(WHITE_SPACE, 14, 15, " "),
      Token(CLOSE_DIRECTIVE, 15, 18, "}%%")
    )
    doTest(content, expected)
  }

  fun `test directive with multiple simple properties`() {
    val content = """
    %%{ some: "42", other: 42, more: "value" }%%
    """.trimIndent()
    val expected = listOf(
      Token(OPEN_DIRECTIVE, 0, 3, "%%{"),
      Token(WHITE_SPACE, 3, 4, " "),
      Token(DIRECTIVE_TEXT, 4, 8, "some"),
      Token(COLON, 8, 9, ":"),
      Token(WHITE_SPACE, 9, 10, " "),
      Token(DOUBLE_QUOTE, 10, 11, "\""),
      Token(STRING_VALUE, 11, 13, "42"),
      Token(DOUBLE_QUOTE, 13, 14, "\""),
      Token(COMMA, 14, 15, ","),
      Token(WHITE_SPACE, 15, 16, " "),
      Token(DIRECTIVE_TEXT, 16, 21, "other"),
      Token(COLON, 21, 22, ":"),
      Token(WHITE_SPACE, 22, 23, " "),
      Token(DIRECTIVE_TEXT, 23, 25, "42"),
      Token(COMMA, 25, 26, ","),
      Token(WHITE_SPACE, 26, 27, " "),
      Token(DIRECTIVE_TEXT, 27, 31, "more"),
      Token(COLON, 31, 32, ":"),
      Token(WHITE_SPACE, 32, 33, " "),
      Token(DOUBLE_QUOTE, 33, 34, "\""),
      Token(STRING_VALUE, 34, 39, "value"),
      Token(DOUBLE_QUOTE, 39, 40, "\""),
      Token(WHITE_SPACE, 40, 41, " "),
      Token(CLOSE_DIRECTIVE, 41, 44, "}%%")
    )
    doTest(content, expected)
  }

  fun `test directive with single simple property and whitespaces and newlines`() {
    val content = """
    %%{   some
      
      
      :
       
       
       42
         
         
         }%%
    """.trimIndent()
    val expected = listOf(
      Token(OPEN_DIRECTIVE, 0, 3, "%%{"),
      Token(WHITE_SPACE, 3, 6, "   "),
      Token(DIRECTIVE_TEXT, 6, 10, "some"),
      Token(EOL, 10, 11, "\n"),
      Token(WHITE_SPACE, 11, 13, "  "),
      Token(EOL, 13, 14, "\n"),
      Token(WHITE_SPACE, 14, 16, "  "),
      Token(EOL, 16, 17, "\n"),
      Token(WHITE_SPACE, 17, 19, "  "),
      Token(COLON, 19, 20, ":"),
      Token(EOL, 20, 21, "\n"),
      Token(WHITE_SPACE, 21, 24, "   "),
      Token(EOL, 24, 25, "\n"),
      Token(WHITE_SPACE, 25, 28, "   "),
      Token(EOL, 28, 29, "\n"),
      Token(WHITE_SPACE, 29, 32, "   "),
      Token(DIRECTIVE_TEXT, 32, 34, "42"),
      Token(EOL, 34, 35, "\n"),
      Token(WHITE_SPACE, 35, 40, "     "),
      Token(EOL, 40, 41, "\n"),
      Token(WHITE_SPACE, 41, 46, "     "),
      Token(EOL, 46, 47, "\n"),
      Token(WHITE_SPACE, 47, 52, "     "),
      Token(CLOSE_DIRECTIVE, 52, 55, "}%%")
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
      Token(Pie.TITLE, 6, 11, "title"),
      Token(WHITE_SPACE, 11, 12, " "),
      Token(Pie.TITLE_VALUE, 12, 38, "Pets adopted by volunteers"),
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
      Token(Pie.TITLE, 6, 11, "title"),
      Token(WHITE_SPACE, 11, 12, " "),
      Token(Pie.TITLE_VALUE, 12, 38, "Pets adopted by volunteers"),
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

  data class Token(
    val type: IElementType,
    val start: Int,
    val end: Int,
    val text: String
  ) {
    override fun toString(): String {
      return when (type) {
        EOL -> "Token($type, $start, $end, \"\\n\")"
        DOUBLE_QUOTE -> "Token($type, $start, $end, \"\\\"\")"
        else -> "Token($type, $start, $end, \"$text\")"
      }
    }
  }

  private fun doTest(content: String, expectedTokens: List<Token>) {
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
