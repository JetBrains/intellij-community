// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.pipenv

import com.intellij.application.options.ModuleListCellRenderer
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.PlatformUtils
import com.intellij.util.text.nullize
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.PythonModuleTypeBase
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.add.PyAddNewEnvPanel
import com.jetbrains.python.sdk.add.PySdkPathChoosingComboBox
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ItemEvent
import java.io.File
import java.nio.file.Files
import javax.swing.Icon
import javax.swing.JComboBox
import javax.swing.event.DocumentEvent

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

  private val pipEnvPathField = TextFieldWithBrowseButton().apply {
    addBrowseFolderListener(null, null, null, FileChooserDescriptorFactory.createSingleFileDescriptor())
    val field = textField as? JBTextField ?: return@apply
    detectPipEnvExecutable()?.let {
      field.emptyText.text = "Auto-detected: ${it.absolutePath}"
    }
    PropertiesComponent.getInstance().pipEnvPath?.let {
      field.text = it
    }
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
          update()
        }
      }
    }

    pipEnvPathField.textField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent?) {
        update()
      }
    })

    val builder = FormBuilder.createFormBuilder().apply {
      if (module == null && modules.size > 1) {
        val associatedObject = if (PlatformUtils.isPyCharm()) "project" else "module"
        addLabeledComponent("Associated $associatedObject:", moduleField)
      }
      addLabeledComponent("Base interpreter:", baseSdkField)
      addComponent(installPackagesCheckBox)
      addLabeledComponent("Pipenv executable:", pipEnvPathField)
    }
    add(builder.panel, BorderLayout.NORTH)
    update()
  }

  override fun getOrCreateSdk(): Sdk? {
    PropertiesComponent.getInstance().pipEnvPath = pipEnvPathField.text.nullize()
    return setupPipEnvSdkUnderProgress(project, selectedModule, existingSdks, newProjectPath,
                                       baseSdkField.selectedSdk?.homePath, installPackagesCheckBox.isSelected)?.apply {
      PySdkSettings.instance.preferredVirtualEnvBaseSdk = baseSdkField.selectedSdk?.homePath
    }
  }

  override fun validateAll(): List<ValidationInfo> =
    listOfNotNull(validatePipEnvExecutable(), validatePipEnvIsNotAdded())

  override fun addChangeListener(listener: Runnable) {
    pipEnvPathField.textField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent?) {
        listener.run()
      }
    })
    super.addChangeListener(listener)
  }

  /**
   * Updates the view according to the current state of UI controls.
   */
  private fun update() {
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
    val executable = pipEnvPathField.text.nullize()?.let { File(it) } ?:
                     detectPipEnvExecutable() ?:
                     return ValidationInfo("Pipenv executable is not found")
    return when {
      !executable.exists() -> ValidationInfo("File ${executable.absolutePath} is not found")
      !Files.isExecutable(executable.toPath()) || !executable.isFile -> ValidationInfo("Cannot execute ${executable.absolutePath}")
      else -> null
    }
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