// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.session

import com.intellij.openapi.project.Project
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.frontend.session.ghostty.createGhosttyTerminalSession
import com.intellij.terminal.frontend.session.jediterm.createJediTerminalSession
import com.jediterm.terminal.TtyConnector
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.session.impl.TerminalSession

@ApiStatus.Internal
fun startTerminalProcess(
  project: Project,
  options: ShellStartupOptions,
): Pair<TtyConnector, ShellStartupOptions> {
  val runner = ReworkedLocalTerminalRunner(project)
  val configuredOptions = runner.configureStartupOptions(options)
  val connector = runner.createTtyConnector(configuredOptions)

  return connector to configuredOptions
}

/**
 * The created session lifecycle is bound to the [coroutineScope].
 * If it cancels, then the process will be terminated.
 * And if the process is terminated on its own, for example, if user executes `exit` or press Ctrl+D,
 * then the [coroutineScope] will be canceled as well.
 */
@ApiStatus.Internal
fun createTerminalSession(
  project: Project?,
  ttyConnector: TtyConnector,
  options: ShellStartupOptions,
  settings: JBTerminalSystemSettingsProviderBase,
  coroutineScope: CoroutineScope,
): TerminalSession {
  val emulatorType = options.emulatorType ?: TerminalEmulatorType.default
  return when (emulatorType) {
    TerminalEmulatorType.JediTerm -> {
      createJediTerminalSession(project, ttyConnector, options, settings, coroutineScope)
    }
    TerminalEmulatorType.Ghostty -> {
      createGhosttyTerminalSession(project, ttyConnector, options, settings, coroutineScope)
    }
  }
}