// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal

import com.intellij.internal.statistic.collectors.fus.ClassNameRuleValidator
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
import java.util.*

class TerminalUsageTriggerCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP = EventLogGroup(GROUP_ID, 5)

    private val TERMINAL_COMMAND_HANDLER_FIELD = EventFields.StringValidatedByCustomRule("terminalCommandHandler",
                                                                                         ClassNameRuleValidator::class.java)
    private val RUN_ANYTHING_PROVIDER_FIELD = EventFields.StringValidatedByCustomRule("runAnythingProvider",
                                                                                      ClassNameRuleValidator::class.java)

    private val sshExecEvent = GROUP.registerEvent("ssh.exec")
    private val terminalCommandExecutedEvent = GROUP.registerEvent("terminal.command.executed")
    private val terminalSmartCommandExecutedEvent = GROUP.registerVarargEvent("terminal.smart.command.executed",
                                                                              TERMINAL_COMMAND_HANDLER_FIELD,
                                                                              RUN_ANYTHING_PROVIDER_FIELD)
    private val terminalSmartCommandNotExecutedEvent = GROUP.registerVarargEvent("terminal.smart.command.not.executed",
                                                                                 TERMINAL_COMMAND_HANDLER_FIELD,
                                                                                 RUN_ANYTHING_PROVIDER_FIELD)
    private val localExecEvent = GROUP.registerEvent("local.exec",
                                                     EventFields.StringValidatedByRegexp("os-version", "version"),
                                                     EventFields.String("shell", KNOWN_SHELLS.toList()))

    @JvmStatic
    fun triggerSshShellStarted(project: Project) = sshExecEvent.log(project)

    @JvmStatic
    fun triggerCommandExecuted(project: Project) = terminalCommandExecutedEvent.log(project)

    @JvmStatic
    fun triggerSmartCommand(project: Project,
                            workingDirectory: String?,
                            localSession: Boolean,
                            command: String,
                            handler: TerminalShellCommandHandler,
                            executed: Boolean) {
      val data: MutableList<EventPair<*>> = mutableListOf(TERMINAL_COMMAND_HANDLER_FIELD.with(handler::class.java.name))

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
}

private const val GROUP_ID = "terminalShell"

private val KNOWN_SHELLS = setOf("unspecified",
                                 "other",
                                 "activate",
                                 "anaconda3",
                                 "bash",
                                 "cexec",
                                 "cmd",
                                 "cmder",
                                 "cmder_shell",
                                 "cygwin",
                                 "fish",
                                 "git",
                                 "git-bash",
                                 "git-cmd",
                                 "init",
                                 "miniconda3",
                                 "msys2_shell",
                                 "powershell",
                                 "pwsh",
                                 "sh",
                                 "tcsh",
                                 "ubuntu",
                                 "ubuntu1804",
                                 "wsl",
                                 "zsh")
private val KNOWN_EXTENSIONS = setOf("exe", "bat", "cmd")