// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.impl.tabInEditor.ToolWindowEditorTabDescriptor
import com.intellij.openapi.wm.impl.tabInEditor.ToolWindowEditorTabSupport
import com.intellij.ui.content.Content
import org.jetbrains.plugins.terminal.TerminalTabCloseListener.CloseCheckResult

internal class TerminalToolWindowEditorTabSupport : ToolWindowEditorTabSupport {
  override fun getEditorTabDescriptor(toolWindow: ToolWindow, content: Content): ToolWindowEditorTabDescriptor? {
    if (!TerminalEditorTabSupportUtil.isNewImplementationEnabled()) return null

    val info = content.getUserData(TerminalEditorTabSupportUtil.TERMINAL_EDITOR_TAB_INFO_KEY) ?: return null
    return ToolWindowEditorTabDescriptor(
      title = info.getEditorTabTitle(),
      icon = content.icon ?: toolWindow.icon,
    )
  }

  override fun canCloseFile(project: Project, content: Content): Boolean {
    return TerminalTabCloseListener.runCloseQuery(project, content, projectClosing = false) {
      val info = content.getUserData(TerminalEditorTabSupportUtil.TERMINAL_EDITOR_TAB_INFO_KEY)
                 ?: return@runCloseQuery CloseCheckResult.CAN_CLOSE_SILENTLY
      TerminalTabCloseListener.runCloseCheckBlocking(project) {
        info.shouldConfirmClosing()
      }
    }
  }
}
