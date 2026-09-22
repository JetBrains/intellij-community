package com.intellij.ide.starter.driver.engine

import com.intellij.driver.client.impl.DriverImpl
import com.intellij.driver.client.impl.JmxHost
import com.intellij.driver.sdk.getOpenProjects
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.coroutine.CommonScope.scopeForProcesses
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.isRemDevContext
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.IDEHandle
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.events.IdeLaunchEvent
import com.intellij.tools.ide.performanceTesting.commands.MarshallableCommand
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.util.common.logError
import io.qameta.allure.Allure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.time.Duration

class LocalDriverRunner : DriverRunner {

  override fun runIdeWithDriver(
    context: IDETestContext,
    commandLine: (IDERunContext) -> IDECommandLine,
    commands: Iterable<MarshallableCommand>,
    runTimeout: Duration,
    useStartupScript: Boolean,
    launchName: String,
    expectedKill: Boolean,
    expectedExitCode: Int,
    collectNativeThreads: Boolean,
    pauseOnIndicators: Duration?,
    configure: IDERunContext.() -> Unit,
  ): BackgroundRun {
    val driverOptions = DriverOptions()
    val driver = DriverWithDetailedLogging(
      driver = DriverImpl(JmxHost(address = driverOptions.address), isRemDevMode = false) {
        pauseOnIndicators?.let { timeout ->
          if (isConnected) {
            getOpenProjects().forEach {
              waitForIndicators(it, timeout, false)
            }
          }
        }
      },
      logUiHierarchy = !context.isRemDevContext())
    val currentStep = Allure.getLifecycle().currentTestCaseOrStep
    val process = CompletableDeferred<IDEHandle>()
    val runContext = CompletableDeferred<IDERunContext>()
    EventsBus.subscribeOnce(process) { event: IdeLaunchEvent ->
      runContext.complete(event.runContext)
      process.complete(event.ideProcess)
    }
    val runResult = scopeForProcesses.async {
      Allure.getLifecycle().setCurrentTestCase(currentStep.orElse(UUID.randomUUID().toString()))
      try {
        context.runIdeSuspending(
          commandLine = commandLine,
          commands = commands,
          runTimeout = runTimeout,
          useStartupScript = useStartupScript,
          launchName = launchName,
          expectedKill = expectedKill,
          expectedExitCode = expectedExitCode,
          collectNativeThreads = collectNativeThreads,
        ) {
          provideDriverProperties(driverOptions)
          // in split mode RemDevDriverRunner already enabled it for the frontend and the backend together
          if (!context.isRemDevContext()) {
            addVMOptionsPatch { enableFocusRequestsLog() }
          }
          configure()
        }
      }
      catch (e: Throwable) {
        logError("Exception starting IDE. Even if it was started, it will be killed now.", e)
        process.completeExceptionally(e)
        throw e
      }
    }
    @Suppress("RAW_RUN_BLOCKING")
    return runBlocking {
      val processAwaited = process.await()
      val runContextAwaited = runContext.await()
      BackgroundRun(
        startResult = runResult,
        driverWithoutAwaitedConnection = driver,
        process = processAwaited,
        runContext = runContextAwaited)
    }
  }

  private fun IDERunContext.provideDriverProperties(driverOptions: DriverOptions) {
    addVMOptionsPatch {
      for ((key, value) in driverOptions.systemProperties) {
        addSystemProperty(key, value)
      }
    }
  }
}
