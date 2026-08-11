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
import org.kodein.di.direct
import org.kodein.di.instanceOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun IDETestContext.runIdeWithDriver(commandLine: (IDERunContext) -> IDECommandLine = determineDefaultCommandLineArguments(),
                                    commands: Iterable<MarshallableCommand> = CommandChain(),
                                    runTimeout: Duration = 10.minutes,
                                    useStartupScript: Boolean = true,
                                    launchName: String = "",
                                    expectedKill: Boolean = false,
                                    expectedExitCode: Int = 0,
                                    collectNativeThreads: Boolean = false,
                                    pauseOnIndexing: Duration? = null,
                                    configure: IDERunContext.() -> Unit = {}): BackgroundRun {
  return selectedDriverRunner().runIdeWithDriver(this, commandLine, commands, runTimeout, useStartupScript, launchName, expectedKill, expectedExitCode, collectNativeThreads, pauseOnIndexing) {
    if (System.getenv("SCREEN_RECORDING_ENABLED").toBoolean()) {
      withScreenRecording()
    }
    configure()
  }
}

/**
 * The runner of the current test: a [DriverRunner] bound in Starter DI, or [defaultDriverRunner] when nothing is bound -
 * which is the case for containers built on the base Starter one, since it can't even see this module.
 *
 * Every site that starts an IDE with a driver should go through this, or launch modes selected through the binding
 * (IJ Light, for one) will silently not apply there.
 */
fun selectedDriverRunner(): DriverRunner = di.direct.instanceOrNull<DriverRunner>() ?: defaultDriverRunner()

/** The mode-agnostic choice; a [DriverRunner] binding that doesn't handle the mode of the current test can delegate to it. */
fun defaultDriverRunner(): DriverRunner = if (ConfigurationStorage.splitMode()) RemDevDriverRunner() else LocalDriverRunner()