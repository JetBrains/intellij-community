package com.intellij.ide.starter.driver.engine

import com.intellij.driver.client.Driver
import com.intellij.driver.client.impl.JmxHost
import com.intellij.ide.starter.coroutine.CommonScope.perClassSupervisorScope
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
import kotlinx.coroutines.*
import java.util.*
import kotlin.time.Duration

class LocalDriverRunner : DriverRunner {
  override fun runIdeWithDriver(context: IDETestContext, commandLine: (IDERunContext) -> IDECommandLine, commands: Iterable<MarshallableCommand>, runTimeout: Duration, useStartupScript: Boolean, launchName: String, expectedKill: Boolean, expectedExitCode: Int, collectNativeThreads: Boolean, configure: IDERunContext.() -> Unit): BackgroundRun {
    val driverOptions = DriverOptions()
    val driver = DriverWithDetailedLogging(Driver.create(JmxHost(address = driverOptions.address)), logUiHierarchy = !context.isRemDevContext())
    val currentStep = Allure.getLifecycle().currentTestCaseOrStep
    val process = CompletableDeferred<IDEHandle>()
    EventsBus.subscribeOnce(process) { event: IdeLaunchEvent ->
      process.complete(event.ideProcess)
    }
    val runResult = perClassSupervisorScope.async {
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