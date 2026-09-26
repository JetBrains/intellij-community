// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class TerminalCommandCompletionAction {
  companion object {
    // This property is placed here to preserve compatibility with its usage in TerminalTextToCommandSession,
    // which has a different release cycle and may require this key to remain in sync across different versions.
    val SUPPRESS_COMPLETION: Key<Boolean> = Key.create("SUPPRESS_TERMINAL_COMPLETION")
  }
}