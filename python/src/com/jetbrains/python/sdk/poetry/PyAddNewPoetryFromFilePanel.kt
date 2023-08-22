package com.jetbrains.python.sdk.poetry


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

/**
 *  This source code is created by @koxudaxi Koudai Aono <koxudaxi@gmail.com>
 */

class PyAddNewPoetryFromFilePanel(private val module: Module) : JPanel() {

  val envData: Data
    get() = Data(poetryPathField.text)

  private val poetryPathField = TextFieldWithBrowseButton()

  init {
    poetryPathField.apply {
      getPoetryExecutable()?.absolutePath?.also { text = it }

      addBrowseFolderListener(
        PyBundle.message("python.sdk.poetry.select.executable.title"),
        null,
        module.project,
        FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor()
      )
    }

    layout = BorderLayout()
    val formPanel = FormBuilder.createFormBuilder()
      .addLabeledComponent(PyBundle.message("python.sdk.poetry.executable"), poetryPathField)
      .panel
    add(formPanel, BorderLayout.NORTH)
  }

  fun validateAll(): List<ValidationInfo> = listOfNotNull(validatePoetryExecutable(poetryPathField.text))

  data class Data(val poetryPath: @NlsSafe @SystemDependent String)
}