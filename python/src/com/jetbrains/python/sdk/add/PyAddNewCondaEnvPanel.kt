// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add

import com.intellij.execution.ExecutionException
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.PathUtil
import com.intellij.util.SystemProperties
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyCondaPackageManagerImpl
import com.jetbrains.python.packaging.PyCondaPackageService
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.associateWithModule
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.conda.PyCondaSdkCustomizer
import com.jetbrains.python.sdk.createSdkByGenerateTask
import com.jetbrains.python.sdk.excludeInnerVirtualEnv
import com.jetbrains.python.sdk.flavors.CondaEnvSdkFlavor
import icons.PythonIcons
import org.jetbrains.annotations.SystemIndependent
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JComboBox
import javax.swing.event.DocumentEvent

/**
 * @author vlan
 */
open class PyAddNewCondaEnvPanel(
  private val project: Project?,
  private val module: Module?,
  private val existingSdks: List<Sdk>,
  newProjectPath: String?
) : PyAddNewEnvPanel() {
  override val envName: String = "Conda"
  override val panelName: String get() = PyBundle.message("python.add.sdk.panel.name.new.environment")
  override val icon: Icon = PythonIcons.Python.Anaconda

  protected val languageLevelsField: JComboBox<String>
  protected val condaPathField = TextFieldWithBrowseButton().apply {
    val path = PyCondaPackageService.getCondaExecutable(null)
    path?.let {
      text = it
    }
    addBrowseFolderListener(PyBundle.message("python.sdk.select.conda.path.title"), null, project,
                            FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor())
    textField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        updatePathField()
      }
    })
  }

  protected val pathField = TextFieldWithBrowseButton().apply {
    addBrowseFolderListener(PyBundle.message("python.sdk.select.location.for.conda.title"), null, project,
                            FileChooserDescriptorFactory.createSingleFolderDescriptor())
  }
  private val makeSharedField = JBCheckBox(PyBundle.message("available.to.all.projects"))

  override var newProjectPath: String? = newProjectPath
    set(value) {
      field = value
      updatePathField()
    }

  init {
    layout = BorderLayout()

    val supportedLanguageLevels = LanguageLevel.SUPPORTED_LEVELS
      .asReversed()
      .filter { it < LanguageLevel.PYTHON311 }
      .map { it.toPythonVersion() }

    languageLevelsField = ComboBox(supportedLanguageLevels.toTypedArray()).apply {
      selectedItem = if (itemCount > 0) getItemAt(0) else null
    }

    if (PyCondaSdkCustomizer.instance.sharedEnvironmentsByDefault) {
      makeSharedField.isSelected = true
    }

    updatePathField()

    @Suppress("LeakingThis")
    layoutComponents()
  }

  protected open fun layoutComponents() {
    layout = BorderLayout()

    val formPanel = FormBuilder.createFormBuilder()
      .addLabeledComponent(PyBundle.message("sdk.create.venv.conda.dialog.label.location"), pathField)
      .addLabeledComponent(PyBundle.message("sdk.create.venv.conda.dialog.label.python.version"), languageLevelsField)
      .addLabeledComponent(PyBundle.message("python.sdk.conda.path"), condaPathField)
      .addComponent(makeSharedField)
      .panel
    add(formPanel, BorderLayout.NORTH)
  }

  override fun validateAll(): List<ValidationInfo> =
    listOfNotNull(CondaEnvSdkFlavor.validateCondaPath(condaPathField.text), validateEnvironmentDirectoryLocation(pathField))

  override fun getOrCreateSdk(): Sdk? {
    val condaPath = condaPathField.text
    val task = object : Task.WithResult<String, ExecutionException>(project, PyBundle.message("python.sdk.creating.conda.environment.title"), false) {
      override fun compute(indicator: ProgressIndicator): String {
        indicator.isIndeterminate = true
        return PyCondaPackageManagerImpl.createVirtualEnv(condaPath, pathField.text, selectedLanguageLevel)
      }
    }
    val shared = makeSharedField.isSelected
    val associatedPath = if (!shared) projectBasePath else null
    val sdk = createSdkByGenerateTask(task, existingSdks, null, associatedPath, null) ?: return null
    if (!shared) {
      sdk.associateWithModule(module, newProjectPath)
    }
    PyCondaPackageService.onCondaEnvCreated(condaPath)
    project.excludeInnerVirtualEnv(sdk)
    return sdk
  }

  override fun addChangeListener(listener: Runnable) {
    val documentListener = object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
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

  private val defaultBaseDir: String?
    get() {
      val conda = condaPathField.text
      val condaFile = LocalFileSystem.getInstance().findFileByPath(conda) ?: return null
      return condaFile.parent?.parent?.findChild("envs")?.path
    }

  private val projectBasePath: @SystemIndependent String?
    get() = newProjectPath ?: module?.basePath ?: project?.basePath

  private val selectedLanguageLevel: String
    get() = languageLevelsField.getItemAt(languageLevelsField.selectedIndex)
}
