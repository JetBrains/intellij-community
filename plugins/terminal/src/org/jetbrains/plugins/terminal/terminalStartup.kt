// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslPath
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.*
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.utils.EelProcessExecutionResult
import com.intellij.platform.eel.provider.utils.awaitProcessResult
import com.intellij.platform.eel.provider.utils.stderrString
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.util.PathUtil
import com.intellij.util.io.awaitExit
import com.intellij.util.system.OS
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import com.pty4j.PtyProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.plugins.terminal.runner.LocalTerminalStartCommandBuilder
import org.jetbrains.plugins.terminal.util.ShellNameUtil
import org.jetbrains.plugins.terminal.util.terminalApplicationScope
import java.lang.ref.WeakReference
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

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
): Pair<PtyProcess, ShellProcessHolder> {
  return runBlockingMaybeCancellable {
    val context = buildStartupEelContext(initialWorkingDirectory, command)
    val eelApi = context.eelDescriptor.toEelApi()
    val remoteCommand = convertCommandToRemote(eelApi, command)
    val workingDirectory = context.workingDirectoryProvider(eelApi)
    val process = doStartProcess(eelApi, remoteCommand, envs, workingDirectory, initialTermSize)
    val ptyProcess = process.convertToJavaProcess() as PtyProcess
    ptyProcess to ShellProcessHolder(process.pid, WeakReference(ptyProcess), eelApi)
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

private fun buildStartupEelContext(workingDirectory: Path, shellCommand: List<String>): TerminalStartupEelContext {
  val executable = shellCommand.firstOrNull()
  if (OS.CURRENT == OS.Windows && executable != null &&
      (ShellNameUtil.isPowerShell(executable) || OSAgnosticPathUtil.isAbsoluteDosPath(executable))) {
    // Enforce running a Windows shell locally even if the project is opened in WSL.
    //
    // Although WSL can run Windows processes, it's best to avoid it:
    // 1. Command-block shell integration won't work because the order of operations is not maintained.
    //    It works when using the local environment with bundled ConPTY (IJPL-190952).
    // 2. WSL fails to run a Windows process if the executable is specified without the '.exe' extension, e.g. 'powershell'.
    // 3. WSL fails to run a Windows process specified with an absolute Windows path,
    //    e.g. 'C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe'.
    // 4. Using two interoperability layers to run a local Windows process is generally unnecessary.
    return TerminalStartupEelContext(LocalEelDescriptor) {
      EelPath.parse(workingDirectory.toString(), LocalEelDescriptor)
    }
  }
  val wslDistribNameFromCommandline = getWslDistributionNameFromCommand(shellCommand)
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
): EelProcess {
  val execOptions = eelApi.exec.spawnProcess(command.first())
    .args(command.takeLast(command.size - 1))
    .env(envs)
    .workingDirectory(workingDirectory)
    .interactionOptions(EelExecApi.Pty(initialTermSize.columns, initialTermSize.rows, true))
  return execOptions.eelIt()
}

internal fun shouldUseEelApi(): Boolean = Registry.`is`("terminal.use.EelApi", true)

internal class ShellProcessHolder(
  private val shellPid: EelApi.Pid,
  private val ptyProcessRef: WeakReference<PtyProcess>,
  private val eelApi: EelApi,
) {
  val isPosix: Boolean get() = eelApi.platform.isPosix

  fun terminatePosixShell() {
    val ptyProcess = ptyProcessRef.get() ?: return
    terminalApplicationScope().launch(Dispatchers.IO) {
      val killProcess = eelApi.exec.spawnProcess("kill").args("-HUP", shellPid.value.toString()).eelIt()
      val exitCode = withTimeoutOrNull(5.seconds) {
        ptyProcess.awaitExit()
      }
      if (exitCode == null) {
        val killProcessResult = withTimeoutOrNull(1.seconds) {
          killProcess.awaitProcessResult()
        }
        log.info("${ptyProcess::class.java.simpleName}(pid:$shellPid) hasn't been terminated by SIGHUP, performing forceful termination. " +
                 "\"kill -HUP $shellPid\" => ${killProcessResult?.stringify()}")
        ptyProcess.destroyForcibly()
      }
    }
  }

  private fun EelProcessExecutionResult.stringify(): String {
    return "(exitCode=$exitCode, stdout=$stdoutString, stderr=$stderrString)"
  }
}

private val log: Logger = logger<AbstractTerminalRunner<*>>()
