// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v1

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory.Companion.extendWithTargetSpecificFields
import com.jetbrains.python.sdk.LOGGER
import com.jetbrains.python.sdk.PyDetectedSdk
import com.jetbrains.python.sdk.add.PyAddSdkPanel
import com.jetbrains.python.sdk.add.PySdkPathChoosingComboBox
import com.jetbrains.python.sdk.add.addInterpretersAsync
import com.jetbrains.python.sdk.adminPermissionsNeeded
import com.jetbrains.python.sdk.configuration.createSdkForTarget
import com.jetbrains.python.sdk.detectSystemWideSdks
import com.jetbrains.python.sdk.filterSystemWideSdks
import com.jetbrains.python.sdk.getSdksToInstall
import com.jetbrains.python.sdk.installSdkIfNeeded
import com.jetbrains.python.sdk.setup
import com.jetbrains.python.target.PythonLanguageRuntimeConfiguration
import java.awt.BorderLayout
import java.util.function.Supplier

open class PyAddSystemWideInterpreterPanel(private val _project: Project?,
                                           private val module: Module?,
                                           private val existingSdks: List<Sdk>,
                                           private val context: UserDataHolderBase,
                                           private val targetSupplier: Supplier<TargetEnvironmentConfiguration>? = null,
                                           config: PythonLanguageRuntimeConfiguration? = null) : PyAddSdkPanel() {
  private val project: Project?
    get() = _project ?: module?.project

  private val targetEnvironmentConfiguration: TargetEnvironmentConfiguration?
    get() = targetSupplier?.get()

  override val panelName: String get() = PyBundle.message("python.add.sdk.panel.name.system.interpreter")
  protected val sdkComboBox = PySdkPathChoosingComboBox(targetEnvironmentConfiguration = targetEnvironmentConfiguration)

  private lateinit var contentPanel: DialogPanel

  /**
   * Encapsulates the work with the optional target-specific fields, e.g., synchronization options and sudo permission.
   */
  private var targetPanelExtension: TargetPanelExtension? = null

  init {
    layout = BorderLayout()
    val permWarning = JBLabel(PyBundle.message("python.sdk.admin.permissions.needed.consider.creating.venv"))
    // We assume that this is the case with the local target
    val isLocalTarget = targetSupplier == null
    if (isLocalTarget) {
      Runnable {
        permWarning.isVisible = sdkComboBox.selectedSdkIfExists?.adminPermissionsNeeded() ?: false
      }.apply {
        run()
        addChangeListener(this)
      }
    }
    else {
      // There is no such ability yet in Targets API but in the future we might want to add the interactive introspection or gather file
      // options when the file is chosen via `BrowsableTargetEnvironmentType.createBrowser(...)`
      permWarning.isVisible = false
    }
    layoutComponents()
    if (isLocalTarget) {
      addInterpretersAsync(sdkComboBox) {
        detectSystemWideSdks(module, existingSdks, context).takeIf { it.isNotEmpty() || filterSystemWideSdks(existingSdks).isNotEmpty() }
        ?: getSdksToInstall()
      }
    }
    else {
      config?.pythonInterpreterPath?.let { introspectedPythonPath ->
        if (introspectedPythonPath.isNotBlank()) {
          sdkComboBox.addSdkItem(createDetectedSdk(introspectedPythonPath, targetEnvironmentConfiguration))
        }
      }
    }
  }

  protected open fun layoutComponents() {
    contentPanel = panel {
      row(PySdkBundle.message("python.interpreter.label")) {
        cell(sdkComboBox)
          .align(AlignX.FILL)
          .comment(PyBundle.message("python.sdk.admin.permissions.needed.consider.creating.venv.content"),
                   maxLineLength = MAX_LINE_LENGTH_WORD_WRAP)
      }
      targetPanelExtension = extendWithTargetSpecificFields(project, targetEnvironmentConfiguration)
    }
    add(contentPanel, BorderLayout.NORTH)
  }

  override fun validateAll(): List<ValidationInfo> = listOfNotNull(validateSdkComboBox(sdkComboBox, this))

  override fun getOrCreateSdk(): Sdk? {
    contentPanel.apply()

    targetPanelExtension?.applyToTargetConfiguration()

    val currentTargetEnvironmentConfiguration = targetEnvironmentConfiguration
    if (currentTargetEnvironmentConfiguration == null) {
      // this is the local machine case
      return when (val sdk = installSdkIfNeeded(sdkComboBox.selectedSdk, module, existingSdks, context).getOrLogException(LOGGER)) {
        is PyDetectedSdk -> sdk.setup(existingSdks)
        else -> sdk
      }
    }
    else {
      val interpreterPath = sdkComboBox.selectedSdk?.homePath!!
      return createSdkForTarget(project, currentTargetEnvironmentConfiguration, interpreterPath, existingSdks, targetPanelExtension)
    }
  }

  override fun addChangeListener(listener: Runnable) {
    sdkComboBox.childComponent.addItemListener { listener.run() }
  }
}