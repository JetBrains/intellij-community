package com.intellij.ide.starter.driver.engine

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.tools.ide.performanceTesting.commands.MarshallableCommand
import kotlin.time.Duration

interface DriverRunner {
  fun runIdeWithDriver(context: IDETestContext, commandLine: (IDERunContext) -> IDECommandLine,
                       commands: Iterable<MarshallableCommand>,
                       runTimeout: Duration,
                       useStartupScript: Boolean,
                       launchName: String,
                       expectedKill: Boolean,
                       expectedExitCode: Int,
                       collectNativeThreads: Boolean,
                       configure: IDERunContext.() -> Unit): BackgroundRun
}