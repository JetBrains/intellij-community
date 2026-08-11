// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Session-scoped cache for batched tool probes.
 *
 * A successful load caches both returned values and misses: every requested key absent from the loaded map is remembered as missing.
 * Failed, cancelled, or exceptional loads do not modify the cache, so a later request can retry. Loads are intentionally serialized to
 * prevent concurrent target probes from fanning out into multiple remote calls.
 *
 * This cache has no eviction or expiration policy and is not intended as a general-purpose cache.
 */
internal class ToolProbeCache<K : Any, V : Any> {
  private val values = mutableMapOf<K, V?>()
  private val lock = Mutex()

  suspend fun getOrLoad(keys: List<K>, loader: suspend (List<K>) -> PyResult<Map<K, V>>): PyResult<Map<K, V>> = lock.withLock {
    val missingKeys = keys.filterNot(values::containsKey)
    if (missingKeys.isNotEmpty()) {
      val loadedValues = loader(missingKeys).getOr { return@withLock it }
      for (key in missingKeys) {
        values[key] = loadedValues[key]
      }
    }

    PyResult.success(keys.mapNotNull { key -> values[key]?.let { key to it } }.toMap())
  }
}
