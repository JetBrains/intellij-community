package org.jetbrains.plugins.textmate.language.syntax

import com.intellij.textmate.joni.JoniRegexFactory
import org.jetbrains.plugins.textmate.language.TextMateConcurrentMapInterner
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateLexerCore
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateSyntaxMatcherImpl
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherImpl
import org.jetbrains.plugins.textmate.language.syntax.selector.caching
import org.jetbrains.plugins.textmate.plist.JsonPlistReader
import org.jetbrains.plugins.textmate.regex.CaffeineCachingRegexProvider
import org.jetbrains.plugins.textmate.regex.RememberingLastMatchRegexFactory

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