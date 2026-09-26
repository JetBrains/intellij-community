// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.settings.pandoc

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.util.ExecUtil
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.util.EnvironmentUtil
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import org.intellij.plugins.markdown.MarkdownBundle
import java.nio.file.Path
import kotlin.io.path.exists

@OptIn(LowLevelLocalMachineAccess::class)
internal object PandocExecutableDetector {
  private const val WIN_EXECUTABLE = "pandoc.exe"
  private const val WIN_PANDOC_DIR_NAME = "Pandoc"
  private const val UNIX_EXECUTABLE = "pandoc"

  private val PANDOC_VERSION_OUTPUT_FIRST_LINE = "pandoc(?:.exe)? .*".toRegex()

  fun obtainPandocVersion(project: Project, executable: String = "pandoc"): String? {
    return ProgressManager.getInstance().run(GetVersionPandocTask(project, executable))
  }

  fun detect(project: Project): String? {
    if (!TrustedProjects.isProjectTrusted(project)) return null

    val executableFromPath = PathEnvironmentVariableUtil.findFirst("pandoc")

    return when {
      executableFromPath != null -> executableFromPath.toString()
      OS.CURRENT == OS.Windows -> detectForWindows() ?: ""
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
      }
      catch (_: Throwable) {
        return null
      }
    }

    private fun extractVersion(lines: List<String>): String? {
      val line = lines.first()
      if (!line.matches(PANDOC_VERSION_OUTPUT_FIRST_LINE)) {
        return null
      }
      return line.substringAfter(' ')
    }
  }

  private fun detectForUnix(): String? {
    val paths = listOf(
      "/usr/local/bin",
      "/opt/local/bin",
      "/usr/bin",
      "/opt/bin"
    )
    return paths.asSequence()
      .map { Path.of(it, UNIX_EXECUTABLE) }
      .firstOrNull { it.exists() }
      ?.toString()
  }

  private fun detectForWindows(): String? {
    val envVars = listOf(
      "LOCALAPPDATA",
      "ProgramFiles",
      "ProgramFiles(x86)",
      "HOMEPATH"
    )
    return envVars.asSequence()
      .mapNotNull { EnvironmentUtil.getValue(it)?.let { dir -> Path.of(dir, WIN_PANDOC_DIR_NAME, WIN_EXECUTABLE) } }
      .firstOrNull { it.exists() }
      ?.toString()
  }
}
