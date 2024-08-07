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
import com.jetbrains.python.packaging.PyCondaPackageService
import com.jetbrains.python.sdk.PyDetectedSdk
import com.jetbrains.python.sdk.conda.PyCondaSdkCustomizer
import com.jetbrains.python.sdk.setupAssociated
import com.jetbrains.python.icons.PythonIcons
import java.awt.BorderLayout
import java.awt.event.ItemEvent
import javax.swing.Icon

open class PyAddExistingCondaEnvPanel(
  private val project: Project?,
  private val existingSdks: List<Sdk>,
  override var newProjectPath: String?,
) : PyAddSdkPanel() {
  override val panelName: String get() = PyBundle.message("python.add.sdk.panel.name.existing.environment")
  override val icon: Icon = PythonIcons.Python.Anaconda
  protected val sdkComboBox = PySdkPathChoosingComboBox()
  private val condaPathField = TextFieldWithBrowseButton().apply {
    val path = PyCondaPackageService.getCondaExecutable(null)
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
    PyCondaPackageService.onCondaEnvCreated(condaPathField.text)
    return when (sdk) {
      is PyDetectedSdk -> sdk.setupAssociated(existingSdks, newProjectPath ?: project?.basePath, !makeSharedField.isSelected)
        .getOrLogException(thisLogger())
      else -> sdk
    }
  }
}
