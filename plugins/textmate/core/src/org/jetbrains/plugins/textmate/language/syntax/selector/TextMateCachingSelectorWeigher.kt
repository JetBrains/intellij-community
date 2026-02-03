package org.jetbrains.plugins.textmate.language.syntax.selector

import org.jetbrains.plugins.textmate.cache.SLRUTextMateCache
import org.jetbrains.plugins.textmate.cache.TextMateCache
import org.jetbrains.plugins.textmate.cache.use
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope

fun TextMateSelectorWeigher.caching(): TextMateCachingSelectorWeigher {
  val delegate = this
  return TextMateCachingSelectorWeigher(SLRUTextMateCache(
    computeFn = { key -> delegate.weigh(key.scopeSelector, key.scope) },
    disposeFn = { },
    capacity = 1000,
    protectedRatio = 0.5,
  ))
}

class TextMateCachingSelectorWeigher(private val cache: TextMateCache<TextMateSelectorWeigherCacheKey, TextMateWeigh>) : TextMateSelectorWeigher, AutoCloseable {
  override fun weigh(scopeSelector: CharSequence, scope: TextMateScope): TextMateWeigh {
    return cache.use(TextMateSelectorWeigherCacheKey(scopeSelector, scope)) { it }
  }

  fun clearCache() {
    cache.clear()
  }

  override fun close() {
    cache.close()
  }
}

data class TextMateSelectorWeigherCacheKey(val scopeSelector: CharSequence, val scope: TextMateScope)
