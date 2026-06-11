package com.intellij.ide.starter.driver.driver.remoteDev

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.waitNotNull
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.includeRuntimeModuleRepositoryInIde
import com.intellij.ide.starter.config.useDockerContainer
import com.intellij.ide.starter.config.useInstaller
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.DriverOptions
import com.intellij.ide.starter.driver.engine.LocalDriverRunner
import com.intellij.ide.starter.ide.IDERemDevTestContext
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.tools.ide.performanceTesting.commands.MarshallableCommand
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class IDEBackendHandler(
  private val ideRemDevTestContext: IDERemDevTestContext,
  private val options: DriverOptions,
  private val debugPort: Int,
) {
  companion object {
    internal fun Driver.remoteDevDirectLink(): String {
      return waitNotNull("Join link", 20.seconds) {
        val service = runCatching { service(UnattendedModeManagerImpl::class).also {it.remoteDevDirectLink()} }
          .getOrElse { service(UnattendedModeManagerImplFallback::class) }
        service.remoteDevDirectLink()
      }
    }
  }

  private fun buildBackendCommandLine(): (IDERunContext) -> IDECommandLine {
    return { _: IDERunContext ->
      val additionalArg = if (ConfigurationStorage.useDockerContainer()) {
        listOf ("-l", "0.0.0.0") // tells backend to listen to the incoming rd connections on 0.0.0.0 so it is available outside of docker
      } else emptyList()

      if (ideRemDevTestContext.testCase.projectInfo == NoProject) IDECommandLine.Args(listOf("serverMode") + additionalArg)
      else IDECommandLine.OpenTestCaseProject(ideRemDevTestContext, listOf("serverMode") + additionalArg)
    }
  }

  fun run(
    commands: Iterable<MarshallableCommand>,
    runTimeout: Duration,
    useStartupScript: Boolean,
    launchName: String,
    expectedKill: Boolean,
    expectedExitCode: Int,
    collectNativeThreads: Boolean,
    pauseOnIndexing: Duration? = null,
    configure: IDERunContext.() -> Unit = {},
  ): BackgroundRun {
    if (ConfigurationStorage.useInstaller()) {
      ConfigurationStorage.includeRuntimeModuleRepositoryInIde(true)
    }

    applyBackendVMOptionsPatch()
    return LocalDriverRunner().runIdeWithDriver(context = ideRemDevTestContext,
                                                commandLine = buildBackendCommandLine(),
                                                commands = commands,
                                                runTimeout = runTimeout,
                                                useStartupScript = useStartupScript,
                                                launchName = launchName,
                                                expectedKill = expectedKill,
                                                expectedExitCode = expectedExitCode,
                                                collectNativeThreads = collectNativeThreads,
                                                pauseOnIndexing = pauseOnIndexing) {
      configure(this)
    }
  }

  private fun applyBackendVMOptionsPatch(): IDETestContext {
    val context = ideRemDevTestContext
    val vmOptions = context.ide.vmOptions
    vmOptions.configureLoggers(LogLevel.DEBUG, "#com.intellij.remoteDev.downloader.EmbeddedClientLauncher")
    vmOptions.addSystemProperty("rdct.embedded.client.use.custom.paths", true)
    options.systemProperties.forEach(vmOptions::addSystemProperty)
    if (vmOptions.isUnderDebug()) {
      vmOptions.debug(debugPort, suspend = false)
    }
    else {
      vmOptions.dropDebug()
    }
    return context
  }

  @Remote(value = "com.jetbrains.rdserver.unattendedHost.connection.UnattendedModeManagerImpl",
          plugin = "com.jetbrains.remoteDevelopment/intellij.platform.remoteController.backend")
  interface UnattendedModeManagerImpl {
    fun remoteDevDirectLink(): String?
  }

  /**
   * Needed for compatibility of 262 driver with older version of IDE.
   * e.g. for update tests.
   */
  @Remote(value = "com.jetbrains.rdserver.unattendedHost.connection.UnattendedModeManagerImpl",
          plugin = "com.jetbrains.remoteDevelopment")
  interface UnattendedModeManagerImplFallback : UnattendedModeManagerImpl
}