// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.impl

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelsSet

@ApiStatus.Internal
class TerminalOutputModelsSetImpl(
  override val regular: TerminalOutputModel,
  override val alternative: TerminalOutputModel,
) : TerminalOutputModelsSet {
  private val activeModelFlow = MutableStateFlow(regular)

  override val active: StateFlow<TerminalOutputModel> = activeModelFlow.asStateFlow()

  fun setActiveModel(isAlternative: Boolean) {
    activeModelFlow.value = if (isAlternative) alternative else regular
  }
}