// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.prompt

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.TerminalOptionsProvider

internal class TerminalSwitchPromptStyleAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val options = TerminalOptionsProvider.instance
    options.useShellPrompt = !options.useShellPrompt
  }

  override fun update(e: AnActionEvent) {
    e.presentation.text = if (TerminalOptionsProvider.instance.useShellPrompt) {
      @Suppress("DialogTitleCapitalization")  // It triggers on 'Pre-set'
      TerminalBundle.message("action.Terminal.SwitchPromptStyle.use.preset.prompt")
    }
    else TerminalBundle.message("action.Terminal.SwitchPromptStyle.use.shell.prompt")
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
