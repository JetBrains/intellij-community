// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TerminalCommandExecutor {
  fun startCommandExecution(command: String)
}
