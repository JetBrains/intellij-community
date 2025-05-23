package org.jetbrains.plugins.textmate.regex

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.asExecutor
import java.util.concurrent.TimeUnit

class CaffeineCachingRegexFactory(private val delegate: RegexFactory) : RegexFactory {
  companion object {
    private val REGEX_CACHE: Cache<CharSequence, RegexFacade> = Caffeine.newBuilder()
      .maximumSize(100_000)
      .expireAfterAccess(1, TimeUnit.MINUTES)
      .removalListener { _: CharSequence?, v: RegexFacade?, _ -> v?.close() }
      .executor(Dispatchers.Default.asExecutor())
      .build()
  }

  override fun regex(pattern: CharSequence): RegexFacade {
    return REGEX_CACHE.get(pattern) {
      val delegateRegex = delegate.regex(pattern)
      object : RegexFacade {
        override fun match(string: TextMateString, checkCancelledCallback: Runnable?): MatchData {
          return delegateRegex.match(string, checkCancelledCallback)
        }

        override fun match(string: TextMateString, byteOffset: Int, matchBeginPosition: Boolean, matchBeginString: Boolean, checkCancelledCallback: Runnable?): MatchData {
          return delegateRegex.match(string, byteOffset, matchBeginPosition, matchBeginString, checkCancelledCallback)
        }

        override fun close() {
          // do nothing, will be disposed by cache
        }
      }
    }
  }

  override fun string(string: CharSequence): TextMateString {
    return delegate.string(string)
  }
}