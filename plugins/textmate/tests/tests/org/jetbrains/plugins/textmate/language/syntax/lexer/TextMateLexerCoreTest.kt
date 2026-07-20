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

  @Test
  fun `capture groups starting beyond the matched range are ignored`() {
    val grammar = """
      {
        "scopeName": "source.test",
        "patterns": [
          {
            "match": "(a)(?=.*(b))",
            "name": "match.a",
            "captures": {
              "1": { "name": "cap.a" },
              "2": { "name": "cap.b" }
            }
          }
        ]
      }
    """.trimIndent()
    assertEquals(listOf(
      "a" to "source.test match.a cap.a",
      "-b" to "source.test"
    ), textmateTokenize("a-b", grammar).toList())
  }

  @Test
  fun `anchor matches at line start when begin consumed the line end`() {
    val grammar = """
      {
        "scopeName": "source.test",
        "patterns": [
          {
            "name": "block.test",
            "begin": "a\\n",
            "end": "b",
            "patterns": [ { "match": "\\Gx", "name": "anchored.x" } ]
          }
        ]
      }
    """.trimIndent()
    assertEquals(listOf(
      "a\n" to "source.test block.test",
      "x" to "source.test block.test anchored.x",
      "b" to "source.test block.test"
    ), textmateTokenize("a\nxb", grammar).toList())
  }
}
