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

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.sdk.*
import icons.PythonIcons
import java.awt.BorderLayout
import javax.swing.Icon

/**
 * @author vlan
 */
class PyAddExistingCondaEnvPanel(private val project: Project?,
                                 private val existingSdks: List<Sdk>,
                                 override var newProjectPath: String?) : PyAddSdkPanel() {
  override val panelName = "Existing environment"
  override val icon: Icon = PythonIcons.Python.Condaenv
  private val sdkComboBox = PySdkPathChoosingComboBox(detectCondaEnvs(project, existingSdks)
                                                        .filterNot { it.isAssociatedWithAnotherProject(project) },
                                                      null)
  private val makeSharedField = JBCheckBox("Make available to all projects")

  init {
    layout = BorderLayout()
    val formPanel = FormBuilder.createFormBuilder()
      .addLabeledComponent("Interpreter:", sdkComboBox)
      .addComponent(makeSharedField)
      .panel
    add(formPanel, BorderLayout.NORTH)
  }

  override fun validateAll() =
    listOf(validateAnacondaPresense(sdkComboBox),
           validateSdkComboBox(sdkComboBox))
      .filterNotNull()

  override fun getOrCreateSdk(): Sdk? {
    val sdk = sdkComboBox.selectedSdk
    return when (sdk) {
      is PyDetectedSdk -> sdk.setupAssociated(existingSdks, newProjectPath ?: project?.basePath)?.apply {
        if (!makeSharedField.isSelected) {
          associateWithProject(project, newProjectPath != null)
        }
      }
      else -> sdk
    }
  }
}