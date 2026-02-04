// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelExecApi.EnvironmentVariablesException
import com.intellij.platform.eel.EelExecApiHelpers
import com.intellij.platform.eel.EelPathBoundDescriptor
import com.intellij.platform.eel.EelPosixApi
import com.intellij.platform.eel.EelPosixProcess
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.EelResult.Error
import com.intellij.platform.eel.EelResult.Ok
import com.intellij.platform.eel.EelWindowsProcess
import com.intellij.platform.eel.ExecuteProcessException
import com.intellij.platform.eel.environmentVariables
import com.intellij.platform.eel.fs.EelFileInfo
import com.intellij.platform.eel.fs.stat
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.utils.awaitProcessResult
import com.intellij.platform.eel.spawnProcess
import com.intellij.util.asSafely
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import com.pty4j.PtyProcess
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@RequiresReadLockAbsence
@RequiresBackgroundThread
internal fun hasRunningCommandsBlocking(shellEelProcess: ShellEelProcess): Boolean {
  return runBlockingMaybeCancellable {
    try {
      hasRunningCommands(shellEelProcess)
    }
    catch (e: IllegalStateException) {
      LOG.warn("Cannot determine running commands, assuming none ($shellEelProcess)", e)
      false
    }
  }
}

/**
 * Checks if the current process has running commands.
 *
 * @param shellEelProcess The shell process on the remote machine.
 * @return `true` if the process has running commands; otherwise, `false`
 * 
 * @see org.jetbrains.plugins.terminal.TerminalUtil.localProcessHasRunningCommands
 */
@Throws(IllegalStateException::class)
private suspend fun hasRunningCommands(shellEelProcess: ShellEelProcess): Boolean {
  return when (val process = shellEelProcess.eelProcess) {
    is EelPosixProcess -> hasChildProcesses(process, shellEelProcess.eelApi as EelPosixApi)
    is EelWindowsProcess -> {
      // The local case should have been already covered at this point by
      // `org.jetbrains.plugins.terminal.TerminalUtil.localProcessHasRunningCommands`
      // Let's warn in the case of non-local Windows:
      LOG.warn("Cannot determine running commands on non-local Windows, assuming none ($shellEelProcess)")
      false
    }
  }
}

/**
 * @see com.intellij.execution.process.UnixProcessManager.processPSOutput
 */
@Throws(IllegalStateException::class)
private suspend fun hasChildProcesses(shellProcess: EelPosixProcess, eelApi: EelPosixApi): Boolean = coroutineScope {
  val psProcess = try {
    buildPsCommand(eelApi)
      .env(eelApi.exec.environmentVariables().minimal().eelIt().await())
      .scope(this)
      .eelIt()
  }
  catch (e: EnvironmentVariablesException) {
    throw IllegalStateException("Cannot get minimal environment variables to spawn `ps` command", e)
  }
  catch (e: ExecuteProcessException) {
    throw IllegalStateException("Cannot spawn `ps` command", e)
  }
  val result = withTimeoutOrNull(PS_COMMAND_TIMEOUT) {
    psProcess.awaitProcessResult()
  } ?: throw IllegalStateException("Timed out when awaiting `ps` result")
  if (result.exitCode != 0) {
    throw IllegalStateException("`ps` terminated with exit code ${result.exitCode}, stderr: ${result.stderr.decodeToString()}")
  }
  hasChildProcesses(shellProcess.pid, result.stdout.decodeToString())
}

@Throws(IllegalStateException::class)
private fun hasChildProcesses(shellPid: EelApi.Pid, stdout: String): Boolean {
  @Suppress("SpellCheckingInspection")
  return stdout.trimEnd().lineSequence().drop(1 /* drop "PPID PID" header */).any { line ->
    val parentPid = try {
      line.trimStart().splitToSequence(" ").first().toLong()
    }
    catch (e: Exception) {
      // fail on NoSuchElementException, NumberFormatException
      throw IllegalStateException("Cannot parse PPID from `ps` output line: '$line'", e)
    }
    parentPid == shellPid.value
  }
}

/**
 * @see com.intellij.execution.process.UnixProcessManager.getPSCmd
 */
@Suppress("SpellCheckingInspection")
@Throws(IllegalStateException::class)
private suspend fun buildPsCommand(eelApi: EelPosixApi): EelExecApiHelpers.SpawnProcess {
  val psExe = findPsExecutable(eelApi)
  // Unfortunately, it's not possible to filter by parent PID, like `ps --ppid=<shell pid> ...`,
  // because the `--ppid` flag is available in GNU/Linux only.
  // Therefore, we have to list all available processes: 
  return eelApi.exec.spawnProcess(psExe).args("-e", "-o", "ppid,pid")
}

@Throws(IllegalStateException::class)
private suspend fun findPsExecutable(eelApi: EelPosixApi): EelPath {
  // Try the hardcoded paths first, because `eelApi.exec.findExeFilesInPath`
  // searches in PATH from the login shell environment variables. 
  listOf(
    "/bin/ps",
    "/usr/bin/ps",
  ).forEach {
    try {
      val path = EelPath.parse(it, eelApi.descriptor)
      if (path.isFile(eelApi)) {
        return path
      }
    }
    catch (e: EelPathException) {
      LOG.error("Cannot parse path: $it", e)
    }
  }
  // need to search in the minimal PATH
  return eelApi.exec.findExeFilesInPath("ps").firstOrNull() ?: throw IllegalStateException(
    "Cannot find `ps` executable: descriptor=${eelApi.descriptor}, platform=${eelApi.platform}"
  )
}

/**
 * Tests whether a file is a regular file, symlinks are followed.
 * Similar to `Files.isRegularFile(path)`.
 */
private suspend fun EelPath.isFile(eelApi: EelApi): Boolean {
  return when (val result = eelApi.fs.stat(this).resolveAndFollow().eelIt()) {
    is Ok -> result.value.type is EelFileInfo.Type.Regular
    is Error -> false
  }
}

@ApiStatus.Internal
class ShellEelProcess(val eelProcess: EelProcess, val eelApi: EelApi, val process: PtyProcess) {
  override fun toString(): String {
    val root = eelApi.descriptor.asSafely<EelPathBoundDescriptor>()?.rootPath?.let { "(root=$it)" }.orEmpty()
    return "descriptor=${eelApi.descriptor}$root, platform=${eelApi.platform}, process=${process::class.java.name})"
  }
}

private val LOG: Logger = fileLogger()

private val PS_COMMAND_TIMEOUT: Duration = 10.seconds
