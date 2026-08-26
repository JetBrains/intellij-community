// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.highlighting

import com.intellij.lexer.Lexer
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MarkdownHighlightingLexerTest : BasePlatformTestCase() {
  fun `test restart at Markdown block boundaries`() {
    val text = """
      # Header

      Paragraph with *emphasis* and [a link](https://example.com).

      > A quote
      > continued

      - first item
      - second item

      | column | value |
      | --- | --- |
      | one | two |

      ```kotlin
      val answer = 42
      ```
    """.trimIndent()
    val allTokens = tokenize(text, 0, 0)
    val lexer = createLexer()

    lexer.start(text)
    var index = 0
    while (lexer.tokenType != null) {
      if (lexer.state == 0) {
        assertEquals(allTokens.subList(index, allTokens.size), tokenize(text, lexer.tokenStart, lexer.state))
      }
      index++
      lexer.advance()
    }

    for (blockStart in listOf(
      text.indexOf("# Header"),
      text.indexOf("Paragraph with"),
      text.indexOf("> A quote"),
      text.indexOf("- first item"),
      text.indexOf("| column"),
      text.indexOf("```kotlin"),
    )) {
      assertTrue(allTokens.any { it.state == 0 && (it.start == blockStart || it.end == blockStart) })
    }
  }

  private fun createLexer(): Lexer = MarkdownHighlightingLexer(null)

  private fun tokenize(text: String, start: Int, state: Int): List<Token> {
    val lexer = createLexer()
    lexer.start(text, start, text.length, state)
    val tokens = mutableListOf<Token>()
    while (lexer.tokenType != null) {
      tokens.add(Token(lexer.tokenStart, lexer.tokenEnd, lexer.state))
      lexer.advance()
    }
    return tokens
  }

  private data class Token(val start: Int, val end: Int, val state: Int)
}
