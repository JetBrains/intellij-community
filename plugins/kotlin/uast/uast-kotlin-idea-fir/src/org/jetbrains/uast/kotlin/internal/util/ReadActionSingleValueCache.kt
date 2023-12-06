// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.internal.util

import com.intellij.psi.util.ReadActionCache
import org.jetbrains.annotations.ApiStatus

/**
 * A short-living, single-value cache bound to the current read action.
 *
 * The cache stores a *single value* and is thus only appropriate in cases where the same key is requested multiple times in succession.
 * **In any other case, this cache should not be used.** For example, requesting key A, key B, and key A in this order will result in three
 * cache misses.
 *
 * The cache guarantees that [provider] will be evaluated only once per cache miss. However, in accordance with [ReadActionCache] policy,
 * the value will not be cached if [getCachedOrEvaluate] is called outside a read action, and [provider] will be called every time the value
 * is requested, even with the same key.
 *
 * The cached value will be cleared when the read action ends. Also, this cache is thread-local, thus no thread-contention is expected, no
 * synchronisation is needed and there are no requirements for the cached values to be thread-safe.
 *
 * @param provider A lambda for computing the cached value from a given key of type [K]. It is called on every cache miss.
 *
 * @see ReadActionCache
 */
@ApiStatus.Experimental
class ReadActionSingleValueCache<K, V>(private val provider: (K) -> V) {
  fun getCachedOrEvaluate(key: K): V {
    val processingContext = ReadActionCache.getInstance().processingContext ?: return provider(key)

    processingContext.get(this)?.let { entry ->
      val (lastKey, value) = @Suppress("UNCHECKED_CAST") (entry as Pair<K, V>)
      if (lastKey == key) return value
    }

    val value = provider(key)
    processingContext.put(this, key to value)
    return value
  }
}
