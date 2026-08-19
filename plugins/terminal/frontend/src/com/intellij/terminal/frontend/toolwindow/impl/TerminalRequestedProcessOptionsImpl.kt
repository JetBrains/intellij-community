// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.terminal.frontend.toolwindow.TerminalRequestedProcessOptions
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.startup.TerminalProcessType

internal data class TerminalRequestedProcessOptionsImpl(
  override val shellCommand: List<String>?,
  override val workingDirectory: String?,
  override val envVariables: Map<String, String>,
  override val processType: TerminalProcessType,
  override val emulatorType: TerminalEmulatorType?,
) : TerminalRequestedProcessOptions