// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class TerminalCommandCompletionShowingMode {
  NEVER, ONLY_PARAMETERS, ALWAYS
}
