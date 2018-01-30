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

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.python.packaging.PyCondaPackageService
import com.jetbrains.python.sdk.add.wizard.WizardStep
import com.jetbrains.python.sdk.isNotEmptyDirectory
import icons.PythonIcons
import java.awt.Component
import java.io.File
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * @author vlan
 */
abstract class PyAddSdkPanel : JPanel(), PyAddSdkView {
  override abstract val panelName: String
  override val icon: Icon = PythonIcons.Python.Python
  open val sdk: Sdk? = null
  open val nameExtensionComponent: JComponent? = null
  open var newProjectPath: String? = null

  override fun getOrCreateSdk(): Sdk? = sdk

  override fun validateAll(): List<ValidationInfo> = emptyList()

  open fun addChangeListener(listener: Runnable) {}

  /**
   * Returns the "wizard" with the single step.
   */
  override fun getFirstWizardStep(): WizardStep<Sdk?> = object : WizardStep<Sdk?> {
    override fun finish(): Sdk? = getOrCreateSdk()

    override val component: Component = this@PyAddSdkPanel

    override fun hasNext(): Boolean = false

    override fun next(): WizardStep<Sdk?> = throw IllegalStateException()

    override fun hasPrevious(): Boolean = false

    override fun previous(): WizardStep<Sdk?> = throw IllegalStateException()

    override fun validateAll(): List<ValidationInfo> = this@PyAddSdkPanel.validateAll()
  }

  companion object {
    @JvmStatic
    protected fun validateEnvironmentDirectoryLocation(field: TextFieldWithBrowseButton): ValidationInfo? {
      val text = field.text
      val file = File(text)
      val message = when {
        StringUtil.isEmptyOrSpaces(text) -> "Environment location field is empty"
        file.exists() && !file.isDirectory -> "Environment location field path is not a directory"
        file.isNotEmptyDirectory -> "Environment location directory is not empty"
        else -> return null
      }
      return ValidationInfo(message, field)
    }

    @JvmStatic
    protected fun validateSdkComboBox(field: PySdkPathChoosingComboBox): ValidationInfo? =
      when {
        field.selectedSdk == null -> ValidationInfo("Interpreter field is empty", field)
        else -> null
      }
  }
}