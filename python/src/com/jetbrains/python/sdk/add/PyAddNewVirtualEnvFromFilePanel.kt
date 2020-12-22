// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.sdk.PySdkSettings
import com.jetbrains.python.sdk.basePath
import org.jetbrains.annotations.SystemDependent
import org.jetbrains.annotations.SystemIndependent
import java.awt.BorderLayout
import javax.swing.JPanel

class PyAddNewVirtualEnvFromFilePanel(private val module: Module,
                                      existingSdks: List<Sdk>,
                                      suggestedRequirementsTxtOrSetupPy: VirtualFile? = null) : JPanel() {
  val envData: Data
    get() = Data(pathField.text, baseSdkField.selectedSdk, requirementsTxtOrSetupPyField.text)

  private val baseSdkField = PySdkPathChoosingComboBox()
  private val pathField = TextFieldWithBrowseButton()
  private val requirementsTxtOrSetupPyField = TextFieldWithBrowseButton()

  private val projectBasePath: @SystemIndependent String?
    get() = module.basePath ?: module.project.basePath

  init {
    pathField.apply {
      text = FileUtil.toSystemDependentName(PySdkSettings.instance.getPreferredVirtualEnvBasePath(projectBasePath))

      addBrowseFolderListener(
        PySdkBundle.message("python.venv.location.chooser"),
        null,
        module.project,
        FileChooserDescriptorFactory.createSingleFolderDescriptor())
    }

    requirementsTxtOrSetupPyField.apply {
      suggestedRequirementsTxtOrSetupPy?.path?.also {
        text = FileUtil.toSystemDependentName(it)
        setTextFieldPreferredWidth(it.length)
      }

      addBrowseFolderListener(
        PyBundle.message("sdk.create.venv.dependencies.chooser"),
        null,
        module.project,
        FileChooserDescriptorFactory.createSingleFileDescriptor().withFileFilter { file ->
          file.fileType.let { it == PlainTextFileType.INSTANCE || it == PythonFileType.INSTANCE }
        }
      )
    }

    layout = BorderLayout()
    val formPanel = FormBuilder.createFormBuilder()
      .addLabeledComponent(PySdkBundle.message("python.venv.location.label"), pathField)
      .addLabeledComponent(PySdkBundle.message("python.venv.base.label"), baseSdkField)
      .addLabeledComponent(PyBundle.message("sdk.create.venv.dependencies.label"), requirementsTxtOrSetupPyField)
      .panel
    add(formPanel, BorderLayout.NORTH)
    addBaseInterpretersAsync(baseSdkField, existingSdks, module, UserDataHolderBase())
  }

  fun validateAll(defaultButtonName: @NlsContexts.Button String): List<ValidationInfo> =
    listOfNotNull(PyAddSdkPanel.validateEnvironmentDirectoryLocation(pathField),
                  PyAddSdkPanel.validateSdkComboBox(baseSdkField, defaultButtonName))

  data class Data(
    val path: @NlsSafe @SystemDependent String,
    val baseSdk: Sdk?,
    val requirementsTxtOrSetupPyPath: @NlsSafe @SystemDependent String
  )
}
