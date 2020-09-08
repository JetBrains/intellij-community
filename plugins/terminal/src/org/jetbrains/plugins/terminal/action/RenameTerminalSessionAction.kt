// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.action

import com.intellij.ide.actions.ToolWindowTabRenameActionBase
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory

class RenameTerminalSessionAction : ToolWindowTabRenameActionBase(
  TerminalToolWindowFactory.TOOL_WINDOW_ID,
  TerminalBundle.message("action.RenameSession.newSessionName.label")
), DumbAware

const val ACTION_ID : String = "Terminal.RenameSession"
