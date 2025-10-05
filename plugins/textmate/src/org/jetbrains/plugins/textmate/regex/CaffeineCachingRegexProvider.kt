package org.jetbrains.plugins.textmate.regex

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.util.concurrent.TimeUnit

class CaffeineCachingRegexProvider(private val regexFactory: RegexFactory) : RegexProvider {
  private val REGEX_CACHE: Cache<CharSequence, RegexFacade> = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterAccess(1, TimeUnit.MINUTES)
    .removalListener { _: CharSequence?, v: RegexFacade?, _ -> v?.close() }
    .executor(Dispatchers.Default.asExecutor())
    .build()

  override fun <T> withRegex(pattern: CharSequence, body: (RegexFacade) -> T): T {
    return body(REGEX_CACHE.get(pattern) { regexFactory.regex(pattern) })
  }

  override fun <T> withString(string: CharSequence, body: (TextMateString) -> T): T {
    return regexFactory.string(string).use(body)
  }
}

@Deprecated("Use CaffeineCachingRegexProvider")
class CachingRegexFactory(private val regexFactory: RegexFactory) : RegexFactory {
  private val REGEX_CACHE: Cache<CharSequence, RegexFacade> = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterAccess(1, TimeUnit.MINUTES)
    .removalListener { _: CharSequence?, v: RegexFacade?, _ -> v?.close() }
    .executor(Dispatchers.Default.asExecutor())
    .build()

  override fun regex(pattern: CharSequence): RegexFacade {
    return REGEX_CACHE.get(pattern) { regexFactory.regex(pattern) }
  }

  override fun string(string: CharSequence): TextMateString {
    return regexFactory.string(string)
  }
}