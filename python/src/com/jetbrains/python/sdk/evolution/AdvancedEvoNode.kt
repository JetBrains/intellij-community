// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.evolution

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.python.sdk.backend.PySdkBundle
import com.intellij.python.sdk.backend.evolution.EvoToolContext
import com.intellij.python.sdk.backend.evolution.evoActionLeaf
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoNodeDto
import com.intellij.python.sdk.common.evolution.EvoNodeIds
import com.intellij.python.sdk.common.evolution.EvoNodeKind
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.addInterpreterPopupFooterText
import com.jetbrains.python.sdk.collectAddInterpreterActions
import com.jetbrains.python.sdk.collectExtraAddInterpreterActions

/**
 * The "Custom" node: the full set of add-interpreter actions.
 *
 * It manages no environments and has no tool behind it, so it is not a
 * [com.intellij.python.sdk.backend.evolution.PyEvoEnvironmentProvider]. The core shows it after every provider.
 */
internal object AdvancedEvoNode {
  const val EXTRA_ACTION_ID_PREFIX: String = "extra:"

  val node: EvoNodeDto
    get() = EvoNodeDto(
      id = EvoNodeIds.ADVANCED,
      label = PySdkBundle.message("evolution.node.custom"),
      icon = AllIcons.Toolwindows.ToolWindowInternal.rpcId(),
      kind = EvoNodeKind.ADVANCED,
    )

  suspend fun loadSections(context: EvoToolContext): EvoLoadResultDto {
    val actions = collectAddInterpreterActions(ModuleOrProject.ModuleAndProject(context.pyProject.pyProject)) { }
    // Serialize each add-interpreter action by its stable index; the same list is re-collected on click to run it
    // (see PyEvoSdkApiProvider.performNodeAction).
    val leaves = actions.mapIndexed { index, action ->
      val title = action.templatePresentation.text ?: ""
      evoActionLeaf(title = title, icon = action.templatePresentation.icon ?: AllIcons.Toolwindows.ToolWindowInternal,
                    actionId = index.toString())
    }
    val project = context.workspace.project
    val extraLeaves = collectExtraAddInterpreterActions().mapNotNull { action ->
      val actionId = ActionManager.getInstance().getId(action) ?: return@mapNotNull null
      val presentation = createExtraActionEvent(project, action).presentation
      if (!presentation.isVisible) return@mapNotNull null
      val title = presentation.text ?: actionId
      evoActionLeaf(title = title,
                    description = presentation.description ?: title,
                    icon = presentation.icon ?: AllIcons.Toolwindows.ToolWindowInternal,
                    trailingIcon = presentation.getClientProperty(ActionUtil.SECONDARY_ICON),
                    actionId = EXTRA_ACTION_ID_PREFIX + actionId)
    }
    return EvoLoadResultDto.Ok(listOf(EvoSectionDto(label = null, leaves = leaves + extraLeaves)),
                               stepDescription = addInterpreterPopupFooterText())
  }

  suspend fun createExtraActionEvent(project: Project, action: AnAction): AnActionEvent {
    val context = SimpleDataContext.getProjectContext(project)
    val event = AnActionEvent.createEvent(context, action.templatePresentation.clone(), ActionPlaces.POPUP, ActionUiKind.POPUP, null)
    readAction { ActionUtil.updateAction(action, event) }
    return event
  }
}
