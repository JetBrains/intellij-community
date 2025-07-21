package org.jetbrains.plugins.textmate.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration

interface TextMateCachedValue<V>: AutoCloseable {
  val value: V
}

interface TextMateCache<K, V> {
  fun get(key: K): TextMateCachedValue<V>
  fun contains(key: K): Boolean
  fun cleanup()
  fun clear()
  fun size(): Int
}

inline fun <K, V, T> TextMateCache<K, V>.use(key: K, body: (V) -> T): T {
  return get(key).use { body(it.value) }
}

suspend fun <K, V> withCache(
  cleanupInterval: Duration?,
  cacheFn: () -> TextMateCache<K, V>,
  body: CoroutineScope.(TextMateCache<K, V>) -> Unit,
) {
  coroutineScope {
    val cache = cacheFn()
    val cleaningJob = cleanupInterval?.let {
      launch {
        delay(cleanupInterval)
        cache.cleanup()
      }
    }
    try {
      body(cache)
    }
    finally {
      cache.clear()
      cleaningJob?.cancelAndJoin()
    }
  }
}
