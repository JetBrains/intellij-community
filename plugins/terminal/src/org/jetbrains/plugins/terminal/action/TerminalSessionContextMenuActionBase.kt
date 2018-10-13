// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContextMenuActionBase
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.ui.content.Content
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory

abstract class TerminalSessionContextMenuActionBase : ToolWindowContextMenuActionBase() {
  override fun update(e: AnActionEvent, activeToolWindow: ToolWindow, selectedContent: Content?) {
    val id = (activeToolWindow as ToolWindowImpl).id
    e.presentation.isEnabledAndVisible = e.project != null && id == TerminalToolWindowFactory.TOOL_WINDOW_ID && selectedContent != null
  }
}