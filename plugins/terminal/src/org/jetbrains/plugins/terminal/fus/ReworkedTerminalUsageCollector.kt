// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.fus

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ToolWindowCollector
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Version
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

  private val GROUP = EventLogGroup(GROUP_ID, 7)

  private val OS_VERSION_FIELD = EventFields.StringValidatedByRegexpReference("os-version", "version")
  private val SHELL_STR_FIELD = EventFields.String("shell", KNOWN_SHELLS.toList())
  private val EXIT_CODE_FIELD = EventFields.Int("exit_code")
  private val EXECUTION_TIME_FIELD = EventFields.Long("execution_time", "Time in milliseconds")
  private val EVENT_ID_FIELD = EventFields.Int("event_id")
  private val DURATION_FIELD = EventFields.createDurationField(DurationUnit.MILLISECONDS, "duration_millis")
  private val TEXT_LENGTH_FIELD = EventFields.Int("text_length")
  private val HYPERLINK_INFO_CLASS = EventFields.Class("hyperlink_info_class")
  private val TERMINAL_OPENING_WAY = EventFields.Enum<TerminalOpeningWay>("opening_way")
  private val TABS_COUNT = EventFields.Int("tab_count")
  private val FOCUS = StringEventField.ValidatedByCustomValidationRule("counterpart", TerminalFocusRule::class.java)

  private val tabOpenedEvent = GROUP.registerEvent("tab.opened", TABS_COUNT)

  private val focusGainedEvent = GROUP.registerEvent("focus.gained", FOCUS)

  private val focusLostEvent = GROUP.registerEvent("focus.lost", FOCUS)

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

  private val sessionRestoredEvent = GROUP.registerEvent("session.restored", TABS_COUNT)

  private val hyperlinkFollowedEvent = GROUP.registerEvent("hyperlink.followed", HYPERLINK_INFO_CLASS)

  private val osVersion: String by lazy {
    Version.parseVersion(OS.CURRENT.version)?.toCompactString() ?: "unknown"
  }

  private val frontendTypingLatencyEvent = GROUP.registerVarargEvent(
    "frontend.typing.latency",
    EVENT_ID_FIELD,
    DURATION_FIELD,
    OS_VERSION_FIELD,
  )

  private val backendTypingLatencyEvent = GROUP.registerVarargEvent(
    "backend.typing.latency",
    EVENT_ID_FIELD,
    DURATION_FIELD,
    OS_VERSION_FIELD,
  )

  private val backendOutputLatencyEvent = GROUP.registerVarargEvent(
    "backend.output.latency",
    EVENT_ID_FIELD,
    DURATION_FIELD,
    OS_VERSION_FIELD,
  )

  private val frontendOutputLatencyEvent = GROUP.registerVarargEvent(
    "frontend.output.latency",
    EVENT_ID_FIELD,
    DURATION_FIELD,
    OS_VERSION_FIELD,
  )

  private val backendTextBufferCollectionLatencyEvent = GROUP.registerVarargEvent(
    "backend.text.buffer.collection.latency",
    TEXT_LENGTH_FIELD,
    DURATION_FIELD,
    OS_VERSION_FIELD,
  )

  private val backendDocumentUpdateLatencyEvent = GROUP.registerVarargEvent(
    "backend.document.update.latency",
    TEXT_LENGTH_FIELD,
    DURATION_FIELD,
    OS_VERSION_FIELD,
  )

  private val frontendDocumentUpdateLatencyEvent = GROUP.registerVarargEvent(
    "frontend.document.update.latency",
    TEXT_LENGTH_FIELD,
    DURATION_FIELD,
    OS_VERSION_FIELD,
  )

  private val startupCursorShowingLatency = GROUP.registerVarargEvent(
    "startup.cursor.showing.latency",
    TERMINAL_OPENING_WAY,
    DURATION_FIELD,
  )

  private val startupShellStartingLatency = GROUP.registerVarargEvent(
    "startup.shell.starting.latency",
    TERMINAL_OPENING_WAY,
    DURATION_FIELD,
  )

  private val startupFirstOutputLatency = GROUP.registerVarargEvent(
    "startup.first.output.latency",
    TERMINAL_OPENING_WAY,
    DURATION_FIELD,
  )

  @JvmStatic
  fun logTabOpened(project: Project, tabCount: Int) {
    tabOpenedEvent.log(project, tabCount)
  }

  @JvmStatic
  fun logFocusGained(previousFocus: String) {
    focusGainedEvent.log(previousFocus)
  }

  @JvmStatic
  fun logFocusLost(nextFocus: String) {
    focusLostEvent.log(nextFocus)
  }

  @JvmStatic
  fun logLocalShellStarted(project: Project, shellCommand: Array<String>) {
    localShellStartedEvent.log(project,
                               osVersion,
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

  @JvmStatic
  fun logSessionRestored(project: Project, tabCount: Int) {
    sessionRestoredEvent.log(project, tabCount)
  }

  fun logFrontendTypingLatency(inputEventId: Int, duration: Duration) {
    frontendTypingLatencyEvent.log(
      EVENT_ID_FIELD with inputEventId,
      DURATION_FIELD with duration,
      OS_VERSION_FIELD with osVersion,
    )
  }


  fun logBackendTypingLatency(inputEventId: Int, duration: Duration) {
    backendTypingLatencyEvent.log(
      EVENT_ID_FIELD with inputEventId,
      DURATION_FIELD with duration,
      OS_VERSION_FIELD with osVersion,
    )
  }


  fun logBackendOutputLatency(eventId: Int, duration: Duration) {
    backendOutputLatencyEvent.log(
      EVENT_ID_FIELD with eventId,
      DURATION_FIELD with duration,
      OS_VERSION_FIELD with osVersion,
    )
  }

  fun logFrontendOutputLatency(eventId: Int, duration: Duration) {
    frontendOutputLatencyEvent.log(
      EVENT_ID_FIELD with eventId,
      DURATION_FIELD with duration,
      OS_VERSION_FIELD with osVersion,
    )
  }

  fun logBackendTextBufferCollectionLatency(textLength: Int, duration: Duration) {
    backendTextBufferCollectionLatencyEvent.log(
      TEXT_LENGTH_FIELD with textLength,
      DURATION_FIELD with duration,
      OS_VERSION_FIELD with osVersion,
    )
  }

  fun logBackendDocumentUpdateLatency(textLength: Int, duration: Duration) {
    backendDocumentUpdateLatencyEvent.log(
      TEXT_LENGTH_FIELD with textLength,
      DURATION_FIELD with duration,
      OS_VERSION_FIELD with osVersion,
    )
  }

  fun logFrontendDocumentUpdateLatency(textLength: Int, duration: Duration) {
    frontendDocumentUpdateLatencyEvent.log(
      TEXT_LENGTH_FIELD with textLength,
      DURATION_FIELD with duration,
      OS_VERSION_FIELD with osVersion,
    )
  }

  fun logHyperlinkFollowed(javaClass: Class<HyperlinkInfo>) {
    hyperlinkFollowedEvent.log(javaClass)
  }

  fun logStartupCursorShowingLatency(openingWay: TerminalOpeningWay, duration: Duration) {
    startupCursorShowingLatency.log(
      TERMINAL_OPENING_WAY with openingWay,
      DURATION_FIELD with duration,
    )
  }

  fun logStartupShellStartingLatency(openingWay: TerminalOpeningWay, duration: Duration) {
    startupShellStartingLatency.log(
      TERMINAL_OPENING_WAY with openingWay,
      DURATION_FIELD with duration,
    )
  }

  fun logStartupFirstOutputLatency(openingWay: TerminalOpeningWay, duration: Duration) {
    startupFirstOutputLatency.log(
      TERMINAL_OPENING_WAY with openingWay,
      DURATION_FIELD with duration,
    )
  }
}

@ApiStatus.Internal
enum class TerminalNonToolWindowFocus {
  EDITOR,
  OTHER_COMPONENT,
  OTHER_APPLICATION,
}

internal class TerminalFocusRule : CustomValidationRule() {
  private val toolWindowRule = ToolWindowCollector.ToolWindowUtilValidator()
  private val nonToolWindowValues = TerminalNonToolWindowFocus.entries.map { it.name }

  override fun getRuleId(): String = "terminal_focus"

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    if (data in nonToolWindowValues) {
      return ValidationResultType.ACCEPTED
    }
    else {
      return toolWindowRule.validate(data, context)
    }
  }
}
