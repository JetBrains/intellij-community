// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.toolwindow

import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.startup.TerminalProcessType

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalRequestedProcessOptions {
  val shellCommand: List<String>?
  val workingDirectory: @MultiRoutingFileSystemPath String?
  val envVariables: Map<String, String>
  val processType: TerminalProcessType

  /**
   * The terminal emulator explicitly requested for the session.
   * If null, [TerminalEmulatorType.default] is used.
   */
  @get:ApiStatus.Internal
  val emulatorType: TerminalEmulatorType?
}