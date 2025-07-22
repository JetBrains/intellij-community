package org.jetbrains.plugins.textmate.regex

import org.jetbrains.plugins.textmate.cache.SLRUTextMateCache
import org.jetbrains.plugins.textmate.cache.TextMateCache
import org.jetbrains.plugins.textmate.cache.use

fun <T> withCachingRegexFactory(
  regexFactory: RegexFactory,
  body: (RegexProvider) -> T,
): T {
  val cache = SLRUTextMateCache<CharSequence, RegexFacade>(
    computeFn = { pattern -> regexFactory.regex(pattern) },
    disposeFn = { regex -> regex.close() },
    capacity = 1000,
    protectedRatio = 0.5,
  )
  return try {
    body(CachingRegexProvider(cache, regexFactory))
  }
  finally {
    cache.clear()
  }
}

private class CachingRegexProvider(
  private val cache: TextMateCache<CharSequence, RegexFacade>,
  private val regexFactory: RegexFactory,
) : RegexProvider {
  override fun <T> withRegex(pattern: CharSequence, body: (RegexFacade) -> T): T {
    return cache.use(pattern, body)
  }

  override fun <T> withString(string: CharSequence, body: (TextMateString) -> T): T {
    return regexFactory.string(string).use(body)
  }
}