// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.backend

import com.github.benmanes.caffeine.cache.AsyncCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.future.future
import org.jetbrains.annotations.ApiStatus

/**
 * Returns the value of [key]. When the cache has no value and no load for [key], starts [load] on [scope] and keeps
 * its future in the cache.
 *
 * Callers that ask for the same key during a load share that load. A cancelled caller stops to wait, but the load
 * continues for the other callers, because it runs on [scope] and not on the caller.
 *
 * Use this function, and not `get(key) { ... }.await()`: `CompletionStage.await()` cancels the future when its caller
 * is cancelled, and that cancels the shared load for all callers. This function waits through `asDeferred()`, which
 * does not cancel the future.
 *
 * A load that fails or is cancelled is not kept: Caffeine removes a future that completes exceptionally, and the next
 * caller starts a new load.
 */
@ApiStatus.Internal
suspend fun <K : Any, V : Any> AsyncCache<K, V>.getOrLoad(key: K, scope: CoroutineScope, load: suspend (K) -> V): V =
  get(key) { k, _ -> scope.future { load(k) } }.asDeferred().await()
