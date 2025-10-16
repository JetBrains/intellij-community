package com.intellij.terminal.frontend.view.impl

import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.TerminalBlocksModelImpl
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalShellIntegration

@ApiStatus.Internal
class TerminalShellIntegrationImpl(
  outputModel: TerminalOutputModel,
  coroutineScope: CoroutineScope,
) : TerminalShellIntegration {
  override val blocksModel: TerminalBlocksModelImpl = TerminalBlocksModelImpl(outputModel, coroutineScope.asDisposable())
}