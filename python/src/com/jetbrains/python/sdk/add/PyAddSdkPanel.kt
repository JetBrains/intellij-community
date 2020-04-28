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

import com.intellij.CommonBundle
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.newProject.steps.PyAddNewEnvironmentPanel
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.add.PyAddSdkDialogFlowAction.OK
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
  override val actions: Map<PyAddSdkDialogFlowAction, Boolean>
    get() = mapOf(OK.enabled())

  override val component: Component
    get() = this

  /**
   * [component] is permanent. [PyAddSdkStateListener.onComponentChanged] won't
   * be called anyway.
   */
  override fun addStateListener(stateListener: PyAddSdkStateListener): Unit = Unit

  override fun previous(): Nothing = throw UnsupportedOperationException()

  override fun next(): Nothing = throw UnsupportedOperationException()

  override fun complete(): Unit = Unit

  abstract override val panelName: String
  override val icon: Icon = PythonIcons.Python.Python
  open val sdk: Sdk? = null
  open val nameExtensionComponent: JComponent? = null
  open var newProjectPath: String? = null

  override fun getOrCreateSdk(): Sdk? = sdk

  override fun onSelected(): Unit = Unit

  override fun validateAll(): List<ValidationInfo> = emptyList()

  open fun addChangeListener(listener: Runnable) {}

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
    protected fun validateSdkComboBox(field: PySdkPathChoosingComboBox, view: PyAddSdkView): ValidationInfo? {
      return when (val sdk = field.selectedSdk) {
        null -> ValidationInfo(PyBundle.message("python.sdk.interpreter.field.is.empty"), field)
        is PySdkToInstall -> {
          val message = sdk.getInstallationWarning(getDefaultButtonName(view))
          ValidationInfo(message).asWarning().withOKEnabled()
        }
        else -> null
      }
    }

    private fun getDefaultButtonName(view: PyAddSdkView): String {
      return if (view.component.parent?.parent is PyAddNewEnvironmentPanel) {
        "Create" // ProjectSettingsStepBase.createActionButton
      }
      else {
        CommonBundle.getOkButtonText() // DialogWrapper.createDefaultActions
      }
    }
  }
}

/**
 * Obtains a list of sdk on a pool using [sdkObtainer], then fills [sdkComboBox] on the EDT.
 */
fun addInterpretersAsync(sdkComboBox: PySdkPathChoosingComboBox, sdkObtainer: () -> List<Sdk>) {
  addInterpretersAsync(sdkComboBox, sdkObtainer, {})
}

/**
 * Obtains a list of sdk on a pool using [sdkObtainer], then fills [sdkComboBox] and calls [onAdded] on the EDT.
 */
fun addInterpretersAsync(sdkComboBox: PySdkPathChoosingComboBox,
                         sdkObtainer: () -> List<Sdk>,
                         onAdded: () -> Unit) {
  ApplicationManager.getApplication().executeOnPooledThread {
    val executor = AppUIExecutor.onUiThread(ModalityState.any())
    executor.execute { sdkComboBox.setBusy(true) }
    var sdks = emptyList<Sdk>()
    try {
      sdks = sdkObtainer()
    }
    finally {
      executor.execute {
        sdkComboBox.setBusy(false)
        sdks.forEach(sdkComboBox.childComponent::addItem)
        onAdded()
      }
    }
  }
}

/**
 * Obtains a list of sdk to be used as a base for a virtual environment on a pool,
 * then fills the [sdkComboBox] on the EDT and chooses [PySdkSettings.preferredVirtualEnvBaseSdk] or prepends it.
 */
fun addBaseInterpretersAsync(sdkComboBox: PySdkPathChoosingComboBox,
                             existingSdks: List<Sdk>,
                             module: Module?,
                             context: UserDataHolder) {
  addInterpretersAsync(
    sdkComboBox,
    { findBaseSdks(existingSdks, module, context).takeIf { it.isNotEmpty() } ?: getSdksToInstall() },
    {
      sdkComboBox.apply {
        val preferredSdkPath = PySdkSettings.instance.preferredVirtualEnvBaseSdk.takeIf(FileUtil::exists)
        val detectedPreferredSdk = items.find { it.homePath == preferredSdkPath }
        selectedSdk = when {
          detectedPreferredSdk != null -> detectedPreferredSdk
          preferredSdkPath != null -> PyDetectedSdk(preferredSdkPath).apply {
            childComponent.insertItemAt(this, 0)
          }
          else -> items.getOrNull(0)
        }
      }
    }
  )
}
