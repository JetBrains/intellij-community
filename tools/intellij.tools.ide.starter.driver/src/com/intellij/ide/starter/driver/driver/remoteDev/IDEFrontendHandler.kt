package com.intellij.ide.starter.driver.driver.remoteDev

import com.intellij.ide.starter.coroutine.perClassSupervisorScope
import com.intellij.ide.starter.driver.engine.remoteDev.XorgWindowManagerHandler
import com.intellij.ide.starter.ide.IDERemDevTestContext
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.IDEHandle
import com.intellij.ide.starter.runner.events.IdeAfterLaunchEvent
import com.intellij.ide.starter.runner.events.IdeLaunchEvent
import com.intellij.openapi.util.SystemInfo
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.*
import kotlin.time.Duration

internal class IDEFrontendHandler(private val ideRemDevTestContext: IDERemDevTestContext, private val remoteDevDriverOptions: RemoteDevDriverOptions) {
  private val frontendContext = ideRemDevTestContext.frontendIDEContext

  private fun VMOptions.addDisplayIfNecessary() {
    if (SystemInfo.isLinux && System.getenv("DISPLAY") == null) {
      val displayNum = XorgWindowManagerHandler.provideDisplay()
      withEnv("DISPLAY", ":$displayNum")
    }
  }

  fun runInBackground(launchName: String, joinLink: String, runTimeout: Duration = remoteDevDriverOptions.runTimeout): Pair<Deferred<IDEStartResult>, IDEHandle> {
    frontendContext.ide.vmOptions.let {
      //setup xDisplay
      it.addDisplayIfNecessary()

      //add driver related vmOptions
      remoteDevDriverOptions.frontendOptions.systemProperties.forEach(it::addSystemProperty)
      remoteDevDriverOptions.remoteDevVmOptions.forEach(it::addSystemProperty)

      if (it.isUnderDebug()) {
        it.debug(remoteDevDriverOptions.debugPort, suspend = false)
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
        val thinClientCommand = if (frontendContext.ide.vmOptions.data().contains("-Djava.awt.headless=true")) "thinClient-headless" else "thinClient"

        frontendContext.runIdeSuspending(
          commandLine = IDECommandLine.Args(listOf(thinClientCommand, joinLink)),
          commands = CommandChain(),
          runTimeout = runTimeout,
          launchName = launchName,
          configure = {
            if (System.getenv("DISPLAY") == null && frontendContext.ide.vmOptions.environmentVariables["DISPLAY"] != null && SystemInfo.isLinux) {
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
          })
          .also {
            logOutput("Remote IDE Frontend run ${ideRemDevTestContext.testName} completed")
          }
      }
      catch (e: Exception) {
        logError("Exception starting the frontend. Even if it was started, it will be killed now.", e)
        process.completeExceptionally(e)
        throw e
      }
    }

    return Pair(result, runBlocking { process.await() })
  }
}
