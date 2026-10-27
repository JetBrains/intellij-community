package com.intellij.ide.starter.driver.driver.remoteDev

import com.intellij.ide.starter.coroutine.CommonScope.scopeForProcesses
import com.intellij.ide.starter.driver.engine.DriverOptions
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.IDEHandle
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.events.IdeLaunchEvent
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

internal class IDEFrontendHandler(
  private val frontendContext: IDETestContext,
  private val driverOptions: DriverOptions,
  private val debugPort: Int,
) {

  fun runInBackground(
    launchName: String,
    commandLine: IDECommandLine,
    runTimeout: Duration,
    configure: IDERunContext.() -> Unit = {},
  ): Triple<Deferred<IDEStartResult>, IDEHandle, IDERunContext> {
    frontendContext.ide.vmOptions.let {
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
    val runContext = CompletableDeferred<IDERunContext>()
    EventsBus.subscribeOnce(process) { event: IdeLaunchEvent ->
      runContext.complete(event.runContext)
      process.complete(event.ideProcess)
    }
    val result = scopeForProcesses.async {
      try {
        frontendContext.runIdeSuspending(
          commandLine = commandLine,
          commands = CommandChain(),
          runTimeout = runTimeout,
          launchName = launchName,
          configure = configure)
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

    return runBlocking(CoroutineName("Awaiting for Frontend Process")) {
      withTimeout(2.minutes) {
        Triple(result, process.await(), runContext.await())
      }
    }
  }
}
