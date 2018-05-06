// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add

import com.intellij.execution.ExecutionException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.PathUtil
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.associateWithProject
import com.jetbrains.python.sdk.associatedProjectPath
import com.jetbrains.python.sdk.createSdkByGenerateTask
import com.jetbrains.python.sdk.flavors.*
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JComboBox

/**
 * The UI panel for adding the pipenv interpreter for the project.
 *
 * TODO: Pass a module here, since pipenv interpreters are per module, not per project.
 *
 * @author vlan
 */
class PyAddPipEnvPanel(private val project: Project?,
                       private val existingSdks: List<Sdk>,
                       override var newProjectPath: String?) : PyAddNewEnvPanel() {
  override val envName = "Pipenv"
  override val panelName = "Pipenv Environment"
  override val icon: Icon = PIPENV_ICON

  private val DEFAULT_PYTHON = "Default"
  private val languageLevelsField: JComboBox<String>
  private val installPackagesCheckBox = JBCheckBox("Install packages from Pipfile").apply {
    isEnabled = projectPipFile != null
  }

  init {
    layout = BorderLayout()

    val supportedLanguageLevels =
      listOf(DEFAULT_PYTHON,
             LanguageLevel.PYTHON37,
             LanguageLevel.PYTHON36,
             LanguageLevel.PYTHON35,
             LanguageLevel.PYTHON34,
             LanguageLevel.PYTHON27)
        .map { it.toString() }

    languageLevelsField = ComboBox(supportedLanguageLevels.toTypedArray()).apply {
      selectedItem = if (itemCount > 0) getItemAt(0) else null
    }

    val formPanel = FormBuilder.createFormBuilder()
      .addLabeledComponent("Python version:", languageLevelsField)
      .addComponent(installPackagesCheckBox)
      .panel
    add(formPanel, BorderLayout.NORTH)
  }

  override fun getOrCreateSdk(): Sdk? {
    // TODO: Pass a module here for selecting the proper Pipfile?
    val path = projectPath ?: return null
    val task = object : Task.WithResult<String, ExecutionException>(project, "Configuring Pipenv Environment", true) {
      override fun compute(indicator: ProgressIndicator): String {
        indicator.isIndeterminate = true
        val pipEnv = setupPipEnv(FileUtil.toSystemDependentName(path),
                                 selectedLanguageLevel,
                                 installPackagesCheckBox.isSelected)
        return PythonSdkType.getPythonExecutable(pipEnv) ?: FileUtil.join(pipEnv, "bin", "python")
      }
    }
    val suggestedName = "Pipenv (${PathUtil.getFileName(path)})"
    return createSdkByGenerateTask(task, existingSdks, null, path, suggestedName)?.apply {
      isPipEnv = true
      associateWithProject(project, newProjectPath != null)
    }
  }

  override fun validateAll(): List<ValidationInfo> =
    listOfNotNull(validatePipEnvExecutable(), validatePipEnvIsNotAdded())

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
      it.associatedProjectPath == path && it.isPipEnv
    } ?: return null
    return ValidationInfo("""Pipenv interpreter has been already added, select "${addedPipEnv.name}" in your interpreters list""")
  }

  /**
   * The effective project path for the new project or for the existing project.
   */
  private val projectPath: String?
    get() = newProjectPath ?: project?.basePath

  /**
   * The version of Python selected by the user or `null` if the default version is selected.
   */
  private val selectedLanguageLevel: String?
    get() {
      val text = languageLevelsField.getItemAt(languageLevelsField.selectedIndex)
      return if (text == DEFAULT_PYTHON) null else text
    }

  /**
   * The Pipfile for the effective project path.
   */
  private val projectPipFile: VirtualFile?
    get() {
      val path = projectPath ?: return null
      return StandardFileSystems.local().findFileByPath(path)?.findChild(PIP_FILE)
    }
}