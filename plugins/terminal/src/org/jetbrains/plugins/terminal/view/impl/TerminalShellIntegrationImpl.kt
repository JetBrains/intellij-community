// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.impl

import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.TerminalBlocksModelImpl
import org.jetbrains.plugins.terminal.block.reworked.TerminalOffset
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalShellIntegration

@ApiStatus.Internal
class TerminalShellIntegrationImpl(
  outputModel: TerminalOutputModel,
  coroutineScope: CoroutineScope,
) : TerminalShellIntegration {
  override val blocksModel: TerminalBlocksModelImpl = TerminalBlocksModelImpl(outputModel, coroutineScope.asDisposable())

  fun onPromptStarted(offset: TerminalOffset) {
    blocksModel.startNewBlock(offset)
  }

  fun onPromptFinished(offset: TerminalOffset) {
    blocksModel.updateActiveCommandBlock { block ->
      block.copy(commandStartOffset = offset)
    }
  }

  fun onCommandStarted(offset: TerminalOffset) {
    blocksModel.updateActiveCommandBlock { block ->
      block.copy(outputStartOffset = offset)
    }
  }

  fun onCommandFinished(exitCode: Int) {
    blocksModel.updateActiveCommandBlock { block ->
      block.copy(exitCode = exitCode)
    }
  }
}