// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action.reworked

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.plugins.terminal.action.TerminalEscapeHandler
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.editor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor

internal class CancelSelection : TerminalEscapeHandler {
  override val order: Int
    get() = 200

  override fun isEnabled(e: AnActionEvent): Boolean = e.editor?.selectionModel?.hasSelection() == true

  override fun execute(e: AnActionEvent) {
    e.editor?.selectionModel?.removeSelection()
  }
}

internal class SelectEditor : TerminalEscapeHandler {
  override val order: Int
    get() = 500

  override fun isEnabled(e: AnActionEvent): Boolean = e.project != null && e.editor?.isReworkedTerminalEditor == true

  override fun execute(e: AnActionEvent) {
    val project = e.project ?: return
    ToolWindowManager.getInstance(project).activateEditorComponent()
  }
}
