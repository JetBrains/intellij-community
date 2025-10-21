// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.block.documentation.TerminalDocumentationManager
import org.jetbrains.plugins.terminal.block.history.CommandHistoryPresenter.Companion.isTerminalCommandHistory
import org.jetbrains.plugins.terminal.block.history.CommandSearchPresenter.Companion.isTerminalCommandSearch
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalEditor

internal class TerminalShowDocAction : DumbAwareAction(), HintManagerImpl.ActionToIgnore {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val lookup = LookupManager.getActiveLookup(e.terminalEditor) as? LookupImpl ?: return
    val currentItem = lookup.currentItem ?: return
    TerminalDocumentationManager.getInstance(project).showDocumentationForItem(lookup, currentItem,
                                                                               parentDisposable = lookup,
                                                                               allowEmpty = true,
                                                                               hideLookupOnCancel = false)
  }

  override fun update(e: AnActionEvent) {
    val editor = e.terminalEditor
    val lookup = LookupManager.getActiveLookup(editor)
    // enable this action only in the terminal command completion popup
    e.presentation.isEnabledAndVisible = e.project != null
                                         && editor?.isPromptEditor == true
                                         && lookup != null
                                         && !lookup.isTerminalCommandHistory
                                         && !lookup.isTerminalCommandSearch
                                         && lookup.currentItem != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}
