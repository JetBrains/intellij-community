// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration.interpreter

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.collectAddInterpreterActions
import javax.swing.JComponent

/**
 * The pane of a module that is not a Python project, such as a module of GoLand without the Python facet. Its button
 * adds an interpreter, and the add flow makes the module a Python project, see `asPyProjectAddingFacet`. The page only
 * shows the pane, so opening the settings changes no module.
 */
internal class PyNoPythonProjectPane(module: Module, onInterpreterSet: (PythonInterpreter) -> Unit) {
  private val addInterpreterAction: DumbAwareAction = object : DumbAwareAction(
    PyBundle.messagePointer("configurable.PyWorkspaceStructureConfigurable.sdk.add"),
    Presentation.NULL_STRING,
    AllIcons.General.Add,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      // The module has no PyProject yet, so it goes in without one, and the add flow gives it one.
      val moduleOrProject = ModuleOrProject.ModuleAndProject(module)
      val group = DefaultActionGroup(collectAddInterpreterActions(moduleOrProject) { onInterpreterSet(it) })
      val popup = JBPopupFactory.getInstance().createActionGroupPopup(
        null, group, e.dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false,
      )
      val source = e.inputEvent?.component
      if (source != null) popup.showUnderneathOf(source) else popup.showInBestPositionFor(e.dataContext)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  }

  val component: JComponent

  init {
    val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, DefaultActionGroup(addInterpreterAction), true)
    component = panel {
      row { text(PyBundle.message("configurable.PyWorkspaceStructureConfigurable.not.python.project", module.name)) }
      row { cell(toolbar.component) }
    }
    toolbar.targetComponent = component
  }
}
