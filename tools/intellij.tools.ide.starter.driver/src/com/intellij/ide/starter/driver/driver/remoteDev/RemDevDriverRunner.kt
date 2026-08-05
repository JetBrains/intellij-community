package com.intellij.ide.starter.driver.driver.remoteDev

import com.intellij.driver.client.Driver
import com.intellij.driver.client.impl.JmxHost
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.useDockerContainer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.driver.driver.remoteDev.IDEBackendHandler.Companion.remoteDevDirectLink
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.DriverRunner
import com.intellij.ide.starter.driver.engine.DriverWithDetailedLogging
import com.intellij.ide.starter.driver.engine.remoteDev.RemDevFrontendDriver
import com.intellij.ide.starter.ide.IDERemDevTestContext
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.isRemDevContext
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.IDEHandle
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.utils.catchAll
import com.intellij.openapi.diagnostic.IdeaLogRecordFormatter
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.tools.ide.performanceTesting.commands.MarshallableCommand
import kotlinx.coroutines.Deferred
import org.kodein.di.direct
import org.kodein.di.instanceOrNull
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import kotlin.time.Duration

open class RemDevDriverRunner : DriverRunner {
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
    require(context.isRemDevContext()) { "for split-mode context should be instance of ${IDERemDevTestContext::class.java.simpleName}" }
    context as IDERemDevTestContext
    validate(context)
    addConsoleAllAppender()

    val remoteDevDriverOptions = RemoteDevDriverOptions()
    context.addRemoteDevSpecificTraces()

    val backendRun =
      IDEBackendHandler(context, remoteDevDriverOptions.backendOptions, remoteDevDriverOptions.backendDebugPort)
        .run(backendCommandLine(context),
             commands,
             runTimeout,
             useStartupScript,
             launchName,
             expectedKill,
             expectedExitCode,
             collectNativeThreads,
             pauseOnIndexing = pauseOnIndexing,
             configure = configure)
    val joinLink = customizeJoinLink(backendRun.driver.remoteDevDirectLink())

    // should be run before the actual frontend start as otherwise we miss IdeLaunchEvent
    val frontendDriverWithLogging =
      DriverWithDetailedLogging(RemDevFrontendDriver(JmxHost(address = remoteDevDriverOptions.frontendOptions.address)) {
        backendRun.driver::beforeCall
      })

    val (frontendStartResult, frontendProcess, frontendRunContext) = try {
      IDEFrontendHandler(context.frontendIDEContext,
                         remoteDevDriverOptions.frontendOptions,
                         remoteDevDriverOptions.debugPort)
        .runInBackground(launchName,
                         frontendCommandLine(context, joinLink),
                         runTimeout,
                         configure)

    }
    catch (t: Throwable) {
      catchAll("Kill backend as frontend failed to start") { backendRun.forceKill() }
      throw t
    }

    return createBackgroundRun(backendRun = backendRun,
                               frontendProcess = frontendProcess,
                               frontendDriver = frontendDriverWithLogging,
                               frontendStartResult = frontendStartResult,
                               joinLink = joinLink,
                               frontendRunContext = frontendRunContext)
  }

  /** Fails the run before anything is started if the context doesn't fit the launch mode. */
  protected open fun validate(context: IDERemDevTestContext) {}

  protected open fun backendCommandLine(context: IDERemDevTestContext): IDECommandLine {
    val additionalArg = if (ConfigurationStorage.useDockerContainer()) {
      listOf("-l", "0.0.0.0") // tells backend to listen to the incoming rd connections on 0.0.0.0 so it is available outside of docker
    } else emptyList()

    return if (context.testCase.projectInfo == NoProject) IDECommandLine.Args(listOf("serverMode") + additionalArg)
    else IDECommandLine.OpenTestCaseProject(context, listOf("serverMode") + additionalArg)
  }

  protected open fun frontendCommandLine(context: IDERemDevTestContext, joinLink: String): IDECommandLine {
    val thinClientCommand =
      if (context.frontendIDEContext.ide.vmOptions.data().contains("-Djava.awt.headless=true")) "thinClient-headless" else "thinClient"
    return IDECommandLine.Args(listOf(thinClientCommand, joinLink))
  }

  protected open fun createBackgroundRun(
    backendRun: BackgroundRun,
    frontendProcess: IDEHandle,
    frontendDriver: Driver,
    frontendStartResult: Deferred<IDEStartResult>,
    joinLink: String,
    frontendRunContext: IDERunContext
  ): RemoteDevBackgroundRun = RemoteDevBackgroundRun(backendRun = backendRun,
                                                     frontendProcess = frontendProcess,
                                                     frontendDriver = frontendDriver,
                                                     frontendStartResult = frontendStartResult,
                                                     frontendRunContext = frontendRunContext)

  /** Lets the environment the frontend runs in - dockerized support, for one - rewrite the link the backend reported. */
  private fun customizeJoinLink(joinLink: String): String =
    di.direct.instanceOrNull<RemoteDevJoinLinkCustomizer>()?.customizeJoinLink(joinLink) ?: joinLink

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
