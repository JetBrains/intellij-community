package com.intellij.ide.starter.driver.engine.remoteDev

import com.intellij.driver.sdk.waitForAsync
import com.intellij.driver.sdk.waitNotNullAsync
import com.intellij.ide.starter.process.ProcessInfo
import com.intellij.ide.starter.process.ProcessKiller
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.process.getProcessList
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.utils.getRunningDisplays
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.io.path.div
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

object XorgWindowManagerHandler {

  suspend fun provideDisplay(): Int {
    return waitNotNullAsync("There is a display", 5.seconds) { getRunningDisplays().firstOrNull() }
  }

  // region Fluxbox
  private const val FLUX_BOX_NAME = "fluxbox"
  private const val FLUX_BOX_STARTUP_ATTEMPTS = 3
  private val fluxBoxStartupTimeout = 10.seconds
  private val supportingWmWindowIdRegex = Regex("""window id # (0x[0-9a-fA-F]+|[0-9]+)""")

  private suspend fun getFluxBoxProcessesOnDisplay(displayWithColumn: String): List<ProcessInfo> {
    return getProcessList(processName = FLUX_BOX_NAME).filter { it.arguments.contains(displayWithColumn) }
  }

  private suspend fun isFluxBoxRunning(displayWithColumn: String): Boolean {
    val running = getFluxBoxProcessesOnDisplay(displayWithColumn).isNotEmpty()
    logOutput("$FLUX_BOX_NAME is running: $running")
    return running
  }

  private suspend fun isFluxBoxInstalled(): Boolean {
    val result = ExecOutputRedirect.ToString()
    ProcessExecutor(
      presentableName = "which $FLUX_BOX_NAME",
      args = listOf("which", FLUX_BOX_NAME),
      workDir = null,
      stdoutRedirect = result,
      analyzeProcessExit = false
    ).startCancellable()
    return result.read().isNotBlank()
  }

  private suspend fun isWmRunning(displayWithColumn: String): Boolean {
    val rootSupportingWindowId = readSupportingWmCheck(displayWithColumn) ?: return false
    // EWMH requires the support window to point _NET_SUPPORTING_WM_CHECK back to itself. Checking the self-reference
    // filters out stale root properties left after a previous window manager exited.
    val selfReferencedSupportingWindowId = readSupportingWmCheck(displayWithColumn, rootSupportingWindowId) ?: return false
    return selfReferencedSupportingWindowId == rootSupportingWindowId
  }

  private suspend fun readSupportingWmCheck(displayWithColumn: String, windowId: String? = null): String? {
    val stdout = ExecOutputRedirect.ToString()
    val args = if (windowId == null) {
      listOf("xprop", "-root", "-display", displayWithColumn, "_NET_SUPPORTING_WM_CHECK")
    }
    else {
      listOf("xprop", "-id", windowId, "-display", displayWithColumn, "_NET_SUPPORTING_WM_CHECK")
    }
    ProcessExecutor(
      presentableName = args.joinToString(" "),
      args = args,
      workDir = null,
      stdoutRedirect = stdout,
      analyzeProcessExit = false
    ).startCancellable()
    return parseSupportingWindowId(stdout.read())
  }

  private fun parseSupportingWindowId(output: String): String? {
    return supportingWmWindowIdRegex.find(output)?.groupValues?.get(1)?.lowercase()
  }

  suspend fun startFluxBox(ideRunContext: IDERunContext, ideCoroutineScope: CoroutineScope): Job? {
    val displayWithColumn = ideRunContext.testContext.ide.vmOptions.environmentVariables["DISPLAY"]!!

    if (!isFluxBoxInstalled()) {
      logError("FluxBox is not installed. Please be aware the possibility of test env will be restricted. E.g. you will not be able to resize windows.")
      return null
    }

    if (isWmRunning(displayWithColumn)) {
      logOutput("X11 window manager is already running on display $displayWithColumn")
      return null
    }

    if (isFluxBoxRunning(displayWithColumn)) {
      stopFluxBox(displayWithColumn, "$FLUX_BOX_NAME is running but is not registered as an X11 window manager")
    }

    return coroutineScope {
      var lastError: Exception? = null
      for (attempt in 1..FLUX_BOX_STARTUP_ATTEMPTS) {
        val fluxBoxJob = startFluxBoxProcess(ideRunContext, ideCoroutineScope, displayWithColumn)
        try {
          waitForRunningWM(displayWithColumn)
          return@coroutineScope fluxBoxJob
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Exception) {
          lastError = e
          fluxBoxJob.cancelAndJoin()
          stopFluxBox(displayWithColumn, "$FLUX_BOX_NAME did not register as an X11 window manager after attempt $attempt/$FLUX_BOX_STARTUP_ATTEMPTS")
        }
      }

      val troubleshooting = collectWindowManagerTroubleshooting(displayWithColumn)
      logOutput(troubleshooting)
      throw IllegalStateException(
        buildString {
          appendLine("Failed to observe a registered X11 window manager on display $displayWithColumn after starting $FLUX_BOX_NAME.")
          append(troubleshooting)
        },
        lastError ?: IllegalStateException("$FLUX_BOX_NAME failed to start"),
      )
    }
  }

  private fun startFluxBoxProcess(ideRunContext: IDERunContext, ideCoroutineScope: CoroutineScope, displayWithColumn: String): Job {
    return ideCoroutineScope.launch(Dispatchers.IO) {
      try {
        runFluxBox(ideRunContext, displayWithColumn)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Exception) {
        logError("$FLUX_BOX_NAME process failed on display $displayWithColumn", e)
      }
    }
  }

  private suspend fun stopFluxBox(displayWithColumn: String, reason: String) {
    val processes = getFluxBoxProcessesOnDisplay(displayWithColumn)
    if (processes.isEmpty()) return

    logOutput(buildString {
      appendLine("Stopping $FLUX_BOX_NAME on display $displayWithColumn: $reason")
      append(processes.joinToString(System.lineSeparator()) { it.description })
    })
    val stopped = ProcessKiller.killProcesses(processes, cleanUpDescendants = false, gracefulTimeout = 2.seconds, forcefulTimeout = 2.seconds)
    if (!stopped) {
      logOutput("Failed to stop all $FLUX_BOX_NAME processes on display $displayWithColumn")
    }
  }

  private suspend fun runFluxBox(ideRunContext: IDERunContext, displayWithColumn: String) {
    val fluxboxRunLog = ideRunContext.logsDir / "$FLUX_BOX_NAME.log"
    ProcessExecutor(
      presentableName = "Start $FLUX_BOX_NAME",
      timeout = 2.hours,
      args = listOf("/usr/bin/${FLUX_BOX_NAME}", "-display", displayWithColumn),
      workDir = null,
      stdoutRedirect = ExecOutputRedirect.ToFile(fluxboxRunLog),
      stderrRedirect = ExecOutputRedirect.ToFile(fluxboxRunLog)
    ).startCancellable()
  }

  private suspend fun waitForRunningWM(displayWithColumn: String) {
    waitForAsync(
      message = "$FLUX_BOX_NAME starts on display $displayWithColumn",
      timeout = fluxBoxStartupTimeout,
      getter = { isWmRunning(displayWithColumn) },
      checker = { it },
    )
  }

  private suspend fun collectWindowManagerTroubleshooting(displayWithColumn: String): String {
    val fluxBoxProcesses = getProcessList(processName = FLUX_BOX_NAME)
    return buildString {
      appendLine("X11 window manager troubleshooting for display $displayWithColumn")
      appendLine("$FLUX_BOX_NAME process list:")
      appendLine(fluxBoxProcesses.joinToString(System.lineSeparator()) { it.description }.ifBlank { "<none>" })
      appendLine()
      appendLine(runTroubleshootingCommand("xprop -root", listOf("xprop", "-root", "-display", displayWithColumn)))
      appendLine()
      appendLine(runTroubleshootingCommand("xwininfo -root", listOf("xwininfo", "-root", "-display", displayWithColumn)))
      appendLine()
      appendLine(runTroubleshootingCommand("wmctrl -m", listOf("wmctrl", "-m"), mapOf("DISPLAY" to displayWithColumn)))
    }
  }

  private suspend fun runTroubleshootingCommand(
    presentableName: String,
    args: List<String>,
    extraEnvironmentVariables: Map<String, String> = emptyMap(),
  ): String {
    val stdout = ExecOutputRedirect.ToString()
    val stderr = ExecOutputRedirect.ToString()
    return try {
      val exitCode = ProcessExecutor(
        presentableName = presentableName,
        args = args,
        workDir = null,
        timeout = 10.seconds,
        environmentVariables = System.getenv() + extraEnvironmentVariables,
        stdoutRedirect = stdout,
        stderrRedirect = stderr,
        analyzeProcessExit = false
      ).startCancellable(printEnvVariables = false)

      buildString {
        appendLine("$presentableName exit code: $exitCode")
        appendLine("$presentableName stdout:")
        appendLine(trimTroubleshootingOutput(stdout.read().trim().ifBlank { "<empty>" }))
        appendLine("$presentableName stderr:")
        append(trimTroubleshootingOutput(stderr.read().trim().ifBlank { "<empty>" }))
      }
    }
    catch (e: Exception) {
      "$presentableName failed: ${e.message ?: e.javaClass.name}"
    }
  }

  private fun trimTroubleshootingOutput(output: String): String {
    val lines = output.lines()
    return if (lines.size <= 200) {
      output
    }
    else {
      lines.take(200).joinToString(System.lineSeparator()) + System.lineSeparator() + "... truncated ${lines.size - 200} lines"
    }
  }
  // endregion Fluxbox
}