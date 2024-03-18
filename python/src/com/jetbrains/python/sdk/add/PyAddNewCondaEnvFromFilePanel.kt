// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.add.PyAddNewEnvCollector.InputData
import com.jetbrains.python.sdk.flavors.conda.CondaEnvSdkFlavor
import org.jetbrains.annotations.SystemDependent
import java.awt.BorderLayout
import java.nio.file.Path
import javax.swing.JPanel
import kotlin.io.path.pathString

class PyAddNewCondaEnvFromFilePanel(private val module: Module, localCondaBinaryPath: Path?, environmentYml: VirtualFile? = null) : JPanel() {

  val envData: Data
    get() = Data(condaPathField.text, environmentYmlField.text)

  private val condaPathField = TextFieldWithBrowseButton()
  private val environmentYmlField = TextFieldWithBrowseButton()

  private val initialCondaPath: String
  private val initialEnvironmentYmlPath: String

  init {
    condaPathField.apply {
      localCondaBinaryPath?.let { text = it.pathString }

      addBrowseFolderListener(
        PyBundle.message("python.sdk.select.conda.path.title"),
        null,
        module.project,
        FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor()
      )
    }

    environmentYmlField.apply {
      environmentYml?.path?.also {
        text = FileUtil.toSystemDependentName(it)
        setTextFieldPreferredWidth(it.length)
      }

      addBrowseFolderListener(
        PyBundle.message("python.sdk.environment.yml.chooser"),
        null,
        module.project,
        FileChooserDescriptorFactory.createSingleFileDescriptor("yml")
      )
    }

    layout = BorderLayout()
    val formPanel = FormBuilder.createFormBuilder()
      .addLabeledComponent(PyBundle.message("python.sdk.conda.path"), condaPathField)
      .addLabeledComponent(PyBundle.message("python.sdk.environment.yml.label"), environmentYmlField)
      .panel
    add(formPanel, BorderLayout.NORTH)

    initialCondaPath = condaPathField.text
    initialEnvironmentYmlPath = environmentYmlField.text
  }

  fun validateAll(): List<ValidationInfo> = emptyList() // No validation for pre-target

  /**
   * Must be called if the input is confirmed and the current instance will not be used anymore
   * e.g. ʻOK` was clicked on the outer dialog.
   */
  fun logData() {
    val (condaPath, environmentYmlPath) = envData

    PyAddNewEnvCollector.logCondaEnvFromFileData(
      module.project,
      pathToEventField(initialCondaPath, condaPath),
      pathToEventField(initialEnvironmentYmlPath, environmentYmlPath)
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
      InputData.UNCHANGED
    }
  }

  data class Data(val condaPath: @NlsSafe @SystemDependent String, val environmentYmlPath: @NlsSafe @SystemDependent String)
}
