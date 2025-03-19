// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.pipenv.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.PythonSdkCoroutineService
import com.jetbrains.python.sdk.pipenv.getPipEnvExecutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.BorderLayout
import java.nio.file.Path
import javax.swing.JPanel
import kotlin.io.path.pathString

@Internal
class PyAddNewPipEnvFromFilePanel(private val module: Module) : JPanel() {

  val envData: Data
    get() = Data(Path.of(pipEnvPathField.text))

  private val pipEnvPathField = TextFieldWithBrowseButton()

  init {
    service<PythonSdkCoroutineService>().cs.launch {
      pipEnvPathField.apply {
        getPipEnvExecutable().getOrNull()?.pathString?.also { text = it }

        addBrowseFolderListener(module.project, withContext(Dispatchers.IO) { FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor() }
          .withTitle(PyBundle.message("python.sdk.pipenv.select.executable.title")))
      }

      layout = BorderLayout()
      val formPanel = FormBuilder.createFormBuilder()
        .addLabeledComponent(PyBundle.message("python.sdk.pipenv.executable"), pipEnvPathField)
        .panel
      add(formPanel, BorderLayout.NORTH)
    }
  }

  fun validateAll(): List<ValidationInfo> = emptyList() // Pre-target validation is not supported

  data class Data(val pipEnvPath: Path)
}
