// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings.pandoc

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.EnvironmentUtil
import org.intellij.plugins.markdown.MarkdownBundle
import java.io.File

object PandocExecutableDetector {
  private const val WIN_EXECUTABLE = "pandoc.exe"
  private const val WIN_PANDOC_DIR_NAME = "Pandoc"
  private const val UNIX_EXECUTABLE = "pandoc"

  private val UNIX_PATHS by lazy {
    listOf(
      "/usr/local/bin",
      "/opt/local/bin",
      "/usr/bin",
      "/opt/bin"
    )
  }

  fun obtainPandocVersion(project: Project, executable: String = "pandoc"): String? {
    return ProgressManager.getInstance().run(GetVersionPandocTask(project, executable))
  }

  fun detect(): String {
    val executableFromPath = PathEnvironmentVariableUtil.findInPath(
      if (SystemInfo.isWindows) WIN_EXECUTABLE else UNIX_EXECUTABLE,
      PathEnvironmentVariableUtil.getPathVariableValue(),
      null
    )

    return when {
      executableFromPath != null -> return executableFromPath.absolutePath
      SystemInfo.isWindows -> detectForWindows() ?: ""
      else -> detectForUnix() ?: ""
    }
  }

  private class GetVersionPandocTask(project: Project, private val executableName: String = "pandoc"): Task.WithResult<String?, Exception>(
    project,
    MarkdownBundle.message("markdown.settings.pandoc.executable.version.process"),
    true
  ) {
    override fun compute(indicator: ProgressIndicator): String? {
      val command = GeneralCommandLine(executableName, "-v")
      try {
        val output = ExecUtil.execAndGetOutput(command).takeIf { it.stderr.isEmpty() }
        return output?.stdoutLines?.let(::extractVersion)
      } catch (exception: Throwable) {
        return null
      }
    }

    private fun extractVersion(lines: List<String>): String? {
      val line = lines.first()
      val firstLinePrefix = "pandoc "
      if (!line.startsWith(firstLinePrefix)) {
        return null
      }
      return line.substringAfter(firstLinePrefix)
    }
  }

  private fun detectForUnix(): String? {
    return UNIX_PATHS.asSequence().map { File(it, UNIX_EXECUTABLE) }.firstOrNull { it.exists() }?.path
  }

  private fun detectForWindows(): String? {
    val paths = listOf(
      EnvironmentUtil.getValue("LOCALAPPDATA"),
      EnvironmentUtil.getValue("ProgramFiles"),
      EnvironmentUtil.getValue("ProgramFiles(x86)"),
      EnvironmentUtil.getValue("HOMEPATH")
    )
    for (basePath in paths) {
      val path = File(basePath, WIN_PANDOC_DIR_NAME)
      val file = File(path, WIN_EXECUTABLE)
      if (file.exists()) {
        return file.path
      }
    }
    return null
  }
}
