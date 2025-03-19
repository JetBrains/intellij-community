// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.fus

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Version
import com.intellij.platform.rpc.UID
import com.intellij.terminal.session.TerminalContentUpdatedEvent
import com.intellij.terminal.session.TerminalInputEvent
import com.intellij.terminal.session.TerminalSession
import com.intellij.terminal.session.TerminalWriteBytesEvent
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.system.OS
import fleet.multiplatform.shims.ConcurrentHashMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.session.FrontendTerminalSession
import org.jetbrains.plugins.terminal.fus.TerminalShellInfoStatistics.KNOWN_SHELLS
import org.jetbrains.plugins.terminal.fus.TerminalShellInfoStatistics.getShellNameForStat
import java.awt.event.KeyEvent
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private const val GROUP_ID = "terminal"

@ApiStatus.Internal
object ReworkedTerminalUsageCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup(GROUP_ID, 1)

  private val OS_VERSION_FIELD = EventFields.StringValidatedByRegexpReference("os-version", "version")
  private val SHELL_STR_FIELD = EventFields.String("shell", KNOWN_SHELLS.toList())
  private val EXIT_CODE_FIELD = EventFields.Int("exit_code")
  private val EXECUTION_TIME_FIELD = EventFields.Long("execution_time", "Time in milliseconds")
  private val INPUT_EVENT_ID_FIELD = EventFields.Int("input_event_id")
  private val SESSION_ID = EventFields.Int("session_id")
  private val CHAR_INDEX = EventFields.Long("char_index")
  private val FIRST_CHAR_INDEX = EventFields.Long("first_char_index")
  private val LAST_CHAR_INDEX = EventFields.Long("last_char_index")
  private val DURATION_FIELD = EventFields.createDurationField(DurationUnit.MICROSECONDS, "duration_micros")
  private val REPAINTED_FIELD = EventFields.Boolean("editor_repainted")

  private val localShellStartedEvent = GROUP.registerEvent("local.exec",
                                                           OS_VERSION_FIELD,
                                                           SHELL_STR_FIELD)

  private val commandStartedEvent = GROUP.registerEvent("terminal.command.executed",
                                                        TerminalCommandUsageStatistics.commandExecutableField,
                                                        TerminalCommandUsageStatistics.subCommandField,
                                                        "Fired each time when command is started")

  private val commandFinishedEvent = GROUP.registerVarargEvent("terminal.command.finished",
                                                               "Fired each time when command is finished",
                                                               TerminalCommandUsageStatistics.commandExecutableField,
                                                               TerminalCommandUsageStatistics.subCommandField,
                                                               EXIT_CODE_FIELD,
                                                               EXECUTION_TIME_FIELD)

  private val frontendTypingLatencyEvent = GROUP.registerVarargEvent(
    "terminal.frontend.typing.latency",
    INPUT_EVENT_ID_FIELD,
    DURATION_FIELD,
  )

  private val backendTypingLatencyEvent = GROUP.registerVarargEvent(
    "terminal.backend.typing.latency",
    INPUT_EVENT_ID_FIELD,
    DURATION_FIELD,
  )

  private val backendMinOutputLatencyEvent = GROUP.registerVarargEvent(
    "terminal.backend.min.output.latency",
    SESSION_ID,
    CHAR_INDEX,
    DURATION_FIELD,
  )

  private val backendMaxOutputLatencyEvent = GROUP.registerVarargEvent(
    "terminal.backend.max.output.latency",
    SESSION_ID,
    CHAR_INDEX,
    DURATION_FIELD,
  )

  private val frontendOutputLatencyEvent = GROUP.registerVarargEvent(
    "terminal.frontend.output.latency",
    SESSION_ID,
    FIRST_CHAR_INDEX,
    LAST_CHAR_INDEX,
    DURATION_FIELD,
    REPAINTED_FIELD,
  )

  @JvmStatic
  fun logLocalShellStarted(project: Project, shellCommand: Array<String>) {
    localShellStartedEvent.log(project,
                               Version.parseVersion(OS.CURRENT.version)?.toCompactString() ?: "unknown",
                               getShellNameForStat(shellCommand.firstOrNull()))
  }

  @JvmStatic
  fun logCommandStarted(project: Project, userCommandLine: String) {
    val commandData = TerminalCommandUsageStatistics.getLoggableCommandData(userCommandLine)
    commandStartedEvent.log(project, commandData?.command, commandData?.subCommand)
  }

  fun logCommandFinished(project: Project, userCommandLine: String, exitCode: Int, executionTime: Duration) {
    val commandData = TerminalCommandUsageStatistics.getLoggableCommandData(userCommandLine)
    commandFinishedEvent.log(project,
                             TerminalCommandUsageStatistics.commandExecutableField with commandData?.command,
                             TerminalCommandUsageStatistics.subCommandField with commandData?.subCommand,
                             EXIT_CODE_FIELD with exitCode,
                             EXECUTION_TIME_FIELD with executionTime.inWholeMilliseconds)
  }

  internal fun logFrontendLatency(inputEventId: Int, duration: Duration) {
    frontendTypingLatencyEvent.log(
      INPUT_EVENT_ID_FIELD with inputEventId,
      DURATION_FIELD with duration
    )
  }

  internal fun logBackendLatency(inputEventId: Int, duration: Duration) {
    backendTypingLatencyEvent.log(
      INPUT_EVENT_ID_FIELD with inputEventId,
      DURATION_FIELD with duration
    )
  }

  internal fun logBackendMinOutputLatency(sessionId: UID, charIndex: Long, duration: Duration) {
    backendMinOutputLatencyEvent.log(
      SESSION_ID with sessionId,
      CHAR_INDEX with charIndex,
      DURATION_FIELD with duration
    )
  }

  internal fun logBackendMaxOutputLatency(sessionId: UID, charIndex: Long, duration: Duration) {
    backendMaxOutputLatencyEvent.log(
      SESSION_ID with sessionId,
      CHAR_INDEX with charIndex,
      DURATION_FIELD with duration
    )
  }

  internal fun logFrontendOutputLatency(sessionId: UID, firstCharIndex: Long, lastCharIndex: Long, duration: Duration, repainted: Boolean) {
    frontendOutputLatencyEvent.log(
      SESSION_ID with sessionId,
      FIRST_CHAR_INDEX with firstCharIndex,
      LAST_CHAR_INDEX with lastCharIndex,
      DURATION_FIELD with duration,
      REPAINTED_FIELD with repainted,
    )
  }

  fun startFrontendTypingActivity(e: KeyEvent): FrontendTypingActivity? {
    ThreadingAssertions.softAssertEventDispatchThread()
    if (e.id != KeyEvent.KEY_TYPED) return null
    val activity = FrontendTypingActivityImpl(frontendTypingActivityId.incrementAndGet())
    currentKeyEventTypingActivity = activity
    return activity
  }

  fun getCurrentKeyEventTypingActivityOrNull(): FrontendTypingActivity? {
    ThreadingAssertions.softAssertEventDispatchThread()
    return currentKeyEventTypingActivity
  }

  fun getFrontendTypingActivityOrNull(event: TerminalInputEvent): FrontendTypingActivity? {
    return frontendTypingActivityByInputEvent[event.toIdentity()]
  }

  fun tryStartBackendTypingActivity(event: TerminalWriteBytesEvent) {
    val id = event.id ?: return
    val bytes = event.bytes
    val activity = BackendTypingActivityImpl(id, bytes)
    backendTypingActivityByByteArray[bytes] = activity
  }

  @JvmStatic
  fun getBackendTypingActivityOrNull(bytes: ByteArray): BackendTypingActivity? {
    return backendTypingActivityByByteArray[bytes]
  }

  @JvmStatic
  fun startBackendOutputActivity(): BackendOutputActivity {
    return BackendOutputActivityImpl()
  }

  @JvmStatic
  fun startFrontendOutputActivity(
    sessionFuture: CompletableFuture<TerminalSession>,
    outputEditor: EditorImpl,
    alternateBufferEditor: EditorImpl,
  ): FrontendOutputActivity {
    return FrontendOutputActivityImpl(sessionFuture, outputEditor, alternateBufferEditor)
  }
}

@ApiStatus.Internal
interface FrontendTypingActivity {
  val id: Int
  fun startTerminalInputEventProcessing(writeBytesEvent: TerminalWriteBytesEvent)
  fun finishKeyEventProcessing()
  fun reportDuration()
  fun finishTerminalInputEventProcessing()
}

@ApiStatus.Internal
interface BackendTypingActivity {
  val id: Int
  fun reportDuration()
  fun finishBytesProcessing()
}

@ApiStatus.Internal
interface BackendOutputActivity {
  var sessionId: UID?
  fun charsRead(count: Int)
  fun charProcessingStarted()
  fun charsProcessed(count: Int)
  fun processedCharsReachedTextBuffer()
  fun charProcessingFinished()
  fun textBufferCharacterIndices(): LongRange
  fun textBufferCollected(event: TerminalContentUpdatedEvent)
  fun eventCollected(event: TerminalContentUpdatedEvent)
}

@ApiStatus.Internal
interface FrontendOutputActivity {
  fun eventReceived(event: TerminalContentUpdatedEvent)
  fun beforeModelUpdate()
  fun afterModelUpdate()
}

private val frontendTypingActivityId = AtomicInteger()
private var currentKeyEventTypingActivity: FrontendTypingActivityImpl? = null
private val frontendTypingActivityByInputEvent = ConcurrentHashMap<IdentityWrapper<TerminalInputEvent>, FrontendTypingActivityImpl>()

private val backendTypingActivityByByteArray = ConcurrentHashMap<ByteArray, BackendTypingActivityImpl>()

// used to track individual instances of data classes
private class IdentityWrapper<T : Any>(private val instance: T) {
  override fun equals(other: Any?): Boolean = instance === (other as? IdentityWrapper<T>)?.instance
  override fun hashCode(): Int = System.identityHashCode(instance)
}

private fun <T : Any> T.toIdentity(): IdentityWrapper<T> = IdentityWrapper(this)

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

private class BackendTypingActivityImpl(override val id: Int, private val bytes: ByteArray) : BackendTypingActivity {
  private val start = TimeSource.Monotonic.markNow()

  override fun reportDuration() {
    val duration = start.elapsedNow()
    ReworkedTerminalUsageCollector.logBackendLatency(
      inputEventId = id,
      duration = duration,
    )
  }

  override fun finishBytesProcessing() {
    backendTypingActivityByByteArray.remove(bytes)
  }
}

private class BackendOutputActivityImpl : BackendOutputActivity {
  private val sessionIdReference = AtomicReference<UID>()

  override var sessionId: UID?
    get() = sessionIdReference.get()
    set(value) { sessionIdReference.set(value) }

  // split into subclasses to simplify reasoning about threads and locks

  private val readingState = TerminalThreadReadingState()
  private val processingState = TerminalThreadProcessingState()
  private val textBufferState = TerminalThreadStateUnderTextBufferLock()
  private val eventFlowState = EventFlowState()

  private data class ReadRange(val range: LongRange, val time: TimeMark)
  private data class LatencyPair(val min: Latency?, val max: Latency?)
  private data class Latency(val index: Long, val duration: Duration)

  /** The part of the state that is affected by the terminal emulator thread when reading and buffering characters from the TTY. */
  private class TerminalThreadReadingState {
    /** The total number of characters read from the TTY stream and buffered. **/
    private var totalCharsRead = 0L
    /** The queue of character index ranges read from the TTY and timestamped. **/
    val readRanges = LinkedBlockingQueue<ReadRange>()

    /** Invoked every time a new buffer is read from the TTY. */
    fun charsRead(count: Int) {
      val from = totalCharsRead
      totalCharsRead += count.toLong()
      val to = totalCharsRead
      readRanges.add(ReadRange(from until to, TimeSource.Monotonic.markNow()))
    }
  }

  /**
   *  The part of the state that is affected by reading characters from the buffer.
   *
   *  Usually accessed outside the text buffer lock, always in the terminal emulator thread.
   */
  private class TerminalThreadProcessingState {
    /** The total number of characters read from the buffer and processed by the emulator. */
    var totalCharsProcessed = 0L
      private set
    /** The index of the first character processed during this iteration. `null` when we're not inside an iteration. */
    var thisProcessingIterationStart: Long? = null
      private set

    /** Invoked at the start of each iteration. */
    fun charProcessingStarted() {
      thisProcessingIterationStart = totalCharsProcessed
    }

    /** Invoked every time some characters are processed or pushed back into the buffer. In the latter case the argument is negative. */
    fun charsProcessed(count: Int) {
      totalCharsProcessed += count.toLong()
    }

    /** Invoked at the end of each iteration. */
    fun charProcessingFinished() {
      thisProcessingIterationStart = null
    }
  }

  /**
   *  The part of the state that is only updated or accessed under the text buffer lock.
   *
   *  Not necessarily accessed from the terminal emulator thread.
   */
  private class TerminalThreadStateUnderTextBufferLock {
    /**
     *  The index of the first character that will be included in the next buffer collection event.
     *  `null` if there have been no changes in the buffer since the last collection.
     */
    private var nextTextBufferCollectionStart: Long? = null
    /**
     *  The index of the last character that will be included in the next buffer collection event.
     *  `null` if there have been no changes in the buffer since the last collection.
     */
    private var nextTextBufferCollectionEnd: Long? = null

    /**
     * Invoked every time when a processed character affects the text buffer.
     *
     * Invoked on the terminal emulator thread.
     *
     * @param processingIterationStart the index of the first character processed during this processing iteration
     * @param totalCharsProcessed the total number of processed characters, the same as the index of the last character processed so far
     */
    fun processedCharsReachedTextBuffer(processingIterationStart: Long, totalCharsProcessed: Long) {
      // This is a bit complicated. The exact sequence is this:
      // 1. A processing iteration (com.intellij.terminal.backend.StopAwareTerminalStarter.FusAwareEmulator.next) starts.
      // 2. A character is processed.
      // 3. The text buffer may or may not be affected. If it's affected, this function is called.
      // 4. Steps 2-3 continue to repeat until the end of the iteration.
      // 5. The iteration ends.
      // Characters that don't affect the buffer are usually control characters. For example, cursor movement.
      // At any given moment the text buffer may be collected asynchronously from another thread.
      // But this collection happens under the same lock this function is invoked, so it's not THAT asynchronous.
      // The tricky part is to determine the range of character indices that match the buffer collection event.
      // We always know the number of chars already processed, but we don't know which characters actually affected the buffer.
      // We know for sure that when this callback is invoked, all characters processed so far are included in the text buffer.
      // But for the next callback, if we assume that the next range starts where the previous one ended,
      // we may end up with falsely large latencies because no-change characters from the previous iteration will be included as well.
      // To avoid this situation, we ignore the previous range end and start the range from the first character of THIS iteration.
      // But we must also account for the case when several processing iterations happen before the buffer is collected.
      // That's why we only set the range start if it wasn't set yet.
      if (nextTextBufferCollectionStart == null) {
        nextTextBufferCollectionStart = processingIterationStart
      }
      nextTextBufferCollectionEnd = totalCharsProcessed
    }

    /**
     * Invoked every time the text buffer is collected ("scrapped").
     *
     * Invoked _not_ from the terminal emulator thread but from the collecting coroutine.
     *
     * @return the range of the characters that have made their way into the buffer since the last collection
     */
    fun textBufferCollected(): LongRange? {
      val from = nextTextBufferCollectionStart
      val to = nextTextBufferCollectionEnd
      nextTextBufferCollectionStart = null
      nextTextBufferCollectionEnd = null
      if (from == null || to == null) {
        LOG.error("textBufferCollected, but from==$from and to==$to, both should be non-null at this point")
        return null
      }
      return from until to
    }
  }

  /**
   *  The part of the state that is affected by collecting the text buffer and further event processing.
   *
   *  Accessed from different threads, though never from the terminal emulator thread.
   */
  private class EventFlowState {
    private val collectedRanges = ConcurrentHashMap<IdentityWrapper<TerminalContentUpdatedEvent>, LongRange>()

    /**
     * Invoked every time the text buffer is collected ("scrapped").
     */
    fun textBufferCollected(event: TerminalContentUpdatedEvent) {
      collectedRanges[event.toIdentity()] = event.firstCharIndex..event.lastCharIndex
    }

    /**
     * Invoked every time the event produced by scrapping the text buffer is collected from the output flow.
     *
     * @return a pair of min/max latencies corresponding to the event char range, all non-`null` unless there's a bug somewhere
     */
    fun eventCollected(event: TerminalContentUpdatedEvent, readRanges: LinkedBlockingQueue<ReadRange>): LatencyPair? {
      val range = collectedRanges.remove(event.toIdentity()) ?: return null
      var firstCharTime: TimeMark? = null
      var lastCharTime: TimeMark? = null
      while (true) {
        val nextRange = readRanges.peek() ?: break
        if (nextRange.range.first > range.last) break // reached the part not collected yet
        if (nextRange.range.last <= range.last) { // the entire range has been collected
          readRanges.remove()
        }
        if (range.first in nextRange.range) {
          firstCharTime = nextRange.time
        }
        if (range.last in nextRange.range) {
          lastCharTime = nextRange.time
          break
        }
      }
      // The first char will have the maximum latency, as it was sitting in the buffer the longest.
      // Compute the minimum latency first, as otherwise when they're essentially equal,
      // we can end up in a situation when the maximum is less than the minimum by some microseconds.
      val minLatency = if (lastCharTime != null) {
        Latency(range.last, lastCharTime.elapsedNow())
      }
      else {
        LOG.warn("The last char ${range.last} was lost somewhere, it's a bug")
        null
      }
      val maxLatency = if (firstCharTime != null) {
        Latency(range.first, firstCharTime.elapsedNow())
      }
      else {
        LOG.warn("The first char ${range.first} was lost somewhere, it's a bug")
        null
      }
      return LatencyPair(minLatency, maxLatency)
    }
  }

  override fun charsRead(count: Int) = readingState.charsRead(count)

  override fun charProcessingStarted() = processingState.charProcessingStarted()

  override fun charsProcessed(count: Int) = processingState.charsProcessed(count)

  override fun processedCharsReachedTextBuffer() {
    // cross-state safe interaction: this function is called in the same thread that updates processingState,
    // and under the same lock that is always used to access textBufferState
    val processingIterationStart = processingState.thisProcessingIterationStart
    if (processingIterationStart == null) {
      LOG.error("processedCharsReachedTextBuffer should not be called outside of a processing iteration")
      return
    }
    textBufferState.processedCharsReachedTextBuffer(processingIterationStart, processingState.totalCharsProcessed)
  }

  override fun charProcessingFinished() = processingState.charProcessingFinished()

  // cross-state safe interaction: these two functions are called under the same text buffer lock textBufferState is updated under

  override fun textBufferCharacterIndices(): LongRange {
    return textBufferState.textBufferCollected() ?: LongRange.EMPTY
  }

  override fun textBufferCollected(event: TerminalContentUpdatedEvent) {
    eventFlowState.textBufferCollected(event)
  }

  override fun eventCollected(event: TerminalContentUpdatedEvent) {
    // cross-state safe interaction: using the shared queue to transfer read ranges
    val latency = eventFlowState.eventCollected(event, readingState.readRanges) ?: return
    // If the sessionId is not known yet, we still collect statistics to ensure a consistent state but skip reporting.
    // This can only happen very early during the session startup.
    val sessionId = this.sessionId ?: return
    if (latency.min != null) {
      ReworkedTerminalUsageCollector.logBackendMinOutputLatency(sessionId, latency.min.index, latency.min.duration)
    }
    if (latency.max != null) {
      ReworkedTerminalUsageCollector.logBackendMaxOutputLatency(sessionId, latency.max.index, latency.max.duration)
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
      sessionId.set((session as? FrontendTerminalSession?)?.id?.uid)
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

private val LOG = logger<FrontendTerminalSession>()
