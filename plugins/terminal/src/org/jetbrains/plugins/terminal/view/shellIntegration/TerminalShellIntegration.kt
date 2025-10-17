// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.shellIntegration

import com.intellij.openapi.Disposable
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalShellIntegration {
  val blocksModel: TerminalBlocksModel

  val outputStatus: StateFlow<TerminalOutputStatus>

  fun addCommandExecutionListener(parentDisposable: Disposable, listener: TerminalCommandExecutionListener)
}