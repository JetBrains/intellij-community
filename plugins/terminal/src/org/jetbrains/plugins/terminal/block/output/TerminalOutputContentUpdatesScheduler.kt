// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.output

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.util.Disposer
import com.jediterm.terminal.model.TerminalTextBuffer
import kotlinx.coroutines.*
import org.jetbrains.plugins.terminal.block.session.TerminalModel.Companion.withLock
import org.jetbrains.plugins.terminal.block.util.ActionCoordinator
import org.jetbrains.plugins.terminal.fus.TerminalUsageTriggerCollector
import org.jetbrains.plugins.terminal.fus.TimeSpanType
import org.jetbrains.plugins.terminal.util.ShellIntegration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Tracks the changes of the terminal output and schedules [applyUpdate] calls when something is changed.
 *
 * Starts tracking and scheduling on [startUpdating] and finishes on [finishUpdating].
 * All already scheduled updates are canceled on [coroutineScope] cancellation.
 */
internal class TerminalOutputContentUpdatesScheduler(
  private val textBuffer: TerminalTextBuffer,
  private val shellIntegration: ShellIntegration,
  private val coroutineScope: CoroutineScope,
  private val applyUpdate: (PartialCommandOutput) -> Unit,
) {
  // Variables are guarded by the TerminalTextBuffer lock.
  private var changesTracker: TerminalOutputChangesTracker? = null
  private var trackerDisposable: Disposable = Disposer.newDisposable()
  private var updatingJob: Job? = null

  var finished: Boolean = false
    private set

  fun startUpdating() = textBuffer.withLock {
    val tracker = TerminalOutputChangesTracker(
      textBuffer,
      shellIntegration,
      trackerDisposable,
      onUpdateStart = { metricTextInBufferToTextVisible.started(Unit, TimeSource.Monotonic.markNow()) }
    )
    changesTracker = tracker

    val job = coroutineScope.launch {
      // Schedule the first update with a small delay.
      // So fast commands might finish already, and there will be no blinking
      // because of several fast document updates.
      delay(100)

      // Collect the changes not faster than we are able to process them.
      while (true) {
        val partialChange = tracker.collectChangedOutputOrWait()

        scheduleChangeApplying(partialChange).join()
      }
    }

    job.invokeOnCompletion {
      Disposer.dispose(trackerDisposable)
    }
    updatingJob = job
  }

  private fun scheduleChangeApplying(output: PartialCommandOutput): Job {
    // Launch it in the new scope to not cancel already scheduled requests on `finishUpdating`.
    return coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      applyUpdate(output)
      metricTextInBufferToTextVisible.finished(Unit)
    }
  }

  fun finishUpdating(): PartialCommandOutput? = textBuffer.withLock {
    val tracker = changesTracker ?: error("Finish updating called before start updating")
    changesTracker = null
    updatingJob?.cancel()
    finished = true

    tracker.collectChangedOutputOrNull()
  }

  private val metricTextInBufferToTextVisible = ActionCoordinator<Unit, TimeMark>(
    10,
    onActionComplete = { unit, startTime ->
      TerminalUsageTriggerCollector.logBlockTerminalTimeSpanFinished(
        null,
        shellIntegration.shellType,
        TimeSpanType.FROM_TEXT_IN_BUFFER_TO_TEXT_VISIBLE,
        startTime.elapsedNow()
      )
    },
    onActionDiscarded = { unit, startTime ->
      // do nothing
    },
    onActionUnknown = { unit ->
      // do nothing
    }
  )

}
