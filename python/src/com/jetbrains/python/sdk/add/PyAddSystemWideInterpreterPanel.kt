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

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.*
import java.awt.BorderLayout

/**
 * @author vlan
 */
class PyAddSystemWideInterpreterPanel(private val module: Module?,
                                      private val existingSdks: List<Sdk>,
                                      private val context: UserDataHolderBase) : PyAddSdkPanel() {
  override val panelName: String get() = PyBundle.message("python.add.sdk.panel.name.system.interpreter")
  private val sdkComboBox = PySdkPathChoosingComboBox()

  init {
    layout = BorderLayout()
    val permWarning = JBLabel(
      """|<html><strong>Note:</strong> You'll need admin permissions to install packages for this interpreter. Consider
         |creating a per-project virtual environment instead.</html>""".trimMargin()).apply {
    }
    Runnable {
      permWarning.isVisible = sdkComboBox.selectedSdk?.adminPermissionsNeeded() ?: false
    }.apply {
      run()
      addChangeListener(this)
    }
    val formPanel = FormBuilder.createFormBuilder()
      .addLabeledComponent(PyBundle.message("interpreter"), sdkComboBox)
      .addComponentToRightColumn(permWarning)
      .panel
    add(formPanel, BorderLayout.NORTH)
    addInterpretersAsync(sdkComboBox) {
      detectSystemWideSdks(module, existingSdks, context).takeIf { it.isNotEmpty() || filterSystemWideSdks(existingSdks).isNotEmpty() }
      ?: getSdksToInstall()
    }
  }

  override fun validateAll(): List<ValidationInfo> = listOfNotNull(validateSdkComboBox(sdkComboBox, this))

  override fun getOrCreateSdk(): Sdk? {
    return when (val sdk = sdkComboBox.selectedSdk) {
      is PySdkToInstall -> sdk.install(module) { detectSystemWideSdks(module, existingSdks, context) }?.setup(existingSdks)
      is PyDetectedSdk -> sdk.setup(existingSdks)
      else -> sdk
    }
  }

  override fun addChangeListener(listener: Runnable) {
    sdkComboBox.childComponent.addItemListener { listener.run() }
  }
}
