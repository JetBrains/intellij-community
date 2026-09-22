// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.internal.platformBridge

import com.intellij.openapi.diagnostic.fileLogger
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Processes batches serially and retries I/O failures from [rebuild] after [retryDelays].
 * After the last retry, retains the failed batch and merges it with the next emission.
 * Logs the first I/O failure as a warning and logs again only after a successful rebuild.
 * Other exceptions and errors propagate. Cancellation discards retained work; a new session loads all roots.
 *
 * @param rebuild loads directories and rebuilds the model. An [IOException] requests a retry of the batch.
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
      catch (e: IOException) {
        currentCoroutineContext().ensureActive()
        val reportFailure = failedBatch == null
        failedBatch = batch
        if (reportFailure) log.warn("Could not rebuild the pyproject.toml model (${batch.reason})", e)
      }
      if (attempt < retryDelays.size) delay(retryDelays[attempt])
    }
  }
}

private val log = fileLogger()
