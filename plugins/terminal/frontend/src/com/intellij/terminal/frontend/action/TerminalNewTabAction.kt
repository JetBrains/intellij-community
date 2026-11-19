package com.intellij.terminal.frontend.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.terminal.frontend.toolwindow.impl.requestOpenTerminalTab
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.block.TerminalPromotedDumbAwareAction
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo
import org.jetbrains.plugins.terminal.fus.TerminalTabOpeningWay

@ApiStatus.Internal
open class TerminalNewTabAction : TerminalPromotedDumbAwareAction() {
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
    requestOpenTerminalTab(
      project,
      contentManager = e.getData(PlatformDataKeys.TOOL_WINDOW_CONTENT_MANAGER),
      startupFusInfo = TerminalStartupFusInfo(TerminalTabOpeningWay.OPEN_NEW_TAB),
    )
  }
}
