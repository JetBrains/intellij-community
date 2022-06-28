// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.action

import com.intellij.ide.actions.ToolWindowTabRenameActionBase
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalView

class RenameTerminalSessionAction : ToolWindowTabRenameActionBase(
  TerminalToolWindowFactory.TOOL_WINDOW_ID,
  TerminalBundle.message("action.RenameSession.newSessionName.label")
), DumbAware {
  override fun getContentDisplayNameToEdit(content: Content, project: Project): String =
    TerminalView.getWidgetByContent(content)?.terminalTitle?.let {
      it.userDefinedTitle ?: it.applicationTitle ?: it.defaultTitle
    } ?: content.displayName

  override fun applyContentDisplayName(content: Content, project: Project, @Nls newContentName: String) {
    TerminalView.getWidgetByContent(content)?.terminalTitle?.change {
      userDefinedTitle = newContentName
    }
  }
}

const val ACTION_ID : String = "Terminal.RenameSession"
