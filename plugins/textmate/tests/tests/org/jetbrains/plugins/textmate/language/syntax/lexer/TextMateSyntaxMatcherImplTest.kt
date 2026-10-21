package org.jetbrains.plugins.textmate.language.syntax.lexer

import com.intellij.textmate.joni.JoniRegexFactory
import org.jetbrains.plugins.textmate.language.TextMateConcurrentMapInterner
import org.jetbrains.plugins.textmate.language.syntax.SyntaxNodeDescriptor
import org.jetbrains.plugins.textmate.language.syntax.TextMateSyntaxTableBuilder
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherImpl
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateWeigh
import org.jetbrains.plugins.textmate.language.syntax.selector.caching
import org.jetbrains.plugins.textmate.plist.JsonPlistReader
import org.jetbrains.plugins.textmate.regex.CaffeineCachingRegexProvider
import org.jetbrains.plugins.textmate.regex.RememberingLastMatchRegexFactory
import org.jetbrains.plugins.textmate.regex.byteOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TextMateSyntaxMatcherImplTest {
  private val grammar = """
    {
      "scopeName": "source.test",
      "patterns": [
        { "match": "a", "name": "match.a" },
        { "match": "b", "name": "match.b" }
      ]
    }
  """.trimIndent()

  @Test
  fun `a rule whose children all fail gives back the not-matched state of the rule`() {
    matchRoot(text = "zzz") { rootNode, state ->
      assertFalse(state.matchData.matched)
      // `parseLine` reads the rule off this state, so it must be the rule that was scanned
      assertSame(rootNode, state.syntaxRule)
      assertEquals(TextMateLexerState.notMatched(rootNode), state)
    }
  }

  @Test
  fun `a rule whose child matches gives back the state of that child`() {
    matchRoot(text = "-b-") { rootNode, state ->
      assertTrue(state.matchData.matched)
      assertEquals(1, state.matchData.byteRange().start.offset)
      assertEquals("Syntax rule: match.b", state.syntaxRule.toString())
      assertNotSame(rootNode, state.syntaxRule)
    }
  }

  private fun matchRoot(text: String, check: (SyntaxNodeDescriptor, TextMateLexerState) -> Unit) {
    val builder = TextMateSyntaxTableBuilder(TextMateConcurrentMapInterner())
    val rootScope = builder.addSyntax(JsonPlistReader().read(grammar.encodeToByteArray()))
                    ?: error("scopeName is missing in the grammar")
    val rootNode = builder.build().getLanguageDescriptor(rootScope).rootSyntaxNode

    val regexProvider = CaffeineCachingRegexProvider(RememberingLastMatchRegexFactory(JoniRegexFactory()))
    TextMateSelectorWeigherImpl().caching().use { weigher ->
      val matcher = TextMateSyntaxMatcherImpl(regexProvider, weigher)
      matcher.matchingString(text) { string ->
        val state = matcher.matchRule(syntaxNodeDescriptor = rootNode,
                                      string = string,
                                      byteOffset = 0.byteOffset(),
                                      matchBeginPosition = true,
                                      matchBeginString = true,
                                      priority = TextMateWeigh.Priority.NORMAL,
                                      currentScope = TextMateScope.EMPTY,
                                      injections = emptyList(),
                                      checkCancelledCallback = null)
        check(rootNode, state)
      }
    }
  }
}
