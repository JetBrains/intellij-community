package com.intellij.terminal.frontend.fus

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.terminal.session.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.fus.*
import java.util.concurrent.ArrayBlockingQueue
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal class FrontendLatencyServiceImpl : FrontendLatencyService {
  override fun startFrontendOutputActivity(
    outputEditor: EditorImpl,
    alternateBufferEditor: EditorImpl,
  ): FrontendOutputActivity {
    return FrontendOutputActivityImpl(outputEditor, alternateBufferEditor)
  }
}

private class FrontendOutputActivityImpl(
  private val outputEditor: EditorImpl,
  private val alternateBufferEditor: EditorImpl,
) : FrontendOutputActivity {
  private val pendingEvents = ArrayBlockingQueue<ReceivedEvent>(100)
  private val pendingPaints = ArrayBlockingQueue<ReceivedEvent>(100)
  private var editorRepaintRequests = 0L
  private var editorRepaintRequestsBeforeModelUpdate = 0L

  private val latencyReporter = BatchLatencyReporter(batchSize = 100) { samples ->
    ReworkedTerminalUsageCollector.logFrontendOutputLatency(
      totalDuration = samples.totalDuration(),
      duration90 = samples.percentile(90),
      thirdLargestDuration = samples.thirdLargest(),
    )
  }

  init {
    outputEditor.setRepaintCallback { editorRepaintRequested() }
    alternateBufferEditor.setRepaintCallback { editorRepaintRequested() }
    outputEditor.setPaintCallback { editorPainted() }
    alternateBufferEditor.setPaintCallback { editorPainted() }
  }

  override fun eventReceived(event: TerminalContentUpdatedEvent) {
    pendingEvents.addDroppingOldest(ReceivedEvent(TimeSource.Monotonic.markNow(), event))
  }

  override fun beforeModelUpdate() {
    editorRepaintRequestsBeforeModelUpdate = editorRepaintRequests
  }

  private fun editorRepaintRequested() {
    ++editorRepaintRequests
  }

  override fun afterModelUpdate() {
    val repaintRequested = editorRepaintRequests > editorRepaintRequestsBeforeModelUpdate
    val editorShowing = outputEditor.component.isShowing || alternateBufferEditor.component.isShowing
    if (!editorShowing) {
      pendingPaints.clear() // editor no longer showing, so if there were unprocessed requests, they won't complete
    }
    val repaintExpected = repaintRequested && editorShowing
    while (true) {
      val pendingEvent = pendingEvents.poll() ?: break
      if (repaintExpected) {
        pendingPaints.addDroppingOldest(pendingEvent)
      }
    }
  }

  private fun editorPainted() {
    while (true) {
      val pendingPaint = pendingPaints.poll() ?: break
      reportLatency(pendingPaint)
    }
  }

  private fun reportLatency(receivedEvent: ReceivedEvent) {
    val latency = receivedEvent.time.elapsedNow()
    latencyReporter.update(latency)
  }

  private data class ReceivedEvent(val time: TimeMark, val event: TerminalContentUpdatedEvent)
}

private fun <T> ArrayBlockingQueue<T>.addDroppingOldest(element: T) {
  var overflow = false
  while (!offer(element)) {
    overflow = true
    poll()
  }
  if (overflow) {
    LOG.warn("Overflow in the frontend output activity queue, too many requests, maybe the queue is too small?")
  }
}

private val LOG = logger<FrontendLatencyService>()
