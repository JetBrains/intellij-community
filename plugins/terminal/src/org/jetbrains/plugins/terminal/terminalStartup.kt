// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslPath
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelExecApi.ExecuteProcessError
import com.intellij.platform.eel.ExecuteProcessException
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.spawnProcess
import com.intellij.util.PathUtil
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import com.pty4j.PtyProcess
import org.jetbrains.plugins.terminal.runner.LocalTerminalStartCommandBuilder
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

internal class TerminalStartupMoment {

  private val startupTime: Instant = Instant.now()

  fun elapsedNow(): Duration {
    return Duration.between(startupTime, Instant.now())
  }
}

internal fun ShellStartupOptions.Builder.initStartupMomentIfNeeded(): ShellStartupOptions.Builder = apply {
  startupMoment(this.startupMoment ?: TerminalStartupMoment())
}


internal fun logCommonStartupInfo(
  connector: TtyConnector,
  process: Process,
  durationBetweenStartupAndComponentResized: Duration,
  durationBetweenStartupAndConnectorCreated: Duration,
) {
  log.info("Terminal started with ${connector::class.java.name} (${process::class.java.name})" +
           ", time to UI laid out: ${durationBetweenStartupAndComponentResized.toMillis()} ms" +
           ", time to process created: ${durationBetweenStartupAndConnectorCreated.toMillis()} ms")
}

@Throws(ErrnoException::class)
internal fun startProcess(
  command: List<String>,
  envs: Map<String, String>,
  initialWorkingDirectory: Path,
  initialTermSize: TermSize,
): PtyProcess {
  return runBlockingMaybeCancellable {
    val (eelApi, workingDirectory) = getEelApi(initialWorkingDirectory, command)
    val remoteCommand = convertCommandToRemote(eelApi, command)
    doStartProcess(eelApi, remoteCommand, envs, workingDirectory, initialTermSize)
  }
}

private suspend fun convertCommandToRemote(eelApi: EelApi, command: List<String>): List<String> {
  if (isWslCommand(command)) {
    val shell = eelApi.exec.fetchLoginShellEnvVariables()["SHELL"] ?: "/bin/sh"
    return listOf(shell, LocalTerminalDirectRunner.LOGIN_CLI_OPTION, LocalTerminalStartCommandBuilder.INTERACTIVE_CLI_OPTION)
  }
  return command
}

private fun isWslCommand(command: List<String>): Boolean {
  if (SystemInfo.isWindows) {
    val exePath = command.getOrNull(0) ?: return false
    val exeFileName = PathUtil.getFileName(exePath)
    return exeFileName.equals("wsl.exe", true) || exeFileName.equals("wsl", true)
  }
  return false
}

private suspend fun getEelApi(
  workingDirectory: Path,
  command: List<String>,
): Pair<EelApi, Path> {
  val wslDistribNameFromCommandline = getWslDistributionNameFromCommand(command)
  if (wslDistribNameFromCommandline != null) {
    val wslDistribNameFromWorkingDirectory = WslPath.parseWindowsUncPath(workingDirectory.toString())?.distributionId
    if (wslDistribNameFromCommandline != wslDistribNameFromWorkingDirectory) {
      val wslRootPath = WSLDistribution(wslDistribNameFromCommandline).getUNCRootPath()
      val eelApi = wslRootPath.getEelDescriptor().upgrade()
      val userHome = runCatching { eelApi.exec.fetchLoginShellEnvVariables()["HOME"] }.getOrNull()
      return eelApi to wslRootPath.resolve(userHome ?: ".")
    }
  }
  return workingDirectory.getEelDescriptor().upgrade() to workingDirectory
}

private fun getWslDistributionNameFromCommand(command: List<String>): String? {
  if (isWslCommand(command)) {
    val distributionOptionName = command.getOrNull(1)
    if (distributionOptionName == "-d" || distributionOptionName == "--distribution") {
      return command.getOrNull(2)
    }
  }
  return null
}

@Throws(ErrnoException::class)
private suspend fun doStartProcess(
  eelApi: EelApi,
  command: List<String>,
  envs: Map<String, String>,
  workingDirectory: Path,
  initialTermSize: TermSize,
): PtyProcess {
  val execOptions = eelApi.exec.spawnProcess(command.first())
    .args(command.takeLast(command.size - 1))
    .env(envs)
    .workingDirectory(workingDirectory.asEelPath())
    .ptyOrStdErrSettings(EelExecApi.Pty(initialTermSize.columns, initialTermSize.rows, true))
  return try {
    execOptions.eelIt().convertToJavaProcess() as PtyProcess
  } catch (e : ExecuteProcessException) {
    throw ErrnoException(e)
  }
}

internal fun shouldUseEelApi(): Boolean {
  return `is`("terminal.use.EelApi", false)
}

internal class ErrnoException(val error: ExecuteProcessException): Exception(error.message)

private val log: Logger = logger<AbstractTerminalRunner<*>>()
