// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.session.impl

import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class TerminalState(
  val isCursorVisible: Boolean,
  /** Null means default */
  val cursorShape: CursorShape?,
  val mouseMode: MouseMode,
  val mouseFormat: MouseFormat,
  val isAlternateScreenBuffer: Boolean,
  val isApplicationArrowKeys: Boolean,
  val isApplicationKeypad: Boolean,
  val isAutoNewLine: Boolean,
  val isAltSendsEscape: Boolean,
  val isBracketedPasteMode: Boolean,
  val windowTitle: String,
  /** Whether such events as command started/finished are supported by the shell integration */
  val isShellIntegrationEnabled: Boolean,
  /**
   * Absolute OS-dependent path of the current shell directory.
   * Though, when shell is started in non-local environments (WSL/Docker),
   * this path is related to the remote file system root.
   *
   * For example,
   * 1. Shell started locally -> `/home/user/dir` or `C:\Users\user\dir`
   * 2. Shell started in WSL -> `/home/user/dir` (relative to WSL root path like `\\wsl.localhost\DistName`)
   * 3. Shell started in Docker container -> `/home/user/dir` (relative to Docker container root path like `/docker-123456`)
   *
   * Can be null in case the current directory is unknown.
   */
  val currentDirectory: String?,
)