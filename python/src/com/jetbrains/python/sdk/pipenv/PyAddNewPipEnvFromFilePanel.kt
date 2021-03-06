// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.pipenv

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.PyBundle
import org.jetbrains.annotations.SystemDependent
import java.awt.BorderLayout
import javax.swing.JPanel

class PyAddNewPipEnvFromFilePanel(private val module: Module) : JPanel() {

  val envData: Data
    get() = Data(pipEnvPathField.text)

  private val pipEnvPathField = TextFieldWithBrowseButton()

  init {
    pipEnvPathField.apply {
      getPipEnvExecutable()?.absolutePath?.also { text = it }

      addBrowseFolderListener(
        PyBundle.message("python.sdk.pipenv.select.executable.title"),
        null,
        module.project,
        FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor()
      )
    }

    layout = BorderLayout()
    val formPanel = FormBuilder.createFormBuilder()
      .addLabeledComponent(PyBundle.message("python.sdk.pipenv.executable"), pipEnvPathField)
      .panel
    add(formPanel, BorderLayout.NORTH)
  }

  fun validateAll(): List<ValidationInfo> = listOfNotNull(validatePipEnvExecutable(pipEnvPathField.text))

  data class Data(val pipEnvPath: @NlsSafe @SystemDependent String)
}