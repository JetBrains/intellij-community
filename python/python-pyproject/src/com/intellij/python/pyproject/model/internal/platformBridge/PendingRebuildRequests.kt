// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.internal.platformBridge

import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlin.time.Duration

/**
 * Merges pending rebuilds and retains at most [directoryLimit] directory references.
 * Overflow replaces the directory set with a request to load all project roots.
 */
internal class PendingRebuildRequests(private val directoryLimit: Int = DIRECTORY_LIMIT) {
  init {
    require(directoryLimit > 0)
  }

  private val state = MutableStateFlow<PendingRebuild?>(null)

  fun add(request: RebuildRequest) {
    val batch = PendingRebuild(request.directoriesToLoad, request.reason, reloadProjectRoots = false)
    state.update { pending ->
      mergeRebuilds(pending, batch, directoryLimit)
    }
  }

  /**
   * Debounces state changes for [quietPeriod] and emits pending batches. Use one collector per instance.
   * Each emission atomically removes the latest batch. Later requests belong to the next batch.
   * Requests remain pending before collection starts and while the collector processes a batch.
   */
  @OptIn(FlowPreview::class)
  fun batches(quietPeriod: Duration): Flow<PendingRebuild> =
    state.debounce(quietPeriod).mapNotNull { state.getAndUpdate { null } }
}

/** An immutable batch for one build. [reason] describes the latest request, or the overflow. */
internal class PendingRebuild(
  val directoriesToLoad: Set<VirtualFile>,
  val reason: String,
  val reloadProjectRoots: Boolean,
)

/** Merges failed or pending work with [next], preserving full scans and the directory limit. */
internal fun mergeRebuilds(previous: PendingRebuild?, next: PendingRebuild, directoryLimit: Int = DIRECTORY_LIMIT): PendingRebuild {
  if (next.reloadProjectRoots) return next
  if (previous?.reloadProjectRoots == true) return previous
  val directories = LinkedHashSet(previous?.directoriesToLoad.orEmpty())
  for (directory in next.directoriesToLoad) {
    directories.add(directory)
    if (directories.size > directoryLimit) {
      return PendingRebuild(emptySet(), "the pending directory limit was exceeded", reloadProjectRoots = true)
    }
  }
  return PendingRebuild(directories, next.reason, reloadProjectRoots = false)
}

private const val DIRECTORY_LIMIT = 100
