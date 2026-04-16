// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelExecPosixApi
import com.intellij.platform.eel.EelExecWindowsApi
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.ExecuteProcessException
import com.intellij.platform.eel.environmentVariables
import com.intellij.platform.eel.isPosix
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.EelProcessExecutionResult
import com.intellij.platform.eel.provider.utils.awaitProcessResult
import com.intellij.platform.eel.provider.utils.stderrString
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.platform.eel.spawnProcess
import com.intellij.util.EnvironmentUtil
import com.intellij.util.io.awaitExit
import com.intellij.util.system.OS
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import com.pty4j.PtyProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.plugins.terminal.startup.ShellExecCommand
import org.jetbrains.plugins.terminal.startup.ShellExecCommandImpl
import org.jetbrains.plugins.terminal.startup.WslShellExecCommand
import org.jetbrains.plugins.terminal.util.ShellNameUtil
import org.jetbrains.plugins.terminal.util.terminalApplicationScope
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
  eelDescriptor: EelDescriptor,
  command: List<String>,
  envs: Map<String, String>,
  workingDirectory: EelPath,
  initialTermSize: TermSize,
): ShellProcessHolder {
  return runBlockingMaybeCancellable {
    val eelApi = eelDescriptor.toEelApi()
    val process = doStartProcess(eelApi, command, envs, workingDirectory, initialTermSize)
    ShellProcessHolder(process, eelApi)
  }
}

internal fun buildStartupEelContext(workingDir: Path, shellCommand: List<String>): TerminalStartupEelContext {
  if (!shouldUseEelApi()) {
    return TerminalStartupEelContext(workingDir.asEelPath(LocalEelDescriptor), ShellExecCommandImpl(shellCommand))
  }
  return runBlockingMaybeCancellable {
    try {
      doBuildStartupEelContext(workingDir, shellCommand)
    }
    catch (e: Exception) {
      log.warn("Cannot find EelDescriptor", e)
      TerminalStartupEelContext(workingDir.asEelPath(LocalEelDescriptor), ShellExecCommandImpl(shellCommand))
    }
  }
}

@Throws(EelPathException::class)
private suspend fun doBuildStartupEelContext(workingDirectory: Path, shellCommand: List<String>): TerminalStartupEelContext {
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
    return TerminalStartupEelContext(
      EelPath.parse(workingDirectory.toString(), LocalEelDescriptor),
      ShellExecCommandImpl(shellCommand)
    )
  }

  WslShellExecCommand.parse(shellCommand)?.toIJEntStartupEelContext(workingDirectory)?.let {
    log.info(
      "Original WSL configuration (command: $shellCommand, workingDir: $workingDirectory) was translated to IJEnt configuration (" +
      "command: ${it.shellCommand}, workingDir: ${it.workingDirectory}, eelDescriptor: ${it.eelDescriptor.name})"
    )
    return it
  }

  val workingDirectoryEelPath = workingDirectory.asEelPath()
  return TerminalStartupEelContext(
    workingDirectoryEelPath,
    ShellExecCommandImpl(shellCommand)
  )
}

internal class TerminalStartupEelContext(
  val workingDirectory: EelPath,
  val shellCommand: ShellExecCommand,
) {
  val eelDescriptor: EelDescriptor
    get() = workingDirectory.descriptor
}

@Throws(ExecuteProcessException::class)
internal fun startLocalProcess(
  command: List<String>,
  envs: Map<String, String>,
  workingDirectory: String,
  initialTermSize: TermSize,
): ShellProcessHolder {
  return runBlockingMaybeCancellable {
    val eelWorkingDirectory = EelPath.parse(workingDirectory, LocalEelDescriptor)
    val eelProcess = doStartProcess(localEel, command, envs, eelWorkingDirectory, initialTermSize)
    ShellProcessHolder(eelProcess, localEel)
  }
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
  @Suppress("checkedExceptions")
  return execOptions.eelIt()
}

internal fun shouldUseEelApi(): Boolean = Registry.`is`("terminal.use.EelApi", true)

/**
 * @return The environment variables of this EelApi.
 * These environment variables should be non-interactive and non-login.
 * Specifically, they should not include variables defined in shell initialization files
 * such as ~/.bashrc, ~/.bash_profile, ~/.zshrc, or ~/.zprofile.
 * Because shell configuration files are loaded during a shell startup and
 * loading the same configuration file twice might break things. 
 */
internal suspend fun EelApi.fetchMinimalEnvironmentVariables(): Map<String, String> {
  return try {
    when (val exec = this.exec) {
      is EelExecPosixApi -> exec.environmentVariables().minimal().eelIt().await()
      is EelExecWindowsApi -> exec.environmentVariables().eelIt().await()
    }
  }
  catch (err: EelExecApi.EnvironmentVariablesException) {
    log.warn("Failed to fetch minimal environment variables for ${this.descriptor}, using an empty environment", err)
    return emptyMap()
  }
}

internal fun fetchMinimalEnvironmentVariablesBlocking(eelDescriptor: EelDescriptor): Map<String, String> {
  if (eelDescriptor == LocalEelDescriptor) {
    return System.getenv()
  }
  return runBlockingMaybeCancellable {
    eelDescriptor.toEelApi().fetchMinimalEnvironmentVariables()
  }
}

internal suspend fun EelApi.fetchDefaultEnvironmentVariables(): Map<String, String> {
  return try {
    when (val exec = this.exec) {
      is EelExecPosixApi -> exec.environmentVariables().onlyActual(true).default().eelIt().await()
      is EelExecWindowsApi -> exec.environmentVariables().eelIt().await()
    }
  }
  catch (err: EelExecApi.EnvironmentVariablesException) {
    log.warn("Failed to fetch default environment variables for ${this.descriptor}, using an empty environment", err)
    return emptyMap()
  }
}

internal fun fetchDefaultEnvironmentVariablesBlocking(eelDescriptor: EelDescriptor): Map<String, String> {
  if (eelDescriptor == LocalEelDescriptor) {
    return EnvironmentUtil.getEnvironmentMap()
  }
  return runBlockingMaybeCancellable {
    eelDescriptor.toEelApi().fetchDefaultEnvironmentVariables()
  }
}

internal class ShellProcessHolder(
  val eelProcess: EelProcess,
  val eelApi: EelApi,
) {
  val isPosix: Boolean get() = eelApi.platform.isPosix

  val ptyProcess: PtyProcess = eelProcess.convertToJavaProcess() as PtyProcess
  private val shellPid: EelApi.Pid = eelProcess.pid

  val descriptor: EelDescriptor get() = eelApi.descriptor

  fun terminatePosixShell() {
    terminalApplicationScope().launch(Dispatchers.IO) {
      if (!ptyProcess.isAlive) {
        log.debug { "Shell process ${processInfo(ptyProcess)} is already terminated" }
        return@launch
      }
      log.debug { "Sending SIGHUP to shell process ${processInfo(ptyProcess)}" }
      val killProcess = try {
        eelApi.exec.spawnProcess("kill").args("-HUP", shellPid.value.toString()).eelIt()
      }
      catch (e: ExecuteProcessException) {
        log.warn("Unable to send SIGHUP to ${processInfo(ptyProcess)}", e)
        return@launch
      }
      if (ptyProcess.awaitExit(5.seconds) == null) {
        val killProcessResult = withTimeoutOrNull(1.seconds) {
          killProcess.awaitProcessResult()
        }
        if (ptyProcess.isAlive) {
          log.info("Shell process ${processInfo(ptyProcess)} hasn't been terminated by SIGHUP, performing forceful termination. " +
                   "\"kill -HUP $shellPid\" => ${killProcessResult?.stringify()}")
          ptyProcess.destroyForcibly()
        }
      }
      val exitCode = ptyProcess.awaitExit(2.seconds)
      if (exitCode != null) {
        log.debug { "Shell process ${processInfo(ptyProcess)} has been terminated with exit code $exitCode" }
      }
      else {
        log.warn("Shell process ${processInfo(ptyProcess)} has not been terminated!")
      }
    }
  }

  /**
   * @return The exit value of the process if it exits within the timeout or null otherwise.
   */
  private suspend fun Process.awaitExit(timeout: kotlin.time.Duration): Int? {
    return withTimeoutOrNull(timeout) {
      this@awaitExit.awaitExit()
    }
  }

  private fun processInfo(process: PtyProcess): String {
    return "${process::class.java.name}($shellPid)"
  }

  private fun EelProcessExecutionResult.stringify(): String {
    return "(exitCode=$exitCode, stdout=$stdoutString, stderr=$stderrString)"
  }
}

private val log: Logger = logger<AbstractTerminalRunner<*>>()
