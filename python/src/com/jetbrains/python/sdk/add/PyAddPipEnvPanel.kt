// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add

import com.intellij.application.options.ModuleListCellRenderer
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.PythonModuleTypeBase
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.associatedModulePath
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.flavors.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ItemEvent
import javax.swing.Icon
import javax.swing.JComboBox

/**
 * The UI panel for adding the pipenv interpreter for the project.
 *
 * @author vlan
 */
class PyAddPipEnvPanel(private val project: Project?,
                       private val module: Module?,
                       private val existingSdks: List<Sdk>,
                       override var newProjectPath: String?) : PyAddNewEnvPanel() {
  override val envName = "Pipenv"
  override val panelName = "Pipenv Environment"
  override val icon: Icon = PIPENV_ICON

  private val DEFAULT_PYTHON = "<Default>"
  private val moduleField: JComboBox<Module>
  private val languageLevelsField: JComboBox<String>
  private val installPackagesCheckBox = JBCheckBox("Install packages from Pipfile").apply {
    isVisible = newProjectPath == null
  }

  init {
    layout = BorderLayout()

    val modules = project?.let {
      ModuleUtil.getModulesOfType(it, PythonModuleTypeBase.getInstance())
    } ?: emptyList()

    moduleField = JComboBox(modules.toTypedArray()).apply {
      renderer = ModuleListCellRenderer()
      preferredSize = Dimension(Int.MAX_VALUE, preferredSize.height)
      addItemListener {
        if (it.stateChange == ItemEvent.SELECTED) {
          updateInstallPackagesCheckBox()
        }
      }
    }

    val supportedLanguageLevels =
      listOf(DEFAULT_PYTHON,
             LanguageLevel.PYTHON37,
             LanguageLevel.PYTHON36,
             LanguageLevel.PYTHON35,
             LanguageLevel.PYTHON34,
             LanguageLevel.PYTHON27)
        .map { it.toString() }

    languageLevelsField = JComboBox(supportedLanguageLevels.toTypedArray()).apply {
      selectedItem = if (itemCount > 0) getItemAt(0) else null
    }

    val builder = FormBuilder.createFormBuilder().apply {
      if (module == null && modules.size > 1) {
        val associatedObject = if (PlatformUtils.isPyCharm()) "project" else "module"
        addLabeledComponent("Associated $associatedObject:", moduleField)
      }
      addLabeledComponent("Python version:", languageLevelsField)
      addComponent(installPackagesCheckBox)
    }
    add(builder.panel, BorderLayout.NORTH)
    updateInstallPackagesCheckBox()
  }

  override fun getOrCreateSdk(): Sdk? {
    return setupPipEnvSdkUnderProgress(project, selectedModule, existingSdks, newProjectPath,
                                       selectedLanguageLevel, installPackagesCheckBox.isSelected)
  }

  override fun validateAll(): List<ValidationInfo> =
    listOfNotNull(validatePipEnvExecutable(), validatePipEnvIsNotAdded())

  private fun updateInstallPackagesCheckBox() {
    selectedModule?.let {
      installPackagesCheckBox.isEnabled = it.pipFile != null
    }
  }

  private val selectedModule: Module?
    get() = module ?: moduleField.selectedItem as? Module

  /**
   * Checks if `pipenv` is available on `$PATH`.
   */
  private fun validatePipEnvExecutable(): ValidationInfo? =
    when (getPipEnvExecutable()) {
      null -> ValidationInfo("Pipenv executable is not found on \$PATH")
      else -> null
    }

  /**
   * Checks if the pipenv for the project hasn't been already added.
   */
  private fun validatePipEnvIsNotAdded(): ValidationInfo? {
    val path = projectPath ?: return null
    val addedPipEnv = existingSdks.find {
      it.associatedModulePath == path && it.isPipEnv
    } ?: return null
    return ValidationInfo("""Pipenv interpreter has been already added, select "${addedPipEnv.name}" in your interpreters list""")
  }

  /**
   * The effective project path for the new project or for the existing project.
   */
  private val projectPath: String?
    get() = newProjectPath ?: selectedModule?.basePath ?: project?.basePath

  /**
   * The version of Python selected by the user or `null` if the default version is selected.
   */
  private val selectedLanguageLevel: String?
    get() {
      val text = languageLevelsField.getItemAt(languageLevelsField.selectedIndex)
      return if (text == DEFAULT_PYTHON) null else text
    }
}