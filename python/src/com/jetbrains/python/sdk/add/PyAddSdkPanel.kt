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
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.newProject.steps.PyAddNewEnvironmentPanel
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.add.PyAddSdkDialogFlowAction.OK
import com.jetbrains.python.sdk.configuration.PyProjectVirtualEnvConfiguration
import com.jetbrains.python.sdk.flavors.MacPythonSdkFlavor
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
    fun validateEnvironmentDirectoryLocation(field: TextFieldWithBrowseButton): ValidationInfo? {
      val text = field.text
      val file = File(text)
      val message = when {
        StringUtil.isEmptyOrSpaces(text) -> PySdkBundle.message("python.venv.location.field.empty")
        file.exists() && !file.isDirectory -> PySdkBundle.message("python.venv.location.field.not.directory")
        file.isNotEmptyDirectory -> PySdkBundle.message("python.venv.location.directory.not.empty")
        else -> return null
      }
      return ValidationInfo(message, field)
    }

    /** Should be protected. Please, don't use outside the class. KT-48508 */
    @JvmStatic
    @PublishedApi
    internal fun validateSdkComboBox(field: PySdkPathChoosingComboBox, view: PyAddSdkView): ValidationInfo? {
      return validateSdkComboBox(field, getDefaultButtonName(view))
    }

    @JvmStatic
    fun validateSdkComboBox(field: PySdkPathChoosingComboBox, @NlsContexts.Button defaultButtonName: String): ValidationInfo? {
      return when (val sdk = field.selectedSdk) {
        null -> ValidationInfo(PySdkBundle.message("python.sdk.field.is.empty"), field)
        is PySdkToInstall -> {
          val message = sdk.getInstallationWarning(defaultButtonName)
          ValidationInfo(message).asWarning().withOKEnabled()
        }
        is PyDetectedSdk -> {
          if (SystemInfo.isMac) MacPythonSdkFlavor.checkDetectedPython(sdk) else null
        }
        else -> null
      }
    }

    @NlsContexts.Button
    private fun getDefaultButtonName(view: PyAddSdkView): String {
      return if (view.component.parent?.parent is PyAddNewEnvironmentPanel) {
        IdeBundle.message("new.dir.project.create") // ProjectSettingsStepBase.createActionButton
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
                         onAdded: (List<Sdk>) -> Unit) {
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
        sdkComboBox.removeAllItems()
        sdks.forEach(sdkComboBox::addSdkItem)
        onAdded(sdks)
      }
    }
  }
}

/**
 * Keeps [NewPySdkComboBoxItem] if it is present in the combobox.
 */
private fun PySdkPathChoosingComboBox.removeAllItems() {
  if (childComponent.itemCount > 0 && childComponent.getItemAt(0) is NewPySdkComboBoxItem) {
    while (childComponent.itemCount > 1) {
      childComponent.removeItemAt(1)
    }
  }
  else {
    childComponent.removeAllItems()
  }
}

/**
 * Obtains a list of sdk to be used as a base for a virtual environment on a pool,
 * then fills the [sdkComboBox] on the EDT and chooses [PySdkSettings.preferredVirtualEnvBaseSdk] or prepends it.
 */
fun addBaseInterpretersAsync(sdkComboBox: PySdkPathChoosingComboBox,
                             existingSdks: List<Sdk>,
                             module: Module?,
                             context: UserDataHolder,
                             callback: () -> Unit = {}) {
  addInterpretersAsync(
    sdkComboBox,
    { findBaseSdks(existingSdks, module, context).takeIf { it.isNotEmpty() } ?: getSdksToInstall() },
    {
      sdkComboBox.apply {
        val preferredSdk = PyProjectVirtualEnvConfiguration.findPreferredVirtualEnvBaseSdk(items)
        if (preferredSdk != null) {
          if (items.find { it.homePath == preferredSdk.homePath } == null) {
            addSdkItemOnTop(preferredSdk)
          }
          selectedSdk = preferredSdk
        }
      }
      callback()
    }
  )
}
