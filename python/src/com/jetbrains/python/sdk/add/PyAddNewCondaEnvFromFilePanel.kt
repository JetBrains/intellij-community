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
import com.jetbrains.python.packaging.PyCondaPackageService
import com.jetbrains.python.sdk.flavors.CondaEnvSdkFlavor
import org.jetbrains.annotations.SystemDependent
import java.awt.BorderLayout
import javax.swing.JPanel

class PyAddNewCondaEnvFromFilePanel(private val module: Module, environmentYml: VirtualFile? = null) : JPanel() {

  val envData: Data
    get() = Data(condaPathField.text, environmentYmlField.text)

  private val condaPathField = TextFieldWithBrowseButton()
  private val environmentYmlField = TextFieldWithBrowseButton()

  init {
    condaPathField.apply {
      PyCondaPackageService.getCondaExecutable(null)?.also { text = it }

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
  }

  fun validateAll(): List<ValidationInfo> = listOfNotNull(CondaEnvSdkFlavor.validateCondaPath(condaPathField.text))

  data class Data(val condaPath: @NlsSafe @SystemDependent String, val environmentYmlPath: @NlsSafe @SystemDependent String)
}
