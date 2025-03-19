// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.prompt

import org.jetbrains.annotations.ApiStatus

/** [org.jetbrains.plugins.terminal.fus.TerminalSettingsStateCollector.GROUP] must be updated if any new value added or renamed. */
@ApiStatus.Internal
enum class TerminalPromptStyle {
  SINGLE_LINE, DOUBLE_LINE, SHELL
}