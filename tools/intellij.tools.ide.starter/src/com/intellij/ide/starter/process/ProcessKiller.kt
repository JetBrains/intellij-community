package com.intellij.ide.starter.process

import com.intellij.ide.starter.process.ProcessKiller.killProcessUsingHandle
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.utils.catchAll
import com.intellij.openapi.diagnostic.Logger
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.system.OS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object ProcessKiller {
  private val logger = Logger.getInstance(ProcessKiller::class.java)

  /**
   * Returns true if the processes were killed successfully or found in a killed state.
   */
  suspend fun killProcesses(
    processInfosToKill: List<ProcessInfo>,
    workDir: Path? = null,
    timeout: Duration = 1.minutes,
  ): Boolean = coroutineScope {
    check(processInfosToKill.isNotEmpty())
    val results = processInfosToKill.map { processInfo ->
      async {
        if (processInfo.processHandle != null) {
          if (!killProcessUsingHandle(processInfo.processHandle, timeout)) {
            killProcessUsingCommandLine(processInfo.pid, workDir, timeout)
          }
          else {
            true
          }
        }
        else {
          // According to the Doc, a process handle is null only for the non-existing processes
          true
        }
      }
    }

    results.awaitAll().all { it }
  }

  /**
   * Kills a process using the command line.
   * IF possible it's better to use [killProcessUsingHandle].
   * Returns true if the process was killed successfully or found in a killed state.
   */
  suspend fun killProcessUsingCommandLine(
    pid: Long,
    workDir: Path? = null,
    timeout: Duration,
  ): Boolean {
    logOutput("Killing process $pid using command line")

    val args: List<String> = if (OS.CURRENT == OS.Windows) {
      listOf("taskkill", "/pid", pid.toString(), "/f")
    }
    else {
      listOf("kill", "-9", pid.toString())
    }

    val stdout = ExecOutputRedirect.ToStdOutAndString("[kill-pid-${pid}]")
    val stderr = ExecOutputRedirect.ToStdOutAndString("[kill-pid-${pid}]")

    ProcessExecutor(
      presentableName = "Kill Process $pid",
      workDir = workDir,
      timeout = timeout,
      args = args,
      stdoutRedirect = stdout,
      stderrRedirect = stderr,
    ).startCancellable()

    val errorMsg = stderr.read()
    return if (errorMsg.isNotEmpty()) {
      if (errorMsg.contains("No such process")) {
        logger.warn("Process $pid is already terminated")
        return true
      }
      logger.warn("Process kill command reported errors: $errorMsg")
      false
    }
    else {
      true
    }
  }

  /**
   * Kills a process using the [ProcessHandle].
   * Waits for the process to exit for up to [timeout].
   * Returns true if the process was killed successfully or found in a killed state.
   */
  suspend fun killProcessUsingHandle(processHandle: ProcessHandle, timeout: Duration = 30.seconds): Boolean = withContext(Dispatchers.IO) {
    logOutput("Kill process '${processHandle.pid()} ${processHandle.info().command()}' using ProcessHandle")
    if (processHandle.destroy()) {
      catchAll("Waiting on exit for process '${processHandle.pid()}'") {
        // Usually daemons wait 2 requests for 10 seconds after ide shutdown
        withTimeoutOrNull(timeout) {
          processHandle.onExit().await()
        }
        return@withContext true
      }
    }

    processHandle.destroyForcibly()
    catchAll("Waiting on exit for process '${processHandle.pid()}' after forcible termination") {
      withTimeoutOrNull(2.seconds) {
        processHandle.onExit().await()
      }
      return@withContext true
    }

    false
  }
}
