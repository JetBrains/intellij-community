// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.internal.platformBridge

import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * Merges pending rebuilds and retains at most [directoryLimit] directory references.
 * Overflow replaces the directory set with a request to load all project roots.
 */
internal class PendingRebuildRequests(private val directoryLimit: Int = DIRECTORY_LIMIT) {
  init {
    require(directoryLimit > 0)
  }

  private data class State(val batch: PendingRebuild? = null, val closed: Boolean = false)

  private val state = MutableStateFlow(State())

  fun add(request: RebuildRequest) {
    add(PendingRebuild(request.directoriesToLoad, request.reason, reloadProjectRoots = false))
  }

  fun add(batch: PendingRebuild) {
    state.update { current ->
      check(!current.closed)
      current.copy(batch = mergeRebuilds(current.batch, batch, directoryLimit))
    }
  }

  /** Completes collection after the remaining batch is emitted. Call after all producers finish. */
  fun close() {
    state.update { it.copy(closed = true) }
  }

  /**
   * Debounces state changes for [quietPeriod] and emits pending batches. Use one collector per instance.
   * Each emission atomically removes the latest batch. Later requests belong to the next batch.
   * Requests remain pending before collection starts and while the collector processes a batch.
   */
  @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
  fun batches(quietPeriod: Duration): Flow<PendingRebuild> =
    state.debounce { if (it.closed) Duration.ZERO else quietPeriod }.transformWhile {
      val current = state.getAndUpdate { it.copy(batch = null) }
      current.batch?.let { emit(it) }
      !current.closed
    }
}

/**
 * Merges both sources into bounded batches and emits an initial full scan.
 * Each source must establish its subscription before its first suspension.
 * Both sources start before the initial scan. Collection owns their lifetimes.
 */
internal fun Flow<PendingRebuild>.mergeRebuildRequests(
  other: Flow<PendingRebuild>,
  quietPeriod: Duration,
): Flow<PendingRebuild> = flow {
  coroutineScope {
    ensureActive()
    val requests = PendingRebuildRequests()
    val producers = listOf(this@mergeRebuildRequests, other).map { source ->
      launch(start = CoroutineStart.UNDISPATCHED) {
        source.collect(requests::add)
      }
    }
    launch {
      producers.joinAll()
      requests.close()
    }
    emit(PendingRebuild(emptySet(), "the start of the sync", reloadProjectRoots = true))
    emitAll(requests.batches(quietPeriod))
  }
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
