// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.internal.statistic.collectors.fus.TerminalFusAwareHandler
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.Version
import com.intellij.terminal.TerminalShellCommandHandler
import com.intellij.util.PathUtil
import org.jetbrains.plugins.terminal.fus.TerminalCommandUsageStatistics
import java.util.*

object TerminalUsageTriggerCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup(GROUP_ID, 16)

  private val TERMINAL_COMMAND_HANDLER_FIELD = EventFields.Class("terminalCommandHandler")
  private val RUN_ANYTHING_PROVIDER_FIELD = EventFields.Class("runAnythingProvider")

  private val sshExecEvent = GROUP.registerEvent("ssh.exec")
  private val terminalSmartCommandExecutedEvent = GROUP.registerVarargEvent("terminal.smart.command.executed",
                                                                            TERMINAL_COMMAND_HANDLER_FIELD,
                                                                            RUN_ANYTHING_PROVIDER_FIELD)
  private val terminalSmartCommandNotExecutedEvent = GROUP.registerVarargEvent("terminal.smart.command.not.executed",
                                                                               TERMINAL_COMMAND_HANDLER_FIELD,
                                                                               RUN_ANYTHING_PROVIDER_FIELD)
  private val localExecEvent = GROUP.registerEvent("local.exec",
                                                   EventFields.StringValidatedByRegexp("os-version", "version"),
                                                   EventFields.String("shell", KNOWN_SHELLS.toList()))

  private val commandExecutedEvent = GROUP.registerEvent("terminal.command.executed",
                                                         TerminalCommandUsageStatistics.commandExecutableField,
                                                         TerminalCommandUsageStatistics.subCommandField)

  @JvmStatic
  fun triggerSshShellStarted(project: Project) = sshExecEvent.log(project)

  @JvmStatic
  fun triggerCommandExecuted(project: Project, userCommandLine: String) {
    TerminalCommandUsageStatistics.triggerCommandExecuted(commandExecutedEvent, project, userCommandLine)
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
  fun triggerLocalShellStarted(project: Project, shellCommand: Array<String>) =
    localExecEvent.log(project,
                       Version.parseVersion(SystemInfo.OS_VERSION)?.toCompactString() ?: "unknown",
                       getShellNameForStat(shellCommand.firstOrNull()))

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