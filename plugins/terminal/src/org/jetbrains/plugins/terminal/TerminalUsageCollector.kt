// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal

import com.intellij.internal.statistic.eventLog.FeatureUsageGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext
import com.intellij.internal.statistic.utils.createData
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.PathUtil
import java.util.*

private var GROUP =  FeatureUsageGroup("statistics.terminalShell",1)

class TerminalUsageTriggerCollector {
  companion object {
    @JvmStatic
    fun trigger(project: Project, featureId: String, context: FUSUsageContext) {
      FeatureUsageLogger.log(GROUP, featureId, createData(project, context))
    }

    @JvmStatic
    fun getShellNameForStat(shellName: String?): String {
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
