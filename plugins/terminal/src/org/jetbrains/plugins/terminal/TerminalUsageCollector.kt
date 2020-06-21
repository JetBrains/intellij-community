// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal

import com.intellij.execution.configurations.ParametersList
import com.intellij.internal.statistic.collectors.fus.os.OsVersionUsageCollector
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.PathUtil
import java.util.*

class TerminalUsageTriggerCollector {
  companion object {
    @JvmStatic
    fun triggerSshShellStarted(project: Project) {
      FUCounterUsageLogger.getInstance().logEvent(project, GROUP_ID, "ssh.exec")
    }

    @JvmStatic
    fun triggerSmartCommandExecuted(project: Project, command: String) {
      FUCounterUsageLogger.getInstance().logEvent(project, GROUP_ID, "terminal.command.executed")
      FUCounterUsageLogger.getInstance().logEvent(project, GROUP_ID, "terminal.smart.command.executed",
                                                  FeatureUsageData().addData("smart.command", ParametersList.parse(command)[0]))
    }

    @JvmStatic
    fun triggerLocalShellStarted(project: Project, shellCommand: Array<String>) {
      val osVersion = OsVersionUsageCollector.parse(SystemInfo.OS_VERSION)
      FUCounterUsageLogger.getInstance().logEvent(project, GROUP_ID, "local.exec", FeatureUsageData()
        .addData("os-version", if (osVersion == null) "unknown" else osVersion.toCompactString())
        .addData("shell", getShellNameForStat(shellCommand.firstOrNull()))
      )
    }

    @JvmStatic
    private fun getShellNameForStat(shellName: String?): String {
      if (shellName == null) return "unspecified"
      var name = shellName.trimStart()
      val ind = name.indexOf(" ")
      name = if (ind < 0) name else name.substring(0, ind)
      if (SystemInfo.isFileSystemCaseSensitive) {
        name = name.toLowerCase(Locale.ENGLISH)
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

private val KNOWN_SHELLS = setOf("activate",
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