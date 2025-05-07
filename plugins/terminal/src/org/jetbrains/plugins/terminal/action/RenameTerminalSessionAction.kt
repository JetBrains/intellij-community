// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.action

import com.intellij.ide.actions.ToolWindowTabRenameActionBase
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

class RenameTerminalSessionAction : ToolWindowTabRenameActionBase(
  TerminalToolWindowFactory.TOOL_WINDOW_ID,
  TerminalBundle.message("action.RenameSession.newSessionName.label")
), DumbAware, ActionRemoteBehaviorSpecification.Frontend {
  override fun getContentDisplayNameToEdit(content: Content, project: Project): String {
    val widget = TerminalToolWindowManager.findWidgetByContent(content) ?: return content.displayName
    return widget.terminalTitle.buildFullTitle()
  }

  override fun applyContentDisplayName(content: Content, project: Project, @Nls newContentName: String) {
    TerminalToolWindowManager.findWidgetByContent(content)?.terminalTitle?.change {
      userDefinedTitle = newContentName
    }
  }
}

const val ACTION_ID : String = "Terminal.RenameSession"
