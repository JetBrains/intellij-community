package com.intellij.terminal.frontend.fus

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.platform.rpc.UID
import com.intellij.terminal.session.TerminalContentUpdatedEvent
import com.intellij.terminal.session.TerminalInputEvent
import com.intellij.terminal.session.TerminalSession
import com.intellij.terminal.session.TerminalWriteBytesEvent
import com.intellij.util.concurrency.ThreadingAssertions
import fleet.multiplatform.shims.ConcurrentHashMap
import org.jetbrains.plugins.terminal.block.reworked.session.FrontendTerminalSession
import org.jetbrains.plugins.terminal.fus.FrontendLatencyService
import org.jetbrains.plugins.terminal.fus.FrontendOutputActivity
import org.jetbrains.plugins.terminal.fus.FrontendTypingActivity
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import java.awt.event.KeyEvent
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal class FrontendLatencyServiceImpl : FrontendLatencyService {
  override fun startFrontendTypingActivity(e: KeyEvent): FrontendTypingActivity? {
    ThreadingAssertions.softAssertEventDispatchThread()
    if (e.id != KeyEvent.KEY_TYPED) return null
    val activity = FrontendTypingActivityImpl(frontendTypingActivityId.incrementAndGet())
    currentKeyEventTypingActivity = activity
    return activity
  }

  override fun getCurrentKeyEventTypingActivityOrNull(): FrontendTypingActivity? {
    ThreadingAssertions.softAssertEventDispatchThread()
    return currentKeyEventTypingActivity
  }

  override fun getFrontendTypingActivityOrNull(event: TerminalInputEvent): FrontendTypingActivity? {
    return frontendTypingActivityByInputEvent[event.toIdentity()]
  }

  override fun startFrontendOutputActivity(
    sessionFuture: CompletableFuture<TerminalSession>,
    outputEditor: EditorImpl,
    alternateBufferEditor: EditorImpl,
  ): FrontendOutputActivity {
    return FrontendOutputActivityImpl(sessionFuture, outputEditor, alternateBufferEditor)
  }
}

private val frontendTypingActivityId = AtomicInteger()
private var currentKeyEventTypingActivity: FrontendTypingActivityImpl? = null
private val frontendTypingActivityByInputEvent = ConcurrentHashMap<IdentityWrapper<TerminalInputEvent>, FrontendTypingActivityImpl>()

private class FrontendTypingActivityImpl(override val id: Int) : FrontendTypingActivity {
  private val start = TimeSource.Monotonic.markNow()
  private var writeBytesEvent: TerminalWriteBytesEvent? = null

  override fun startTerminalInputEventProcessing(writeBytesEvent: TerminalWriteBytesEvent) {
    this.writeBytesEvent = writeBytesEvent
    frontendTypingActivityByInputEvent[writeBytesEvent.toIdentity()] = this
    if (frontendTypingActivityByInputEvent.size > 10000) {
      LOG.error(Throwable(
        "Too many simultaneous frontend typing activities, likely a leak!" +
        " Ensure that startTerminalInputEventProcessing() calls are paired with finishTerminalInputEventProcessing()"
      ))
    }
  }

  override fun finishKeyEventProcessing() {
    ThreadingAssertions.softAssertEventDispatchThread()
    currentKeyEventTypingActivity = null
  }

  override fun reportDuration() {
    val duration = start.elapsedNow()
    ReworkedTerminalUsageCollector.logFrontendLatency(
      inputEventId = id,
      duration,
    )
  }

  override fun finishTerminalInputEventProcessing() {
    val inputEvent = writeBytesEvent
    if (inputEvent != null) {
      frontendTypingActivityByInputEvent.remove(inputEvent.toIdentity())
    }
  }
}

private class FrontendOutputActivityImpl(
  sessionFuture: CompletableFuture<TerminalSession>,
  private val outputEditor: EditorImpl,
  private val alternateBufferEditor: EditorImpl,
) : FrontendOutputActivity {

  private val sessionId = AtomicReference<UID?>()
  private val pendingEvents = ArrayBlockingQueue<ReceivedEvent>(100)
  private val pendingPaints = ArrayBlockingQueue<ReceivedEvent>(100)
  private var editorRepaintRequests = 0L
  private var editorRepaintRequestsBeforeModelUpdate = 0L

  init {
    sessionFuture.whenComplete { session, _ ->
      sessionId.set((session as? FrontendTerminalSession?)?.id?.eid)
    }
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
      else {
        reportLatency(pendingEvent, false)
      }
    }
  }

  private fun editorPainted() {
    while (true) {
      val pendingPaint = pendingPaints.poll() ?: break
      reportLatency(pendingPaint, true)
    }
  }

  private fun reportLatency(receivedEvent: ReceivedEvent, painted: Boolean) {
    val latency = receivedEvent.time.elapsedNow()
    val sessionId = this.sessionId.get()
    if (sessionId == null) {
      LOG.error("For some reason sessionId was not initialized, likely a bug")
      return
    }
    ReworkedTerminalUsageCollector.logFrontendOutputLatency(
      sessionId = sessionId,
      firstCharIndex = receivedEvent.event.firstCharIndex,
      lastCharIndex = receivedEvent.event.lastCharIndex,
      duration = latency,
      repainted = painted,
    )
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

// used to track individual instances of data classes
private class IdentityWrapper<T : Any>(private val instance: T) {
  override fun equals(other: Any?): Boolean = instance === (other as? IdentityWrapper<T>)?.instance
  override fun hashCode(): Int = System.identityHashCode(instance)
}

private fun <T : Any> T.toIdentity(): IdentityWrapper<T> = IdentityWrapper(this)

private val LOG = logger<FrontendLatencyService>()
