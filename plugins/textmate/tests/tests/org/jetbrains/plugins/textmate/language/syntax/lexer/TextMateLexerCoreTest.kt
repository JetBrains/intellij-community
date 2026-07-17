package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.language.syntax.assertTokenize
import kotlin.test.Test

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
    assertTokenize(grammar, "ax-b", """
      source.test block.test begin.a: [0, 1], {a}
      source.test block.test: [1, 2], {x}
      source.test block.test: [2, 4], {-b}
    """.trimIndent())
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
    assertTokenize(grammar, "a-b", """
      source.test block.test common.char: [0, 1], {a}
      source.test block.test: [1, 2], {-}
      source.test block.test common.char: [2, 3], {b}
    """.trimIndent())
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
    assertTokenize(grammar, "a-b", """
      source.test match.a cap.a: [0, 1], {a}
      source.test: [1, 3], {-b}
    """.trimIndent())
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
    assertTokenize(grammar, "a\nxb",
                   """
                     source.test block.test: [0, 2], {a
                     }
                     source.test block.test anchored.x: [2, 3], {x}
                     source.test block.test: [3, 4], {b}
                   """.trimIndent())
  }

  /**
   * Verifies the `applyEndPatternLast` grammar option of begin/end rules.
   *
   * By default, when the `end` pattern and a nested pattern both match at the same offset,
   * the `end` pattern wins and closes the scope. With `applyEndPatternLast` enabled, the nested
   * patterns are applied first and the `end` pattern is applied only when it matches strictly earlier.
   */
  // the same input is tokenized differently depending on the `applyEndPatternLast` option
  private val applyEndPatternLastInput = "{a}}b}"

  /**
   * The `block` rule is delimited by `{`..`}` and contains a nested rule that matches a double brace `}}`.
   * The nested match and the `end` match both start at the first `}` of `}}`, so the tie-break decides
   * whether the block ends prematurely (default) or the `}}` is consumed as a nested token (applyEndPatternLast).
   */
  private fun applyEndPatternLastGrammar(applyEndPatternLast: Boolean): String {
    val option = if (applyEndPatternLast) "\"applyEndPatternLast\": 1," else ""
    return """
      {
        "scopeName": "source.aeptest",
        "patterns": [{ "include": "#block" }],
        "repository": {
          "block": {
            "name": "meta.block",
            "begin": "\\{",
            "end": "\\}",
            $option
            "patterns": [
              { "name": "constant.double-brace", "match": "\\}\\}" }
            ]
          }
        }
      }
    """.trimIndent()
  }

  @Test
  fun `end pattern wins over nested pattern by default`() {
    // the block ends at the very first `}`, so `}}` is never recognized and the tail stays outside the block
    assertTokenize(applyEndPatternLastGrammar(applyEndPatternLast = false), applyEndPatternLastInput, """
      source.aeptest meta.block: [0, 1], {{}
      source.aeptest meta.block: [1, 3], {a}}
      source.aeptest: [3, 6], {}b}}
    """.trimIndent())
  }

  @Test
  fun `nested pattern wins over end pattern with applyEndPatternLast`() {
    // the nested `}}` is consumed as constant.double-brace, and the block is only closed by the trailing `}`
    assertTokenize(applyEndPatternLastGrammar(applyEndPatternLast = true), applyEndPatternLastInput, """
      source.aeptest meta.block: [0, 1], {{}
      source.aeptest meta.block: [1, 2], {a}
      source.aeptest meta.block constant.double-brace: [2, 4], {}}}
      source.aeptest meta.block: [4, 6], {b}}
    """.trimIndent())
  }

  @Test
  fun `failed while condition pops all nested rules and their scopes`() {
    val grammar = """
      {
        "scopeName": "source.test",
        "patterns": [
          {
            "name": "outer.block",
            "begin": "^A",
            "while": "^a",
            "patterns": [
              {
                "name": "inner.block",
                "begin": "B",
                "while": "b"
              }
            ]
          }
        ]
      }
    """.trimIndent()
    // on the second line the outer while fails while the inner one would match;
    // both rules must be popped and both scopes closed
    assertTokenize(grammar, "AB\nbx\nx\n", """
      source.test outer.block: [0, 1], {A}
      source.test outer.block inner.block: [1, 2], {B}
      source.test outer.block inner.block: [2, 3], {
      }
      source.test: [3, 6], {bx
      }
      source.test: [6, 8], {x
      }
    """.trimIndent())
  }
}
