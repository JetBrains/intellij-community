package org.jetbrains.plugins.textmate.regex

import kotlinx.coroutines.Runnable
import org.jetbrains.plugins.textmate.createTextMateThreadLocal
import org.jetbrains.plugins.textmate.regex.MatchData.Companion.NOT_MATCHED

class RememberingLastMatchRegexFactory(private val delegate: RegexFactory) : RegexFactory {
  override fun regex(pattern: CharSequence): RegexFacade {
    var hasGMatch = false
    var hasAMatch = false
    var i = 0
    while (i < pattern.length - 1 && !hasGMatch && !hasAMatch) {
      val c = pattern[i]
      if (c == '\\') {
        i += 1
        when (pattern[i]) {
          'G' -> hasGMatch = true
          'A' -> hasAMatch = true
        }
      }
      i += 1
    }
    return TextMateRegexFacadeRememberLastMatch(delegate = delegate.regex(pattern),
                                                hasGMatch = hasGMatch,
                                                hasAMatch = hasAMatch)
  }

  override fun string(string: CharSequence): TextMateString {
    return delegate.string(string)
  }
}

private class TextMateRegexFacadeRememberLastMatch(private val delegate: RegexFacade,
                                                   private val hasGMatch: Boolean,
                                                   private val hasAMatch: Boolean): RegexFacade {
  private val matchResult: TextMateThreadLocal<LastMatch?> = createTextMateThreadLocal()

  override fun match(string: TextMateString, checkCancelledCallback: Runnable?): MatchData {
    return delegate.match(string, checkCancelledCallback)
  }

  override fun match(string: TextMateString, byteOffset: Int, matchBeginPosition: Boolean, matchBeginString: Boolean, checkCancelledCallback: Runnable?): MatchData {
    val lastResult = matchResult.get()
    val lastStringId = lastResult?.stringId
    val lastOffset = lastResult?.offset ?: Int.MAX_VALUE
    val lastMatchBeginPosition = lastResult?.matchBeginPosition ?: true
    val lastMatchBeginString = lastResult?.matchBeginString ?: true
    val lastMatch = lastResult?.matchData ?: NOT_MATCHED

    if (lastStringId == string.id &&
        lastOffset <= byteOffset &&
        (!hasAMatch || (byteOffset == lastOffset && matchBeginString == lastMatchBeginString)) &&
        (!hasGMatch || (byteOffset == lastOffset && matchBeginPosition == lastMatchBeginPosition))) {
      if (!lastMatch.matched || lastMatch.byteRange().start >= byteOffset) {
        return lastMatch
      }
    }

    return delegate.match(string, byteOffset, matchBeginPosition, matchBeginString, checkCancelledCallback).also { matchData ->
      matchResult.set(LastMatch(string.id, byteOffset, matchBeginPosition, matchBeginString, matchData))
    }
  }

  override fun close() {
    delegate.close()
  }

  private class LastMatch(
    val stringId: Any?,
    val offset: Int,
    val matchBeginPosition: Boolean,
    val matchBeginString: Boolean,
    val matchData: MatchData,
  )
}