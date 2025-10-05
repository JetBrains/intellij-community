package com.intellij.python.sdk.ui.evolution.sdk

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.system.OS
import com.jetbrains.python.sdk.PySdkUtil
import com.jetbrains.python.sdk.basePath
import java.nio.file.Path


fun Module?.executeInModuleDir(executable: String, vararg arguments: String): ProcessOutput {
  val pyProjectPath = this?.basePath
  val commandLine = GeneralCommandLine(executable, *arguments)
  return PySdkUtil.getProcessOutput(commandLine, pyProjectPath, emptyMap(), 5_000)
}

fun Module?.runCmd(executablePath: Path, vararg arguments: String): ProcessOutput {
  return executeInModuleDir(executablePath.toString(), *arguments)
}


fun Module?.which(command: String): Path? {
  val processOutput = if (OS.CURRENT == OS.Windows) {
    this.executeInModuleDir("where.exe", command)
  }
  else {
    this.executeInModuleDir("command", "-v", command)
  }
  val path = processOutput.stdout.trim().takeIf { it.isNotBlank() }?.let { NioFiles.toPath(it) }

  return path
}
