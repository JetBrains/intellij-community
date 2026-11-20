// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.session

import com.intellij.openapi.project.Project
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.frontend.session.ghostty.createGhosttyTerminalSession
import com.intellij.terminal.frontend.session.jediterm.createJediTerminalSession
import com.intellij.terminal.frontend.session.ketraterm.createKetraTermTerminalSession
import com.jediterm.terminal.TtyConnector
import io.github.ketraterm.shell.launch.TerminalShellLaunch
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.session.impl.TerminalSession

@ApiStatus.Internal
fun startTerminalProcess(
  project: Project,
  options: ShellStartupOptions,
): TerminalProcessStartResult {
  val runner = ReworkedLocalTerminalRunner(project)
  var started = false
  try {
    val configuredOptions = runner.configureStartupOptions(options)
    val connector = runner.createTtyConnector(configuredOptions)
    started = true
    return TerminalProcessStartResult(connector, configuredOptions, runner.shellLaunch)
  }
  finally {
    if (!started) runner.shellLaunch?.close()
  }
}

/** The caller owns the connector and shell launch until session creation succeeds. */
@ApiStatus.Internal
data class TerminalProcessStartResult(
  val ttyConnector: TtyConnector,
  val options: ShellStartupOptions,
  val shellLaunch: TerminalShellLaunch?,
)

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
  shellLaunch: TerminalShellLaunch? = null,
): TerminalSession {
  val emulatorType = options.emulatorType ?: TerminalEmulatorType.default
  return when (emulatorType) {
    TerminalEmulatorType.JediTerm -> {
      createJediTerminalSession(project, ttyConnector, options, settings, coroutineScope)
    }
    TerminalEmulatorType.KetraTerm -> {
      createKetraTermTerminalSession(project, ttyConnector, options, settings, coroutineScope, shellLaunch = shellLaunch)
    }
    TerminalEmulatorType.Ghostty -> {
      createGhosttyTerminalSession(project, ttyConnector, options, settings, coroutineScope)
    }
  }
}
