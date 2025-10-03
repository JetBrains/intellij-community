package com.intellij.terminal.frontend.impl

import com.intellij.terminal.frontend.TerminalOutputModelsSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel

internal class TerminalOutputModelsSetImpl(
  override val regular: TerminalOutputModel,
  override val alternative: TerminalOutputModel,
) : TerminalOutputModelsSet {
  private val activeModelFlow = MutableStateFlow(regular)

  override val active: StateFlow<TerminalOutputModel> = activeModelFlow.asStateFlow()

  fun setActiveModel(isAlternative: Boolean) {
    activeModelFlow.value = if (isAlternative) alternative else regular
  }
}