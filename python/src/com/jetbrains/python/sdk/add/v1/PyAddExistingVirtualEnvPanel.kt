// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v1

import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.UserDataHolder
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.sdk.*
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.sdk.add.PyAddSdkPanel
import com.jetbrains.python.sdk.add.PySdkPathChoosingComboBox
import com.jetbrains.python.sdk.add.addInterpretersAsync
import java.awt.BorderLayout
import javax.swing.Icon

open class PyAddExistingVirtualEnvPanel(
  private val project: Project?,
  private val module: Module?,
  private val existingSdks: List<Sdk>,
  override var newProjectPath: String?,
  context: UserDataHolder,
) : PyAddSdkPanel() {
  override val panelName: String get() = PyBundle.message("python.add.sdk.panel.name.existing.environment")
  override val icon: Icon = PythonIcons.Python.Virtualenv
  protected val sdkComboBox = PySdkPathChoosingComboBox()
  private val makeSharedField = JBCheckBox(PyBundle.message("available.to.all.projects"))

  init {
    @Suppress("LeakingThis")
    layoutComponents()
    addInterpretersAsync(sdkComboBox) {
      detectVirtualEnvs(module, existingSdks, context)
        .filterNot { it.isAssociatedWithAnotherModule(module) }
    }
  }

  protected open fun layoutComponents() {
    layout = BorderLayout()
    val formPanel = FormBuilder.createFormBuilder()
      .addLabeledComponent(PySdkBundle.message("python.interpreter.label"), sdkComboBox)
      .addComponent(makeSharedField)
      .panel
    add(formPanel, BorderLayout.NORTH)
  }

  override fun validateAll(): List<ValidationInfo> = listOfNotNull(validateSdkComboBox(sdkComboBox, this))

  override fun getOrCreateSdk(): Sdk? {
    return when (val sdk = sdkComboBox.selectedSdk) {
      is PyDetectedSdk -> sdk.setupAssociated(existingSdks, newProjectPath ?: project?.basePath, !makeSharedField.isSelected)
        .getOrLogException(LOGGER)
      else -> sdk
    }
  }
}
