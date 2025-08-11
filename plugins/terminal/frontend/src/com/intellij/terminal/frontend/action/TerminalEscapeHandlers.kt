// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.frontend.action.TerminalFrontendDataContextUtils.terminalSearchController
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputModelEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalEditor

internal class CancelSelection : TerminalEscapeHandler {
  override val order: Int
    get() = 200

  override fun isEnabled(e: AnActionEvent): Boolean = e.terminalEditor?.selectionModel?.hasSelection() == true

  override fun execute(e: AnActionEvent) {
    e.terminalEditor?.selectionModel?.removeSelection()
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

internal class SelectEditor : TerminalEscapeHandler {
  override val order: Int
    get() = 500

  override fun isEnabled(e: AnActionEvent): Boolean =
    e.project != null &&
    e.terminalEditor?.isOutputModelEditor == true && // only for the regular buffer, as apps with the alternate buffer may need Esc themselves
    e.getData(PlatformDataKeys.TOOL_WINDOW) != null && // if null, it means the terminal itself is in an editor tab (!)
    AdvancedSettings.getBoolean("terminal.escape.moves.focus.to.editor")

  override fun execute(e: AnActionEvent) {
    val project = e.project ?: return
    ToolWindowManager.getInstance(project).activateEditorComponent()
  }
}
