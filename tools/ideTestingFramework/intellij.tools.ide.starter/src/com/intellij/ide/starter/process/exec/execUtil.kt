package com.intellij.ide.starter.process.exec

import com.intellij.ide.starter.system.SystemInfo
import com.intellij.ide.starter.utils.logOutput
import java.nio.file.Path
import kotlin.io.path.div
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

fun execGradlew(pathToProject: Path, args: List<String>) {
  val stdout = ExecOutputRedirect.ToString()
  val stderr = ExecOutputRedirect.ToString()

  val command = when (SystemInfo.isWindows) {
    true -> (pathToProject / "gradlew.bat").toString()
    false -> "./gradlew"
  }

  if (!SystemInfo.isWindows) {
    ProcessExecutor(
      presentableName = "chmod gradlew",
      workDir = pathToProject,
      timeout = 1.minutes,
      args = listOf("chmod", "+x", "gradlew"),
      stdoutRedirect = stdout,
      stderrRedirect = stderr
    ).start()
  }

  ProcessExecutor(
    presentableName = "Gradle Format",
    workDir = pathToProject,
    timeout = 1.minutes,
    args = listOf(command) + args,
    stdoutRedirect = stdout,
    stderrRedirect = stderr
  ).start()
}
