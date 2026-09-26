// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.action

import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.terminal.frontend.action.TerminalFrontendDataContextUtils.terminalSearchController
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalEditor

internal class ClosePopupCompletion : TerminalEscapeHandler {
  override val order: Int
    get() = 100

  override fun isEnabled(e: AnActionEvent): Boolean {
    return LookupManager.getActiveLookup(e.terminalEditor) != null
  }

  override fun execute(e: AnActionEvent) {
    val lookup = LookupManager.getActiveLookup(e.terminalEditor)
    lookup?.hideLookup(true)
  }
}

internal class CancelInlineCompletion : TerminalEscapeHandler {
  override val order: Int
    get() = 150

  override fun isEnabled(e: AnActionEvent): Boolean {
    return e.terminalEditor?.let { InlineCompletionSession.getOrNull(it) != null } == true
  }

  override fun execute(e: AnActionEvent) {
    e.terminalEditor?.let { InlineCompletion.getHandlerOrNull(it)?.cancel(FinishType.ESCAPE_PRESSED) }
  }
}

internal class CancelSelection : TerminalEscapeHandler {
  override val order: Int
    get() = 200

  override fun isEnabled(e: AnActionEvent): Boolean = e.terminalEditor?.selectionModel?.hasSelection() == true

  override fun execute(e: AnActionEvent) {
    e.terminalEditor?.selectionModel?.removeSelection(true)
  }
}

internal class CloseSearch : TerminalEscapeHandler {
  override val order: Int
    get() = 300

  override fun isEnabled(e: AnActionEvent): Boolean = e.dataContext.terminalSearchController?.hasActiveSession() == true

  override fun execute(e: AnActionEvent) {
    e.dataContext.terminalSearchController?.finishSearchSession()
  }
}