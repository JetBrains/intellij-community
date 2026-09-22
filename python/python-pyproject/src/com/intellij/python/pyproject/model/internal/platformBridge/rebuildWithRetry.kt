// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.internal.platformBridge

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.fileLogger
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Processes batches serially and retries failed loads or rebuilds after [retryDelays].
 * After the last retry, retains the failed batch and merges it with the next emission.
 * Logs the first exception and logs again only after a successful rebuild.
 * Control-flow exceptions and errors propagate. Cancellation discards retained work; a new session loads all roots.
 */
internal suspend fun Flow<PendingRebuild>.collectRebuilds(
  retryDelays: List<Duration> = listOf(1.seconds, 2.seconds),
  rebuild: suspend (PendingRebuild) -> Unit,
) {
  var failedBatch: PendingRebuild? = null
  collect { next ->
    val batch = mergeRebuilds(failedBatch, next)
    for (attempt in 0..retryDelays.size) {
      currentCoroutineContext().ensureActive()
      try {
        rebuild(batch)
        failedBatch = null
        break
      }
      catch (e: Exception) {
        rethrowControlFlowException(e)
        currentCoroutineContext().ensureActive()
        val reportFailure = failedBatch == null
        failedBatch = batch
        if (reportFailure) log.error("Could not rebuild the pyproject.toml model (${batch.reason})", e)
      }
      if (attempt < retryDelays.size) delay(retryDelays[attempt])
    }
  }
}

private val log = fileLogger()
