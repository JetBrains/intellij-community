// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.newProject.steps

import com.intellij.execution.ExecutionException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.remote.PyProjectSynchronizer
import com.jetbrains.python.remote.PythonRemoteInterpreterManager
import com.jetbrains.python.sdk.PySdkUtil
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.add.PyAddSdkPanel
import icons.PythonIcons
import java.awt.BorderLayout
import java.awt.Component

/**
 * @author vlan
 */
class PyAddExistingSdkPanel(project: Project?,
                            existingSdks: List<Sdk>,
                            newProjectPath: String?,
                            preferredSdk: Sdk?) : PyAddSdkPanel() {

  override val panelName = "Existing interpreter"

  override val sdk: Sdk?
    get() = sdkChooserCombo.comboBox.selectedItem as? Sdk

  val remotePath: String?
    get() = if (remotePathField.mainPanel.isVisible) remotePathField.textField.text else null

  override var newProjectPath: String? = newProjectPath
    set(value) {
      field = value
      sdkChooserCombo.setNewProjectPath(value)
    }

  private val sdkChooserCombo: PythonSdkChooserCombo
  private val remotePathField = PyRemotePathField().apply {
    addActionListener {
      val currentSdk = sdk ?: return@addActionListener
      if (!PySdkUtil.isRemote(currentSdk)) return@addActionListener
      textField.text = currentSdk.chooseRemotePath(parent) ?: return@addActionListener
    }
  }

  init {
    layout = BorderLayout()
    sdkChooserCombo = PythonSdkChooserCombo(project, existingSdks, newProjectPath, { it != null && it == preferredSdk }).apply {
      if (SystemInfo.isMac && !UIUtil.isUnderDarcula()) {
        putClientProperty("JButton.buttonType", null)
      }
      setButtonIcon(PythonIcons.Python.InterpreterGear)
      addChangedListener {
        update()
      }
    }
    val formPanel = FormBuilder.createFormBuilder()
      .addLabeledComponent("Interpreter:", sdkChooserCombo)
      .addComponent(remotePathField.mainPanel)
      .panel
    add(formPanel, BorderLayout.NORTH)
    update()
  }

  override fun validateAll() =
    listOf(validateSdkChooserField(),
           validateRemotePathField())
      .filterNotNull()

  override fun addChangeListener(listener: Runnable) {
    sdkChooserCombo.addChangedListener { listener.run() }
    remotePathField.addTextChangeListener { listener.run() }
  }

  private fun validateSdkChooserField(): ValidationInfo? {
    val selectedSdk = sdk
    val message = when {
      selectedSdk == null -> "No Python interpreter selected"
      PythonSdkType.isInvalid(selectedSdk) -> "Choose valid Python interpreter"
      else -> return null
    }
    return ValidationInfo(message, sdkChooserCombo)
  }

  private fun validateRemotePathField(): ValidationInfo? {
    val path = remotePath
    return when {
      path != null && path.isBlank() -> ValidationInfo("Remote path not provided")
      else -> null
    }
  }

  private fun update() {
    val synchronizer = sdk?.projectSynchronizer
    remotePathField.mainPanel.isVisible = synchronizer != null
    if (synchronizer != null) {
      val defaultRemotePath = synchronizer.getDefaultRemotePath()
      val textField = remotePathField.textField
      if (defaultRemotePath != null && StringUtil.isEmpty(textField.text)) {
        textField.text = defaultRemotePath
      }
    }
  }

  companion object {
    private val Sdk.projectSynchronizer: PyProjectSynchronizer?
      get() = PythonRemoteInterpreterManager.getInstance()?.getSynchronizer(this)

    private fun Sdk.chooseRemotePath(owner: Component): String? {
      val remoteManager = PythonRemoteInterpreterManager.getInstance() ?: return null
      val (supplier, panel) = try {
        remoteManager.createServerBrowserForm(this) ?: return null
      }
      catch (e: Exception) {
        when (e) {
          is ExecutionException, is InterruptedException -> {
            Logger.getInstance(PyAddExistingSdkPanel::class.java).warn("Failed to create server browse button", e)
            JBPopupFactory.getInstance()
              .createMessage("Failed to browse remote server. Make sure you have permissions.")
              .show(owner)
            return null
          }
          else -> throw e
        }
      }
      panel.isVisible = true
      val wrapper = object : DialogWrapper(true) {
        init { init() }
        override fun createCenterPanel() = panel
      }
      return if (wrapper.showAndGet()) supplier.get() else null
    }
  }
}