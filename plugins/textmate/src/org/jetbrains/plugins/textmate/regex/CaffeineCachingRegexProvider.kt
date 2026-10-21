package org.jetbrains.plugins.textmate.regex

import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.util.concurrent.TimeUnit

class CaffeineCachingRegexProvider(private val regexFactory: RegexFactory) : RegexProvider {
  /**
   * The loader is a field, not a lambda passed to every `get` call. The lexer asks for a regex once
   * per child rule per offset, so a capturing lambda there costs an allocation on every hit.
   */
  private val regexLoader: CacheLoader<CharSequence, RegexFacade> = CacheLoader { pattern ->
    regexFactory.regex(pattern)
  }

  private val REGEX_CACHE: LoadingCache<CharSequence, RegexFacade> = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterAccess(1, TimeUnit.MINUTES)
    .removalListener { _: CharSequence?, v: RegexFacade?, _ -> v?.close() }
    .executor(Dispatchers.Default.asExecutor())
    .build(regexLoader)

  override fun <T> withRegex(pattern: CharSequence, body: (RegexFacade) -> T): T {
    return body(REGEX_CACHE.get(pattern))
  }

  override fun <T> withString(string: CharSequence, body: (TextMateString) -> T): T {
    return regexFactory.string(string).use(body)
  }
}

@Deprecated("Use CaffeineCachingRegexProvider")
class CachingRegexFactory(private val regexFactory: RegexFactory) : RegexFactory {
  private val regexLoader: CacheLoader<CharSequence, RegexFacade> = CacheLoader { pattern ->
    regexFactory.regex(pattern)
  }

  private val REGEX_CACHE: LoadingCache<CharSequence, RegexFacade> = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterAccess(1, TimeUnit.MINUTES)
    .removalListener { _: CharSequence?, v: RegexFacade?, _ -> v?.close() }
    .executor(Dispatchers.Default.asExecutor())
    .build(regexLoader)

  override fun regex(pattern: CharSequence): RegexFacade {
    return REGEX_CACHE.get(pattern)
  }

  override fun string(string: CharSequence): TextMateString {
    return regexFactory.string(string)
  }
}
