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

import com.intellij.execution.ExecutionException
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.SystemProperties
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.packaging.PyCondaPackageManagerImpl
import com.jetbrains.python.packaging.PyCondaPackageService
import com.jetbrains.python.sdk.associateWithProject
import com.jetbrains.python.sdk.createSdkByGenerateTask
import com.jetbrains.python.sdk.flavors.CondaEnvSdkFlavor
import com.jetbrains.python.validation.UnsupportedFeaturesUtil
import icons.PythonIcons
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import javax.swing.Icon
import javax.swing.JComboBox

/**
 * @author vlan
 */
class PyAddNewCondaEnvPanel(private val project: Project?,
                            private val existingSdks: List<Sdk>,
                            override var newProjectPath: String?) : PyAddNewEnvPanel() {
  override val envName = "Conda"
  override val panelName = "New environment"
  override val icon: Icon = PythonIcons.Python.Anaconda

  private val pathField = TextFieldWithBrowseButton().apply {
    val baseDir = defaultBaseDir ?: SystemProperties.getUserHome()
    text = FileUtil.findSequentNonexistentFile(File(baseDir), "untitled", "").path
    addBrowseFolderListener("Select Location for Conda Environment", null, project,
                            FileChooserDescriptorFactory.createSingleFolderDescriptor())
  }
  private val languageLevelsField: JComboBox<String>
  private val makeSharedField = JBCheckBox("Make available to all projects")

  init {
    layout = BorderLayout()
    val languageLevels = UnsupportedFeaturesUtil.ALL_LANGUAGE_LEVELS.reversed()
    languageLevelsField = JComboBox(languageLevels.toTypedArray()).apply {
      selectedItem = if (itemCount > 0) getItemAt(0) else null
      preferredSize = Dimension(Int.MAX_VALUE, preferredSize.height)
    }
    val formPanel = FormBuilder.createFormBuilder()
      .addLabeledComponent("Location:", pathField)
      .addLabeledComponent("Python version:", languageLevelsField)
      .addComponent(makeSharedField)
      .panel
    add(formPanel, BorderLayout.NORTH)
  }

  override fun validateAll() =
    listOf(validateAnacondaPresense(pathField),
           validateEmptyOrNonExistingDirectoryLocation(pathField))
      .filterNotNull()

  override fun getOrCreateSdk(): Sdk? {
    val task = object : Task.WithResult<String, ExecutionException>(project, "Creating Conda Environment", false) {
      override fun compute(indicator: ProgressIndicator): String {
        indicator.isIndeterminate = true
        return PyCondaPackageManagerImpl.createVirtualEnv(pathField.text, selectedLanguageLevel)
      }
    }
    val shared = makeSharedField.isSelected
    val associatedPath = if (!shared) newProjectPath ?: project?.basePath else null
    val sdk = createSdkByGenerateTask(task, existingSdks, null, associatedPath) ?: return null
    if (!shared) {
      sdk.associateWithProject(project, newProjectPath != null)
    }
    return sdk
  }

  private val defaultBaseDir: String?
    get() {
      val location = CondaEnvSdkFlavor.getCondaDefaultLocations().firstOrNull()
      if (location != null) {
        return location.path
      }
      val conda = PyCondaPackageService.getSystemCondaExecutable() ?: return null
      val condaFile = LocalFileSystem.getInstance().findFileByPath(conda) ?: return null
      return condaFile.parent?.parent?.findChild("envs")?.path
    }

  private val selectedLanguageLevel: String
    get() = languageLevelsField.getItemAt(languageLevelsField.selectedIndex)
}