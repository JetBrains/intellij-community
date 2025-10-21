package org.jetbrains.plugins.textmate.regex

import org.jetbrains.plugins.textmate.cache.SLRUTextMateCache
import org.jetbrains.plugins.textmate.cache.TextMateCache
import org.jetbrains.plugins.textmate.cache.use

fun RegexFactory.cachingRegexProvider(): TextMateCachingRegexProvider {
  val regexFactory = this
  return TextMateCachingRegexProvider(SLRUTextMateCache(
    computeFn = { pattern -> regexFactory.regex(pattern) },
    disposeFn = { regex -> regex.close() },
    capacity = 1000,
    protectedRatio = 0.5,
  ), regexFactory)
}

class TextMateCachingRegexProvider(
  val cache: TextMateCache<CharSequence, RegexFacade>,
  val regexFactory: RegexFactory,
) : RegexProvider, AutoCloseable {
  override fun <T> withRegex(pattern: CharSequence, body: (RegexFacade) -> T): T {
    return cache.use(pattern, body)
  }

  override fun <T> withString(string: CharSequence, body: (TextMateString) -> T): T {
    return regexFactory.string(string).use(body)
  }

  override fun close() {
    cache.close()
  }
}