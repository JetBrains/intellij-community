package org.jetbrains.plugins.textmate.cache

import kotlinx.coroutines.*
import kotlin.time.Duration

interface TextMateCachedValue<V>: AutoCloseable {
  val value: V
}

interface TextMateCache<K, V>: AutoCloseable {
  fun get(key: K): TextMateCachedValue<V>
  fun contains(key: K): Boolean
  fun cleanup(ttl: Duration = Duration.ZERO)
  fun clear()
  fun size(): Int
}

inline fun <K, V, T> TextMateCache<K, V>.use(key: K, body: (V) -> T): T {
  return get(key).use { body(it.value) }
}

suspend fun <K, V> withCache(
  cacheFn: () -> TextMateCache<K, V>,
  cleanupInterval: Duration,
  ttl: Duration = Duration.ZERO,
  body: CoroutineScope.(TextMateCache<K, V>) -> Unit,
) {
  coroutineScope {
    cacheFn().use { cache ->
      val cleaningJob = launch {
        delay(cleanupInterval)
        cache.cleanup()
      }
      try {
        body(cache)
      }
      finally {
        cleaningJob.cancelAndJoin()
      }
    }
  }
}
