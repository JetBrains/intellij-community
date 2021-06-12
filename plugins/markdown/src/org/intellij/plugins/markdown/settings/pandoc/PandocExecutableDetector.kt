// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings.pandoc

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.MarkdownNotifier
import java.io.File

class PandocExecutableDetector {
  private val UNIX_PATHS = listOf("/usr/local/bin", "/opt/local/bin", "/usr/bin", "/opt/bin")
  private val UNIX_EXECUTABLE = "pandoc"
  private val WIN_EXECUTABLE = "pandoc.exe"

  var isCanceled = false
    private set

  fun tryToGetPandocVersion(project: Project, executable: String = "pandoc"): String? {
    val cmd = GeneralCommandLine(executable, "-v")
    var pandocVersion: String? = null

    object : Task.Modal(project, MarkdownBundle.message("markdown.settings.pandoc.executable.version.process"), true) {
      private lateinit var output: ProcessOutput

      override fun run(indicator: ProgressIndicator) {
        output = ExecUtil.execAndGetOutput(cmd)
      }

      override fun onThrowable(error: Throwable) {
        MarkdownNotifier.notifyPandocNotDetected(project)
      }

      override fun onCancel() {
        isCanceled = true
      }

      override fun onSuccess() {
        if (output.stderr.isEmpty()) {
          MarkdownNotifier.notifyPandocDetected(project)
          pandocVersion = output.stdoutLines.first()
        } else {
          MarkdownNotifier.notifyPandocDetectionFailed(project, output.stderr)
        }
      }
    }.queue()

    return pandocVersion
  }

  fun detect(): String {
    val executableFromPath = PathEnvironmentVariableUtil.findInPath(
      if (SystemInfo.isWindows) WIN_EXECUTABLE else UNIX_EXECUTABLE,
      PathEnvironmentVariableUtil.getPathVariableValue(),
      null
    )

    return when {
      executableFromPath != null -> return executableFromPath.absolutePath
      SystemInfo.isWindows -> "" //todo
      else -> detectForUnix() ?: ""
    }
  }

  private fun detectForUnix(): String? {
    UNIX_PATHS.forEach {
      val file = File(it, UNIX_EXECUTABLE)
      if (file.exists()) return file.path
    }

    return null
  }
}
