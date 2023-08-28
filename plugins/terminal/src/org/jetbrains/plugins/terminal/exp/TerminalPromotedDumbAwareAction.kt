// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAwareAction

abstract class TerminalPromotedDumbAwareAction : DumbAwareAction(), ActionPromoter {
  /**
   * Prioritize terminal actions if there are actions with the same shortcuts.
   * It's safe because terminal actions are enabled only in terminal.
   */
  override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
    return listOf(this)
  }

  protected val AnActionEvent.editor: Editor?
    get() = getData(CommonDataKeys.EDITOR)
  protected val AnActionEvent.outputController: TerminalOutputController?
    get() = editor?.getUserData(TerminalOutputController.KEY)
  protected val AnActionEvent.promptController: TerminalPromptController?
    get() = editor?.getUserData(TerminalPromptController.KEY)
  protected val AnActionEvent.selectionController: TerminalSelectionController?
    get() = editor?.getUserData(TerminalSelectionController.KEY)
}