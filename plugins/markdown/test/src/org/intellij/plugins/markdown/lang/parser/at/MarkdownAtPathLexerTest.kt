// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.parser.at

import org.assertj.core.api.Assertions.assertThat
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.lexer.GeneratedLexer
import org.junit.jupiter.api.Test

class MarkdownAtPathLexerTest {
  @Test
  fun `splits at path from text`() {
    val text = "before @path/file.md after"
    val lexer = createLexer(text)

    assertThat(lexer.tokens(text)).containsExactly(
      Token("before", 0, 6),
      Token(" ", 6, 7),
      Token("@path/file.md", 7, 20, true),
      Token(" after", 20, 26),
    )
  }

  @Test
  fun `splits multiple paths and preserves ranges`() {
    val text = "@one and @two/file.md, done"
    val lexer = createLexer(text)

    assertThat(lexer.tokens(text)).containsExactly(
      Token("@one", 0, 4, true),
      Token(" and", 4, 8),
      Token(" ", 8, 9),
      Token("@two/file.md", 9, 21, true),
      Token(",", 21, 22),
      Token(" ", 22, 23),
      Token("done", 23, 27),
    )
  }

  @Test
  fun `does not split at path inside word or link label`() {
    val text = "email@example.com [@label]"
    val lexer = createLexer(text)

    assertThat(lexer.tokens(text)).noneMatch(Token::isPath)
  }

  @Test
  fun `path ends before unsupported punctuation`() {
    val text = "@dir/file.md:next"
    val lexer = createLexer(text)

    assertThat(lexer.tokens(text)).containsExactly(
      Token("@dir/file.md", 0, 12, true),
      Token(":", 12, 13),
      Token("next", 13, 17),
    )
  }

  @Test
  fun `state is non-zero inside at path`() {
    val lexer = createLexer("before @path/file.md after")

    assertThat(lexer.advance()).isNotNull
    assertThat(lexer.state).isZero()
    assertThat(lexer.advance()).isNotNull
    assertThat(lexer.state).isZero()
    assertThat(lexer.advance()).isEqualTo(MarkdownAtPathElementTypes.PATH_TOKEN)
    assertThat(lexer.state).isNotZero()
    assertThat(lexer.advance()).isNotNull
    assertThat(lexer.state).isZero()
  }

  private fun createLexer(text: String): MarkdownAtPathLexer {
    return MarkdownAtPathLexer(GFMFlavourDescriptor().createInlinesLexer()).also {
      it.reset(text, 0, text.length, 0)
    }
  }

  private fun GeneratedLexer.tokens(text: String): List<Token> = buildList {
    var type = advance()
    while (type != null) {
      add(Token(text.substring(tokenStart, tokenEnd), tokenStart, tokenEnd, type == MarkdownAtPathElementTypes.PATH_TOKEN))
      type = advance()
    }
  }

  private data class Token(val text: String, val start: Int, val end: Int, val isPath: Boolean = false)
}
