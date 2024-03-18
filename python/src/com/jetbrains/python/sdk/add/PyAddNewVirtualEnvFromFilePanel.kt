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
import com.jetbrains.python.sdk.add.PyAddNewEnvCollector.InputData
import com.jetbrains.python.sdk.add.PyAddNewEnvCollector.RequirementsTxtOrSetupPyData
import com.jetbrains.python.pathValidation.PlatformAndRoot
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

  private var initialBaseSdk: Sdk? = null
  private val initialPath: String
  private val initialRequirementsTxtOrSetupPy: String

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
    addBaseInterpretersAsync(baseSdkField, existingSdks, module, UserDataHolderBase()) { initialBaseSdk = baseSdkField.selectedSdk }

    initialPath = pathField.text
    initialRequirementsTxtOrSetupPy = requirementsTxtOrSetupPyField.text
  }

  fun validateAll(@NlsContexts.Button defaultButtonName: String): List<ValidationInfo> =
    listOfNotNull(PyAddSdkPanel.validateEnvironmentDirectoryLocation(pathField, PlatformAndRoot.local),
                  PyAddSdkPanel.validateSdkComboBox(baseSdkField, defaultButtonName))

  /**
   * Must be called if the input is confirmed and the current instance will not be used anymore
   * e.g. `OK` was clicked on the outer dialog.
   */
  fun logData() {
    val (path, baseSdk, requirementsTxtOrSetupPyPath) = envData

    PyAddNewEnvCollector.logVirtualEnvFromFileData(
      module.project,
      pathToEventField(initialPath, path),
      baseSdkToEventField(initialBaseSdk, baseSdk),
      requirementsTxtOrSetupPyPathToEventField(initialRequirementsTxtOrSetupPy, requirementsTxtOrSetupPyPath)
    )
  }

  private fun pathToEventField(initial: String, result: String): InputData {
    return if (initial.isBlank()) {
      if (result.isBlank()) InputData.BLANK_UNCHANGED else InputData.SPECIFIED
    }
    else if (initial != result) {
      InputData.CHANGED
    }
    else {
      PyAddNewEnvCollector.InputData.UNCHANGED
    }
  }

  private fun baseSdkToEventField(initial: Sdk?, result: Sdk?): InputData {
    return if (initial == null) {
      if (result == null) InputData.BLANK_UNCHANGED else InputData.SPECIFIED
    }
    else if (initial != result) {
      InputData.CHANGED
    }
    else {
      InputData.UNCHANGED
    }
  }

  private fun requirementsTxtOrSetupPyPathToEventField(initial: String, result: String): RequirementsTxtOrSetupPyData {
    return if (initial.isBlank()) {
      if (result.isBlank()) RequirementsTxtOrSetupPyData.BLANK_UNCHANGED
      else if (result.endsWith(".txt", true)) RequirementsTxtOrSetupPyData.TXT_SPECIFIED
      else if (result.endsWith(".py", true)) RequirementsTxtOrSetupPyData.PY_SPECIFIED
      else RequirementsTxtOrSetupPyData.OTHER_SPECIFIED
    }
    else if (initial != result) {
      if (initial.endsWith(".txt", true)) {
        when {
          result.endsWith(".py", true) -> RequirementsTxtOrSetupPyData.CHANGED_TXT_TO_PY
          result.endsWith(".txt", true) -> RequirementsTxtOrSetupPyData.CHANGED_TXT_TO_TXT
          else -> RequirementsTxtOrSetupPyData.CHANGED_TXT_TO_OTHER
        }
      }
      else if (initial.endsWith(".py", true)) {
        when {
          result.endsWith(".py", true) -> RequirementsTxtOrSetupPyData.CHANGED_PY_TO_PY
          result.endsWith(".txt", true) -> RequirementsTxtOrSetupPyData.CHANGED_PY_TO_TXT
          else -> RequirementsTxtOrSetupPyData.CHANGED_PY_TO_OTHER
        }
      }
      else {
        when {
          result.endsWith(".py", true) -> RequirementsTxtOrSetupPyData.CHANGED_OTHER_TO_PY
          result.endsWith(".txt", true) -> RequirementsTxtOrSetupPyData.CHANGED_OTHER_TO_TXT
          else -> RequirementsTxtOrSetupPyData.CHANGED_OTHER_TO_OTHER
        }
      }
    }
    else {
      RequirementsTxtOrSetupPyData.UNCHANGED
    }
  }

  data class Data(
    val path: @NlsSafe @SystemDependent String,
    val baseSdk: Sdk?,
    val requirementsTxtOrSetupPyPath: @NlsSafe @SystemDependent String
  )
}
