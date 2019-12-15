// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.newProject.steps

import com.intellij.execution.ExecutionException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PathMappingSettings
import com.intellij.util.component1
import com.intellij.util.component2
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.Result
import com.jetbrains.python.remote.PyProjectSynchronizer
import com.jetbrains.python.remote.PyProjectSynchronizerProvider
import com.jetbrains.python.remote.PythonSshInterpreterManager
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.add.PyAddSdkPanel
import com.jetbrains.python.sdk.associatedModulePath
import java.awt.BorderLayout
import java.awt.Component

/**
 * @author vlan
 */
class PyAddExistingSdkPanel(project: Project?,
                            module: Module?,
                            existingSdks: List<Sdk>,
                            newProjectPath: String?,
                            preferredSdk: Sdk?) : PyAddSdkPanel() {

  override val panelName: String = "Existing interpreter"

  /**
   * Path mappings of current synchronizer.
   * Once set, [remotePathField] will be updated on any change of local path passed through mappings
   */
  private var defaultMappings: List<PathMappingSettings.PathMapping>? = null

  override val sdk: Sdk?
    get() = sdkChooserCombo.comboBox.selectedItem as? Sdk

  val remotePath: String?
    get() = if (remotePathField.mainPanel.isVisible) remotePathField.textField.text else null

  override var newProjectPath: String? = newProjectPath
    set(value) {
      field = value
      sdkChooserCombo.setNewProjectPath(value)
      updateRemotePathIfNeeded()
    }

  private val sdkChooserCombo: PythonSdkChooserCombo
  private val remotePathField = PyRemotePathField().apply {
    addActionListener {
      val currentSdk = sdk ?: return@addActionListener
      if (!PythonSdkUtil.isRemote(currentSdk)) return@addActionListener
      textField.text = currentSdk.chooseRemotePath(parent) ?: return@addActionListener
    }
  }

  init {
    layout = BorderLayout()
    val sdksForNewProject = existingSdks.filter { it.associatedModulePath == null }
    sdkChooserCombo = PythonSdkChooserCombo(project, module, sdksForNewProject, newProjectPath) {
      it != null && it == preferredSdk
    }.apply {
      if (SystemInfo.isMac && !UIUtil.isUnderDarcula()) {
        putClientProperty("JButton.buttonType", null)
      }
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

  override fun validateAll(): List<ValidationInfo> =
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
      PythonSdkUtil.isInvalid(selectedSdk) -> "Choose valid Python interpreter"
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
      synchronizer.getAutoMappings()?.let {
        when (it) {
          is Result.Success -> defaultMappings = it.result
          is Result.Failure -> {
            remotePathField.textField.text = it.error
            remotePathField.setReadOnly(true)
            return
          }
        }
      }
      assert(defaultRemotePath == null || defaultMappings == null) { "Can't have both: default mappings and default value" }
      assert(!(defaultRemotePath?.isEmpty() ?: false)) { "Mappings are empty" }

      val textField = remotePathField.textField
      if (defaultRemotePath != null && StringUtil.isEmpty(textField.text)) {
        textField.text = defaultRemotePath
      }
    }
    // DefaultMappings revokes user ability to change mapping by her self, so field is readonly
    remotePathField.setReadOnly(defaultMappings != null)
    updateRemotePathIfNeeded()
  }

  /**
   * Remote path should be updated automatically if [defaultMappings] are set.
   * See [PyProjectSynchronizer.getAutoMappings].
   */
  private fun updateRemotePathIfNeeded() {
    val path = newProjectPath ?: return
    val mappings = defaultMappings ?: return
    remotePathField.textField.text = mappings.find { it.canReplaceLocal(path) }?.mapToRemote(path) ?: "?"
  }

  companion object {
    private val Sdk.projectSynchronizer: PyProjectSynchronizer?
      get() = PyProjectSynchronizerProvider.getSynchronizer(this)

    private fun Sdk.chooseRemotePath(owner: Component): String? {
      val remoteManager = PythonSshInterpreterManager.Factory.getInstance() ?: return null
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
        init {
          init()
        }

        override fun createCenterPanel() = panel
      }
      return if (wrapper.showAndGet()) supplier.get() else null
    }
  }
}
