// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.session.impl

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.session.TerminalStartupOptions
import org.jetbrains.plugins.terminal.startup.TerminalProcessType

@ApiStatus.Internal
data class TerminalStartupOptionsImpl(
  override val shellCommand: List<String>,
  override val workingDirectory: String,
  override val envVariables: Map<String, String>,
  override val processType: TerminalProcessType,
  override val pid: Long?,
) : TerminalStartupOptions