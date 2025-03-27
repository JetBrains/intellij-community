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

  @ApiStatus.Internal
  fun logBackendLatency(inputEventId: Int, duration: Duration) {
    backendTypingLatencyEvent.log(
      INPUT_EVENT_ID_FIELD with inputEventId,
      DURATION_FIELD with duration
    )
  }

  @ApiStatus.Internal
  fun logBackendMinOutputLatency(sessionId: UID, charIndex: Long, duration: Duration) {
    backendMinOutputLatencyEvent.log(
      SESSION_ID with sessionId,
      CHAR_INDEX with charIndex,
      DURATION_FIELD with duration
    )
  }

  @ApiStatus.Internal
  fun logBackendMaxOutputLatency(sessionId: UID, charIndex: Long, duration: Duration) {
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
interface FrontendOutputActivity {
  fun eventReceived(event: TerminalContentUpdatedEvent)
  fun beforeModelUpdate()
  fun afterModelUpdate()
}

private val frontendTypingActivityId = AtomicInteger()
private var currentKeyEventTypingActivity: FrontendTypingActivityImpl? = null
private val frontendTypingActivityByInputEvent = ConcurrentHashMap<IdentityWrapper<TerminalInputEvent>, FrontendTypingActivityImpl>()

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
