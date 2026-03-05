package com.intellij.ide.starter.driver.engine

import com.intellij.driver.client.Driver
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
  private fun Driver.beforeCall(pauseOnIndexing: Duration?) {
    pauseOnIndexing?.let { timeout ->
      var isInsideWaiting = false
      if (isConnected && !isInsideWaiting) {
        isInsideWaiting = true
        try {
          getOpenProjects().forEach {
            waitForIndicators(it, timeout, false)
          }
        }
        finally {
          isInsideWaiting = false
        }
      }
    }
  }

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
    pauseOnIndexing: Duration?,
    configure: IDERunContext.() -> Unit,
  ): BackgroundRun {
    val driverOptions = DriverOptions()
    val driver = DriverWithDetailedLogging(
      driver = DriverImpl(JmxHost(address = driverOptions.address), isRemDevMode = false) { beforeCall(pauseOnIndexing) },
      logUiHierarchy = !context.isRemDevContext())
    val currentStep = Allure.getLifecycle().currentTestCaseOrStep
    val process = CompletableDeferred<IDEHandle>()
    EventsBus.subscribeOnce(process) { event: IdeLaunchEvent ->
      process.complete(event.ideProcess)
    }
    val runResult = scopeForProcesses.async {
      Allure.getLifecycle().setCurrentTestCase(currentStep.orElse(UUID.randomUUID().toString()))
      try {
        context.runIdeSuspending(commandLine,
                                 commands,
                                 runTimeout,
                                 useStartupScript,
                                 launchName,
                                 expectedKill,
                                 expectedExitCode,
                                 collectNativeThreads) {
          provideDriverProperties(driverOptions)
          configure()
        }
      }
      catch (e: Throwable) {
        logError("Exception starting IDE. Even if it was started, it will be killed now.", e)
        process.completeExceptionally(e)
        throw e
      }
    }
    return runBlocking { BackgroundRun(runResult, driver, process.await()) }
  }

  private fun IDERunContext.provideDriverProperties(driverOptions: DriverOptions) {
    addVMOptionsPatch {
      for (entry in driverOptions.systemProperties) {
        addSystemProperty(entry.key, entry.value)
      }
    }
  }
}