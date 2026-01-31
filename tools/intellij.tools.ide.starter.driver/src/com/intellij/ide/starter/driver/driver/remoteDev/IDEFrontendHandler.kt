package com.intellij.ide.starter.driver.driver.remoteDev

import com.intellij.ide.starter.coroutine.CommonScope.perClassSupervisorScope
import com.intellij.ide.starter.driver.engine.DriverOptions
import com.intellij.ide.starter.driver.engine.remoteDev.XorgWindowManagerHandler
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.IDEHandle
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.events.IdeAfterLaunchEvent
import com.intellij.ide.starter.runner.events.IdeLaunchEvent
import com.intellij.openapi.util.SystemInfo
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

internal class IDEFrontendHandler(
  private val frontendContext: IDETestContext,
  private val driverOptions: DriverOptions,
  private val debugPort: Int,
) {

  private fun VMOptions.addDisplayIfNecessary() {
    if (SystemInfo.isLinux && System.getenv("DISPLAY") == null) {
      val displayNum = XorgWindowManagerHandler.provideDisplay()
      withEnv("DISPLAY", ":$displayNum")
    }
  }

  fun runInBackground(
    launchName: String,
    joinLink: String,
    runTimeout: Duration,
    configure: IDERunContext.() -> Unit = {},
  ): Pair<Deferred<IDEStartResult>, IDEHandle> {
    frontendContext.ide.vmOptions.let {
      //setup xDisplay
      it.addDisplayIfNecessary()

      //add driver related vmOptions
      driverOptions.systemProperties.forEach(it::addSystemProperty)

      if (it.isUnderDebug()) {
        it.debug(debugPort, suspend = false)
      }
      else {
        it.dropDebug()
      }
    }
    val process = CompletableDeferred<IDEHandle>()
    EventsBus.subscribeOnce(process) { event: IdeLaunchEvent ->
      process.complete(event.ideProcess)
    }
    val result = perClassSupervisorScope.async {
      try {
        val thinClientCommand =
          if (frontendContext.ide.vmOptions.data().contains("-Djava.awt.headless=true")) "thinClient-headless" else "thinClient"

        frontendContext.runIdeSuspending(
          commandLine = IDECommandLine.Args(listOf(thinClientCommand, joinLink)),
          commands = CommandChain(),
          runTimeout = runTimeout,
          launchName = launchName,
          configure = {
            if (System.getenv("DISPLAY") == null && frontendContext.ide.vmOptions.environmentVariables["DISPLAY"] != null && SystemInfo.isLinux
                && !frontendContext.ide.vmOptions.hasHeadlessMode()) {
              // It means the ide will be started on a new display, so we need to add win manager
              val fluxboxJob = this@async.launch(Dispatchers.IO) {
                XorgWindowManagerHandler.startFluxBox(this@runIdeSuspending)
              }
              EventsBus.subscribeOnce(fluxboxJob) { event: IdeAfterLaunchEvent ->
                if (event.runContext === this@runIdeSuspending) {
                  fluxboxJob.cancelAndJoin()
                }
              }
            }
            withScreenRecording()

            configure(this)
          })
          .also {
            logOutput("Remote IDE Frontend run ${frontendContext.testName} completed")
          }
      }
      catch (e: Exception) {
        logError("Exception starting the frontend. Even if it was started, it will be killed now.", e)
        process.completeExceptionally(e)
        throw e
      }
    }

    return Pair(result, runBlocking(CoroutineName("Awaiting for Frontend Process")) { withTimeout(2.minutes) { process.await() } })
  }
}
