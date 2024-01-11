// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.components.JBComboBoxLabel
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.selected
import com.jetbrains.python.PySymbolFieldWithBrowseButton
import com.jetbrains.python.extensions.ModuleBasedContextAnchor
import com.jetbrains.python.extensions.ProjectSdkContextAnchor
import com.jetbrains.python.isPythonModule
import com.jetbrains.python.PyBundle
import java.util.*
import javax.swing.ButtonGroup
import javax.swing.ButtonModel
import javax.swing.JCheckBox

internal class PythonRunConfigurationPanel(configuration: PythonRunConfiguration, commonOptionsForm: AbstractPyCommonOptionsForm,
                                           @JvmField val targetComboBox: JBComboBoxLabel) {

  @JvmField
  val scriptTextField = TextFieldWithBrowseButton()

  @JvmField
  val moduleField: PySymbolFieldWithBrowseButton

  @JvmField
  val scriptParametersTextField = RawCommandLineEditor()
  lateinit var emulateTerminalCheckbox: JCheckBox
  lateinit var showCommandLineCheckbox: JCheckBox
  lateinit var redirectInputCheckBox: JCheckBox

  @JvmField
  val inputFileTextFieldWithBrowseButton = TextFieldWithBrowseButton()

  @JvmField
  val panel: DialogPanel

  private lateinit var targetPlaceholder: Placeholder

  init {
    val project = configuration.project
    val module = configuration.module
    val sdk = configuration.sdk

    val contentAnchor = if (module == null) ProjectSdkContextAnchor(project, sdk) else ModuleBasedContextAnchor(module)
    val workingDirectory = commonOptionsForm.workingDirectory
    val startFromDirectory: (() -> VirtualFile)? = if (StringUtil.isEmpty(workingDirectory)) {
      null
    }
    else {
      { LocalFileSystem.getInstance().findFileByPath(workingDirectory)!! }
    }

    moduleField = PySymbolFieldWithBrowseButton(contentAnchor,
                                                { element -> (element is PsiFileSystemItem && isPythonModule(element)) },
                                                startFromDirectory)

    panel = panel {
      row {
        cell(targetComboBox)
        targetPlaceholder = placeholder()
          .align(AlignX.FILL)
      }.layout(RowLayout.LABEL_ALIGNED)

      row(PyBundle.message("runcfg.labels.script_parameters")) {
        cell(scriptParametersTextField)
          .align(AlignX.FILL)
      }

      row {
        cell(commonOptionsForm.mainPanel)
          .align(AlignX.FILL)
      }

      collapsibleGroup(PyBundle.message("runcfg.labels.execution")) {
        row {
          emulateTerminalCheckbox = checkBox(PyBundle.message("form.python.run.configuration.emulate.terminal.in.output.console"))
            .selected(true)
            .component
        }
        row {
          showCommandLineCheckbox = checkBox(PyBundle.message("form.python.run.configuration.run.with.python.console"))
            .selected(true)
            .component
        }
        row {
          redirectInputCheckBox = checkBox(PyBundle.message("form.python.run.configuration.redirect.input.from"))
            .gap(RightGap.SMALL)
            .component

          cell(inputFileTextFieldWithBrowseButton)
            .align(AlignX.FILL)
            .enabledIf(redirectInputCheckBox.selected)
            .component
        }
      }.apply {
        expanded = true
      }
    }

    val group = object : ButtonGroup() {
      override fun setSelected(model: ButtonModel?, isSelected: Boolean) {
        if (!isSelected && Objects.equals(selection, model)) {
          clearSelection()
          return
        }
        super.setSelected(model, isSelected)
      }
    }
    group.add(emulateTerminalCheckbox)
    group.add(showCommandLineCheckbox)
    group.add(redirectInputCheckBox)
  }

  fun setModuleMode(moduleMode: Boolean) {
    targetPlaceholder.component = if (moduleMode) moduleField else scriptTextField
  }
}