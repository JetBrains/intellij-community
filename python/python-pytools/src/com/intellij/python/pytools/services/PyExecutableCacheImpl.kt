// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.services

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.getResolvedEelMachine
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.python.pytools.PyExecutable
import com.intellij.python.pytools.PyExecutableCache
import com.intellij.python.pytools.impl.detectExecutableOnEel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.TimeUnit
import kotlin.io.path.isExecutable

/**
 * Caffeine-backed [PyExecutableCache]. The user-chosen custom path ([PyCustomExecutablePaths]) wins and
 * is read live; detection is cached with a short TTL (`expireAfterWrite`) so a moved/upgraded tool is
 * re-found, and runs through Caffeine's [AsyncCache] so racing callers share one in-flight detection
 * (mirrors `ActivatableEnvironmentService`). A `null` detection is cached too, so repeated availability
 * checks don't re-scan `PATH`.
 *
 * Neither source is trusted to still be true. A tool the user removes from the machine leaves both a pinned
 * path and a cached detection behind, and returning either made the interpreter widget keep offering a tool
 * that was gone (PY-91882). So every answer is checked against the file system before it is handed out.
 */
internal class PyExecutableCacheImpl(private val coroutineScope: CoroutineScope) : PyExecutableCache {
  private data class Key(val machineInternalName: String, val fusId: String)

  private val detectionCache: AsyncCache<Key, Optional<Path>> = Caffeine.newBuilder()
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .buildAsync()

  override suspend fun get(eelDescriptor: EelDescriptor, executable: PyExecutable): Path? {
    // Custom path is authoritative and always read live, so a browsed path stays pinned. It names one exact file,
    // so once that file is gone the tool is gone: searching elsewhere would quietly replace the user's choice.
    PyCustomExecutablePaths.getInstance().get(eelDescriptor, executable)?.let { pinned ->
      return pinned.takeIf { it.isStillExecutable() }
    }

    // No resolvable machine (shouldn't normally happen): detect without caching. `toEelApi()` is only
    // resolved here (and in the cache loader) — after the custom-path hit and cache hit have been ruled out.
    val cacheKey = eelDescriptor.getResolvedEelMachine()?.let { Key(it.internalName, executable.fusId) }
                   ?: return detectExecutableOnEel(eelDescriptor.toEelApi(), executable.toolCommandSpec)

    val cached = detect(cacheKey, eelDescriptor, executable)
    if (cached == null || cached.isStillExecutable()) return cached
    // The tool was found once and has been removed since. Retire that answer and look again, so it stops being
    // reported now rather than whenever the TTL happens to run out.
    detectionCache.synchronous().invalidate(cacheKey)
    return detect(cacheKey, eelDescriptor, executable)
  }

  /**
   * The detected path for [cacheKey], from the cache or by detecting it now.
   *
   * AsyncCache.get runs the mapping at most once per key: racing callers share the single in-flight detection
   * future. The load runs on the service scope, so a cancelled caller doesn't kill it.
   */
  private suspend fun detect(cacheKey: Key, eelDescriptor: EelDescriptor, executable: PyExecutable): Path? =
    detectionCache.get(cacheKey) { _, _ ->
      coroutineScope.future { Optional.ofNullable(detectExecutableOnEel(eelDescriptor.toEelApi(), executable.toolCommandSpec)) }
    }.await().orElse(null)

  override fun invalidate(eelDescriptor: EelDescriptor, executable: PyExecutable) {
    eelDescriptor.getResolvedEelMachine()?.let {
      detectionCache.synchronous().invalidate(Key(it.internalName, executable.fusId))
    }
  }
}

/**
 * True while this path still names a file that can be run.
 *
 * A stat, not a run. Both a pinned path and a cached detection were true when they were recorded, and the only
 * question here is whether the file survived. The test is the one detection itself applies (see
 * `detectExecutableOnEel`), so nothing detection would accept is rejected here.
 */
private suspend fun Path.isStillExecutable(): Boolean = withContext(Dispatchers.IO) { isExecutable() }
