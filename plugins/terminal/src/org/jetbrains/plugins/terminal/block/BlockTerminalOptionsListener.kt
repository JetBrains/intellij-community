// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptStyle
import java.util.*

@ApiStatus.Internal
interface BlockTerminalOptionsListener : EventListener {
  fun promptStyleChanged(promptStyle: TerminalPromptStyle) {}

  fun showSeparatorsBetweenBlocksChanged(shouldShow: Boolean) {}
}