// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.util.PopupUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.agent.TerminalAgentsStateService
import org.jetbrains.plugins.terminal.agent.rpc.TerminalAgentMode
import org.jetbrains.plugins.terminal.fus.TerminalOpeningWay
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import javax.swing.Icon
import javax.swing.JComponent

internal class TerminalAgentsSelectorAction : DumbAwareAction(), CustomComponentAction, ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent) {
    showTerminalAgentsPopup(e)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
    e.presentation.setTextWithMnemonic(templatePresentation.textWithPossibleMnemonic)
    e.presentation.isEnabledAndVisible = getTerminalAgentsToolbarState(e.project)?.let { state ->
      state.selectedAgent == null && state.canShowSelector
    } ?: false
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return object : ActionButtonWithText(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
      override fun shallPaintDownArrow(): Boolean = true

      override fun getDownArrowIcon(): Icon = AllIcons.General.ChevronDown
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal class TerminalAgentsChevronSelectorAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent) {
    showTerminalAgentsPopup(e)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = getTerminalAgentsToolbarState(e.project)?.let { state ->
      state.selectedAgent != null && state.canShowSelector
    } ?: false
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal class LaunchSelectedAgentAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  init {
    templatePresentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val agentKey = TerminalAgentsStateService.getInstance().lastLaunchedAgentKey ?: return
    launchTerminalAgent(
      project,
      agentKey,
      e.getData(PlatformDataKeys.TOOL_WINDOW_CONTENT_MANAGER),
      TerminalStartupFusInfo(TerminalOpeningWay.OPEN_NEW_TAB),
    )
  }

  override fun update(e: AnActionEvent) {
    val toolbarState = getTerminalAgentsToolbarState(e.project)
    val lastLaunchedAgent = toolbarState?.selectedAgent
    if (lastLaunchedAgent == null) {
      e.presentation.isEnabledAndVisible = false
    }
    else {
      e.presentation.isEnabledAndVisible = toolbarState.canShowSelector
      e.presentation.description = TerminalBundle.message(
        "action.Terminal.AiAgents.LaunchSelectedAgent.description", lastLaunchedAgent.terminalAgent.displayName
      )
      e.presentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
      e.presentation.text = lastLaunchedAgent.terminalAgent.displayName
      e.presentation.icon = lastLaunchedAgent.terminalAgent.icon
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal class ToggleShowAiAgentsInToolbarAction : DumbAwareToggleAction(), ActionRemoteBehaviorSpecification.Frontend {
  init {
    templatePresentation.keepPopupOnPerform = KeepPopupOnPerform.IfRequested
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return TerminalAgentsStateService.getInstance().isSelectorVisible
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    TerminalAgentsStateService.getInstance().isSelectorVisible = state
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = e.project != null && isTerminalAgentsEnabled()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

@ApiStatus.Internal
fun createTerminalAgentActions(
  project: Project,
): List<AnAction> {
  val stateService = TerminalAgentsStateService.getInstance()
  return getAvailableTerminalAgentEntries(project)
    .sortedBy { entry ->
      when(entry.mode) {
        TerminalAgentMode.RUN -> 0
        TerminalAgentMode.INSTALL_AND_RUN -> 1 // put all install requests below launching any existing agent
      }
    }
    .map { entry ->
    val showNewBadge = entry.terminalAgent.showsNewBadge && stateService.consumeJunieNewBadgePresentation()
    TerminalAgentSelectAndLaunchAction(entry, showNewBadge)
  }
}

internal class TerminalAgentSelectAndLaunchAction(
  private val agent: TerminalAvailableAgentEntry,
  private val showNewBadge: Boolean,
) : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {

  override fun update(e: AnActionEvent) {
    TerminalAgentsSelectorPresentationUtil.applyToPresentation(e.presentation, agent.terminalAgent, agent.mode, showNewBadge)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    TerminalAgentsStateService.getInstance().lastLaunchedAgentKey = agent.terminalAgent.agentKey

    launchTerminalAgent(
      project,
      agent.terminalAgent.agentKey,
      e.getData(PlatformDataKeys.TOOL_WINDOW_CONTENT_MANAGER),
      TerminalStartupFusInfo(TerminalOpeningWay.AI_AGENTS_BUTTON)
    )
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal class ResetJunieNewBadgeCounterAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent) {
    TerminalAgentsStateService.getInstance().resetJunieNewBadgePresentations()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private data class TerminalAgentsToolbarState(
  val canShowSelector: Boolean,
  val selectedAgent: TerminalAvailableAgentEntry?,
)

private fun getTerminalAgentsToolbarState(project: Project?): TerminalAgentsToolbarState? {
  if (project == null) return null

  val stateService = TerminalAgentsStateService.getInstance()
  val availableAgents = getAvailableTerminalAgentEntries(project)
  return TerminalAgentsToolbarState(
    canShowSelector = isTerminalAgentsEnabled() && stateService.isSelectorVisible && availableAgents.isNotEmpty(),
    selectedAgent = availableAgents.firstOrNull { it.terminalAgent.agentKey == stateService.lastLaunchedAgentKey },
  )
}

private fun showTerminalAgentsPopup(e: AnActionEvent) {
  val project = e.project ?: return
  val popupPoint = e.getPreferredPopupPoint()
  val dataContext = e.dataContext
  val toggleComponent = e.inputEvent?.component

  terminalProjectScope(project).launch {
    val availableAgents = TerminalAgentsAvailabilityService.getInstance(project).refreshAvailableAgents()
    if (availableAgents.isEmpty()) return@launch

    withContext(Dispatchers.EDT) {
      if (project.isDisposed) return@withContext

      val popup = createTerminalAgentsPopup(project, dataContext)
      if (popupPoint != null) {
        popup.show(popupPoint)
      }
      else {
        popup.showInFocusCenter()
      }
      if (toggleComponent != null) {
        PopupUtil.setPopupToggleComponent(popup, toggleComponent)
      }
    }
  }
}

private fun createTerminalAgentsPopup(project: Project, dataContext: DataContext): ListPopup {
  val group = DefaultActionGroup().apply {
    addAll(createTerminalAgentActions(project))
  }
  return JBPopupFactory.getInstance().createActionGroupPopup(
    null, group, dataContext, false, true, false, null, -1, null
  )
}
