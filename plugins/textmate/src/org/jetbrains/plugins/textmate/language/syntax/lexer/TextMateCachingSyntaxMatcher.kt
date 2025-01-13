package org.jetbrains.plugins.textmate.language.syntax.lexer

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import org.jetbrains.plugins.textmate.Constants
import org.jetbrains.plugins.textmate.language.syntax.SyntaxNodeDescriptor
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateWeigh
import org.jetbrains.plugins.textmate.regex.MatchData
import org.jetbrains.plugins.textmate.regex.TextMateString
import java.util.concurrent.TimeUnit

class TextMateCachingSyntaxMatcher(private val delegate: TextMateSyntaxMatcher) : TextMateSyntaxMatcher {
  companion object {
    private val CACHE: Cache<MatchKey?, TextMateLexerState> = Caffeine.newBuilder()
      .maximumSize(100000)
      .expireAfterAccess(1, TimeUnit.MINUTES)
      .executor(Dispatchers.Default.asExecutor())
      .build<MatchKey?, TextMateLexerState?>()
  }

  override fun matchRule(
    syntaxNodeDescriptor: SyntaxNodeDescriptor,
    string: TextMateString,
    byteOffset: Int,
    gosOffset: Int,
    matchBeginOfString: Boolean,
    priority: TextMateWeigh.Priority,
    currentScope: TextMateScope,
    checkCancelledCallback: Runnable?,
  ): TextMateLexerState {
    return CACHE.get(
      MatchKey(syntaxNodeDescriptor, string, byteOffset, gosOffset, matchBeginOfString, priority, currentScope)) {
      requireNotNull(it)
      delegate.matchRule(it.syntaxNodeDescriptor, it.string, it.byteOffset, it.gosOffset, it.matchBeginOfString,
                         it.priority, it.currentScope, checkCancelledCallback)
    }
  }

  override fun matchStringRegex(
    keyName: Constants.StringKey,
    string: TextMateString,
    byteOffset: Int,
    anchorOffset: Int,
    matchBeginOfString: Boolean,
    lexerState: TextMateLexerState,
    checkCancelledCallback: Runnable?,
  ): MatchData {
    return delegate.matchStringRegex(keyName, string, byteOffset, anchorOffset, matchBeginOfString, lexerState, checkCancelledCallback)
  }

  private data class MatchKey(
    val syntaxNodeDescriptor: SyntaxNodeDescriptor,
    val string: TextMateString,
    val byteOffset: Int,
    val gosOffset: Int,
    val matchBeginOfString: Boolean,
    val priority: TextMateWeigh.Priority,
    val currentScope: TextMateScope,
  )
}