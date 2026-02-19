package com.intellij.ide.starter.process.exec

import com.intellij.tools.ide.util.common.logOutput
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes


fun executeScript(fileNameToExecute: String, projectDirPath: Path) {
  val stdout = ExecOutputRedirect.ToString()
  val stderr = ExecOutputRedirect.ToString()

  ProcessExecutor(
    presentableName = "Executing of $fileNameToExecute",
    workDir = projectDirPath,
    timeout = 20.minutes,
    args = listOf(fileNameToExecute),
    stdoutRedirect = stdout,
    stderrRedirect = stderr
  ).start()

  val commit = stdout.read().trim()
  val error = stderr.read().trim()

  logOutput("Stdout of command execution $commit")
  logOutput("Stderr of command execution $error")
}
