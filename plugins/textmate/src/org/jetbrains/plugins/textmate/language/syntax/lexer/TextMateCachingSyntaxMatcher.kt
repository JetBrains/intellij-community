package org.jetbrains.plugins.textmate.language.syntax.lexer

import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import org.jetbrains.plugins.textmate.Constants
import org.jetbrains.plugins.textmate.language.syntax.SyntaxNodeDescriptor
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateWeigh
import org.jetbrains.plugins.textmate.regex.MatchData
import org.jetbrains.plugins.textmate.regex.TextMateByteOffset
import org.jetbrains.plugins.textmate.regex.TextMateString
import java.util.concurrent.TimeUnit

@Deprecated("Use TextMateCachingSyntaxMatcherCore instead")
class TextMateCachingSyntaxMatcher(private val delegate: TextMateSyntaxMatcher) : TextMateSyntaxMatcher {
  private val CACHE = Caffeine.newBuilder()
    .maximumSize(10000)
    .expireAfterAccess(1, TimeUnit.MINUTES)
    .executor(Dispatchers.Default.asExecutor())
    .build<MatchKey, TextMateLexerState>()

  override fun matchRule(
    syntaxNodeDescriptor: SyntaxNodeDescriptor,
    string: TextMateString,
    byteOffset: TextMateByteOffset,
    matchBeginPosition: Boolean,
    matchBeginString: Boolean,
    priority: TextMateWeigh.Priority,
    currentScope: TextMateScope,
    checkCancelledCallback: Runnable?,
  ): TextMateLexerState {
    return CACHE.get(
      MatchKey(syntaxNodeDescriptor, string, byteOffset, matchBeginPosition, matchBeginString, priority, currentScope)) {
      delegate.matchRule(syntaxNodeDescriptor = it.syntaxNodeDescriptor,
                         string = it.string,
                         byteOffset = it.byteOffset,
                         matchBeginPosition = it.matchBeginPosition,
                         matchBeginString = it.matchBeginString,
                         priority = it.priority,
                         currentScope = it.currentScope,
                         checkCancelledCallback = checkCancelledCallback)
    }
  }

  override fun matchStringRegex(
    keyName: Constants.StringKey,
    string: TextMateString,
    byteOffset: TextMateByteOffset,
    matchBeginPosition: Boolean,
    matchBeginString: Boolean,
    lexerState: TextMateLexerState,
    checkCancelledCallback: Runnable?,
  ): MatchData {
    return delegate.matchStringRegex(keyName, string, byteOffset, matchBeginPosition, matchBeginString, lexerState, checkCancelledCallback)
  }

  override fun <T> matchingString(s: CharSequence, body: (TextMateString) -> T): T {
    return delegate.matchingString(s, body)
  }

  private data class MatchKey(
    val syntaxNodeDescriptor: SyntaxNodeDescriptor,
    val string: TextMateString,
    val byteOffset: TextMateByteOffset,
    val matchBeginPosition: Boolean,
    val matchBeginString: Boolean,
    val priority: TextMateWeigh.Priority,
    val currentScope: TextMateScope,
  )
}