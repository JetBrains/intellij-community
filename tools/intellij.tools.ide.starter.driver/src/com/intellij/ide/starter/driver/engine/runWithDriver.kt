package com.intellij.ide.starter.driver.engine

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.splitMode
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.driver.driver.remoteDev.RemDevDriverRunner
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.MarshallableCommand
import org.kodein.di.DI
import org.kodein.di.bindProvider
import org.kodein.di.direct
import org.kodein.di.instance
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val remDevAwareDi
  get() = DI {
    extend(di)
    bindProvider<DriverRunner> { if (ConfigurationStorage.splitMode()) RemDevDriverRunner() else LocalDriverRunner() }
  }


fun IDETestContext.runIdeWithDriver(commandLine: (IDERunContext) -> IDECommandLine = determineDefaultCommandLineArguments(),
                                    commands: Iterable<MarshallableCommand> = CommandChain(),
                                    runTimeout: Duration = 10.minutes,
                                    useStartupScript: Boolean = true,
                                    launchName: String = "",
                                    expectedKill: Boolean = false,
                                    expectedExitCode: Int = 0,
                                    collectNativeThreads: Boolean = false,
                                    configure: IDERunContext.() -> Unit = {}): BackgroundRun {
  val driverRunner = remDevAwareDi.direct.instance<DriverRunner>()
  return driverRunner.runIdeWithDriver(this, commandLine, commands, runTimeout, useStartupScript, launchName, expectedKill, expectedExitCode, collectNativeThreads) {
    if (System.getenv("SCREEN_RECORDING_ENABLED").toBoolean()) {
      withScreenRecording()
    }
    configure()
  }
}