// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v1

import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.packaging.PyCondaPackageService
import com.jetbrains.python.sdk.PyDetectedSdk
import com.jetbrains.python.sdk.add.PyAddSdkPanel
import com.jetbrains.python.sdk.add.PySdkPathChoosingComboBox
import com.jetbrains.python.sdk.conda.PyCondaSdkCustomizer
import com.jetbrains.python.sdk.setupAssociated
import java.awt.BorderLayout
import java.awt.event.ItemEvent
import javax.swing.Icon

internal open class PyAddExistingCondaEnvPanel(
  private val project: Project?,
  private val existingSdks: List<Sdk>,
  override var newProjectPath: String?,
) : PyAddSdkPanel() {
  override val panelName: String get() = PyBundle.message("python.add.sdk.panel.name.existing.environment")
  override val icon: Icon = PythonIcons.Python.Anaconda
  protected val sdkComboBox = PySdkPathChoosingComboBox()
  private val condaPathField = TextFieldWithBrowseButton().apply {
    val path = PyCondaPackageService.Companion.getCondaExecutable(null)
    if (path != null) {
      text = path
    }
    addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor()
      .withTitle(PyBundle.message("python.sdk.select.conda.path.title")))
  }

  private val makeSharedField = JBCheckBox(PyBundle.message("available.to.all.projects"))

  init {
    sdkComboBox.childComponent.addItemListener {
      if (it.stateChange == ItemEvent.SELECTED) {
        val respectiveCondaExecutable = PyCondaPackageService.Companion.getCondaExecutable(sdkComboBox.selectedSdk.homePath)
        condaPathField.text = respectiveCondaExecutable.orEmpty()
      }
    }

    if (PyCondaSdkCustomizer.Companion.instance.sharedEnvironmentsByDefault) {
      makeSharedField.isSelected = true
    }

    @Suppress("LeakingThis")
    layoutComponents()


  }

  protected open fun layoutComponents() {
    layout = BorderLayout()
    val formPanel = FormBuilder.createFormBuilder()
      .addLabeledComponent(PySdkBundle.message("python.interpreter.label"), sdkComboBox)
      .addLabeledComponent(PyBundle.message("python.sdk.conda.path"), condaPathField)
      .addComponent(makeSharedField)
      .panel
    add(formPanel, BorderLayout.NORTH)
  }

  override fun validateAll(): List<ValidationInfo> {
    return listOfNotNull(validateSdkComboBox(sdkComboBox, this))
  }

  override fun getOrCreateSdk(): Sdk? {
    val sdk = sdkComboBox.selectedSdk
    PyCondaPackageService.Companion.onCondaEnvCreated(condaPathField.text)
    return when (sdk) {
      is PyDetectedSdk -> sdk.setupAssociated(existingSdks, newProjectPath ?: project?.basePath, !makeSharedField.isSelected)
        .getOrLogException(thisLogger())
      else -> sdk
    }
  }
}