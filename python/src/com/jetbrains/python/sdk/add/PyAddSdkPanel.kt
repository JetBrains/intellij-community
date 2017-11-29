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
import com.jetbrains.python.sdk.isNotEmptyDirectory
import icons.PythonIcons
import java.io.File
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * @author vlan
 */
abstract class PyAddSdkPanel : JPanel() {
  abstract val panelName: String
  open val icon: Icon = PythonIcons.Python.Python
  open val sdk: Sdk? = null
  open val nameExtensionComponent: JComponent? = null
  open var newProjectPath: String? = null

  open fun getOrCreateSdk(): Sdk? = sdk

  open fun validateAll(): List<ValidationInfo> = emptyList()

  open fun addChangeListener(listener: Runnable) {}

  companion object {
    @JvmStatic
    protected fun validateEmptyOrNonExistingDirectoryLocation(field: TextFieldWithBrowseButton): ValidationInfo? {
      val text = field.text
      val file = File(text)
      val message = when {
        StringUtil.isEmptyOrSpaces(text) -> "Location field is empty"
        file.exists() && !file.isDirectory -> "Location field path is not a directory"
        file.isNotEmptyDirectory -> "Location directory is not empty"
        else -> return null
      }
      return ValidationInfo(message, field)
    }

    @JvmStatic protected fun validateAnacondaPresense(component: JComponent?): ValidationInfo? =
      when {
        PyCondaPackageService.getSystemCondaExecutable() == null ->
          ValidationInfo("Anaconda installation is not found", component)
        else -> null
      }

    @JvmStatic
    protected fun validateSdkComboBox(field: PySdkPathChoosingComboBox): ValidationInfo? =
      when {
        field.selectedSdk == null -> ValidationInfo("Interpreter field is empty", field)
        else -> null
      }
  }
}