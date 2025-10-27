package com.intellij.ide.starter.driver.engine

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.MarshallableCommand
import org.kodein.di.direct
import org.kodein.di.instanceOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun IDETestContext.runIdeWithDriverScreenRec(commandLine: (IDERunContext) -> IDECommandLine = determineDefaultCommandLineArguments(),
                                    commands: Iterable<MarshallableCommand> = CommandChain(),
                                    runTimeout: Duration = 10.minutes,
                                    useStartupScript: Boolean = true,
                                    launchName: String = "",
                                    expectedKill: Boolean = false,
                                    expectedExitCode: Int = 0,
                                    collectNativeThreads: Boolean = false,
                                    configure: IDERunContext.() -> Unit = {}): BackgroundRun {
  val driverRunner = di.direct.instanceOrNull<DriverRunner>() ?: LocalDriverRunner()
  return driverRunner.runIdeWithDriverScreenRec(this, commandLine, commands, runTimeout, useStartupScript, launchName, expectedKill, expectedExitCode, collectNativeThreads, configure)
}