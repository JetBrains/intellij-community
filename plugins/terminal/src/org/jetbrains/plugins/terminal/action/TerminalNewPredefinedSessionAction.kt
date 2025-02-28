// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableWithId
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.terminal.*
import org.jetbrains.plugins.terminal.ui.OpenPredefinedTerminalActionProvider
import java.awt.Point
import java.awt.event.MouseEvent
import java.util.function.Predicate
import javax.swing.Icon

class TerminalNewPredefinedSessionAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val popupPoint = getPreferredPopupPoint(e)
    ApplicationManager.getApplication().executeOnPooledThread(Runnable {
      val shells = detectShells()
      val customActions = OpenPredefinedTerminalActionProvider.collectAll(project)
      ApplicationManager.getApplication().invokeLater(Runnable {
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
      }, project.getDisposed())
    })
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
    group.add(TerminalSettingsAction())

    return JBPopupFactory.getInstance().createActionGroupPopup(null, group, dataContext, false, true, false, null, -1, null)
  }

  private fun detectShells(): List<OpenShellAction> {
    return TerminalShellsDetector.detectShells()
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

  private class TerminalSettingsAction : DumbAwareAction(IdeBundle.message("action.text.settings"), null, AllIcons.General.Settings) {
    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project ?: return

      // Match the Terminal configurable by ID.
      // Can't use matching by configurable class name because actual configurable can be wrapped in case of Remote Dev.
      ShowSettingsUtil.getInstance().showSettingsDialog(project, Predicate { configurable: Configurable ->
        configurable is ConfigurableWithId && configurable.getId() == TERMINAL_CONFIGURABLE_ID
      }, null)
    }
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
      TerminalToolWindowManager.getInstance(project).createNewSession(tabState)
    }
  }
}