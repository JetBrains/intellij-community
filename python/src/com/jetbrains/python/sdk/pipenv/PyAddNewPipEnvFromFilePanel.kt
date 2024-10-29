// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

      addBrowseFolderListener(module.project, FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor()
        .withTitle(PyBundle.message("python.sdk.pipenv.select.executable.title")))
    }

    layout = BorderLayout()
    val formPanel = FormBuilder.createFormBuilder()
      .addLabeledComponent(PyBundle.message("python.sdk.pipenv.executable"), pipEnvPathField)
      .panel
    add(formPanel, BorderLayout.NORTH)
  }

  fun validateAll(): List<ValidationInfo> = emptyList() // Pre-target validation is not supported

  data class Data(val pipEnvPath: @NlsSafe @SystemDependent String)
}
