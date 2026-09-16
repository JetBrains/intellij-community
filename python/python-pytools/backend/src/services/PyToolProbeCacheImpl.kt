// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.backend.services

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.getResolvedEelMachine
import com.intellij.python.pytools.backend.GenericPyToolManagerProvider
import com.intellij.python.pytools.backend.InstalledInfo
import com.intellij.python.pytools.backend.PyTool
import com.intellij.python.pytools.backend.PyToolProbeCache
import com.intellij.python.pytools.backend.Version
import com.intellij.python.pytools.backend.validateCustomPath
import com.jetbrains.python.getOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.TimeUnit

/**
 * Caffeine-backed [PyToolProbeCache]. Both caches run through an [AsyncCache], so racing callers share one
 * in-flight run: a settings page and a row it expands at the same time cost one listing and one version run
 * between them. The load runs on the service scope, so a cancelled caller does not kill it.
 *
 * The TTL matches `PyExecutableCacheImpl`, so a tool installed or upgraded outside the IDE surfaces within the
 * same window as one that appears on `PATH`. An install or upgrade made through the IDE invalidates the machine
 * instead of waiting for the TTL.
 */
internal class PyToolProbeCacheImpl(private val coroutineScope: CoroutineScope) : PyToolProbeCache {
  private data class VersionKey(val machineInternalName: String, val fusId: String, val path: String)

  private val listingCache: AsyncCache<String, Map<PyTool<*>, InstalledInfo>> = Caffeine.newBuilder()
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .buildAsync()

  private val versionCache: AsyncCache<VersionKey, Optional<Version>> = Caffeine.newBuilder()
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .buildAsync()

  override suspend fun listing(eel: EelApi): Map<PyTool<*>, InstalledInfo> {
    // No resolvable machine (shouldn't normally happen): list without caching.
    val machine = eel.descriptor.getResolvedEelMachine() ?: return list(eel)
    return listingCache.get(machine.internalName) { _, _ -> coroutineScope.future { list(eel) } }.await()
  }

  override suspend fun version(eelDescriptor: EelDescriptor, tool: PyTool<*>, path: Path): Version? {
    val machine = eelDescriptor.getResolvedEelMachine() ?: return probe(tool, path)
    val key = VersionKey(machine.internalName, tool.fusId, path.toString())
    return versionCache.get(key) { _, _ -> coroutineScope.future { Optional.ofNullable(probe(tool, path)) } }
      .await().orElse(null)
  }

  override fun invalidate(eelDescriptor: EelDescriptor) {
    val machine = eelDescriptor.getResolvedEelMachine() ?: return
    listingCache.synchronous().invalidate(machine.internalName)
    // A version belongs to a file, and an upgrade replaces the file behind an unchanged path, so every version of
    // the machine goes. What follows re-runs only what a caller asks for.
    versionCache.synchronous().asMap().keys.removeIf { it.machineInternalName == machine.internalName }
  }

  private suspend fun list(eel: EelApi): Map<PyTool<*>, InstalledInfo> =
    GenericPyToolManagerProvider.managerFor(eel)?.list().orEmpty()

  private suspend fun probe(tool: PyTool<*>, path: Path): Version? = tool.validateCustomPath(path).getOrNull()
}
