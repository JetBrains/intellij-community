package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.language.syntax.textmateTokenize
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies the `applyEndPatternLast` grammar option of begin/end rules.
 *
 * By default, when the `end` pattern and a nested pattern both match at the same offset,
 * the `end` pattern wins and closes the scope. With `applyEndPatternLast` enabled, the nested
 * patterns are applied first and the `end` pattern is applied only when it matches strictly earlier.
 *
 * See the vscode-textmate reference implementation for the same behavior.
 */
class ApplyEndPatternLastLexerTest {
  // the same input is tokenized differently depending on the `applyEndPatternLast` option
  private val input = "{a}}b}"

  /**
   * The `block` rule is delimited by `{`..`}` and contains a nested rule that matches a double brace `}}`.
   * The nested match and the `end` match both start at the first `}` of `}}`, so the tie-break decides
   * whether the block ends prematurely (default) or the `}}` is consumed as a nested token (applyEndPatternLast).
   */
  private fun grammar(applyEndPatternLast: Boolean): String {
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
    assertEquals(
      listOf(
        "{" to "source.aeptest meta.block",
        "a}" to "source.aeptest meta.block",
        "}b}" to "source.aeptest",
      ),
      textmateTokenize(input, grammar(applyEndPatternLast = false)).toList(),
    )
  }

  @Test
  fun `nested pattern wins over end pattern with applyEndPatternLast`() {
    // the nested `}}` is consumed as constant.double-brace, and the block is only closed by the trailing `}`
    assertEquals(
      listOf(
        "{" to "source.aeptest meta.block",
        "a" to "source.aeptest meta.block",
        "}}" to "source.aeptest meta.block constant.double-brace",
        "b}" to "source.aeptest meta.block",
      ),
      textmateTokenize(input, grammar(applyEndPatternLast = true)).toList(),
    )
  }

}
