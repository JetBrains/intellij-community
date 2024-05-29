// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.util

import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.TerminalColorPalette
import com.jediterm.core.util.TermSize
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.exp.prompt.TerminalSessionInfo
import org.jetbrains.plugins.terminal.exp.ui.BlockTerminalColorPalette

internal class TestTerminalSessionInfo(
  override val terminalSize: TermSize = TermSize(80, 20)
) : TerminalSessionInfo {
  override val settings: JBTerminalSystemSettingsProviderBase = JBTerminalSystemSettingsProvider()
  override val colorPalette: TerminalColorPalette = BlockTerminalColorPalette()
}
