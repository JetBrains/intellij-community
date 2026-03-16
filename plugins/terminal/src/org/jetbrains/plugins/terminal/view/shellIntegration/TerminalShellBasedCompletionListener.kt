// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.shellIntegration

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TerminalShellBasedCompletionListener {
  fun completionFinished(result: String)
}