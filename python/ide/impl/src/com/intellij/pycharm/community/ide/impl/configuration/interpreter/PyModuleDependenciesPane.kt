// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

import com.intellij.pycharm.community.ide.impl.configuration.interpreter.PyModuleDependenciesPresenter.DependencyId
import com.intellij.ui.CheckBoxList
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.EditableListModelDecorator
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.project.PyProject
import java.awt.BorderLayout
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Dependencies tab. Dumb UI: owns no workspace-model / Module access — [PyModuleDependenciesModel]
 * does. Read-only mode driven by [PyModuleDependenciesModel.isEditable].
 */
internal class PyModuleDependenciesPane(
  subject: PyProject,
  candidates: List<PyProject>,
  initialDependencies: Set<PyProject>,
) {

  private val model: PyModuleDependenciesModel = PyModuleDependenciesModelImpl(subject, candidates, initialDependencies)
  private val listModel: DefaultListModel<DependencyId> = DefaultListModel()
  private val list: CheckBoxList<DependencyId> = CheckBoxList(listModel)
  private val header: JBLabel = JBLabel().apply {
    foreground = UIUtil.getContextHelpForeground()
    border = JBUI.Borders.emptyBottom(4)
  }
  private val root: JPanel = JPanel(BorderLayout()).apply {
    add(header, BorderLayout.NORTH)
    add(
      ToolbarDecorator.createDecorator(list, EditableListModelDecorator(listModel))
        .disableRemoveAction()
        .createPanel(),
      BorderLayout.CENTER,
    )
  }

  val component: JComponent get() = root

  init {
    renderCandidates()
    refreshEnabled()
  }

  fun isModified(): Boolean = model.isModified(checkedFromList())

  fun apply() {
    if (!model.isEditable) return
    model.apply(checkedFromList())
  }

  fun reset() {
    model.syncBaselineFromWorkspace()
    renderCandidates()
    refreshEnabled()
  }

  /**
   * Recomputes read-only banner + list enablement from [PyModuleDependenciesModel.isEditable].
   * Called after the page-level checkbox commits so the tab reflects the new mode without a
   * Settings reopen.
   */
  fun refreshEnabled() {
    val readOnly = !model.isEditable
    list.isEnabled = !readOnly
    val (text, visible) = if (readOnly) {
      PyBundle.message("python.pyproject.toml.dependencies.managed.hint") to true
    }
    else {
      "" to false
    }
    header.text = text
    header.isVisible = visible
  }

  private fun checkedFromList(): Set<DependencyId> = list.checkedItems.toHashSet()

  private fun renderCandidates() {
    val initial = model.initialDependencies
    list.clear()
    for (candidate in model.candidates) {
      list.addItem(candidate, candidate.name, candidate in initial)
    }
  }
}
