package com.intellij.ide.starter.driver.engine

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.tools.ide.performanceTesting.commands.MarshallableCommand
import kotlin.time.Duration

interface DriverRunner {
  // Default method for running IDE with driver, recommended to use this method in performance tests
  fun runIdeWithDriver(
    context: IDETestContext, commandLine: (IDERunContext) -> IDECommandLine,
    commands: Iterable<MarshallableCommand>,
    runTimeout: Duration,
    useStartupScript: Boolean,
    launchName: String,
    expectedKill: Boolean,
    expectedExitCode: Int,
    collectNativeThreads: Boolean,
    configure: IDERunContext.() -> Unit,
  ): BackgroundRun

  // Copy of runIdeWithDriver, but with screen recording, recommended using this method over runIdeWithDriver in UI tests
  fun runIdeWithDriverScreenRec(
    context: IDETestContext, commandLine: (IDERunContext) -> IDECommandLine,
    commands: Iterable<MarshallableCommand>,
    runTimeout: Duration,
    useStartupScript: Boolean,
    launchName: String,
    expectedKill: Boolean,
    expectedExitCode: Int,
    collectNativeThreads: Boolean,
    configure: IDERunContext.() -> Unit,
  ): BackgroundRun =
    runIdeWithDriver(context,
                     commandLine,
                     commands,
                     runTimeout,
                     useStartupScript,
                     launchName,
                     expectedKill,
                     expectedExitCode,
                     collectNativeThreads,
                     { withScreenRecording(); configure() })
}