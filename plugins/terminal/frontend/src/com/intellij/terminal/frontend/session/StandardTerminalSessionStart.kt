// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.session

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.jediterm.core.util.TermSize
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.session.impl.TerminalStartupOptionsImpl
import org.jetbrains.plugins.terminal.session.TerminalStartupOptions

/**
 * Starts the standard reconnectable terminal session without assigning its ownership to a project service.
 *
 * [project] is used synchronously to resolve the startup connector and EEL environment. [statisticsProject]
 * controls optional project-scoped statistics listeners and may be null for application-owned runtimes.
 */
@ApiStatus.Internal
fun startStandardTerminalSession(
  project: Project,
  options: ShellStartupOptions,
  scope: CoroutineScope,
  statisticsProject: Project? = project,
): StandardTerminalSessionStartResult {
  val termSize = options.initialTermSize ?: run {
    LOG.warn("No initial terminal size provided, using default 80x24. $options")
    TermSize(80, 24)
  }
  val optionsWithSize = options.builder().initialTermSize(termSize).build()
  val (ttyConnector, configuredOptions) = startTerminalProcess(project, optionsWithSize)
  val observableTtyConnector = ObservableTtyConnector(ttyConnector)

  val jediTermScope = scope.childScope("JediTerm session")
  val delegate = createTerminalSession(
    project = statisticsProject,
    ttyConnector = observableTtyConnector,
    options = configuredOptions,
    settings = JBTerminalSystemSettingsProvider(),
    coroutineScope = jediTermScope,
  )
  val startupOptions = TerminalStartupOptionsImpl(
    shellCommand = configuredOptions.shellCommand!!,
    workingDirectory = configuredOptions.workingDirectory!!,
    envVariables = configuredOptions.envVariables,
    processType = configuredOptions.processType,
    pid = getLocalPid(ttyConnector),
  )
  return StandardTerminalSessionStartResult(
    configuredOptions = configuredOptions,
    session = createStandardStateAwareTerminalSession(delegate, startupOptions, scope),
    ttyConnector = observableTtyConnector,
  )
}

@ApiStatus.Internal
data class StandardTerminalSessionStartResult(
  val configuredOptions: ShellStartupOptions,
  val session: TerminalSession,
  val ttyConnector: ObservableTtyConnector,
)

@ApiStatus.Internal
fun createStandardStateAwareTerminalSession(
  delegate: TerminalSession,
  startupOptions: TerminalStartupOptions,
  scope: CoroutineScope,
): TerminalSession = StateAwareTerminalSession(delegate, startupOptions, scope)

private fun getLocalPid(ttyConnector: com.jediterm.terminal.TtyConnector): Long? {
  val processTtyConnector = ShellTerminalWidget.getProcessTtyConnector(ttyConnector) ?: run {
    LOG.warn("Failed to get ProcessTtyConnector from $ttyConnector.")
    return null
  }
  return try {
    processTtyConnector.process.pid()
  }
  catch (_: UnsupportedOperationException) {
    // IjentChildPtyProcessAdapter does not expose a real PID for a remote process.
    null
  }
  catch (t: Throwable) {
    LOG.warn("Failed to get pid of the started process: $ttyConnector", t)
    null
  }
}

private val LOG = logger<StandardTerminalSessionStartResult>()
