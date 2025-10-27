package com.intellij.ide.starter.driver.driver.remoteDev

import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitNotNull
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.includeRuntimeModuleRepositoryInIde
import com.intellij.ide.starter.config.useInstaller
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.LocalDriverRunner
import com.intellij.ide.starter.ide.IDERemDevTestContext
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.tools.ide.performanceTesting.commands.MarshallableCommand
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class IDEBackendHandler(private val ideRemDevTestContext: IDERemDevTestContext, private val options: RemoteDevDriverOptions) {
  private fun buildBackendCommandLine(): (IDERunContext) -> IDECommandLine {
    return { _: IDERunContext ->
      if (ideRemDevTestContext.testCase.projectInfo == NoProject) IDECommandLine.Args(listOf("serverMode"))
      else IDECommandLine.OpenTestCaseProject(ideRemDevTestContext, listOf("serverMode"))
    }
  }


  private fun awaitForLogFile(logFile: Path) {
    waitFor("Log file exists", 30.seconds, 1.seconds) {
      logFile.exists()
    }
  }

  private fun awaitJoinLink(logFile: Path, logLinesBeforeBackendStarted: Int): String {
    awaitForLogFile(logFile)
    val joinLinkEntryPrefix = "Join link: tcp"
    val joinLinkEntryRegex = "tcp://.+".toRegex()

    return waitNotNull("Awaiting join link", timeout = 14.seconds, interval = 2.seconds) {
      val matchingLogLines = Files.readAllLines(logFile).drop(logLinesBeforeBackendStarted)
        .filter { it.contains(joinLinkEntryPrefix) }
        .also { logOutput("Found join links: $it") }

      val links = matchingLogLines.mapNotNull { joinLinkEntryRegex.find(it) }.map { it.value }.distinct()
      links.singleOrNull()
    }
  }

  fun run(commands: Iterable<MarshallableCommand>, runTimeout: Duration, useStartupScript: Boolean, launchName: String, expectedKill: Boolean, expectedExitCode: Int, collectNativeThreads: Boolean, configure: IDERunContext.() -> Unit): Pair<BackgroundRun, String> {
    if (ConfigurationStorage.useInstaller()) {
      ConfigurationStorage.includeRuntimeModuleRepositoryInIde(true)
    }

    applyBackendVMOptionsPatch(options)
    var logLinesBeforeBackendStarted: Int? = null
    var logFile: Path? = null
    val backgroundRun = LocalDriverRunner().runIdeWithDriver(context = ideRemDevTestContext,
                                                             commandLine = buildBackendCommandLine(),
                                                             commands = commands,
                                                             runTimeout = runTimeout,
                                                             useStartupScript = useStartupScript,
                                                             launchName = launchName,
                                                             expectedKill = expectedKill,
                                                             expectedExitCode = expectedExitCode,
                                                             collectNativeThreads = collectNativeThreads) {
      configure(this)
      logFile = logsDir.resolve("idea.log")
      logLinesBeforeBackendStarted = Result.runCatching { Files.readAllLines(logFile).size }.getOrDefault(0)
    }

    val joinLink = try {
      awaitJoinLink(logFile = waitNotNull("Wait for log file resolved") { logFile },
                    logLinesBeforeBackendStarted = waitNotNull("Wait for number of exiting backend line resolved") { logLinesBeforeBackendStarted })
    }
    catch (t: Throwable) {
      logError("Failed to await join link. Log file: ${logFile?.toAbsolutePath()}", t)
      backgroundRun.process.kill()
      throw t
    }
    return backgroundRun to joinLink
  }


  private fun applyBackendVMOptionsPatch(options: RemoteDevDriverOptions): IDETestContext {
    val context = ideRemDevTestContext
    val vmOptions = context.ide.vmOptions
    vmOptions.configureLoggers(LogLevel.DEBUG, "#com.intellij.remoteDev.downloader.EmbeddedClientLauncher")
    vmOptions.addSystemProperty("rdct.embedded.client.use.custom.paths", true)
    options.backendOptions.systemProperties.forEach(vmOptions::addSystemProperty)
    options.remoteDevVmOptions.forEach(vmOptions::addSystemProperty)
    if (vmOptions.isUnderDebug()) {
      vmOptions.debug(options.backendDebugPort, suspend = false)
    }
    else {
      vmOptions.dropDebug()
    }
    return context
  }
}