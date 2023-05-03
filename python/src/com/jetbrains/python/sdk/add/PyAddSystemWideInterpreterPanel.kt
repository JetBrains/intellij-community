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

import com.intellij.execution.target.TargetEnvironmentConfiguration
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
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory.Companion.projectSyncRows
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.add.target.ProjectSync
import com.jetbrains.python.sdk.add.target.PyAddSdkPanelBase.Companion.createSdkForTarget
import com.jetbrains.python.sdk.add.target.PyAddTargetBasedSdkView
import com.jetbrains.python.sdk.add.target.createDetectedSdk
import com.jetbrains.python.target.PythonLanguageRuntimeConfiguration
import java.awt.BorderLayout
import java.util.function.Supplier

open class PyAddSystemWideInterpreterPanel(private val _project: Project?,
                                           private val module: Module?,
                                           private val existingSdks: List<Sdk>,
                                           private val context: UserDataHolderBase,
                                           private val targetSupplier: Supplier<TargetEnvironmentConfiguration>? = null,
                                           config: PythonLanguageRuntimeConfiguration? = null) : PyAddSdkPanel(), PyAddTargetBasedSdkView {
  private val project: Project?
    get() = _project ?: module?.project

  private val targetEnvironmentConfiguration: TargetEnvironmentConfiguration?
    get() = targetSupplier?.get()

  override val panelName: String get() = PyBundle.message("python.add.sdk.panel.name.system.interpreter")
  protected val sdkComboBox = PySdkPathChoosingComboBox(targetEnvironmentConfiguration = targetEnvironmentConfiguration)

  private lateinit var contentPanel: DialogPanel

  /**
   * Encapsulates the work with the files synchronization options.
   */
  private var projectSync: ProjectSync? = null

  init {
    layout = BorderLayout()
    val permWarning = JBLabel(PyBundle.message("python.sdk.admin.permissions.needed.consider.creating.venv"))
    // We assume that this is the case with the local target
    val isLocalTarget = targetSupplier == null
    if (isLocalTarget) {
      Runnable {
        permWarning.isVisible = sdkComboBox.selectedSdk?.adminPermissionsNeeded() ?: false
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
      projectSync = projectSyncRows(project, targetEnvironmentConfiguration)
    }
    add(contentPanel, BorderLayout.NORTH)
  }

  override fun validateAll(): List<ValidationInfo> = listOfNotNull(validateSdkComboBox(sdkComboBox, this))

  override fun getOrCreateSdk(): Sdk? = getOrCreateSdk(targetEnvironmentConfiguration = null)

  override fun getOrCreateSdk(targetEnvironmentConfiguration: TargetEnvironmentConfiguration?): Sdk? {
    contentPanel.apply()

    applyOptionalProjectSyncConfiguration(targetEnvironmentConfiguration)

    if (targetEnvironmentConfiguration == null) {
      // this is the local machine case
      return when (val sdk = installSdkIfNeeded(sdkComboBox.selectedSdk, module, existingSdks, context)) {
        is PyDetectedSdk -> sdk.setup(existingSdks)
        else -> sdk
      }
    }
    else {
      val interpreterPath = sdkComboBox.selectedSdk?.homePath!!
      return createSdkForTarget(project, targetEnvironmentConfiguration, interpreterPath, existingSdks)
    }
  }

  private fun applyOptionalProjectSyncConfiguration(targetConfiguration: TargetEnvironmentConfiguration?) {
    if (targetConfiguration != null) projectSync?.apply(targetConfiguration)
  }

  override fun addChangeListener(listener: Runnable) {
    sdkComboBox.childComponent.addItemListener { listener.run() }
  }
}
