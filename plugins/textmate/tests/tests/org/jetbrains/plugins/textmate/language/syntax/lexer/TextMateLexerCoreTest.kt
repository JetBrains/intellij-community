package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.language.syntax.textmateTokenize
import kotlin.test.Test
import kotlin.test.assertEquals

class TextMateLexerCoreTest {
  @Test
  fun `captures are ignored on begin when beginCaptures are present`() {
    val grammar = """
      {
        "scopeName": "source.test",
        "patterns": [
          {
            "name": "block.test",
            "begin": "(a)(x)",
            "end": "(b)",
            "beginCaptures": { "1": { "name": "begin.a" } },
            "captures": { "2": { "name": "common.x" } }
          }
        ]
      }
    """.trimIndent()
    assertEquals(listOf(
      "a" to "source.test block.test begin.a",
      "x" to "source.test block.test",
      "-b" to "source.test block.test"
    ), textmateTokenize("ax-b", grammar).toList())
  }

  @Test
  fun `captures are applied on begin and end when no beginCaptures and endCaptures are present`() {
    val grammar = """
      {
        "scopeName": "source.test",
        "patterns": [
          {
            "name": "block.test",
            "begin": "(a)",
            "end": "(b)",
            "captures": { "1": { "name": "common.char" } }
          }
        ]
      }
    """.trimIndent()
    assertEquals(listOf(
      "a" to "source.test block.test common.char",
      "-" to "source.test block.test",
      "b" to "source.test block.test common.char"
    ), textmateTokenize("a-b", grammar).toList())
  }
}
