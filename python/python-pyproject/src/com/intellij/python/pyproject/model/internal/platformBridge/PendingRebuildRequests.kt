// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.internal.platformBridge

import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

/**
 * Merges pending rebuilds and retains at most [directoryLimit] directory references.
 * Overflow replaces the directory set with a request to load all project roots.
 */
internal class PendingRebuildRequests(private val directoryLimit: Int = 100) {
  init {
    require(directoryLimit > 0)
  }

  private val state = MutableStateFlow<PendingRebuild?>(null)
  val changes = state.asStateFlow()

  fun add(request: RebuildRequest) {
    state.update { pending ->
      if (pending?.reloadProjectRoots == true) return@update pending
      val directories = LinkedHashSet(pending?.directoriesToLoad.orEmpty())
      for (directory in request.directoriesToLoad) {
        directories.add(directory)
        if (directories.size > directoryLimit) {
          return@update PendingRebuild(emptySet(), "the pending directory limit was exceeded", reloadProjectRoots = true)
        }
      }
      PendingRebuild(directories, request.reason, reloadProjectRoots = false)
    }
  }

  /** Removes the pending work atomically. Later requests belong to the next build. */
  fun take(): PendingRebuild? = state.getAndUpdate { null }
}

/** An immutable batch for one build. [reason] describes the latest request, or the overflow. */
internal class PendingRebuild(
  val directoriesToLoad: Set<VirtualFile>,
  val reason: String,
  val reloadProjectRoots: Boolean,
)
