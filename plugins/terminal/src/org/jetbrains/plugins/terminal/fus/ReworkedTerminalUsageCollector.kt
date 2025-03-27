// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.fus

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Version
import com.intellij.platform.rpc.UID
import com.intellij.util.system.OS
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.fus.TerminalShellInfoStatistics.KNOWN_SHELLS
import org.jetbrains.plugins.terminal.fus.TerminalShellInfoStatistics.getShellNameForStat
import kotlin.time.Duration
import kotlin.time.DurationUnit

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

  @ApiStatus.Internal
  fun logFrontendLatency(inputEventId: Int, duration: Duration) {
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

  @ApiStatus.Internal
  fun logFrontendOutputLatency(sessionId: UID, firstCharIndex: Long, lastCharIndex: Long, duration: Duration, repainted: Boolean) {
    frontendOutputLatencyEvent.log(
      SESSION_ID with sessionId,
      FIRST_CHAR_INDEX with firstCharIndex,
      LAST_CHAR_INDEX with lastCharIndex,
      DURATION_FIELD with duration,
      REPAINTED_FIELD with repainted,
    )
  }
}
