// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext

class TerminalCtrlCActionsPromoter : ActionPromoter {
  /**
   * On Windows and Linux these actions have the same shortcut - Ctrl+C.
   * So, it is required to properly prioritize them.
   */
  override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
    val copyTextAction = actions.find { it is TerminalCopyTextAction }
    val copyBlockAction = actions.find { it is TerminalCopyBlockAction }
    val clearPromptAction = actions.find { it is TerminalClearPrompt }
    val interruptAction = actions.find { it is TerminalInterruptCommandAction }
    return listOfNotNull(copyTextAction, copyBlockAction, clearPromptAction, interruptAction)
  }
}