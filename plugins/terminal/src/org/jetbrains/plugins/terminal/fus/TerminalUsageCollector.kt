// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.fus

import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.collectors.fus.TerminalFusAwareHandler
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.Version
import com.intellij.terminal.TerminalShellCommandHandler
import com.intellij.util.PathUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.util.ShellType
import java.util.*
import kotlin.time.Duration

@ApiStatus.Internal
object TerminalUsageTriggerCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup(GROUP_ID, 26)

  private val TERMINAL_COMMAND_HANDLER_FIELD = EventFields.Class("terminalCommandHandler")
  private val RUN_ANYTHING_PROVIDER_FIELD = EventFields.Class("runAnythingProvider")
  private val OS_VERSION_FIELD = EventFields.StringValidatedByRegexpReference("os-version", "version")
  private val SHELL_STR_FIELD = EventFields.String("shell", KNOWN_SHELLS.toList())
  private val SHELL_TYPE_FIELD = EventFields.Enum<ShellType>("shell")
  private val BLOCK_TERMINAL_FIELD = EventFields.Boolean("new_terminal")
  private val EXIT_CODE_FIELD = EventFields.Int("exit_code")
  private val EXECUTION_TIME_FIELD = EventFields.Long("execution_time", "Time in milliseconds")

  private val sshExecEvent = GROUP.registerEvent("ssh.exec")
  private val terminalSmartCommandExecutedEvent = GROUP.registerVarargEvent("terminal.smart.command.executed",
                                                                            TERMINAL_COMMAND_HANDLER_FIELD,
                                                                            RUN_ANYTHING_PROVIDER_FIELD)
  private val terminalSmartCommandNotExecutedEvent = GROUP.registerVarargEvent("terminal.smart.command.not.executed",
                                                                               TERMINAL_COMMAND_HANDLER_FIELD,
                                                                               RUN_ANYTHING_PROVIDER_FIELD)
  private val localExecEvent = GROUP.registerEvent("local.exec",
                                                   OS_VERSION_FIELD,
                                                   SHELL_STR_FIELD,
                                                   BLOCK_TERMINAL_FIELD)

  /** New Terminal only event with additional information about shell version and plugins */
  private val shellStartedEvent = GROUP.registerVarargEvent("local.shell.started",
                                                            OS_VERSION_FIELD,
                                                            SHELL_STR_FIELD,
                                                            TerminalShellInfoStatistics.shellVersionField,
                                                            TerminalShellInfoStatistics.promptThemeField,
                                                            TerminalShellInfoStatistics.isOhMyZshField,
                                                            TerminalShellInfoStatistics.isOhMyPoshField,
                                                            TerminalShellInfoStatistics.isP10KField,
                                                            TerminalShellInfoStatistics.isStarshipField,
                                                            TerminalShellInfoStatistics.isSpaceshipField,
                                                            TerminalShellInfoStatistics.isPreztoField,
                                                            TerminalShellInfoStatistics.isOhMyBashField,
                                                            TerminalShellInfoStatistics.isBashItField)


  private val commandStartedEvent = GROUP.registerEvent("terminal.command.executed",
                                                        TerminalCommandUsageStatistics.commandExecutableField,
                                                        TerminalCommandUsageStatistics.subCommandField,
                                                        BLOCK_TERMINAL_FIELD,
                                                        "Fired each time when command is started")


  private val timespanFinishedEvent = GROUP.registerEvent("terminal.timespan.finished",
                                                          SHELL_TYPE_FIELD,
                                                          EventFields.Enum<TimeSpanType>("time_span_type"),
                                                          EventFields.DurationMs,
                                                          "Logs performance/responsiveness metrics")

  private val commandFinishedEvent = GROUP.registerVarargEvent("terminal.command.finished",
                                                               "Fired each time when command is finished. New Terminal only.",
                                                               TerminalCommandUsageStatistics.commandExecutableField,
                                                               TerminalCommandUsageStatistics.subCommandField,
                                                               EXIT_CODE_FIELD,
                                                               EXECUTION_TIME_FIELD)

  private val promotionShownEvent = GROUP.registerEvent("promotion.shown")
  private val promotionGotItClickedEvent = GROUP.registerEvent("promotion.got.it.clicked")

  private val blockTerminalSwitchedEvent = GROUP.registerEvent("new.terminal.switched",
                                                               EventFields.Boolean("enabled"),
                                                               EventFields.Enum<BlockTerminalSwitchPlace>("switch_place"))
  private val feedbackSurveyEvent = GROUP.registerEvent("feedback.event.happened",
                                                        EventFields.Enum<TerminalFeedbackEvent>("event_type"),
                                                        EventFields.Enum<TerminalFeedbackMoment>("moment"))

  private val commandGenerationEvent = GROUP.registerEvent("command.generation.event.happened",
                                                           EventFields.Enum<TerminalCommandGenerationEvent>("event_type"),
                                                           "Events related to generate command from natural language feature of New Terminal")

  @JvmStatic
  fun triggerSshShellStarted(project: Project) = sshExecEvent.log(project)

  @JvmStatic
  fun triggerCommandStarted(project: Project, userCommandLine: String, isBlockTerminal: Boolean) {
    val commandData = TerminalCommandUsageStatistics.getLoggableCommandData(userCommandLine)
    commandStartedEvent.log(project, commandData?.command, commandData?.subCommand, isBlockTerminal)
  }

  @JvmStatic
  internal fun logBlockTerminalTimeSpanFinished(project: Project?, shellType: ShellType, timeSpanType: TimeSpanType, duration: Duration) {
    timespanFinishedEvent.log(project, shellType, timeSpanType, duration.inWholeMilliseconds)
  }

  fun triggerCommandFinished(project: Project, userCommandLine: String, exitCode: Int, executionTime: Duration) {
    val commandData = TerminalCommandUsageStatistics.getLoggableCommandData(userCommandLine)
    commandFinishedEvent.log(project,
                             TerminalCommandUsageStatistics.commandExecutableField with commandData?.command,
                             TerminalCommandUsageStatistics.subCommandField with commandData?.subCommand,
                             EXIT_CODE_FIELD with exitCode,
                             EXECUTION_TIME_FIELD with executionTime.inWholeMilliseconds)
  }

  @JvmStatic
  fun triggerSmartCommand(project: Project,
                          workingDirectory: String?,
                          localSession: Boolean,
                          command: String,
                          handler: TerminalShellCommandHandler,
                          executed: Boolean) {
    val data: MutableList<EventPair<*>> = mutableListOf(TERMINAL_COMMAND_HANDLER_FIELD.with(handler::class.java))

    if (handler is TerminalFusAwareHandler) {
      handler.fillData(project, workingDirectory, localSession, command, data)
    }

    if (executed) {
      terminalSmartCommandExecutedEvent.log(project, data)
    }
    else {
      terminalSmartCommandNotExecutedEvent.log(project, data)
    }
  }

  @JvmStatic
  fun triggerLocalShellStarted(project: Project, shellCommand: Array<String>, isBlockTerminal: Boolean) {
    localExecEvent.log(project,
                       Version.parseVersion(SystemInfo.OS_VERSION)?.toCompactString() ?: "unknown",
                       getShellNameForStat(shellCommand.firstOrNull()),
                       isBlockTerminal)
    if (isBlockTerminal) {
      val propertiesComponent = PropertiesComponent.getInstance()
      val version = ApplicationInfo.getInstance().build.asStringWithoutProductCodeAndSnapshot()
      propertiesComponent.setValue(BLOCK_TERMINAL_LAST_USED_VERSION, version)
      propertiesComponent.setValue(BLOCK_TERMINAL_LAST_USED_DATE, (System.currentTimeMillis() / 1000).toInt(), 0)
    }
  }

  /** New Terminal only event with additional information about shell version and plugins */
  internal fun triggerLocalShellStarted(project: Project, shellName: String, shellInfo: TerminalShellInfoStatistics.LoggableShellInfo) {
    val osVersion = Version.parseVersion(SystemInfo.OS_VERSION)?.toCompactString() ?: "unknown"
    shellStartedEvent.log(project,
                          OS_VERSION_FIELD with osVersion,
                          SHELL_STR_FIELD with shellName.lowercase(),
                          TerminalShellInfoStatistics.shellVersionField with shellInfo.shellVersion,
                          TerminalShellInfoStatistics.promptThemeField with shellInfo.promptTheme,
                          TerminalShellInfoStatistics.isOhMyZshField with shellInfo.isOhMyZsh,
                          TerminalShellInfoStatistics.isOhMyPoshField with shellInfo.isOhMyPosh,
                          TerminalShellInfoStatistics.isP10KField with shellInfo.isP10K,
                          TerminalShellInfoStatistics.isStarshipField with shellInfo.isStarship,
                          TerminalShellInfoStatistics.isSpaceshipField with shellInfo.isSpaceship,
                          TerminalShellInfoStatistics.isPreztoField with shellInfo.isPrezto,
                          TerminalShellInfoStatistics.isOhMyBashField with shellInfo.isOhMyBash,
                          TerminalShellInfoStatistics.isBashItField with shellInfo.isBashIt)
  }

  internal fun triggerPromotionShown(project: Project) {
    promotionShownEvent.log(project)
  }

  internal fun triggerPromotionGotItClicked(project: Project) {
    promotionGotItClickedEvent.log(project)
  }

  @JvmStatic
  internal fun triggerBlockTerminalSwitched(project: Project, enabled: Boolean, place: BlockTerminalSwitchPlace) {
    blockTerminalSwitchedEvent.log(project, enabled, place)
  }

  internal fun triggerFeedbackSurveyEvent(project: Project, event: TerminalFeedbackEvent, moment: TerminalFeedbackMoment) {
    feedbackSurveyEvent.log(project, event, moment)
  }

  fun triggerCommandGenerationEvent(project: Project, event: TerminalCommandGenerationEvent) {
    commandGenerationEvent.log(project, event)
  }

  @JvmStatic
  private fun getShellNameForStat(shellName: String?): String {
    if (shellName == null) return "unspecified"
    var name = shellName.trimStart()
    val ind = name.indexOf(" ")
    name = if (ind < 0) name else name.substring(0, ind)
    if (SystemInfo.isFileSystemCaseSensitive) {
      name = name.lowercase(Locale.ENGLISH)
    }
    name = PathUtil.getFileName(name)
    name = trimKnownExt(name)
    return if (KNOWN_SHELLS.contains(name)) name else "other"
  }

  private fun trimKnownExt(name: String): String {
    val ext = PathUtil.getFileExtension(name)
    return if (ext != null && KNOWN_EXTENSIONS.contains(ext)) name.substring(0, name.length - ext.length - 1) else name
  }
}

internal enum class BlockTerminalSwitchPlace {
  SETTINGS, TOOLWINDOW_OPTIONS
}

internal enum class TerminalFeedbackEvent {
  NOTIFICATION_SHOWN, DIALOG_SHOWN, FEEDBACK_SENT
}

internal enum class TerminalFeedbackMoment {
  ON_DISABLING, AFTER_USAGE
}

@ApiStatus.Internal
enum class TerminalCommandGenerationEvent {
  MODE_ENABLED, MODE_DISABLED, GENERATION_FINISHED, GENERATION_INTERRUPTED, GENERATION_FAILED
}

internal enum class TimeSpanType(val description: String) {
  FROM_STARTUP_TO_SHOWN_CURSOR("time from startup to terminal cursor shown in initialization block"),
  FROM_STARTUP_TO_READY_PROMPT("time from startup to prompt ready for command input"),
  FROM_COMMAND_SUBMIT_TO_VISUALLY_STARTED("time from command submitted by user to command visually started"),
  FROM_COMMAND_SUBMIT_TO_ACTUALLY_STARTED("time from command submitted by user to command actually started in shell"),
  FROM_TEXT_IN_BUFFER_TO_TEXT_VISIBLE("time from command output text is read into text buffer to output is visible"),
}

private const val GROUP_ID = "terminalShell"

private val KNOWN_SHELLS = setOf("unspecified",
                                 "other",
                                 "activate",
                                 "anaconda3",
                                 "ash",
                                 "bash",
                                 "bbsh",
                                 "cexec",
                                 "cmd",
                                 "cmder",
                                 "cmder_shell",
                                 "csh",
                                 "cygwin",
                                 "dash",
                                 "es",
                                 "eshell",
                                 "fish",
                                 "fsh",
                                 "git",
                                 "git-bash",
                                 "git-cmd",
                                 "hamilton",
                                 "init",
                                 "ion",
                                 "ksh",
                                 "miniconda3",
                                 "mksh",
                                 "msys2_shell",
                                 "nushell",
                                 "powershell",
                                 "pwsh",
                                 "rc",
                                 "scsh",
                                 "sh",
                                 "tcsh",
                                 "ubuntu",
                                 "ubuntu1804",
                                 "wsl",
                                 "xonsh",
                                 "zsh")
private val KNOWN_EXTENSIONS = setOf("exe", "bat", "cmd")

private const val BLOCK_TERMINAL_LAST_USED_VERSION = "BLOCK_TERMINAL_LAST_USED_VERSION"
/** Timestamp in seconds */
private const val BLOCK_TERMINAL_LAST_USED_DATE = "BLOCK_TERMINAL_LAST_USED_DATE"