package org.jetbrains.plugins.textmate.language.syntax

import com.intellij.lexer.Lexer
import com.intellij.lexer.RestartableLexer
import com.intellij.psi.tree.IElementType
import com.intellij.textmate.joni.JoniRegexFactory
import org.jetbrains.plugins.textmate.language.TextMateConcurrentMapInterner
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateLexerCore
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateSyntaxMatcherImpl
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherImpl
import org.jetbrains.plugins.textmate.language.syntax.selector.caching
import org.jetbrains.plugins.textmate.plist.JsonPlistReader
import org.jetbrains.plugins.textmate.regex.CaffeineCachingRegexProvider
import org.jetbrains.plugins.textmate.regex.RememberingLastMatchRegexFactory
import kotlin.test.assertEquals

internal fun textmateTokenize(text: String, grammar: String): Sequence<Pair<String, String>> {
  val syntaxTableBuilder = TextMateSyntaxTableBuilder(TextMateConcurrentMapInterner())
  val plist = JsonPlistReader().read(grammar.encodeToByteArray())
  val rootScope = syntaxTableBuilder.addSyntax(plist) ?: error("scopeName is missing in the grammar")
  val syntaxTable = syntaxTableBuilder.build()
  val languageDescriptor = syntaxTable.getLanguageDescriptor(rootScope)

  val regexProvider = CaffeineCachingRegexProvider(RememberingLastMatchRegexFactory(JoniRegexFactory()))
  return TextMateSelectorWeigherImpl().caching().use { weigher ->
    val syntaxMatcher = TextMateSyntaxMatcherImpl(regexProvider, weigher)
    val lexer = TextMateLexerCore(languageDescriptor, syntaxMatcher, myLineLimit = -1, myStripWhitespaces = false)
    lexer.init(text, 0)

    sequence {
      var lastOffset = -1
      while (lexer.getCurrentOffset() < text.length && lexer.getCurrentOffset() != lastOffset) {
        lastOffset = lexer.getCurrentOffset()
        yieldAll(lexer.advanceLine(null))
      }
    }.map { token ->
      text.substring(token.startOffset, token.endOffset) to token.scope.toString()
    }
  }
}

/**
 * Tokenizes [text] with [grammar] and asserts that the tokens rendered in the same textual form as
 * [org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase.doTest]
 * (one line per token as `scope: [start, end], {tokenText}`) equal [expectedResult].
 */
internal fun assertTokenize(grammar: String, text: String, expectedResult: String) {
  var offset = 0
  val actual = textmateTokenize(text, grammar).joinToString(separator = "\n") { (tokenText, scope) ->
    val start = offset
    val end = start + tokenText.length
    offset = end
    "$scope: [$start, $end], {$tokenText}"
  }
  assertEquals(expectedResult, actual)
}

/**
 * Restarts [lexer] at every restartable state and checks that it makes the same tokens as a full run.
 *
 * Pass one lexer instance, as in production: a state is an id in the table of the lexer that made it.
 * The lexer reports a state at each line start, so a wrong or an over-merged continuation shows up within
 * the first tokens after the restart. Only [TOKENS_TO_COMPARE] tokens are compared, and only [MAX_RESTARTS]
 * restart points are sampled, to keep the check linear on the large test files.
 */
internal fun assertRestartMakesTheSameTokens(lexer: Lexer, text: String) {
  val tokenTypes = ArrayList<IElementType>()
  val tokenStarts = ArrayList<Int>()
  val tokenEnds = ArrayList<Int>()
  val restartIndices = ArrayList<Int>()
  val restartStates = ArrayList<Int>()

  lexer.start(text)
  while (lexer.tokenType != null) {
    if (lexer is RestartableLexer && lexer.isRestartableState(lexer.state)) {
      restartIndices.add(tokenTypes.size)
      restartStates.add(lexer.state)
    }
    tokenTypes.add(lexer.tokenType!!)
    tokenStarts.add(lexer.tokenStart)
    tokenEnds.add(lexer.tokenEnd)
    lexer.advance()
  }

  val step = maxOf(1, restartIndices.size / MAX_RESTARTS)
  var i = 0
  while (i < restartIndices.size) {
    val tokenIndex = restartIndices[i]
    val startOffset = tokenStarts[tokenIndex]
    lexer.start(text, startOffset, text.length, restartStates[i])
    var expectedIndex = tokenIndex
    val lastIndex = minOf(tokenTypes.size, tokenIndex + TOKENS_TO_COMPARE)
    while (expectedIndex < lastIndex) {
      val message = "restart at offset $startOffset, state ${restartStates[i]}, token #$expectedIndex"
      assertEquals(tokenTypes[expectedIndex].toString(), lexer.tokenType?.toString(), "$message: token type")
      assertEquals(tokenStarts[expectedIndex], lexer.tokenStart, "$message: token start")
      assertEquals(tokenEnds[expectedIndex], lexer.tokenEnd, "$message: token end")
      expectedIndex++
      lexer.advance()
    }
    i += step
  }
}

private const val TOKENS_TO_COMPARE = 100
private const val MAX_RESTARTS = 300
