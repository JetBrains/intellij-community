// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add.target

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.joinTargetPaths
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.not
import com.intellij.util.PathUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.pathValidation.PlatformAndRoot.Companion.getPlatformAndRoot
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory.Companion.extendWithTargetSpecificFields
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.add.ExistingPySdkComboBoxItem
import com.jetbrains.python.sdk.add.PySdkPathChoosingComboBox
import com.jetbrains.python.sdk.add.addBaseInterpretersAsync
import com.jetbrains.python.sdk.add.addInterpretersAsync
import com.jetbrains.python.sdk.configuration.createSdkForTarget
import com.jetbrains.python.sdk.configuration.createVirtualEnvSynchronously
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.VirtualEnvReader.Companion.DEFAULT_VIRTUALENVS_DIR
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.target.PythonLanguageRuntimeConfiguration
import java.awt.BorderLayout
import java.util.function.Supplier

/**
 * Panel with a control that allows to add either new or selecting existing virtualenv.
 */
class PyAddVirtualEnvPanel(
  project: Project?,
  module: Module?,
  private val existingSdks: List<Sdk> = emptyList(),
  allowAddNewVirtualenv: Boolean = false,
  private val context: UserDataHolder,
  targetSupplier: Supplier<TargetEnvironmentConfiguration>?,
  config: PythonLanguageRuntimeConfiguration,
) : PyAddSdkPanelBase(project, module, targetSupplier) {

  override val panelName = PyBundle.message("python.add.sdk.panel.name.virtualenv.environment")

  override val icon = PythonIcons.Python.Virtualenv

  private val interpreterCombobox: PySdkPathChoosingComboBox

  private val locationField: TextFieldWithBrowseButton

  private val baseInterpreterCombobox: PySdkPathChoosingComboBox

  private var isCreateNewVirtualenv: Boolean = false

  private var isInheritSitePackages: Boolean = false

  /**
   * Encapsulates the work with the optional target-specific fields, e.g., synchronization options and sudo permission.
   */
  private var targetPanelExtension: TargetPanelExtension? = null

  private val contentPanel: DialogPanel

  private lateinit var newEnvironmentModeSelected: ComponentPredicate

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
    contentPanel = panel {
      if (allowAddNewVirtualenv && isMutableTarget) {
        isCreateNewVirtualenv = true
        buttonsGroup {
          row {
            label(PyBundle.message("sdk.create.venv.environment.label"))
            radioButton(PyBundle.message("sdk.create.venv.existing.option.label"), false)
            radioButton(PyBundle.message("sdk.create.venv.new.option.label"), true).apply {
              newEnvironmentModeSelected = selected
            }
          }
        }.bind(getter = { isCreateNewVirtualenv }, setter = { isCreateNewVirtualenv = it })
      }
      else {
        newEnvironmentModeSelected = ComponentPredicate.FALSE
      }

      row(PyBundle.message("sdk.create.venv.dialog.interpreter.label")) {
        cell(interpreterCombobox).align(AlignX.FILL)
      }.visibleIf(newEnvironmentModeSelected.not())

      row(PyBundle.message("sdk.create.venv.dialog.location.label")) {
        cell(locationField).align(AlignX.FILL)
      }.visibleIf(newEnvironmentModeSelected)
      row(PyBundle.message("sdk.create.venv.dialog.base.interpreter.label")) {
        cell(baseInterpreterCombobox).align(AlignX.FILL)
      }.visibleIf(newEnvironmentModeSelected)
      row {
        checkBox(PyBundle.message("sdk.create.venv.dialog.label.inherit.global.site.packages"))
          .bindSelected(::isInheritSitePackages)
      }.visibleIf(newEnvironmentModeSelected)

      targetPanelExtension = extendWithTargetSpecificFields(project, targetEnvironmentConfiguration)
    }

    add(contentPanel, BorderLayout.NORTH)

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
          baseInterpreterCombobox.addSdkItem(createDetectedSdk(introspectedPythonPath, targetEnvironmentConfiguration))
        }
      }
    }
  }

  override fun validateAll(): List<ValidationInfo> {
    if (newEnvironmentModeSelected()) {
      val platformAndRoot = targetEnvironmentConfiguration.getPlatformAndRoot()
      return listOfNotNull(validateEnvironmentDirectoryLocation(locationField, platformAndRoot),
                           validateSdkComboBox(baseInterpreterCombobox, this))
    }
    else {
      return listOfNotNull(validateSdkComboBox(interpreterCombobox, this))
    }
  }

  override fun getOrCreateSdk(): Sdk {
    // applies components' states for bound properties (e.g. selected radio button to `isCreateNewVirtualenv` field)
    contentPanel.apply()

    // TODO [targets] Refactor this workaround
    targetPanelExtension?.applyToTargetConfiguration()

    if (isCreateNewVirtualenv) return createNewVirtualenvSdk(targetEnvironmentConfiguration)

    val item = interpreterCombobox.selectedItem as ExistingPySdkComboBoxItem
    // there should *not* be other items other than `ExistingPySdkComboBoxItem`
    return configureExistingVirtualenvSdk(targetEnvironmentConfiguration, item.sdk)
  }

  /**
   * Note: there is a careful work with SDK names because of the caching of Python package managers in
   * [com.jetbrains.python.packaging.PyPackageManagersImpl.forSdk].
   */
  private fun createNewVirtualenvSdk(targetEnvironmentConfiguration: TargetEnvironmentConfiguration?): Sdk {
    // TODO [targets] Do *not* silently `return null`
    val baseSelectedSdk = baseInterpreterCombobox.selectedSdk
    val virtualenvRoot = locationField.text
    val baseSdk = if (targetEnvironmentConfiguration != null) {
      // TODO [targets] why don't we use `baseSelectedSdk`
      // tune `baseSelectedSdk`
      val sdkAdditionalData = PyTargetAwareAdditionalData(PyFlavorAndData(PyFlavorData.Empty, virtualEnvSdkFlavor))
      sdkAdditionalData.targetEnvironmentConfiguration = targetEnvironmentConfiguration
      val homePath = baseSelectedSdk.homePath!!
      // suggesting the proper name for the base SDK fixes the problem with clashing caching key of Python package manager
      val customSdkSuggestedName = PythonInterpreterTargetEnvironmentFactory.findDefaultSdkName(project, sdkAdditionalData, version = null)
      sdkAdditionalData.interpreterPath = homePath
      SdkConfigurationUtil.createSdk(existingSdks, homePath, PythonSdkType.getInstance(), sdkAdditionalData, customSdkSuggestedName)
    }
    else {
      baseSelectedSdk
    }
    return createVirtualEnvSynchronously(baseSdk, existingSdks, virtualenvRoot, projectBasePath, project, module, context,
                                         isInheritSitePackages, false, targetPanelExtension)
  }

  private fun configureExistingVirtualenvSdk(targetEnvironmentConfiguration: TargetEnvironmentConfiguration?, selectedSdk: Sdk): Sdk {
    if (targetEnvironmentConfiguration == null) {
      return when (selectedSdk) {
        is PyDetectedSdk -> selectedSdk.setupAssociated(existingSdks, newProjectPath ?: project?.basePath, true)
          .getOrThrow()
        else -> selectedSdk
      }
    }
    else {
      // TODO get rid of `!!`
      val homePath = selectedSdk.homePath!!
      return createSdkForTarget(project, targetEnvironmentConfiguration, homePath, existingSdks, targetPanelExtension)
    }
  }
}