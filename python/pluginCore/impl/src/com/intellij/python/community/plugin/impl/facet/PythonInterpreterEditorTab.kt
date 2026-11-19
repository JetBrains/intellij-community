// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.impl.facet

import com.intellij.facet.ui.FacetEditorContext
import com.intellij.facet.ui.FacetEditorTab
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.python.PyBundle
import com.jetbrains.python.configuration.interpreter.PyInterpreterComboBox
import com.jetbrains.python.configuration.setPythonInterpreterBlocking
import org.jetbrains.annotations.Nls
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent
import javax.swing.JLabel

/**
 * The tab of the Python facet in the interpreter library flow: the interpreter of the module, picked from the
 * interpreters of the Python projects. The legacy flow has no tab, see [PythonFacetConfiguration].
 *
 * Project Structure applies a facet editor in a write action, and its own models of the module commit after it. So
 * apply sets the interpreter on the module only when no modal dialog is open, see [setPythonInterpreterBlocking].
 */
internal class PythonInterpreterEditorTab(private val editorContext: FacetEditorContext) : FacetEditorTab() {
  private val combo = PyInterpreterComboBox(editorContext.project, { editorContext.module })

  /** The interpreter of the module at the last read or apply. */
  private var appliedInterpreter: PythonInterpreter? = null

  private val component: JComponent = panel {
    row(JLabel(PyBundle.message("configurable.PyWorkspaceStructureConfigurable.sdk.label")).apply { labelFor = combo.labeledComponent }) {
      cell(combo.component).align(AlignX.FILL).resizableColumn()
    }.layout(RowLayout.LABEL_ALIGNED)
  }.apply {
    // Project Structure puts a facet editor at the edges of its area, so the tab keeps the gaps of a settings page.
    border = JBUI.Borders.empty(UIUtil.LARGE_VGAP, UIUtil.DEFAULT_HGAP * 2)
  }

  override fun getDisplayName(): @Nls String = PyBundle.message("configurable.PythonInterpreterEditorTab.display.name")

  override fun createComponent(): JComponent = component

  override fun isModified(): Boolean = combo.selectedInterpreter != appliedInterpreter

  override fun apply() {
    val chosen = combo.selectedInterpreter
    val module = editorContext.module
    ApplicationManager.getApplication().invokeLater({ setPythonInterpreterBlocking(module, chosen) }, ModalityState.nonModal(), module.disposed)
    appliedInterpreter = chosen
  }

  override fun reset() {
    combo.reload { moduleInterpreter ->
      appliedInterpreter = moduleInterpreter
      moduleInterpreter
    }
  }
}
