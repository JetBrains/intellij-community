package com.intellij.terminal.frontend.view.impl

import org.jetbrains.plugins.terminal.block.reworked.TerminalBlocksModel
import org.jetbrains.plugins.terminal.view.TerminalShellIntegration

internal class TerminalShellIntegrationImpl(
  override val blocksModel: TerminalBlocksModel,
) : TerminalShellIntegration {

}