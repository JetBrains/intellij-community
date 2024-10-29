// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.newProject.steps.PyAddNewEnvironmentPanel
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.pathValidation.PlatformAndRoot
import com.jetbrains.python.pathValidation.ValidationRequest
import com.jetbrains.python.pathValidation.validateEmptyDir
import com.jetbrains.python.psi.icons.PythonPsiApiIcons
import com.jetbrains.python.sdk.PyDetectedSdk
import com.jetbrains.python.sdk.PySdkToInstall
import com.jetbrains.python.sdk.configuration.findPreferredVirtualEnvBaseSdk
import com.jetbrains.python.sdk.findBaseSdks
import com.jetbrains.python.sdk.flavors.MacPythonSdkFlavor
import com.jetbrains.python.sdk.getSdksToInstall
import com.jetbrains.python.ui.pyModalBlocking
import java.awt.Component
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

abstract class PyAddSdkPanel : JPanel(), PyAddSdkView {
  override val actions: Map<PyAddSdkDialogFlowAction, Boolean>
    get() = mapOf(PyAddSdkDialogFlowAction.OK.enabled())

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
  override val icon: Icon = PythonPsiApiIcons.Python
  open val sdk: Sdk? = null
  open val nameExtensionComponent: JComponent? = null
  open var newProjectPath: String? = null

  override fun getOrCreateSdk(): Sdk? = sdk

  open fun getStatisticInfo(): InterpreterStatisticsInfo? = null

  override fun onSelected(): Unit = Unit

  override fun validateAll(): List<ValidationInfo> = emptyList()

  open fun addChangeListener(listener: Runnable) {}

  companion object {
    @JvmStatic
    @RequiresEdt
    fun validateEnvironmentDirectoryLocation(field: TextFieldWithBrowseButton, platformAndRoot: PlatformAndRoot): ValidationInfo? {
      val path = field.text
      return pyModalBlocking {
        validateEmptyDir(
          ValidationRequest(
            path = path,
            fieldIsEmpty = PySdkBundle.message("python.venv.location.field.empty"),
            platformAndRoot = platformAndRoot
          ),
          notADirectory = PySdkBundle.message("python.venv.location.field.not.directory"),
          directoryNotEmpty = PySdkBundle.message("python.venv.location.directory.not.empty")
        )
      }
    }

    /** Should be protected. Please, don't use outside the class. KT-48508 */
    @JvmStatic
    @PublishedApi
    internal fun validateSdkComboBox(field: PySdkPathChoosingComboBox, view: PyAddSdkView): ValidationInfo? {
      return validateSdkComboBox(field, getDefaultButtonName(view))
    }

    @JvmStatic
    fun validateSdkComboBox(field: PySdkPathChoosingComboBox, @NlsContexts.Button defaultButtonName: String): ValidationInfo? {
      return when (val sdk = field.selectedSdkIfExists) {
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
 * then fills the [sdkComboBox] on the EDT and chooses [com.jetbrains.python.sdk.PySdkSettings.preferredVirtualEnvBaseSdk] or prepends it.
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
        val preferredSdk = findPreferredVirtualEnvBaseSdk(items)
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