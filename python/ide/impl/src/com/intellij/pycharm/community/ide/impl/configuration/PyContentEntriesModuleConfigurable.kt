// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.ModuleConfigurationStateImpl
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider
import com.intellij.python.pyproject.model.PyProjectModelSettings
import com.jetbrains.python.PyBundle
import com.jetbrains.python.module.PyContentEntriesEditor
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class PyContentEntriesModuleConfigurable(private val module: Module) : SearchableConfigurable.Parent.Abstract() {

  private val topPanel = JPanel(BorderLayout())
  private var modifiableModel: ModifiableRootModel? = null
  private var editor: PyContentEntriesEditor? = null
  private val isPyProjectTomlManaged: Boolean
    get() {
      val pending = module.project.getUserData(PythonContentEntriesConfigurable.PYPROJECT_TOML_PENDING_KEY)
      return pending ?: PyProjectModelSettings.getInstance(module.project).usePyprojectToml
    }

  override fun getDisplayName(): String = PyBundle.message("configurable.PyContentEntriesModuleConfigurable.display.name")

  override fun getHelpTopic(): String = "reference.settingsdialog.project.structure"

  override fun createComponent(): JComponent {
    createEditor()
    return topPanel
  }

  private fun createEditor() {
    val model = ReadAction.computeBlocking<ModifiableRootModel, RuntimeException> {
      ModuleRootManager.getInstance(module).modifiableModel
    }
    modifiableModel = model

    val state = object : ModuleConfigurationStateImpl(module.project, DefaultModulesProvider(module.project)) {
      override fun getModifiableRootModel(): ModifiableRootModel = model
      override fun getCurrentRootModel(): ModifiableRootModel = model
    }
    val newEditor = createEditor(module, state)
    editor = newEditor

    val component = ReadAction.computeBlocking<JComponent, RuntimeException> { newEditor.createComponent() }
    topPanel.add(component, BorderLayout.CENTER)
  }

  private fun createEditor(module: Module, state: ModuleConfigurationStateImpl): PyContentEntriesEditor =
    PyContentEntriesEditor(module, state, true, JavaSourceRootType.SOURCE, JavaSourceRootType.TEST_SOURCE)

  override fun isModified(): Boolean = !isPyProjectTomlManaged && editor?.isModified == true

  override fun apply() {
    val currentEditor = editor ?: return
    val editorWasModified = currentEditor.isModified
    currentEditor.apply()
    if (editorWasModified) {
      ApplicationManager.getApplication().runWriteAction { modifiableModel?.commit() }
      resetEditor()
    }
  }

  override fun reset() {
    if (isPyProjectTomlManaged) return
    editor ?: return
    modifiableModel?.dispose()
    resetEditor()
  }

  private fun resetEditor() {
    val currentEditor = editor ?: return
    currentEditor.disposeUIResources()
    topPanel.remove(currentEditor.component)
    createEditor()
  }

  override fun disposeUIResources() {
    editor?.let {
      it.disposeUIResources()
      topPanel.remove(it.component)
    }
    editor = null
    modifiableModel?.dispose()
    modifiableModel = null
  }

  override fun buildConfigurables(): Array<Configurable> = emptyArray()

  override fun getId(): String = "python.project.structure"
}
