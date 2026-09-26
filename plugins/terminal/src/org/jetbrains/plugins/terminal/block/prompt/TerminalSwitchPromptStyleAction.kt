// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.prompt

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.project.DumbAwareToggleAction
import org.jetbrains.plugins.terminal.block.BlockTerminalOptions

internal sealed class TerminalSwitchPromptStyleAction(private val style: TerminalPromptStyle) : DumbAwareToggleAction() {
  override fun isSelected(e: AnActionEvent): Boolean {
    return BlockTerminalOptions.getInstance().promptStyle == style
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (state) {
      BlockTerminalOptions.getInstance().promptStyle = style
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.keepPopupOnPerform = KeepPopupOnPerform.IfRequested
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal class TerminalUseSingleLinePromptAction : TerminalSwitchPromptStyleAction(TerminalPromptStyle.SINGLE_LINE)

internal class TerminalUseDoubleLinePromptAction : TerminalSwitchPromptStyleAction(TerminalPromptStyle.DOUBLE_LINE)

internal class TerminalUseShellPromptAction : TerminalSwitchPromptStyleAction(TerminalPromptStyle.SHELL)
