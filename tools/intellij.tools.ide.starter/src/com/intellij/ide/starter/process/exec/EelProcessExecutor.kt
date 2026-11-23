package com.intellij.ide.starter.process.exec

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.logEnvVariables
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelPosixProcess
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.EelProcessManagementApi
import com.intellij.platform.eel.EelWindowsProcess
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.lines
import com.intellij.platform.eel.spawnProcess
import com.intellij.platform.eel.terminate
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Runs an external process on the machine of [workDir] through Eel.
 *
 * Requires an initialized IntelliJ [com.intellij.openapi.application.Application].
 */
@Suppress("RAW_RUN_BLOCKING", "OPT_IN_USAGE")
class EelProcessExecutor(
  val presentableName: String,
  val workDir: Path,
  val timeout: Duration = 10.minutes,
  val environmentVariables: Map<String, String> = emptyMap(),
  val args: List<String>,
  val stdoutRedirect: ExecOutputRedirect = ExecOutputRedirect.NoRedirect,
  val stderrRedirect: ExecOutputRedirect = ExecOutputRedirect.NoRedirect,
) {
  private val descriptor: EelDescriptor = workDir.getEelDescriptor()

  @Throws(ExecTimeoutException::class)
  fun start(): Int = runBlocking(Dispatchers.IO) { startCancellable() }

  @Throws(ExecTimeoutException::class)
  @Suppress("checkedExceptions")
  suspend fun startCancellable(): Int {
    require(args.isNotEmpty()) { "Arguments must not be empty for external process `$presentableName`" }
    checkNotNull(ApplicationManager.getApplication()) {
      "External process `$presentableName` targets Eel descriptor $descriptor, which requires an initialized Application."
    }
    return startOnEel(descriptor.toEelApi())
  }

  @Suppress("checkedExceptions")
  private suspend fun startOnEel(eel: EelApi): Int {
    val processBuilder = eel.exec.spawnProcess(args.first()).args(args.drop(1))
    processBuilder.workingDirectory(workDir.toAbsolutePath().asEelPath())
    processBuilder.env(environmentVariables)

    logOutput(buildString {
      appendLine("Starting external process for `$presentableName`")
      appendLine("  Working directory: $workDir")
      appendLine("  Eel descriptor: $descriptor")
      appendLine("  Arguments: [${args.joinToString(separator = " ")}]")
      appendLine(
        "  STDOUT will be redirected to: $stdoutRedirect. STDERR will be redirected to: $stderrRedirect. STDIN is empty: true"
      )
      if (ConfigurationStorage.logEnvVariables()) {
        appendLine("  Environment variables added: [${environmentVariables.entries.joinToString { "${it.key}=${it.value}" }}]")
      }
    })

    val process = processBuilder.eelIt()
    logOutput("  ... started external process `$presentableName` with process ID = ${process.pid}")

    return coroutineScope {
      val processFinished = AtomicBoolean(false)
      val stdoutJob = launch(Dispatchers.IO) { redirectProcessOutput(process.stdout, stdoutRedirect) }
      val stderrJob = launch(Dispatchers.IO) { redirectProcessOutput(process.stderr, stderrRedirect) }
      val shutdownTask = Runnable {
        if (processFinished.compareAndSet(false, true)) {
          logOutput("   ... terminating process `$presentableName` by request from external process ...")
          runBlocking(Dispatchers.IO) { terminateProcess(eel.exec.processManagement, process, gracefullyAtFirst = false) }
        }
      }

      try {
        ShutDownTracker.getInstance().registerShutdownTask(shutdownTask)
        process.stdin.close(null)
        val receivedExitCode = withTimeoutOrNull(timeout) { process.exitCode.await() }
        if (receivedExitCode == null) {
          if (processFinished.compareAndSet(false, true)) {
            logOutput("   ... gracefully terminating process `$presentableName` because it runs for more than $timeout ...")
            withContext(NonCancellable) { terminateProcess(eel.exec.processManagement, process, gracefullyAtFirst = true) }
          }
          throw ExecTimeoutException(args.joinToString(" "), timeout)
        }

        processFinished.set(true)
        logOutput("   ... process `$presentableName` (pid=${process.pid}) exited on its own with code $receivedExitCode before timeout.")
        receivedExitCode
      }
      finally {
        if (processFinished.compareAndSet(false, true)) {
          logOutput("   ... terminating process `$presentableName` because of scope cancellation ...")
          withContext(NonCancellable) { terminateProcess(eel.exec.processManagement, process, gracefullyAtFirst = true) }
        }

        withContext(NonCancellable) {
          withTimeoutOrNull(30.seconds) { joinAll(stdoutJob, stderrJob) }
          if (stdoutJob.isActive) stdoutJob.cancelAndJoin()
          if (stderrJob.isActive) stderrJob.cancelAndJoin()
        }

        if (!ShutDownTracker.isShutdownStarted()) {
          ShutDownTracker.getInstance().unregisterShutdownTask(shutdownTask)
        }
      }
    }
  }

  private suspend fun redirectProcessOutput(channel: EelReceiveChannel, redirect: ExecOutputRedirect) {
    redirect.open()
    try {
      channel.lines(StandardCharsets.UTF_8).collect(redirect::redirectLine)
    }
    catch (e: IOException) {
      rethrowControlFlowException(e)
    }
    finally {
      redirect.close()
    }
  }

  /**
   * Stops [process] and all its descendants.
   *
   * On POSIX, [EelProcess.kill] stops only the one process, and a descendant that stays alive holds the output streams of [process]
   * open. So the function reads the descendants before it stops [process], because a child gets another parent when its parent exits.
   *
   * On Windows, the function does not walk the parent pids. Windows reuses pids, so the walk can reach a process of another tree.
   */
  @Suppress("checkedExceptions")
  private suspend fun terminateProcess(processManagement: EelProcessManagementApi, process: EelProcess, gracefullyAtFirst: Boolean) {
    val descendants = when (process) {
      is EelPosixProcess -> descendantPids(processManagement, process.pid.value)
      is EelWindowsProcess -> emptyList()
    }
    try {
      when (process) {
        is EelPosixProcess -> {
          if (!gracefullyAtFirst) {
            safelyKill(process)
            return
          }

          try {
            process.terminate()
            if (withTimeoutOrNull(2.seconds) { process.exitCode.await() } != null) return
          }
          catch (e: Exception) {
            rethrowControlFlowException(e)
            logError("Could not terminate external process `$presentableName`: ${e.message}")
          }
          safelyKill(process)
        }
        is EelWindowsProcess -> safelyKill(process)
      }
    }
    finally {
      // A parent comes before its children in the list, so the reversed order stops a child first.
      for (pid in descendants.asReversed()) {
        try {
          processManagement.kill(pid)
        }
        catch (e: Exception) {
          rethrowControlFlowException(e)
          logError("Could not kill the descendant $pid of external process `$presentableName`: ${e.message}")
        }
      }
    }
  }

  private suspend fun safelyKill(process: EelProcess) {
    try {
      process.kill()
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      logError("Could not kill external process `$presentableName`: ${e.message}")
    }
  }

  /** The pids of all descendants of the process [rootPid]. A parent comes before its children. */
  private suspend fun descendantPids(processManagement: EelProcessManagementApi, rootPid: Long): List<Long> {
    val processes = try {
      processManagement.listProcesses()
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      logError("Could not list the descendants of external process `$presentableName`: ${e.message}")
      return emptyList()
    }

    val descendants = ArrayList<Long>()
    val knownPids = hashSetOf(rootPid)
    do {
      var added = false
      for (process in processes) {
        if (process.parentPid?.value !in knownPids || !knownPids.add(process.pid.value)) continue
        descendants.add(process.pid.value)
        added = true
      }
    }
    while (added)
    return descendants
  }
}
