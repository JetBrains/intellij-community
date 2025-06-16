// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.DetectedShellInfo
import org.jetbrains.plugins.terminal.TerminalTabState
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalShellsDetectorApi
import org.jetbrains.plugins.terminal.fus.TerminalOpeningWay
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo
import org.jetbrains.plugins.terminal.ui.OpenPredefinedTerminalActionProvider
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.Icon

class TerminalNewPredefinedSessionAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val popupPoint = getPreferredPopupPoint(e)
    terminalProjectScope(project).launch {
      val shells = detectShells()
      val customActions = OpenPredefinedTerminalActionProvider.collectAll(project)

      withContext(Dispatchers.EDT) {
        if (project.isDisposed) return@withContext

        val popup = createPopup(shells, customActions, e.dataContext)
        if (popupPoint != null) {
          popup.show(popupPoint)
        }
        else {
          popup.showInFocusCenter()
        }
        val inputEvent = e.inputEvent
        if (inputEvent?.component != null) {
          PopupUtil.setPopupToggleComponent(popup, inputEvent.component)
        }
      }
    }
  }

  private fun getPreferredPopupPoint(e: AnActionEvent): RelativePoint? {
    val inputEvent = e.inputEvent
    if (inputEvent is MouseEvent) {
      val comp = inputEvent.component
      if (comp is AnActionHolder) {
        return RelativePoint(comp.parent, Point(comp.x + JBUI.scale(3), comp.y + comp.height + JBUI.scale(3)))
      }
    }
    return null
  }

  private fun createPopup(
    shells: List<AnAction>,
    customActions: List<AnAction>,
    dataContext: DataContext,
  ): ListPopup {
    val group = DefaultActionGroup()
    group.addAll(shells)
    group.addAll(customActions)
    if (shells.size + customActions.size > 0) {
      group.addSeparator()
    }
    val settingsAction = ActionManager.getInstance().getAction("Terminal.Settings")
    if (settingsAction != null) { // can be null on the backend, but this action is frontend-only anyway
      group.add(settingsAction)
    }

    return JBPopupFactory.getInstance().createActionGroupPopup(null, group, dataContext, false, true, false, null, -1, null)
  }

  private suspend fun detectShells(): List<OpenShellAction> {
    // Fetch shells from the backend
    return TerminalShellsDetectorApi.getInstance()
      .detectShells()
      .groupByTo(LinkedHashMap(), DetectedShellInfo::name)
      .values
      .flatMap { shellInfos ->
        if (shellInfos.size > 1) {
          shellInfos.map { info -> createOpenShellAction(info.path, info.options, "${info.name} (${info.path})") }
        }
        else {
          val info = shellInfos[0]
          listOf(createOpenShellAction(info.path, info.options, info.name))
        }
      }
  }

  private fun createOpenShellAction(
    shellPath: String,
    shellOptions: List<String>,
    presentableName: @NlsSafe String,
  ): OpenShellAction {
    val shellCommand = listOf(shellPath) + shellOptions
    val icon = if (shellPath.endsWith("wsl.exe")) AllIcons.RunConfigurations.Wsl else null
    return OpenShellAction(presentableName, shellCommand, icon)
  }

  private class OpenShellAction(
    presentableName: @NlsActions.ActionText String,
    private val myCommand: List<String>,
    icon: Icon?,
  ) : DumbAwareAction(presentableName, null, icon) {
    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project ?: return

      val tabState = TerminalTabState()
      tabState.myTabName = templateText
      tabState.myShellCommand = myCommand
      val startupFusInfo = TerminalStartupFusInfo(TerminalOpeningWay.START_NEW_PREDEFINED_SESSION)
      TerminalToolWindowManager.getInstance(project).createNewSession(tabState, startupFusInfo)
    }
  }
}