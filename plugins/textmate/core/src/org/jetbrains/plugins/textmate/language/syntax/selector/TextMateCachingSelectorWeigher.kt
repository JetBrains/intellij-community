package org.jetbrains.plugins.textmate.language.syntax.selector

import org.jetbrains.plugins.textmate.cache.SLRUTextMateCache
import org.jetbrains.plugins.textmate.cache.TextMateCache
import org.jetbrains.plugins.textmate.cache.use
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope

fun <T> withCachingSelectorWeigher(delegate: TextMateSelectorWeigher, body: (TextMateSelectorWeigher) -> T): T {
  return createSelectorWeigherCache(delegate).use { cache ->
    body(TextMateCachingSelectorWeigher(cache))
  }
}

fun createSelectorWeigherCache(delegate: TextMateSelectorWeigher): SLRUTextMateCache<TextMateSelectorWeigherCacheKey, TextMateWeigh> {
  return SLRUTextMateCache(
    computeFn = { key -> delegate.weigh(key.scopeSelector, key.scope) },
    disposeFn = { },
    capacity = 1000,
    protectedRatio = 0.5,
  )
}

class TextMateCachingSelectorWeigher(private val cache: TextMateCache<TextMateSelectorWeigherCacheKey, TextMateWeigh>) : TextMateSelectorWeigher {
  override fun weigh(scopeSelector: CharSequence, scope: TextMateScope): TextMateWeigh {
    return cache.use(TextMateSelectorWeigherCacheKey(scopeSelector, scope)) { it }
  }
}

data class TextMateSelectorWeigherCacheKey(val scopeSelector: CharSequence, val scope: TextMateScope)
