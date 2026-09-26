package com.intellij.terminal.frontend.fus

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.impl.TerminalTypingEvent
import com.intellij.terminal.frontend.view.impl.TerminalTypingListener
import com.intellij.terminal.frontend.view.impl.TerminalTypingTracker
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.fus.BatchLatencyReporter
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import org.jetbrains.plugins.terminal.fus.percentile
import org.jetbrains.plugins.terminal.fus.secondLargest
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import kotlin.time.DurationUnit
import kotlin.time.toDuration

// Caps the pending-timestamps queue so a stuck paint can't grow it unbounded.
private const val MAX_PENDING_TYPED_TIMESTAMPS = 100

private val LOG = fileLogger()

/**
 * Measures the time from a key press to the moment its effect is painted in [editor],
 * and reports it via [TerminalTypingLatencyListener.TOPIC] on a background coroutine, so publishing never blocks painting.
 */
@ApiStatus.Internal
fun installTypingLatencyTracker(
  terminalView: TerminalView,
  editor: EditorImpl,
  typingTracker: TerminalTypingTracker,
  coroutineScope: CoroutineScope,
) {
  // Accessed only on the EDT: filled by the typing listener below, drained by the paint callback.
  val pendingTypedTimestamps = ArrayDeque<Long>()
  val latencyEvents = Channel<Long>(capacity = MAX_PENDING_TYPED_TIMESTAMPS, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  val parentDisposable = coroutineScope.asDisposable()

  typingTracker.addTypingListener(parentDisposable, object : TerminalTypingListener {
    override fun onTypingEvent(event: TerminalTypingEvent) {
      LOG.trace { "Typing event received: $event" }
      if (event is TerminalTypingEvent.Confirmed) {
        if (pendingTypedTimestamps.size >= MAX_PENDING_TYPED_TIMESTAMPS) {
          pendingTypedTimestamps.removeFirst()
        }
        if (editor.contentComponent.isShowing) {
          pendingTypedTimestamps.addLast(event.keyEvent.awtEvent.`when`)
        }
      }
    }
  })

  editor.setPaintCallback {
    if (pendingTypedTimestamps.isNotEmpty()) {
      val paintedAt = System.currentTimeMillis()
      val latencies = pendingTypedTimestamps.map { paintedAt - it }
      pendingTypedTimestamps.clear()
      LOG.debug { "Editor painted: typing latencies (ms): $latencies" }

      for (latency in latencies) {
        latencyEvents.trySend(latency)
      }
    }
  }

  // A safeguard against the case when the editor hides between the moment of typing and the moment of painting.
  // Clear the pending events in this case.
  val hierarchyListener = HierarchyListener { event ->
    LOG.trace { "Hierarchy event received: isShowing=${editor.contentComponent.isShowing}" }
    if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L && !editor.contentComponent.isShowing) {
      pendingTypedTimestamps.clear()
    }
  }
  editor.contentComponent.addHierarchyListener(hierarchyListener)
  Disposer.register(parentDisposable) { editor.contentComponent.removeHierarchyListener(hierarchyListener) }


  val typingLatencyReporter = BatchLatencyReporter(batchSize = 50) { samples ->
    ReworkedTerminalUsageCollector.logTypingLatency(
      durationMedian = samples.percentile(50),
      duration90 = samples.percentile(90),
      secondLargestDuration = samples.secondLargest()
    )
  }
  coroutineScope.launch(CoroutineName("Typing latency reporting")) {
    for (latency in latencyEvents) {
      try {
        // Report to listeners in perf tests
        val publisher = ApplicationManager.getApplication().messageBus.syncPublisher(TerminalTypingLatencyListener.TOPIC)
        publisher.recordTypingLatency(terminalView, latency)

        // Report to FUS
        typingLatencyReporter.update(latency.toDuration(DurationUnit.MILLISECONDS))
      }
      catch (e: Exception) {
        rethrowControlFlowException(e)
        LOG.error("Exception when reporting latency", e)
      }
    }
  }
}
