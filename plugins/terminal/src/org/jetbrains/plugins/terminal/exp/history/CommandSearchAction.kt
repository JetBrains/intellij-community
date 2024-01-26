// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.history

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.editor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.promptController
import org.jetbrains.plugins.terminal.exp.TerminalPromotedDumbAwareAction

class CommandSearchAction : TerminalPromotedDumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    e.promptController?.showCommandSearch()
  }

  override fun update(e: AnActionEvent) {
    val editor = e.editor
    e.presentation.isEnabledAndVisible = editor != null && editor.isPromptEditor && LookupManager.getActiveLookup(editor) == null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}