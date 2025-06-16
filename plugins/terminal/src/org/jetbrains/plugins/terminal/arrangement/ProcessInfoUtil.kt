// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.arrangement;

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.ijent.IjentChildPtyProcessAdapter
import com.intellij.execution.process.CapturingProcessRunner
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.system.OS
import com.pty4j.windows.conpty.WinConPtyProcess
import com.pty4j.windows.winpty.WinPtyProcess
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Paths

@ApiStatus.Internal
@Service(Service.Level.APP)
class ProcessInfoUtil(private val coroutineScope: CoroutineScope) {
  // Limit the number of simultaneous CWD compute requests
  private val cwdComputeRequests = Channel<CwdComputeRequest>()

  init {
    coroutineScope.launch(Dispatchers.IO) {
      for (request in cwdComputeRequests) {
        try {
          val cwd = doGetCwd(request.process)
          request.cwdDeferred.complete(cwd)
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Exception) {
          request.cwdDeferred.completeExceptionally(e)
        }
      }
    }
  }

  @Throws(ExecutionException::class, IllegalStateException::class)
  suspend fun getCurrentWorkingDirectory(process: Process): String? {
    return if (process.isAlive) {
      val deferred = CompletableDeferred<String?>()
      val request = CwdComputeRequest(process, deferred)
      cwdComputeRequests.send(request)
      deferred.await()
    }
    else null
  }

  fun getCurrentWorkingDirectoryDeferred(process: Process): CompletableDeferred<String?> {
    return if (process.isAlive) {
      val deferred = CompletableDeferred<String?>()
      val request = CwdComputeRequest(process, deferred)
      coroutineScope.launch {
        cwdComputeRequests.send(request)
      }
      deferred
    }
    else CompletableDeferred(null)
  }

  @Throws(IllegalStateException::class)
  private fun doGetCwd(process: Process): String? {
    return when {
      process is IjentChildPtyProcessAdapter -> {
        // Use shell integration instead
        null
      }
      SystemInfo.isUnix -> {
        val pid = process.pid().toInt()
        tryGetCwdFastOnUnix(pid) ?: getCwdOnUnix(pid)
      }
      SystemInfo.isWindows -> {
        when (process) {
          is WinPtyProcess -> process.workingDirectory
          is WinConPtyProcess -> process.workingDirectory
          else -> error("Cwd cannot be fetched for ${process.javaClass}")
        }
      }
      else -> {
        error("Unsupported OS: " + OS.CURRENT)
      }
    }
  }

  private fun tryGetCwdFastOnUnix(pid: Int): String? {
    val procPath = "/proc/$pid/cwd"
    try {
      val dir = Paths.get(procPath).toRealPath().toFile()
      if (dir.isDirectory()) {
        return dir.absolutePath
      }
    }
    catch (e: Exception) {
      LOG.debug(e) { "Cannot resolve cwd from $procPath, fallback to lsof -a -d cwd -p $pid" }
    }
    return null
  }

  @Suppress("HardCodedStringLiteral")
  @Throws(ExecutionException::class)
  private fun getCwdOnUnix(pid: Int): String {
    val commandLine = GeneralCommandLine("lsof", "-a", "-d", "cwd", "-p", pid.toString(), "-Fn")
    val runner = CapturingProcessRunner(OSProcessHandler(commandLine))
    val output = runner.runProcess(TIMEOUT_MILLIS)
    if (output.isTimeout) {
      throw ExecutionException("Timeout running ${commandLine.commandLineString}")
    }
    else if (output.getExitCode() != 0) {
      throw ExecutionException("Exit code ${output.getExitCode()} for ${commandLine.commandLineString}")
    }
    return parseWorkingDirectory(output.stdoutLines, pid)
           ?: throw ExecutionException("Cannot parse working directory from ${commandLine.commandLineString}")
  }

  private fun parseWorkingDirectory(stdoutLines: List<String>, pid: Int): String? {
    var pidEncountered = false
    for (line in stdoutLines) {
      if (line.startsWith("p")) {
        val p = StringUtil.parseInt(line.substring(1), -1)
        pidEncountered = pidEncountered || p == pid
      }
      else if (pidEncountered && line.startsWith("n")) {
        return line.substring(1)
      }
    }
    return null
  }

  private data class CwdComputeRequest(val process: Process, val cwdDeferred: CompletableDeferred<String?>)

  companion object {
    @JvmStatic
    fun getInstance(): ProcessInfoUtil = service()

    private val LOG = logger<ProcessInfoUtil>()
    private const val TIMEOUT_MILLIS = 2000
  }
}
