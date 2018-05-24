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
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.PathUtil
import com.intellij.util.SystemProperties
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.packaging.PyCondaPackageManagerImpl
import com.jetbrains.python.packaging.PyCondaPackageService
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.associateWithProject
import com.jetbrains.python.sdk.createSdkByGenerateTask
import icons.PythonIcons
import org.jetbrains.annotations.SystemIndependent
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import javax.swing.Icon
import javax.swing.JComboBox
import javax.swing.event.DocumentEvent

/**
 * @author vlan
 */
class PyAddNewCondaEnvPanel(private val project: Project?,
                            private val existingSdks: List<Sdk>,
                            newProjectPath: String?) : PyAddNewEnvPanel() {
  override val envName: String = "Conda"
  override val panelName: String = "New environment"
  override val icon: Icon = PythonIcons.Python.Anaconda

  private val languageLevelsField: JComboBox<String>
  private val condaPathField = TextFieldWithBrowseButton().apply {
    val path = PyCondaPackageService.getInstance().PREFERRED_CONDA_PATH ?: PyCondaPackageService.getSystemCondaExecutable()
    path?.let {
      text = it
    }
    addBrowseFolderListener("Select Path to Conda Executable", null, project,
                            FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor())
    textField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent?) {
        updatePathField()
      }
    })
  }
  private val pathField = TextFieldWithBrowseButton().apply {
    addBrowseFolderListener("Select Location for Conda Environment", null, project,
                            FileChooserDescriptorFactory.createSingleFolderDescriptor())
  }
  private val makeSharedField = JBCheckBox("Make available to all projects")

  override var newProjectPath: String? = newProjectPath
    set(value) {
      field = value
      updatePathField()
    }

  init {
    layout = BorderLayout()

    // https://conda.io/docs/user-guide/install/index.html#system-requirements
    val supportedLanguageLevels =
      listOf(LanguageLevel.PYTHON36, LanguageLevel.PYTHON35, LanguageLevel.PYTHON34, LanguageLevel.PYTHON27)
        .map { it.toString() }

    languageLevelsField = JComboBox(supportedLanguageLevels.toTypedArray()).apply {
      selectedItem = if (itemCount > 0) getItemAt(0) else null
      preferredSize = Dimension(Int.MAX_VALUE, preferredSize.height)
    }

    updatePathField()

    val formPanel = FormBuilder.createFormBuilder()
      .addLabeledComponent("Location:", pathField)
      .addLabeledComponent("Python version:", languageLevelsField)
      .addLabeledComponent("Conda executable:", condaPathField)
      .addComponent(makeSharedField)
      .panel
    add(formPanel, BorderLayout.NORTH)
  }

  override fun validateAll(): List<ValidationInfo> =
    listOfNotNull(validateAnacondaPath(), validateEnvironmentDirectoryLocation(pathField))

  override fun getOrCreateSdk(): Sdk? {
    val condaPath = condaPathField.text
    val task = object : Task.WithResult<String, ExecutionException>(project, "Creating Conda Environment", false) {
      override fun compute(indicator: ProgressIndicator): String {
        indicator.isIndeterminate = true
        return PyCondaPackageManagerImpl.createVirtualEnv(condaPath, pathField.text, selectedLanguageLevel)
      }
    }
    val shared = makeSharedField.isSelected
    val associatedPath = if (!shared) newProjectPath ?: project?.basePath else null
    val sdk = createSdkByGenerateTask(task, existingSdks, null, associatedPath) ?: return null
    if (!shared) {
      sdk.associateWithProject(project, newProjectPath != null)
    }
    PyCondaPackageService.getInstance().PREFERRED_CONDA_PATH = condaPath
    return sdk
  }

  override fun addChangeListener(listener: Runnable) {
    val documentListener = object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent?) {
        listener.run()
      }
    }
    pathField.textField.document.addDocumentListener(documentListener)
    condaPathField.textField.document.addDocumentListener(documentListener)
  }

  private fun updatePathField() {
    val baseDir = defaultBaseDir ?: "${SystemProperties.getUserHome()}/.conda/envs"
    val dirName = PathUtil.getFileName(projectBasePath ?: "untitled")
    pathField.text = FileUtil.toSystemDependentName("$baseDir/$dirName")
  }

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

  private val defaultBaseDir: String?
    get() {
      val conda = condaPathField.text
      val condaFile = LocalFileSystem.getInstance().findFileByPath(conda) ?: return null
      return condaFile.parent?.parent?.findChild("envs")?.path
    }

  private val projectBasePath: @SystemIndependent String?
    get() = newProjectPath ?: project?.basePath

  private val selectedLanguageLevel: String
    get() = languageLevelsField.getItemAt(languageLevelsField.selectedIndex)
}