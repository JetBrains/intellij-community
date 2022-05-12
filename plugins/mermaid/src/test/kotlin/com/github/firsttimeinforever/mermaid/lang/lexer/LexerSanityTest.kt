package com.github.firsttimeinforever.mermaid.lang.lexer

import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.CLOSE_DIRECTIVE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COLON
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COMMA
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COMMENT_TEXT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.DIRECTIVE_TEXT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.DOUBLE_QUOTE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.EOL
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.LINE_COMMENT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.OPEN_DIRECTIVE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.STRING_VALUE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.WHITE_SPACE

class LexerSanityTest: MermaidLexerTestCase() {
  fun `test line comment`() {
    val content = """
    %% This is comment
    """.trimIndent()
    val expected = listOf(
      Token(LINE_COMMENT, 0, 2, "%%"),
      Token(COMMENT_TEXT, 2, 18, " This is comment")
    )
    doTest(content, expected)
  }

  fun `test line comment not eating next newline`() {
    val content = """
    %% This is comment
    
    """.trimIndent()
    val expected = listOf(
      Token(LINE_COMMENT, 0, 2, "%%"),
      Token(COMMENT_TEXT, 2, 18, " This is comment"),
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
}
