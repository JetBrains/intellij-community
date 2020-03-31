/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.sdk.add

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyCondaPackageService
import com.jetbrains.python.sdk.PyDetectedSdk
import com.jetbrains.python.sdk.associateWithModule
import com.jetbrains.python.sdk.conda.PyCondaSdkCustomizer
import com.jetbrains.python.sdk.detectCondaEnvs
import com.jetbrains.python.sdk.setupAssociated
import icons.PythonIcons
import java.awt.BorderLayout
import java.awt.event.ItemEvent
import java.io.File
import javax.swing.Icon

/**
 * @author vlan
 */
class PyAddExistingCondaEnvPanel(private val project: Project?,
                                 private val module: Module?,
                                 private val existingSdks: List<Sdk>,
                                 override var newProjectPath: String?,
                                 context: UserDataHolder) : PyAddSdkPanel() {
  override val panelName: String get() = PyBundle.message("python.add.sdk.panel.name.existing.environment")
  override val icon: Icon = PythonIcons.Python.Anaconda
  private val sdkComboBox = PySdkPathChoosingComboBox()
  private val condaPathField = TextFieldWithBrowseButton().apply {
    val path = PyCondaPackageService.getInstance().PREFERRED_CONDA_PATH ?: PyCondaPackageService.getSystemCondaExecutable()
    if (path != null) {
      text = path
    }
    addBrowseFolderListener(PyBundle.message("python.sdk.select.conda.path.title"), null, project,
                            FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor())
  }

  private val makeSharedField = JBCheckBox(PyBundle.message("available.to.all.projects"))

  init {
    sdkComboBox.childComponent.addItemListener {
      if (it.stateChange == ItemEvent.SELECTED) {
        val respectiveCondaExecutable = PyCondaPackageService.getCondaExecutable(sdkComboBox.selectedSdk?.homePath)
        condaPathField.text = respectiveCondaExecutable.orEmpty()
      }
    }

    if (PyCondaSdkCustomizer.instance.sharedEnvironmentsByDefault) {
      makeSharedField.isSelected = true
    }

    layout = BorderLayout()
    val formPanel = FormBuilder.createFormBuilder()
      .addLabeledComponent(PyBundle.message("interpreter"), sdkComboBox)
      .addLabeledComponent(PyBundle.message("python.sdk.conda.path"), condaPathField)
      .addComponent(makeSharedField)
      .panel
    add(formPanel, BorderLayout.NORTH)
    addInterpretersAsync(sdkComboBox) {
      detectCondaEnvs(module, existingSdks, context)
    }
  }

  override fun validateAll(): List<ValidationInfo> = listOfNotNull(validateSdkComboBox(sdkComboBox, this), validateAnacondaPath())

  private fun validateAnacondaPath(): ValidationInfo? {
    val text = condaPathField.text
    val file = File(text)
    val message = when {
      StringUtil.isEmptyOrSpaces(text) -> "Conda executable path is empty"
      !file.exists() -> "Conda executable not found"
      !file.isFile || !file.canExecute() -> "Conda executable path is not an executable file"
      else -> return null
    }
    return ValidationInfo(message)
  }

  override fun getOrCreateSdk(): Sdk? {
    val sdk = sdkComboBox.selectedSdk
    PyCondaPackageService.getInstance().PREFERRED_CONDA_PATH = condaPathField.text
    return when (sdk) {
      is PyDetectedSdk -> sdk.setupAssociated(existingSdks, newProjectPath ?: project?.basePath)?.apply {
        if (!makeSharedField.isSelected) {
          associateWithModule(module, newProjectPath)
        }
      }
      else -> sdk
    }
  }
}
