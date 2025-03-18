// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.fus

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Version
import com.intellij.terminal.session.TerminalInputEvent
import com.intellij.terminal.session.TerminalWriteBytesEvent
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.system.OS
import fleet.multiplatform.shims.ConcurrentHashMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.session.FrontendTerminalSession
import org.jetbrains.plugins.terminal.fus.TerminalShellInfoStatistics.KNOWN_SHELLS
import org.jetbrains.plugins.terminal.fus.TerminalShellInfoStatistics.getShellNameForStat
import java.awt.event.KeyEvent
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.DurationUnit
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
  private val DURATION_FIELD = EventFields.createDurationField(DurationUnit.MICROSECONDS, "duration_micros")

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

private val LOG = logger<FrontendTerminalSession>()
