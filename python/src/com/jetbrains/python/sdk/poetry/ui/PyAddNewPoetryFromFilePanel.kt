// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry.ui

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.PyBundle
import com.jetbrains.python.getOrNull
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.poetry.getPoetryExecutable
import com.jetbrains.python.sdk.poetry.validatePoetryExecutable
import com.jetbrains.python.util.runWithModalBlockingOrInBackground
import java.awt.BorderLayout
import java.nio.file.Path
import javax.swing.JPanel
import kotlin.io.path.absolutePathString

class PyAddNewPoetryFromFilePanel(private val module: Module) : JPanel() {
  val envData: Data
    get() = Data(Path.of(poetryPathField.text))

  private val poetryPathField = TextFieldWithBrowseButton()

  init {
    PyPackageCoroutine.launch(project = module.project) {
      poetryPathField.apply {
        getPoetryExecutable().getOrNull()?.absolutePathString()?.also { text = it }
        addBrowseFolderListener(module.project, FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor()
          .withTitle(PyBundle.message("python.sdk.poetry.select.executable.title")))
      }

      layout = BorderLayout()
      val formPanel = FormBuilder.createFormBuilder()
        .addLabeledComponent(PyBundle.message("python.sdk.poetry.executable"), poetryPathField)
        .panel
      add(formPanel, BorderLayout.NORTH)
    }
  }

  fun validateAll(): List<ValidationInfo> = runWithModalBlockingOrInBackground(module.project, PyBundle.message("python.sdk.validating.environment")) {
    listOfNotNull(validatePoetryExecutable(Path.of(poetryPathField.text)))
  }

  data class Data(val poetryPath: Path)
}
