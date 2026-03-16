// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.systemPython.impl

import com.intellij.openapi.diagnostic.fileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

/**
 * [K]->[[V]] cache. List of [V] is calculated by [getDataForCache].
 * [get] returns a mutable list, so you can add items there.
 * If cache hasn't been calculated yet, this function suspends till [getDataForCache].
 * Cache could be cleared with [clear].
 *
 * Once [startUpdate] is called (which is idempotent call) cache will be updated on [scope] every [delayer].
 *
 * Cache isn't blocked while updating: a previous version is used.
 */
internal class Cache<K : Any, V>(
  scope: CoroutineScope,
  delayer: UpdateCacheDelayer,
  private val getDataForCache: suspend (K) -> List<V>,
) {
  constructor(
    scope: CoroutineScope,
    updateInterval: Duration,
    getDataForCache: suspend (K) -> List<V>,
  ) : this(scope, UpdateCacheDelayer.TimeBased(updateInterval), getDataForCache)

  private companion object {
    val logger = fileLogger()
  }

  private val cacheUpdateMutex = Mutex()
  private val cache = ConcurrentHashMap<K, MutableSet<V>>()

  /**
   * Laziness prevents postpones a job from creation till first [startUpdate] so a client might decide not to create a job at all
   */
  private val cacheUpdateJob = lazy {
    scope.launch(Dispatchers.Default) {
      while (true) {
        val callAfterUpdate = delayer.delayBeforeUpdate()
        updateCacheForAllKeys()
        if (callAfterUpdate != null) {
          callAfterUpdate()
        }
      }
    }
  }

  private suspend fun updateCacheForAllKeys() {
    val keys = cache.keys
    logger.debug("Updating cache for keys ${keys.joinToString(", ")}")
    val values = keys.toList() // copy keys not to affect the current cache
    for (k in values) {
      updateCache(k)
    }
  }

  /**
   * Updates cache and suspends till finished
   */
  suspend fun updateCache(k: K): Set<V> = cacheUpdateMutex.withLock {
    updateCacheInternal(k)
  }

  /**
   * Starts cache-in-background-updating process (does nothing if started already)
   */
  fun startUpdate() {
    cacheUpdateJob.value
  }

  /**
   * Removes cache for [k]. Next [get] might suspend (if a background process wouldn't fill at in advance)
   */
  fun clear(k: K) {
    cache.remove(k)
    logger.info("Cache flushed for $k")
  }

  /**
   * Returns current cache for [k], might suspend for a while if cache hasn't been filled yet.
   */
  suspend fun get(k: K): MutableCollection<V> {
    cache[k]?.let { return it } // Return current cache
    // No need to update the cache in several coroutines
    cacheUpdateMutex.withLock {
      // It could be that previous coroutine updated cache already (this is why we were blocked), so check again.
      // Kinda Double-checked locking
      cache[k]?.let { return it }
      logger.info("No cache for $k, will fill")
      return updateCacheInternal(k)
    }
  }

  /**
   * Always call under [cacheUpdateMutex].
   * This function can't have mutex itself because double-checking locking is used in [get] but not in [cacheUpdateJob]
   */
  private suspend fun updateCacheInternal(k: K): MutableSet<V> {
    logger.info("Starting update for $k")
    val data = getDataForCache(k)
    val newValue = ConcurrentHashMap.newKeySet<V>(data.size)
    newValue.addAll(data)
    cache[k] = newValue
    logger.info("End update for $k")
    return newValue
  }
}

/**
 * [Cache] calls [delayBeforeUpdate] in a loop and updates cache after each call.
 * [TimeBased] is an implementation that uses [delay].
 *
 * Returned value (if any) is called after each cache update
 */
internal fun interface UpdateCacheDelayer {
  suspend fun delayBeforeUpdate(): (() -> Unit)?
  class TimeBased(private val duration: Duration) : UpdateCacheDelayer {
    override suspend fun delayBeforeUpdate(): (() -> Unit)? {
      delay(duration)
      return null
    }
  }
}