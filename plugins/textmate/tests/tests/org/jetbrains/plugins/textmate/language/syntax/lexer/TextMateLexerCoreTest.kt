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

  @Test
  fun `injections match inside nested include containers`() {
    // injections are matched once per scan at the top level; the result must not depend
    // on how deeply the regular rules are nested in include containers
    val grammar = """
      {
        "scopeName": "source.test",
        "injections": {
          "L:source.test": { "patterns": [ { "match": "i", "name": "inj.i" } ] }
        },
        "patterns": [ { "include": "#container" } ],
        "repository": {
          "container": { "patterns": [ { "match": "a", "name": "m.a" } ] }
        }
      }
    """.trimIndent()
    assertTokenize(grammar, "ai", """
      source.test m.a: [0, 1], {a}
      source.test inj.i: [1, 2], {i}
    """.trimIndent())
  }

  @Test
  fun `while captures are emitted and the position advances past the while match`() {
    val grammar = """
      {
        "scopeName": "source.test",
        "patterns": [
          {
            "name": "quote.block",
            "begin": "(>) ",
            "beginCaptures": { "1": { "name": "punct.quote" } },
            "while": "(>) ",
            "whileCaptures": { "1": { "name": "punct.quote.continued" } },
            "patterns": [ { "match": "x+", "name": "content.x" } ]
          }
        ]
      }
    """.trimIndent()
    assertTokenize(grammar, "> x\n> x\n", """
      source.test quote.block punct.quote: [0, 1], {>}
      source.test quote.block: [1, 2], { }
      source.test quote.block content.x: [2, 3], {x}
      source.test quote.block: [3, 4], {
      }
      source.test quote.block punct.quote.continued: [4, 5], {>}
      source.test quote.block: [5, 6], { }
      source.test quote.block content.x: [6, 7], {x}
      source.test quote.block: [7, 8], {
      }
    """.trimIndent())
  }

  @Test
  fun `nested rules do not match inside the prefix consumed by a while condition`() {
    // after a while-condition matches, the scan continues past its match,
    // so the nested patterns never see the consumed prefix
    val grammar = """
      {
        "scopeName": "source.test",
        "patterns": [
          {
            "name": "quote.block",
            "begin": "> ",
            "while": "> ",
            "patterns": [
              { "match": "> ", "name": "bad.requote" },
              { "match": "x", "name": "content.x" }
            ]
          }
        ]
      }
    """.trimIndent()
    assertTokenize(grammar, "> x\n> x\n", """
      source.test quote.block: [0, 2], {> }
      source.test quote.block content.x: [2, 3], {x}
      source.test quote.block: [3, 4], {
      }
      source.test quote.block: [4, 6], {> }
      source.test quote.block content.x: [6, 7], {x}
      source.test quote.block: [7, 8], {
      }
    """.trimIndent())
  }

  @Test
  fun `while captures of an outer rule are emitted without the scopes of nested rules`() {
    val grammar = """
      {
        "scopeName": "source.test",
        "patterns": [
          {
            "name": "outer.block",
            "begin": "(A)",
            "while": "(a)",
            "whileCaptures": { "1": { "name": "wc.outer" } },
            "patterns": [
              {
                "name": "inner.block",
                "begin": "(B)",
                "while": "(b)",
                "whileCaptures": { "1": { "name": "wc.inner" } }
              }
            ]
          }
        ]
      }
    """.trimIndent()
    assertTokenize(grammar, "AB\nab\n", """
      source.test outer.block: [0, 1], {A}
      source.test outer.block inner.block: [1, 2], {B}
      source.test outer.block inner.block: [2, 3], {
      }
      source.test outer.block wc.outer: [3, 4], {a}
      source.test outer.block inner.block wc.inner: [4, 5], {b}
      source.test outer.block inner.block: [5, 6], {
      }
    """.trimIndent())
  }

  @Test
  fun `rules opened during capture retokenization do not leak their scopes`() {
    // a rule that begins inside a capture with nested patterns and does not end within it
    // must not affect the tokens after the capture
    val grammar = """
      {
        "scopeName": "source.test",
        "patterns": [
          {
            "match": "(\\[\\w+)",
            "name": "m.bracket",
            "captures": {
              "1": {
                "patterns": [ { "name": "inner.block", "begin": "\\[", "end": "\\]" } ]
              }
            }
          },
          { "match": "z", "name": "m.z" }
        ]
      }
    """.trimIndent()
    assertTokenize(grammar, "[a.z\n", """
      source.test m.bracket inner.block: [0, 1], {[}
      source.test m.bracket inner.block: [1, 2], {a}
      source.test: [2, 3], {.}
      source.test m.z: [3, 4], {z}
      source.test: [4, 5], {
      }
    """.trimIndent())
  }

  @Test
  fun `popping a rule restores the anchor that was in effect when the rule was pushed`() {
    val grammar = """
      {
        "scopeName": "source.test",
        "patterns": [
          { "name": "outer", "begin": "a", "end": "z",
            "patterns": [
              { "name": "q", "begin": "\\G\\[", "end": "(?=x)" },
              { "match": "\\Gx", "name": "anchored.x" },
              { "match": "x", "name": "plain.x" }
            ]
          }
        ]
      }
    """.trimIndent()
    assertTokenize(grammar, "a[xz", """
      source.test outer: [0, 1], {a}
      source.test outer q: [1, 2], {[}
      source.test outer plain.x: [2, 3], {x}
      source.test outer: [3, 4], {z}
    """.trimIndent())
  }

  @Test
  fun `a plain match rule does not move the anchor`() {
    // \G is anchored at the end of the enclosing rule's begin match;
    // a plain match rule must not move it (only begin matches do)
    val grammar = """
      {
        "scopeName": "source.test",
        "patterns": [
          {
            "name": "outer",
            "begin": "a",
            "end": "z",
            "patterns": [
              { "match": "x", "name": "plain.x" },
              { "match": "\\Gy", "name": "anchored.y" }
            ]
          }
        ]
      }
    """.trimIndent()
    // directly after the begin match, \G matches
    assertTokenize(grammar, "ayz", """
      source.test outer: [0, 1], {a}
      source.test outer anchored.y: [1, 2], {y}
      source.test outer: [2, 3], {z}
    """.trimIndent())
    // after an intervening match rule, \G no longer matches
    assertTokenize(grammar, "axyz", """
      source.test outer: [0, 1], {a}
      source.test outer plain.x: [1, 2], {x}
      source.test outer: [2, 4], {yz}
    """.trimIndent())
  }

  @Test
  fun `left injection wins the tie against the end pattern`() {
    // "L:" injection matching at the same offset as the end pattern should
    // win over it, so the rule is not closed while the injection consumes its end characters
    val grammar = """
      {
        "scopeName": "source.test",
        "injections": {
          "L:source.test": { "patterns": [ { "match": "b", "name": "inj.b" } ] }
        },
        "patterns": [
          { "name": "block", "begin": "a", "end": "b" }
        ]
      }
    """.trimIndent()
    assertTokenize(grammar, "abb\n", """
      source.test block: [0, 1], {a}
      source.test block inj.b: [1, 2], {b}
      source.test block inj.b: [2, 3], {b}
      source.test block: [3, 4], {
      }
    """.trimIndent())
  }

  @Test
  fun `regular injection loses the tie against the end pattern`() {
    val grammar = """
      {
        "scopeName": "source.test",
        "injections": {
          "source.test": { "patterns": [ { "match": "b", "name": "inj.b" } ] }
        },
        "patterns": [
          { "name": "block", "begin": "a", "end": "b" }
        ]
      }
    """.trimIndent()
    assertTokenize(grammar, "abb\n", """
      source.test block: [0, 1], {a}
      source.test block: [1, 2], {b}
      source.test inj.b: [2, 3], {b}
      source.test: [3, 4], {
      }
    """.trimIndent())
  }

  @Test
  fun `a rule pushed and popped without advancing stays on the stack`() {
    // when a rule is pushed and immediately popped without advancing,
    // the grammar is assumed to have meant to stay in that state: the rest
    // of the line and the following lines keep the rule's scope
    val grammar = """
      {
        "scopeName": "source.test",
        "patterns": [
          { "name": "loop.r", "begin": "(?=x)", "end": "(?=x)" }
        ]
      }
    """.trimIndent()
    assertTokenize(grammar, "x\nx", """
      source.test loop.r: [0, 2], {x
      }
      source.test loop.r: [2, 3], {x}
    """.trimIndent())
  }

  @Test
  fun `begin-string anchor stops matching once tokenization advances`() {
    val grammar = """
      {
        "scopeName": "source.test",
        "patterns": [
          { "match": "x", "name": "m.x" },
          { "match": "(?<=\\Ax)y", "name": "anchored.y" },
          { "match": "y", "name": "plain.y" }
        ]
      }
    """.trimIndent()
    assertTokenize(grammar, "xy\nxy\n", """
      source.test m.x: [0, 1], {x}
      source.test plain.y: [1, 2], {y}
      source.test: [2, 3], {
      }
      source.test m.x: [3, 4], {x}
      source.test plain.y: [4, 5], {y}
      source.test: [5, 6], {
      }
    """.trimIndent())
  }

}
