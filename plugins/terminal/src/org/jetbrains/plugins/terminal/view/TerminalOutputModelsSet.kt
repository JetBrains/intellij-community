// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view

import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalOutputModelsSet {
  val regular: TerminalOutputModel
  val alternative: TerminalOutputModel

  val active: StateFlow<TerminalOutputModel>
}