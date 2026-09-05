// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Merges a burst of calls to [schedule] into one run of [action]. The action runs when [quietPeriod]
 * passes with no new call.
 *
 * The configuration of a project can set the SDK of one module after another. Each new SDK can
 * restart every server of a tool, so one restart after the burst is enough.
 */
@ApiStatus.Internal
class PyToolChangeDebouncer(
  private val cs: CoroutineScope,
  private val quietPeriod: Duration = 2.seconds,
  private val action: suspend () -> Unit,
) {
  private val pending = AtomicReference<Job?>(null)

  /** Starts a new quiet period, and cancels the run that an earlier call scheduled. */
  fun schedule() {
    val job = cs.launch(start = CoroutineStart.LAZY) {
      delay(quietPeriod)
      action()
    }
    pending.getAndSet(job)?.cancel()
    job.start()
  }
}
