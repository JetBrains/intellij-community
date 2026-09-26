// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.Configurable
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Sources tab for the redesigned interpreter settings page (PY-89840).
 *
 * Thin binding layer over [PySourcesModel]. All model / editor lifecycle work — reading the
 * `ModifiableRootModel`, committing on apply, recreating on reset — lives in the model and runs
 * inside `readAction` / `writeAction`, off the EDT. The pane hops back to [Dispatchers.EDT] only
 * to attach the editor component to the visible panel.
 */
internal class PySourcesConfigurable(module: Module) : Configurable {
  private val model = PySourcesModel(module)
  private val topPanel: JPanel = JPanel(BorderLayout())

  override fun getDisplayName(): String = PyBundle.message("configurable.PyWorkspaceStructureConfigurable.tab.sources")
  override fun getHelpTopic(): String? = null

  override fun createComponent(): JComponent {
    launchEditorAttach()
    return topPanel
  }

  override fun isModified(): Boolean = model.isModified()

  override fun apply() {
    PyPackageCoroutine.launch(model.moduleProject()) {
      val committed = model.apply()
      if (committed) {
        withContext(Dispatchers.EDT) {
          detachEditor()
          launchEditorAttach()
        }
      }
    }
  }

  override fun reset() {
    if (model.currentEditor() == null) return
    model.disposeModel()
    detachEditor()
    launchEditorAttach()
  }

  override fun disposeUIResources() {
    detachEditor()
    model.disposeEditor()
    model.disposeModel()
  }

  /**
   * Launches the async editor build. The model resolves the module root through [readAction], then
   * we come back to the EDT to attach the newly created Swing component to [topPanel]. `createComponent`
   * of the editor itself must run on the EDT, so the model returns the (Swing-safe) editor instance
   * and the pane invokes `createComponent()` here.
   */
  private fun launchEditorAttach() {
    PyPackageCoroutine.launch(model.moduleProject()) {
      val editor = model.createEditor()
      withContext(Dispatchers.EDT) {
        val component = editor.createComponent() ?: return@withContext
        topPanel.add(component, BorderLayout.CENTER)
        topPanel.revalidate()
        topPanel.repaint()
      }
    }
  }

  private fun detachEditor() {
    val editor = model.currentEditor() ?: return
    topPanel.remove(editor.component)
    model.disposeEditor()
  }
}
