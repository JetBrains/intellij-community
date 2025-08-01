// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslPath
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.*
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.util.PathUtil
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import com.pty4j.PtyProcess
import org.jetbrains.plugins.terminal.runner.LocalTerminalStartCommandBuilder
import java.nio.file.InvalidPathException
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

@Throws(ExecuteProcessException::class)
internal fun startProcess(
  command: List<String>,
  envs: Map<String, String>,
  initialWorkingDirectory: Path,
  initialTermSize: TermSize,
): PtyProcess {
  return runBlockingMaybeCancellable {
    val context = buildStartupEelContext(initialWorkingDirectory, command)
    val eelApi = context.eelDescriptor.toEelApi()
    val remoteCommand = convertCommandToRemote(eelApi, command)
    val workingDirectory = context.workingDirectoryProvider(eelApi)
    doStartProcess(eelApi, remoteCommand, envs, workingDirectory, initialTermSize)
  }
}

private suspend fun convertCommandToRemote(eelApi: EelApi, command: List<String>): List<String> {
  if (eelApi.descriptor != LocalEelDescriptor && isWslCommand(command)) {
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

internal fun findEelDescriptor(workingDir: String?, shellCommand: List<String>): EelDescriptor {
  if (!shouldUseEelApi()) {
    return LocalEelDescriptor
  }
  if (workingDir.isNullOrBlank()) {
    log.info("Cannot find EelDescriptor due to empty working directory. Fallback to LocalEelDescriptor.")
    return LocalEelDescriptor
  }
  val workingDirectoryNioPath: Path = try {
    Path.of(workingDir)
  }
  catch (e: InvalidPathException) {
    log.warn("Cannot find EelDescriptor due to invalid working directory ($workingDir). Fallback to LocalEelDescriptor", e)
    return LocalEelDescriptor
  }
  return try {
    buildStartupEelContext(workingDirectoryNioPath, shellCommand).eelDescriptor
  }
  catch (e: Exception) {
    log.warn("Cannot find EelDescriptor: " + e.message)
    LocalEelDescriptor
  }
}

private fun buildStartupEelContext(workingDirectory: Path, command: List<String>): TerminalStartupEelContext {
  val wslDistribNameFromCommandline = getWslDistributionNameFromCommand(command)
  if (wslDistribNameFromCommandline != null) {
    val wslDistribNameFromWorkingDirectory = WslPath.parseWindowsUncPath(workingDirectory.toString())?.distributionId
    if (wslDistribNameFromCommandline != wslDistribNameFromWorkingDirectory) {
      val wslRootPath = WSLDistribution(wslDistribNameFromCommandline).getUNCRootPath()
      val eelDescriptor = wslRootPath.getEelDescriptor()
      if (eelDescriptor != LocalEelDescriptor) {
        return TerminalStartupEelContext(eelDescriptor) { eelApi ->
          eelApi.userInfo.home
        }
      }
    }
  }
  val workingDirectoryEelPath = workingDirectory.asEelPath()
  return TerminalStartupEelContext(workingDirectoryEelPath.descriptor) {
    workingDirectoryEelPath
  }
}

internal class TerminalStartupEelContext(
  val eelDescriptor: EelDescriptor,
  val workingDirectoryProvider: suspend (eelApi: EelApi) -> EelPath,
)

private fun getWslDistributionNameFromCommand(command: List<String>): String? {
  if (isWslCommand(command)) {
    val distributionOptionName = command.getOrNull(1)
    if (distributionOptionName == "-d" || distributionOptionName == "--distribution") {
      return command.getOrNull(2)
    }
  }
  return null
}

@Throws(ExecuteProcessException::class)
private suspend fun doStartProcess(
  eelApi: EelApi,
  command: List<String>,
  envs: Map<String, String>,
  workingDirectory: EelPath,
  initialTermSize: TermSize,
): PtyProcess {
  val execOptions = eelApi.exec.spawnProcess(command.first())
    .args(command.takeLast(command.size - 1))
    .env(envs)
    .workingDirectory(workingDirectory)
    .interactionOptions(EelExecApi.Pty(initialTermSize.columns, initialTermSize.rows, true))
  return execOptions.eelIt().convertToJavaProcess() as PtyProcess
}

internal fun shouldUseEelApi(): Boolean = Registry.`is`("terminal.use.EelApi", true)

private val log: Logger = logger<AbstractTerminalRunner<*>>()
