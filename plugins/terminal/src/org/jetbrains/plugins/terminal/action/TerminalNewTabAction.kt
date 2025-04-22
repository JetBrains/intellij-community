// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.block.TerminalPromotedDumbAwareAction
import org.jetbrains.plugins.terminal.fus.TerminalOpeningWay
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo

open class TerminalNewTabAction : TerminalPromotedDumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {

  init {
    templatePresentation.also {
      it.setText(TerminalBundle.messagePointer("action.Terminal.NewTab.text"))
      it.setDescription(TerminalBundle.messagePointer("action.Terminal.NewTab.description"))
      it.setIconSupplier { AllIcons.General.Add }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val toolWindow = e.dataContext.getData(PlatformDataKeys.TOOL_WINDOW)
    e.presentation.isEnabled = TerminalToolWindowManager.isTerminalToolWindow(toolWindow) && e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val startupFusInfo = TerminalStartupFusInfo(TerminalOpeningWay.OPEN_NEW_TAB)
    TerminalToolWindowManager.getInstance(project).createNewSession(startupFusInfo)
  }

  companion object {
    const val ACTION_ID: String = "Terminal.NewTab"
  }
}
