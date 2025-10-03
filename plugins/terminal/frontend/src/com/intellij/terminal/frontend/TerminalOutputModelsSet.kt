package com.intellij.terminal.frontend

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