package com.intellij.ide.starter.process

import com.intellij.execution.Platform
import com.intellij.execution.process.OSProcessUtil
import com.intellij.ide.starter.utils.catchAll
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Termination helpers for child processes spawned by tests/ide from tests.
 *
 * To remember while editing:
 * - On Windows, killing descendants should not be done by iterating descendants — PID rotation can happen (see MRI-4085).
 *   Use [OSProcessUtil.killProcessTree] (which goes through WinP/Job-Objects) instead.
 *
 * - TW-71208: many processes on Linux TC (java for sure, ide we test and small test apps from portUtilTest) ignore SIGINT and only react to SIGTERM,
 *   SIGTERM is sent by `processHandle.destroy()`.
 *   OSProcessUtil.terminateProcessGracefully sends SIGINT on Linux.
 *
 * - SIGTERM does NOT propagate through shells like dash/xvfb-run to their children, so we must
 *   signal each descendant explicitly. We cannot rely on process groups either: Java's
 *   ProcessBuilder on Linux does not call setsid(), so the wrapper inherits the test JVM's PGID
 *   and `kill(-wrapperPid, ...)` targets a non-existing group.
 */
object ProcessKiller {

  private fun logOutput(message: String) {
    com.intellij.tools.ide.util.common.logOutput("[ProcessKiller] $message")
  }

  private fun processHandleToString(processHandle: ProcessHandle) =
    "Process[${processHandle.pid()}] '${processHandle.info()?.command()?.getOrNull() ?: "unknown command"}'"

  // Better to keep the sum of these two below default timeoutRunBlocking (10 sec)
  // so if someone tries to kill the process(that we can't kill) inside timeoutRunBlocking, it works without throwing an exception
  private val DEFAULT_GRACEFUL_TIMEOUT = 6.seconds
  private val DEFAULT_FORCEFUL_TIMEOUT = 2.seconds

  //When we have java.lang.Process reference, it means we started this process, and we have rights to kill it.
  // It may be an IDE we afford to wait longer.
  private val DEFAULT_GRACEFUL_OWN_PROCESS_TIMEOUT = 20.seconds

  /**
   * Returns true if every targeted process was confirmed dead.
   * Entries with a null [ProcessInfo.processHandle] are treated as already exited.
   */
  suspend fun killProcesses(
    processInfosToKill: List<ProcessInfo>,
    cleanUpDescendants: Boolean = true,
    gracefulTimeout: Duration = DEFAULT_GRACEFUL_TIMEOUT,
    forcefulTimeout: Duration = DEFAULT_FORCEFUL_TIMEOUT,
  ): Boolean {
    check(processInfosToKill.isNotEmpty()) { "processInfosToKill shouldn't be empty" }
    return coroutineScope {
      withTimeout(5.minutes) {
        processInfosToKill.map { info ->
          async {
            val handle = info.processHandle ?: run {
              logOutput("Process[${info.pid}] Can't resolve ProcessHandler of this process. " +
                        "It either already finished or we don't have rights to kill it.")
              return@async true
            }
            killProcess(handle, cleanUpDescendants, gracefulTimeout = gracefulTimeout, forcefulTimeout = forcefulTimeout)
          }
        }.awaitAll().all { it }
      }
    }
  }

  /**
   * Kills [process] and optionally kills its descendants according to [cleanUpDescendants].
   *
   * Returns true if confirmed dead.
   */
  suspend fun killProcess(
    process: Process,
    cleanUpDescendants: Boolean = true,
    gracefullyAtFirst: Boolean = true,
    gracefulTimeout: Duration = DEFAULT_GRACEFUL_OWN_PROCESS_TIMEOUT,
    forcefulTimeout: Duration = DEFAULT_FORCEFUL_TIMEOUT,
  ): Boolean {
    val processHandle = process.toHandle()
    return killProcess(
      processHandle = processHandle,
      cleanUpDescendants = cleanUpDescendants,
      gracefullyAtFirst = gracefullyAtFirst,
      gracefulTimeout = gracefulTimeout,
      forcefulTimeout = forcefulTimeout,
    )
  }

  @OptIn(LowLevelLocalMachineAccess::class)
  private suspend fun gracefulStop(processHandle: ProcessHandle, gracefulTimeout: Duration, cleanUpDescendants: Boolean) {
    runCatching {
      when {
        OS.CURRENT.platform == Platform.UNIX -> {
          if (cleanUpDescendants) {
            val descendants = processHandle.descendants().toList()
            descendants.asReversed().forEach {
              logOutput("${processHandleToString(processHandle)}: Stopping descendant ${processHandleToString(it)} gracefully by `destroy` [SIGTERM]")
              gracefulStop(it, 2.seconds, cleanUpDescendants = false)
            }
          }
          logOutput("${processHandleToString(processHandle)}: Stopping gracefully by `destroy` [SIGTERM]")
          processHandle.destroy()
        }
        else -> {
          logOutput("${processHandleToString(processHandle)}: Stopping gracefully `OSProcessUtil.terminateProcessGracefully` [Ctrl+C]")
          OSProcessUtil.terminateProcessGracefully(processHandle.pid().toInt())
        }
      }
    }
      .onFailure {
        logOutput("${processHandleToString(processHandle)}: Graceful stop failed: $it")
      }
      .onSuccess {
        catchAll("${processHandleToString(processHandle)}: Waiting for graceful exit with $gracefulTimeout timeout") {
          withTimeout(gracefulTimeout) { processHandle.onExit().await() }
        }
      }
  }

  @OptIn(LowLevelLocalMachineAccess::class)
  private suspend fun forcefulKill(processHandle: ProcessHandle, forcefulTimeout: Duration, cleanUpDescendants: Boolean = true) {
    // On Windows we deliberately don't iterate descendants — see the MRI-4085 note at the top of the file.
    val descendantsSnapshot: List<ProcessHandle> =
      if (cleanUpDescendants && OS.CURRENT.platform == Platform.UNIX) processHandle.descendants().toList()
      else emptyList()

    runCatching {
      if (cleanUpDescendants) {
        logOutput("${processHandleToString(processHandle)}: Killing process tree")
        return@runCatching OSProcessUtil.killProcessTree(processHandle.pid())
      }

      if (processHandle.isAlive) {
        logOutput("${processHandleToString(processHandle)}: Killing forcibly")
        return@runCatching processHandle.destroyForcibly()
      }

      return@runCatching true
    }
      .onFailure {
        logOutput("${processHandleToString(processHandle)}: Forceful kill failed: $it")
      }.onSuccess { successFulForceKill ->
        if (successFulForceKill) {
          catchAll("${processHandleToString(processHandle)}: Waiting for exit after Forceful kill with $forcefulTimeout timeout") {
            withTimeout(forcefulTimeout) {
              processHandle.onExit().await()
              descendantsSnapshot.forEach { descendant ->
                descendant.onExit().await()
              }
            }
          }
        }
        else {
          logOutput("${processHandleToString(processHandle)}: Forceful kill returned false")
        }
      }

  }

  suspend fun killProcess(
    processHandle: ProcessHandle,
    cleanUpDescendants: Boolean = true,
    gracefullyAtFirst: Boolean = true,
    gracefulTimeout: Duration = DEFAULT_GRACEFUL_TIMEOUT,
    forcefulTimeout: Duration = DEFAULT_FORCEFUL_TIMEOUT,
  ): Boolean = withContext(Dispatchers.IO) {

    if (gracefullyAtFirst) {
      gracefulStop(processHandle, gracefulTimeout, cleanUpDescendants)
    }

    if (processHandle.isAlive) {
      forcefulKill(processHandle, forcefulTimeout, cleanUpDescendants)
    }

    return@withContext !processHandle.isAlive
  }
}
