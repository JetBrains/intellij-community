package com.intellij.ide.starter.driver.driver.remoteDev

import com.intellij.driver.client.impl.JmxHost
import com.intellij.ide.starter.driver.driver.remoteDev.IDEBackendHandler.Companion.remoteDevDirectLink
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.DriverRunner
import com.intellij.ide.starter.driver.engine.DriverWithDetailedLogging
import com.intellij.ide.starter.driver.engine.remoteDev.RemDevFrontendDriver
import com.intellij.ide.starter.ide.IDERemDevTestContext
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.isRemDevContext
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.openapi.diagnostic.IdeaLogRecordFormatter
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.tools.ide.performanceTesting.commands.MarshallableCommand
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import kotlin.time.Duration

class RemDevDriverRunner : DriverRunner {
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
    configure: IDERunContext.() -> Unit,
  ): BackgroundRun {
    require(context.isRemDevContext()) { "for split-mode context should be instance of ${IDERemDevTestContext::class.java.simpleName}" }
    context as IDERemDevTestContext
    addConsoleAllAppender()

    val remoteDevDriverOptions = RemoteDevDriverOptions()
    context.addRemoteDevSpecificTraces()

    val backendRun =
      IDEBackendHandler(context, remoteDevDriverOptions.backendOptions, remoteDevDriverOptions.backendDebugPort)
        .run(commands,
             runTimeout,
             useStartupScript,
             launchName,
             expectedKill,
             expectedExitCode,
             collectNativeThreads,
             configure)
    val joinLink = backendRun.driver.remoteDevDirectLink()

    // should be run before the actual frontend start as otherwise we miss IdeLaunchEvent
    val frontendDriverWithLogging =
      DriverWithDetailedLogging(RemDevFrontendDriver(JmxHost(address = remoteDevDriverOptions.frontendOptions.address)))

    val (frontendStartResult, frontendProcess) = IDEFrontendHandler(context.frontendIDEContext,
                                                                    remoteDevDriverOptions.frontendOptions,
                                                                    remoteDevDriverOptions.debugPort)
      .runInBackground(launchName,
                       joinLink,
                       runTimeout,
                       configure)

    return RemoteDevBackgroundRun(backendRun = backendRun,
                                  frontendProcess = frontendProcess,
                                  frontendDriver = frontendDriverWithLogging,
                                  frontendStartResult = frontendStartResult)
  }

  private fun IDERemDevTestContext.addRemoteDevSpecificTraces() {
    applyVMOptionsPatch {
      configureLoggers(LogLevel.TRACE, "jb.focus.requests")
    }
  }

  companion object {
    private val consoleAppender = ConsoleHandler().apply {
      formatter = IdeaLogRecordFormatter()
    }
  }

  private fun addConsoleAllAppender() {
    Logger.getInstance("") // force to initialize logger model
    val root = java.util.logging.Logger.getLogger("")
    val oldConsoleHandler = root.handlers.find { it is ConsoleHandler }
    if (oldConsoleHandler != null) {
      root.removeHandler(oldConsoleHandler)
    }
    // change to All for local debug
    root.level = Level.INFO
    root.addHandler(consoleAppender)
  }
}