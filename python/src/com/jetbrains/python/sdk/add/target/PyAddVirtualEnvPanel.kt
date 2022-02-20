// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add.target

import com.intellij.execution.ExecutionException
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.joinTargetPaths
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.layout.*
import com.intellij.util.PathUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.PyPackageManagers
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory.Companion.projectSyncRows
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.add.ExistingPySdkComboBoxItem
import com.jetbrains.python.sdk.add.PySdkPathChoosingComboBox
import com.jetbrains.python.sdk.add.addBaseInterpretersAsync
import com.jetbrains.python.sdk.add.addInterpretersAsync
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.target.PythonLanguageRuntimeConfiguration
import icons.PythonIcons
import java.awt.BorderLayout
import java.util.function.Supplier

/**
 * Panel with a control that allows to add either new or selecting existing virtualenv.
 */
class PyAddVirtualEnvPanel constructor(project: Project?,
                                       module: Module?,
                                       private val existingSdks: List<Sdk> = emptyList(),
                                       allowAddNewVirtualenv: Boolean = false,
                                       private val context: UserDataHolder,
                                       targetSupplier: Supplier<TargetEnvironmentConfiguration>?,
                                       config: PythonLanguageRuntimeConfiguration)
  : PyAddSdkPanelBase(project, module, targetSupplier) {

  override val panelName = PyBundle.message("python.add.sdk.panel.name.virtualenv.environment")

  override val icon = PythonIcons.Python.Virtualenv

  private val interpreterCombobox: PySdkPathChoosingComboBox

  private val locationField: TextFieldWithBrowseButton

  private val baseInterpreterCombobox: PySdkPathChoosingComboBox

  private var isCreateNewVirtualenv: Boolean = false

  private var isInheritSitePackages: Boolean = false

  /**
   * Encapsulates the work with the files synchronization options.
   */
  private var projectSync: ProjectSync? = null

  private lateinit var isNewEnvironmentMode: () -> Boolean

  override var newProjectPath: String? = null
    set(value) {
      field = value
      if (isUnderLocalTarget) {
        locationField.text = FileUtil.toSystemDependentName(PySdkSettings.instance.getPreferredVirtualEnvBasePath(projectBasePath))
      }
    }

  init {
    layout = BorderLayout()
    interpreterCombobox = PySdkPathChoosingComboBox(targetEnvironmentConfiguration = targetEnvironmentConfiguration)
    locationField = TextFieldWithBrowseButton().apply {
      val targetEnvironmentConfiguration = targetEnvironmentConfiguration
      if (targetEnvironmentConfiguration == null) {
        text = FileUtil.toSystemDependentName(PySdkSettings.instance.getPreferredVirtualEnvBasePath(projectBasePath))
      }
      else {
        val projectBasePath = projectBasePath
        text = when {
          projectBasePath.isNullOrEmpty() -> config.userHome
          // TODO [run.targets] ideally we want to use '/' or '\' file separators based on the target's OS (which is not available yet)
          else -> joinTargetPaths(config.userHome, DEFAULT_VIRTUALENVS_DIR, PathUtil.getFileName(projectBasePath), fileSeparator = '/')
        }
      }
      addBrowseFolderListener(PySdkBundle.message("python.venv.location.chooser"), project, targetEnvironmentConfiguration,
                              FileChooserDescriptorFactory.createSingleFolderDescriptor())
    }
    baseInterpreterCombobox = PySdkPathChoosingComboBox(targetEnvironmentConfiguration = targetEnvironmentConfiguration)
    val panel = panel {
      lateinit var newEnvironmentModeSelected: ComponentPredicate

      if (allowAddNewVirtualenv && isMutableTarget) {
        isCreateNewVirtualenv = true
        buttonsGroup {
          row {
            label(PyBundle.message("sdk.create.venv.environment.label"))
            radioButton(PyBundle.message("sdk.create.venv.existing.option.label"), false)
            val newEnvironmentRadioButton = radioButton(PyBundle.message("sdk.create.venv.new.option.label"), true)
            newEnvironmentModeSelected = newEnvironmentRadioButton.selected
          }
        }.bind(getter = { isCreateNewVirtualenv }, setter = { isCreateNewVirtualenv = it })
      }
      else {
        newEnvironmentModeSelected = ConstantComponentPredicate.FALSE
      }

      row(PyBundle.message("sdk.create.venv.dialog.interpreter.label")) {
        cell(interpreterCombobox).horizontalAlign(HorizontalAlign.FILL)
      }.visibleIf(newEnvironmentModeSelected.not())

      row(PyBundle.message("sdk.create.venv.dialog.location.label")) {
        cell(locationField).horizontalAlign(HorizontalAlign.FILL)
      }.visibleIf(newEnvironmentModeSelected)
      row(PyBundle.message("sdk.create.venv.dialog.base.interpreter.label")) {
        cell(baseInterpreterCombobox).horizontalAlign(HorizontalAlign.FILL)
      }.visibleIf(newEnvironmentModeSelected)
      row {
        checkBox(PyBundle.message("sdk.create.venv.dialog.label.inherit.global.site.packages"))
          .bindSelected(::isInheritSitePackages)
      }
        .visibleIf(newEnvironmentModeSelected)

      projectSync = projectSyncRows(project, targetEnvironmentConfiguration)
    }

    add(panel, BorderLayout.NORTH)

    if (targetEnvironmentConfiguration.isLocal()) {
      // asynchronously fill the combobox
      addInterpretersAsync(
        interpreterCombobox,
        sdkObtainer = {
          detectVirtualEnvs(module, existingSdks, context)
            .filterNot { it.isAssociatedWithAnotherModule(module) }
        },
        onAdded = { sdks ->
          val associatedVirtualEnv = sdks.find { it.isAssociatedWithModule(module) }
          associatedVirtualEnv?.let { interpreterCombobox.selectedSdk = associatedVirtualEnv }
        }
      )
      addBaseInterpretersAsync(baseInterpreterCombobox, existingSdks, module, context)
    }
    else {
      config.pythonInterpreterPath.let { introspectedPythonPath ->
        if (introspectedPythonPath.isNotBlank()) {
          baseInterpreterCombobox.addSdkItem(createDetectedSdk(introspectedPythonPath, isLocal = false))
        }
      }
    }
  }

  override fun validateAll(): List<ValidationInfo> =
    if (isUnderLocalTarget) {
      when (isCreateNewVirtualenv) {
        true -> listOfNotNull(validateEnvironmentDirectoryLocation(locationField),
                              validateSdkComboBox(baseInterpreterCombobox, this))
        false -> listOfNotNull(validateSdkComboBox(interpreterCombobox, this))
      }
    }
    else emptyList()

  override fun getOrCreateSdk(): Sdk? =
    getOrCreateSdk(targetEnvironmentConfiguration = null)

  override fun getOrCreateSdk(targetEnvironmentConfiguration: TargetEnvironmentConfiguration?): Sdk? {
    // TODO [targets] Refactor this workaround
    applyOptionalProjectSyncConfiguration(targetEnvironmentConfiguration)

    if (isCreateNewVirtualenv) return createNewVirtualenvSdk(targetEnvironmentConfiguration)

    val item = interpreterCombobox.selectedItem
    // there should *not* be other items other than `ExistingPySdkComboBoxItem`
    return if (item is ExistingPySdkComboBoxItem) configureExistingVirtualenvSdk(targetEnvironmentConfiguration, item.sdk) else null
  }

  /**
   * Note: there is a careful work with SDK names because of the caching of Python package managers in
   * [com.jetbrains.python.packaging.PyPackageManagersImpl.forSdk].
   */
  private fun createNewVirtualenvSdk(targetEnvironmentConfiguration: TargetEnvironmentConfiguration?): Sdk? {
    // TODO [targets] Do *not* silently `return null`
    val baseSelectedSdk = baseInterpreterCombobox.selectedSdk ?: return null
    val root = locationField.text
    // TODO [targets] Use `targetEnvironmentConfiguration` to create `baseSdk`
    val baseSdk: Sdk = if (targetEnvironmentConfiguration == null) {
      installSdkIfNeeded(baseSelectedSdk, module, existingSdks, context) ?: return null
    }
    else {
      val sdkAdditionalData = PyTargetAwareAdditionalData(virtualEnvSdkFlavor)
      sdkAdditionalData.targetEnvironmentConfiguration = targetEnvironmentConfiguration
      val homePath = baseSelectedSdk.homePath ?: return null
      // suggesting the proper name for the base SDK fixes the problem with clashing caching key of Python package manager
      val customSdkSuggestedName = PythonInterpreterTargetEnvironmentFactory.findDefaultSdkName(project, sdkAdditionalData, version = null)
      sdkAdditionalData.interpreterPath = homePath
      SdkConfigurationUtil.createSdk(existingSdks, homePath, PythonSdkType.getInstance(), sdkAdditionalData, customSdkSuggestedName)
    }

    val task = object : Task.WithResult<String, ExecutionException>(project, PySdkBundle.message("python.creating.venv.title"), false) {
      override fun compute(indicator: ProgressIndicator): String {
        indicator.isIndeterminate = true
        try {
          val packageManager = PyPackageManager.getInstance(baseSdk)
          return packageManager.createVirtualEnv(root, isInheritSitePackages)
        }
        finally {
          // this fixes the issue with unsuccessful attempts to create the new SDK after removing the underlying Web Deployment
          PyPackageManagers.getInstance().clearCache(baseSdk)
        }
      }
    }
    // TODO [targets] Restore `makeSharedField` flag
    val shared = false
    val associatedPath = if (!shared) projectBasePath else null
    val sdk = targetEnvironmentConfiguration.let {
      if (it == null) {
        // `targetEnvironmentConfiguration.isLocal() == true`
        createSdkByGenerateTask(task, existingSdks, baseSdk, associatedPath, null) ?: return null
      }
      else {
        // TODO [targets] Utilize smth like `createSdkFromExistingServerConfiguration` method in `SshSdkCreationUtil.kt`
        val homePath = ProgressManager.getInstance().run(task)
        createSdkForTarget(project, it, homePath, existingSdks)
      }
    }
    if (!shared) {
      sdk.associateWithModule(module, newProjectPath)
    }
    project.excludeInnerVirtualEnv(sdk)
    if (isUnderLocalTarget) {
      // The method `onVirtualEnvCreated(..)` stores preferred base path to virtual envs. Storing here the base path from the non-local
      // target (e.g. a path from SSH machine or a Docker image) ends up with a meaningless default for the local machine.
      // If we would like to store preferred paths for non-local targets we need to use some key to identify the exact target.
      PySdkSettings.instance.onVirtualEnvCreated(baseSdk, FileUtil.toSystemIndependentName(root), projectBasePath)
    }
    return sdk
  }

  private fun configureExistingVirtualenvSdk(targetEnvironmentConfiguration: TargetEnvironmentConfiguration?, selectedSdk: Sdk): Sdk? {
    if (targetEnvironmentConfiguration == null) {
      return when (selectedSdk) {
        is PyDetectedSdk -> selectedSdk.setupAssociated(existingSdks, newProjectPath ?: project?.basePath)?.apply {
          // TODO [targets] Restore `makeSharedField` flag
          associateWithModule(module, newProjectPath)
        }
        else -> selectedSdk
      }
    }
    else {
      // TODO get rid of `!!`
      val homePath = selectedSdk.homePath!!
      return createSdkForTarget(project, targetEnvironmentConfiguration, homePath, existingSdks)
    }
  }

  private fun applyOptionalProjectSyncConfiguration(targetConfiguration: TargetEnvironmentConfiguration?) {
    if (targetConfiguration != null) projectSync?.apply(targetConfiguration)
  }

  private class ConstantComponentPredicate(private val value: Boolean) : ComponentPredicate() {
    override fun addListener(listener: (Boolean) -> Unit) = Unit

    override fun invoke(): Boolean = value

    companion object {
      val FALSE = ConstantComponentPredicate(value = false)
    }
  }

  companion object {
    /**
     * We assume this is the default name of the directory that is located in user home and which contains user virtualenv Python
     * environments.
     *
     * @see com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor.getDefaultLocation
     */
    private const val DEFAULT_VIRTUALENVS_DIR = ".virtualenvs"
  }
}