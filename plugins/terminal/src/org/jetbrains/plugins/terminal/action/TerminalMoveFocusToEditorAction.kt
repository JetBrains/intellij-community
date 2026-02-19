// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalEditor

@ApiStatus.Internal
class TerminalMoveFocusToEditorAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ToolWindowManager.getInstance(project).activateEditorComponent()
  }

  override fun update(e: AnActionEvent) {
    val enabled = e.project != null &&
                  e.terminalEditor?.isReworkedTerminalEditor == true &&
                  e.getData(PlatformDataKeys.TOOL_WINDOW) != null // if null, it means the terminal itself is in an editor tab (!)
    e.presentation.isEnabledAndVisible = enabled
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}