// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add.target

import com.intellij.execution.ExecutionException
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.*
import com.intellij.util.PathUtil
import com.intellij.util.SystemProperties
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.CondaOnTargetPackageManager
import com.jetbrains.python.packaging.PyCondaPackageManagerImpl
import com.jetbrains.python.packaging.PyCondaPackageService
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.add.ExistingPySdkComboBoxItem
import com.jetbrains.python.sdk.add.NewPySdkComboBoxItem
import com.jetbrains.python.sdk.add.PySdkPathChoosingComboBox
import com.jetbrains.python.sdk.add.addInterpretersAsync
import com.jetbrains.python.sdk.conda.PyCondaSdkCustomizer
import com.jetbrains.python.sdk.flavors.CondaEnvSdkFlavor
import com.jetbrains.python.target.PythonLanguageRuntimeConfiguration
import icons.PythonIcons
import java.awt.BorderLayout
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JComboBox
import javax.swing.event.DocumentEvent

open class PyAddCondaEnvPanel(
  project: Project?,
  module: Module?,
  private val existingSdks: List<Sdk>,
  newProjectPath: String?,
  private val context: UserDataHolder,
  targetSupplier: Supplier<TargetEnvironmentConfiguration>?,
  private val config: PythonLanguageRuntimeConfiguration
) : PyAddSdkPanelBase(project, module, targetSupplier) {
  //override val envName: String = "Conda"
  override val panelName: String get() = PyBundle.message("python.add.sdk.panel.name.conda.environment")
  override val icon: Icon = PythonIcons.Python.Anaconda

  protected val languageLevelsField: JComboBox<String>
  protected val condaPathField = TextFieldWithBrowseButton().apply {
    // TODO [targets] Requires target-based discovery
    val path = PyCondaPackageService.getCondaExecutable(null)
    path?.let {
      text = it
    }
    addBrowseFolderListener(PyBundle.message("python.sdk.select.conda.path.title"), project, targetEnvironmentConfiguration,
                            FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor())
    textField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        updatePathField()
      }
    })
  }

  protected val pathField = TextFieldWithBrowseButton().apply {
    addBrowseFolderListener(PyBundle.message("python.sdk.select.location.for.conda.title"), project, targetEnvironmentConfiguration,
                            FileChooserDescriptorFactory.createSingleFolderDescriptor())
  }
  private val makeSharedField = JBCheckBox(PyBundle.message("available.to.all.projects"))

  override var newProjectPath: String? = newProjectPath
    set(value) {
      field = value
      updatePathField()
    }

  private val interpreterCombobox: PySdkPathChoosingComboBox

  init {
    layout = BorderLayout()

    val newVirtualenvItem = if (isMutableTarget) NewPySdkComboBoxItem("<New Virtualenv>") else null
    interpreterCombobox = PySdkPathChoosingComboBox(newPySdkComboBoxItem = newVirtualenvItem,
                                                    targetEnvironmentConfiguration = targetEnvironmentConfiguration)

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

    if (targetEnvironmentConfiguration.isLocal()) {
      addInterpretersAsync(interpreterCombobox) {
        detectCondaEnvs(module, existingSdks, context)
      }
    }
  }

  protected open fun layoutComponents() {
    layout = BorderLayout()

    val formPanel = panel {
      row(label = PyBundle.message("sdk.create.venv.conda.dialog.label.interpreter")) { interpreterCombobox() }
      val rowsForCreatingNewCondaEnv = listOf(
        row(label = PyBundle.message("sdk.create.venv.conda.dialog.label.location")) { pathField() },
        row(label = PyBundle.message("sdk.create.venv.conda.dialog.label.python.version")) { languageLevelsField() },
        row(label = PyBundle.message("python.sdk.conda.path")) { condaPathField() }
      )
      row() { makeSharedField() }

      fun updateComponentsVisibility() {
        rowsForCreatingNewCondaEnv.forEach { row ->
          row.visible = interpreterCombobox.selectedItem is NewPySdkComboBoxItem
        }
      }

      interpreterCombobox.childComponent.addActionListener { updateComponentsVisibility() }

      updateComponentsVisibility()
    }

    add(formPanel, BorderLayout.NORTH)
  }

  override fun validateAll(): List<ValidationInfo> =
    if (interpreterCombobox.selectedItem is NewPySdkComboBoxItem)
      listOfNotNull(CondaEnvSdkFlavor.validateCondaPath(condaPathField.text), validateEnvironmentDirectoryLocation(pathField))
    else
      listOfNotNull(validateSdkComboBox(interpreterCombobox, this), CondaEnvSdkFlavor.validateCondaPath(condaPathField.text))

  override fun getOrCreateSdk(): Sdk? = getOrCreateSdk(targetEnvironmentConfiguration = null)

  override fun getOrCreateSdk(targetEnvironmentConfiguration: TargetEnvironmentConfiguration?): Sdk? =
    when (val item = interpreterCombobox.selectedItem) {
      is NewPySdkComboBoxItem -> createNewCondaEnv(targetEnvironmentConfiguration)
      is ExistingPySdkComboBoxItem -> useExistingCondaEnv(targetEnvironmentConfiguration, item.sdk)
      null -> null
    }

  private fun createNewCondaEnv(targetEnvironmentConfiguration: TargetEnvironmentConfiguration?): Sdk? {
    val condaPath = condaPathField.text
    val task = object : Task.WithResult<String, ExecutionException>(project,
                                                                    PyBundle.message("python.sdk.creating.conda.environment.title"),
                                                                    false) {
      override fun compute(indicator: ProgressIndicator): String {
        indicator.isIndeterminate = true
        return if (targetEnvironmentConfiguration == null) {
          PyCondaPackageManagerImpl.createVirtualEnv(condaPath, pathField.text, selectedLanguageLevel)
        }
        else {
          val targetEnvironmentRequest =
            targetEnvironmentConfiguration.createEnvironmentRequest(project ?: ProjectManager.getInstance().defaultProject)
          CondaOnTargetPackageManager.createCondaEnv(targetEnvironmentRequest, condaPath, pathField.text, selectedLanguageLevel)
        }
      }
    }
    val shared = makeSharedField.isSelected
    val associatedPath = if (!shared) projectBasePath else null
    val sdk: Sdk
    if (targetEnvironmentConfiguration == null) {
      sdk = (createSdkByGenerateTask(task, existingSdks, null, associatedPath, null) ?: return null)
      if (!shared) {
        sdk.associateWithModule(module, newProjectPath)
      }
    }
    else {
      val homePath = ProgressManager.getInstance().run(task)
      sdk = createSdkForTarget(project, targetEnvironmentConfiguration, homePath, existingSdks)
    }
    PyCondaPackageService.onCondaEnvCreated(condaPath)
    project.excludeInnerVirtualEnv(sdk)
    return sdk
  }

  private fun useExistingCondaEnv(targetEnvironmentConfiguration: TargetEnvironmentConfiguration?, selectedSdk: Sdk): Sdk? {
    if (targetEnvironmentConfiguration == null) {
      val sdk = interpreterCombobox.selectedSdk
      PyCondaPackageService.onCondaEnvCreated(condaPathField.text)
      return when (sdk) {
        is PyDetectedSdk -> sdk.setupAssociated(existingSdks, newProjectPath ?: project?.basePath)?.apply {
          if (!makeSharedField.isSelected) {
            associateWithModule(module, newProjectPath)
          }
        }
        else -> sdk
      }
    }
    else {
      val homePath = selectedSdk.homePath!!
      return createSdkForTarget(project, targetEnvironmentConfiguration, homePath, existingSdks)
    }
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
    val userHome = if (targetEnvironmentConfiguration == null) SystemProperties.getUserHome() else config.userHome
    val baseDir = defaultBaseDir ?: "$userHome/.conda/envs"
    val dirName = PathUtil.getFileName(projectBasePath ?: "untitled")
    val fullPath = "$baseDir/$dirName"
    pathField.text = if (targetEnvironmentConfiguration.isLocal()) FileUtil.toSystemDependentName(fullPath) else fullPath
  }

  private val defaultBaseDir: String?
    get() {
      val conda = condaPathField.text
      val condaFile = LocalFileSystem.getInstance().findFileByPath(conda) ?: return null
      return condaFile.parent?.parent?.findChild("envs")?.path
    }

  private val selectedLanguageLevel: String
    get() = languageLevelsField.getItemAt(languageLevelsField.selectedIndex)
}
