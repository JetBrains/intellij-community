package org.jetbrains.plugins.textmate.language.syntax.lexer

import kotlinx.coroutines.Runnable
import org.jetbrains.plugins.textmate.Constants
import org.jetbrains.plugins.textmate.cache.SLRUTextMateCache
import org.jetbrains.plugins.textmate.cache.TextMateCache
import org.jetbrains.plugins.textmate.cache.use
import org.jetbrains.plugins.textmate.language.syntax.SyntaxNodeDescriptor
import org.jetbrains.plugins.textmate.language.syntax.InjectionNodeDescriptor
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateWeigh
import org.jetbrains.plugins.textmate.regex.MatchData
import org.jetbrains.plugins.textmate.regex.TextMateByteOffset
import org.jetbrains.plugins.textmate.regex.TextMateString

fun TextMateSyntaxMatcher.caching(): TextMateCachingSyntaxMatcherCore {
  val delegate = this
  return TextMateCachingSyntaxMatcherCore(delegate, SLRUTextMateCache(
    computeFn = { key ->
      delegate.matchRule(syntaxNodeDescriptor = key.syntaxNodeDescriptor,
                         string = key.string,
                         byteOffset = key.byteOffset,
                         matchBeginPosition = key.matchBeginPosition,
                         matchBeginString = key.matchBeginString,
                         priority = key.priority,
                         currentScope = key.currentScope,
                         injections = key.injections,
                         checkCancelledCallback = key.checkCancelledCallback)
    },
    disposeFn = { },
    capacity = 1000,
    protectedRatio = 0.5,
  ))
}

class TextMateCachingSyntaxMatcherCore(private val delegate: TextMateSyntaxMatcher,
                                       private val cache: TextMateCache<TextMateMatchRuleCacheKey, TextMateLexerState>) : TextMateSyntaxMatcher, AutoCloseable {
  override fun matchRule(syntaxNodeDescriptor: SyntaxNodeDescriptor, string: TextMateString, byteOffset: TextMateByteOffset, matchBeginPosition: Boolean, matchBeginString: Boolean, priority: TextMateWeigh.Priority, currentScope: TextMateScope, injections: List<InjectionNodeDescriptor>, checkCancelledCallback: Runnable?): TextMateLexerState {
    return cache.use(TextMateMatchRuleCacheKey(syntaxNodeDescriptor = syntaxNodeDescriptor,
                                               string = string,
                                               byteOffset = byteOffset,
                                               matchBeginPosition = matchBeginPosition,
                                               matchBeginString = matchBeginString,
                                               priority = priority,
                                               currentScope = currentScope, injections = injections,
                                               checkCancelledCallback = checkCancelledCallback)) { it }
  }

  override fun matchStringRegex(keyName: Constants.StringKey, string: TextMateString, byteOffset: TextMateByteOffset, matchBeginPosition: Boolean, matchBeginString: Boolean, lexerState: TextMateLexerState, checkCancelledCallback: Runnable?): MatchData {
    return delegate.matchStringRegex(keyName = keyName,
                                     string = string,
                                     byteOffset = byteOffset,
                                     matchBeginPosition = matchBeginPosition,
                                     matchBeginString = matchBeginString,
                                     lexerState = lexerState,
                                     checkCancelledCallback = checkCancelledCallback)
  }

  override fun <T> matchingString(s: CharSequence, body: (TextMateString) -> T): T {
    return delegate.matchingString(s, body)
  }

  override fun close() {
    cache.close()
  }
}

class TextMateMatchRuleCacheKey(
  val syntaxNodeDescriptor: SyntaxNodeDescriptor,
  val string: TextMateString,
  val byteOffset: TextMateByteOffset,
  val matchBeginPosition: Boolean,
  val matchBeginString: Boolean,
  val priority: TextMateWeigh.Priority,
  val currentScope: TextMateScope,
  val injections: List<InjectionNodeDescriptor>,
  val checkCancelledCallback: Runnable?,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true

    other as TextMateMatchRuleCacheKey

    if (byteOffset != other.byteOffset) return false
    if (matchBeginPosition != other.matchBeginPosition) return false
    if (matchBeginString != other.matchBeginString) return false
    if (syntaxNodeDescriptor != other.syntaxNodeDescriptor) return false
    if (injections != other.injections) return false
    if (string != other.string) return false
    if (priority != other.priority) return false
    if (currentScope != other.currentScope) return false

    return true
  }

  override fun hashCode(): Int {
    var result = byteOffset.hashCode()
    result = 31 * result + matchBeginPosition.hashCode()
    result = 31 * result + matchBeginString.hashCode()
    result = 31 * result + syntaxNodeDescriptor.hashCode()
    result = 31 * result + injections.hashCode()
    result = 31 * result + string.hashCode()
    result = 31 * result + priority.hashCode()
    result = 31 * result + currentScope.hashCode()
    return result
  }
}
