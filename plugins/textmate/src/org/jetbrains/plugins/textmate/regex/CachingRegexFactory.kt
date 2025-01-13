package org.jetbrains.plugins.textmate.regex

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.util.concurrent.TimeUnit

class CachingRegexFactory(private val delegate: RegexFactory) : RegexFactory {
  companion object {
    private val REGEX_CACHE: Cache<String, RegexFacade> = Caffeine.newBuilder()
      .maximumSize(100_000)
      .expireAfterAccess(1, TimeUnit.MINUTES)
      .executor(Dispatchers.Default.asExecutor())
      .build()
  }

  override fun regex(regexString: String): RegexFacade {
    return REGEX_CACHE.get(regexString, delegate::regex)
  }

  override fun string(string: CharSequence): TextMateString {
    return delegate.string(string)
  }
}