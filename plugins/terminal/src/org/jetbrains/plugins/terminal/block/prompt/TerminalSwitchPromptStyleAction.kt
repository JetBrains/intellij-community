// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.prompt

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.block.BlockTerminalOptions

internal class TerminalSwitchPromptStyleAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val options = BlockTerminalOptions.getInstance()
    val newPromptStyle = if (options.promptStyle == TerminalPromptStyle.DOUBLE_LINE) {
      TerminalPromptStyle.SHELL
    }
    else TerminalPromptStyle.DOUBLE_LINE
    options.promptStyle = newPromptStyle
  }

  override fun update(e: AnActionEvent) {
    e.presentation.text = if (BlockTerminalOptions.getInstance().promptStyle == TerminalPromptStyle.SHELL) {
      @Suppress("DialogTitleCapitalization")  // It triggers on 'Pre-set'
      TerminalBundle.message("action.Terminal.SwitchPromptStyle.use.preset.prompt")
    }
    else TerminalBundle.message("action.Terminal.SwitchPromptStyle.use.shell.prompt")
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
