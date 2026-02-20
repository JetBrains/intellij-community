// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration

import com.intellij.application.options.ModuleAwareProjectConfigurable
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.PlatformContentEntriesConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Key
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.PlatformUtils
import com.jetbrains.python.PyBundle
import com.intellij.python.pyproject.model.PyProjectModelSettings
import org.jetbrains.jps.model.java.JavaSourceRootType
import javax.swing.JComponent

class PythonContentEntriesConfigurable(project: Project) : ModuleAwareProjectConfigurable<Configurable>(
  project,
  PyBundle.message("configurable.PythonContentEntriesConfigurable.display.name"),
  "reference.settingsdialog.project.structure"
) {

  private var pyprojectPanel: DialogPanel? = null

  companion object {
    @JvmField
    val PYPROJECT_TOML_PENDING_KEY: Key<Boolean> = Key.create("PythonContentEntriesConfigurable.pendingUsePyprojectToml")
  }

  override fun createModuleConfigurable(module: Module): Configurable {
    if (PlatformUtils.isPyCharmCommunity()) {
      return PlatformContentEntriesConfigurable(module, JavaSourceRootType.SOURCE)
    }
    return PyContentEntriesModuleConfigurable(module)
  }

  override fun createComponent(): JComponent? {
    if (!PyProjectModelSettings.isFeatureEnabled) {
      return super.createComponent()
    }

    val settings = PyProjectModelSettings.getInstance(project)
    pyprojectPanel = panel {
      row {
        checkBox(PyBundle.message("python.pyproject.toml.based.project.model"))
          .bindSelected(settings::usePyprojectToml)
          .applyToComponent {
            addActionListener { project.putUserData(PYPROJECT_TOML_PENDING_KEY, isSelected) }
          }
          .contextHelp(PyBundle.message("python.pyproject.toml.based.project.model.comment"))
      }
      super.createComponent()?.let { parentComponent ->
        row {
          cell(parentComponent).align(Align.FILL)
        }.resizableRow()
      }
    }

    return pyprojectPanel
  }

  override fun isModified(): Boolean {
    return (pyprojectPanel?.isModified() == true) || super.isModified()
  }

  override fun apply() {
    pyprojectPanel?.apply()
    project.putUserData(PYPROJECT_TOML_PENDING_KEY, null)
    super.apply()
  }

  override fun reset() {
    pyprojectPanel?.reset()
    project.putUserData(PYPROJECT_TOML_PENDING_KEY, null)
    super.reset()
  }

  override fun disposeUIResources() {
    project.putUserData(PYPROJECT_TOML_PENDING_KEY, null)
    pyprojectPanel = null
    super.disposeUIResources()
  }
}
