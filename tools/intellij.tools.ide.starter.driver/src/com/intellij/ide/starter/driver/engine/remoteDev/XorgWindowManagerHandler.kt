package com.intellij.ide.starter.driver.engine.remoteDev

import com.intellij.driver.sdk.waitNotNull
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.process.getProcessList
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.utils.getRunningDisplays
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import kotlin.io.path.div
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

object XorgWindowManagerHandler {

  fun provideDisplay(): Int {
    return waitNotNull("There is a display", 5.seconds) { getRunningDisplays().firstOrNull() }
  }

  // region Fluxbox
  private val fluxboxName = "fluxbox"

  private fun isFluxBoxIsRunning(displayWithColumn: String): Boolean {
    val running = getProcessList { it.name == fluxboxName && it.arguments.contains(":$displayWithColumn") }.isNotEmpty()
    logOutput("$fluxboxName is running: $running")
    return running
  }

  private suspend fun isFluxBoxInstalled(): Boolean {
    val result = ExecOutputRedirect.ToString()
    ProcessExecutor(
      presentableName = "which $fluxboxName",
      args = listOf("which", fluxboxName),
      workDir = null,
      stdoutRedirect = result,
      analyzeProcessExit = false
    ).startCancellable()
    return result.read().isNotBlank()
  }

  suspend fun startFluxBox(ideRunContext: IDERunContext) {
    val displayWithColumn = ideRunContext.testContext.ide.vmOptions.environmentVariables["DISPLAY"]!!

    if (!isFluxBoxInstalled()) {
      logError("FluxBox is not installed. Please be aware the possibility of test env will be restricted. E.g. you will not be able to resize windows.")
      return
    }

    if (!isFluxBoxIsRunning(displayWithColumn)) {
      val fluxboxRunLog = ideRunContext.logsDir / "$fluxboxName.log"
      ProcessExecutor(
        presentableName = "Start $fluxboxName",
        timeout = 2.hours,
        args = listOf("/usr/bin/${fluxboxName}", "-display", displayWithColumn),
        workDir = null,
        stdoutRedirect = ExecOutputRedirect.ToFile(fluxboxRunLog.toFile()),
        stderrRedirect = ExecOutputRedirect.ToFile(fluxboxRunLog.toFile())
      ).startCancellable()
    }
    else {
      logOutput("$fluxboxName is already running on display $displayWithColumn")
    }
  }
  // endregion Fluxbox
}