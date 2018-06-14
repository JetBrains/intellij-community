// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.pipenv

import com.intellij.application.options.ModuleListCellRenderer
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.PythonModuleTypeBase
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.add.PyAddNewEnvPanel
import com.jetbrains.python.sdk.add.PySdkPathChoosingComboBox
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ItemEvent
import java.nio.file.Files
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

  private val moduleField: JComboBox<Module>
  private val baseSdkField = PySdkPathChoosingComboBox(findBaseSdks(existingSdks), null).apply {
    val preferredSdkPath = PySdkSettings.instance.preferredVirtualEnvBaseSdk
    val detectedPreferredSdk = items.find { it.homePath == preferredSdkPath }
    selectedSdk = when {
      detectedPreferredSdk != null -> detectedPreferredSdk
      preferredSdkPath != null -> PyDetectedSdk(preferredSdkPath).apply {
        childComponent.insertItemAt(this, 0)
      }
      else -> items.getOrNull(0)
    }
  }
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

    val builder = FormBuilder.createFormBuilder().apply {
      if (module == null && modules.size > 1) {
        val associatedObject = if (PlatformUtils.isPyCharm()) "project" else "module"
        addLabeledComponent("Associated $associatedObject:", moduleField)
      }
      addLabeledComponent("Base interpreter:", baseSdkField)
      addComponent(installPackagesCheckBox)
    }
    add(builder.panel, BorderLayout.NORTH)
    updateInstallPackagesCheckBox()
  }

  override fun getOrCreateSdk(): Sdk? {
    return setupPipEnvSdkUnderProgress(project, selectedModule, existingSdks, newProjectPath,
                                       baseSdkField.selectedSdk?.homePath, installPackagesCheckBox.isSelected)?.apply {
      PySdkSettings.instance.preferredVirtualEnvBaseSdk = baseSdkField.selectedSdk?.homePath
    }
  }

  override fun validateAll(): List<ValidationInfo> =
    listOfNotNull(validatePipEnvExecutable(), validatePipEnvIsNotAdded())

  /**
   * Shows the install packages checkbox if we can detect any requirements to install.
   */
  private fun updateInstallPackagesCheckBox() {
    selectedModule?.let {
      installPackagesCheckBox.isEnabled = it.pipFile != null
    }
  }

  /**
   * The effective module for which we add a new environment.
   */
  private val selectedModule: Module?
    get() = module ?: moduleField.selectedItem as? Module

  /**
   * Checks if `pipenv` is available on `$PATH`.
   */
  private fun validatePipEnvExecutable(): ValidationInfo? {
    val tip = "Specify the correct path to pipenv in ${getSettingsMenuName()} | Tools | Python Integrated Tools."
    val executable = getPipEnvExecutable() ?: return ValidationInfo("Pipenv executable is not found on \$PATH. $tip")
    return when {
      !executable.exists() -> ValidationInfo("File ${executable.absolutePath} is not found. $tip")
      Files.isExecutable(executable.toPath()) -> ValidationInfo("Cannot execute ${executable.absolutePath}")
      else -> null
    }
  }
  private fun getSettingsMenuName() =
    when {
      SystemInfo.isMac -> "Preferences"
      else -> "Settings"
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
}