// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.configuration

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.ui.SettingsEditorFragment
import com.intellij.execution.ui.SettingsEditorFragmentType
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.RawCommandLineEditor
import com.jetbrains.python.PyBundle
import com.jetbrains.python.run.PythonRunConfiguration
import java.awt.BorderLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

class PythonConfigurationFragmentedEditor(runConfiguration: PythonRunConfiguration) :
  AbstractPythonConfigurationFragmentedEditor<PythonRunConfiguration>(runConfiguration) {

  override fun customizeFragments(fragments: MutableList<SettingsEditorFragment<PythonRunConfiguration, *>>) {
    fragments.add(PyScriptOrModuleFragment())

    val scriptParametersFragment: SettingsEditorFragment<PythonRunConfiguration, RawCommandLineEditor> = SettingsEditorFragment<PythonRunConfiguration, RawCommandLineEditor>(
      "py.script.parameters",
      PyBundle.message("python.run.configuration.fragments.script.parameters"),
      PyBundle.message("python.run.configuration.fragments.python.group"),
      RawCommandLineEditor(), SettingsEditorFragmentType.COMMAND_LINE,
      { config: PythonRunConfiguration, field: RawCommandLineEditor -> field.text = config.scriptParameters },
      { config: PythonRunConfiguration, field: RawCommandLineEditor -> config.scriptParameters = field.text.trim() },
      { config: PythonRunConfiguration -> !config.scriptParameters.trim().isEmpty() })
    scriptParametersFragment.setHint(PyBundle.message("python.run.configuration.fragments.script.parameters.hint"))
    scriptParametersFragment.actionHint = PyBundle.message("python.run.configuration.fragments.script.parameters.hint")
    fragments.add(scriptParametersFragment)


    val runWithConsole = SettingsEditorFragment.createTag<PythonRunConfiguration>(
      "py.run.with.python.console",
      PyBundle.message("python.run.configuration.fragments.run.with.python.console"),
      PyBundle.message("python.run.configuration.fragments.python.group"),
      { it.showCommandLineAfterwards() },
      { config, value -> config.setShowCommandLineAfterwards(value) })
    runWithConsole.actionHint = PyBundle.message("python.run.configuration.fragments.run.with.python.console.hint")
    fragments.add(runWithConsole)

    val emulateTerminal = SettingsEditorFragment.createTag<PythonRunConfiguration>(
      "py.emulate.terminal",
      PyBundle.message("python.run.configuration.fragments.emulate.terminal"),
      PyBundle.message("python.run.configuration.fragments.python.group"),
      { it.emulateTerminal() },
      { config, value -> config.setEmulateTerminal(value) })
    emulateTerminal.actionHint = PyBundle.message("python.run.configuration.fragments.emulate.terminal.hint")
    fragments.add(emulateTerminal)

    val inputFile = TextFieldWithBrowseButton()
    inputFile.addBrowseFolderListener(TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFileDescriptor(),
                                                               runConfiguration.project))
    val labeledComponent = LabeledComponent.create<TextFieldWithBrowseButton>(inputFile, ExecutionBundle.message("redirect.input.from"))
    labeledComponent.labelLocation = BorderLayout.WEST
    val redirectInputFrom: SettingsEditorFragment<PythonRunConfiguration, LabeledComponent<TextFieldWithBrowseButton>> =
      SettingsEditorFragment<PythonRunConfiguration, LabeledComponent<TextFieldWithBrowseButton>>(
        "py.redirect.input",
        ExecutionBundle.message("redirect.input.from.name"),
        ExecutionBundle.message("group.operating.system"),
        labeledComponent, SettingsEditorFragmentType.COMMAND_LINE,
        { config: PythonRunConfiguration, component: LabeledComponent<TextFieldWithBrowseButton> ->
          component.component.text = config.inputFile
        },
        { config: PythonRunConfiguration, component: LabeledComponent<TextFieldWithBrowseButton> ->
          val filePath = component.component.text
          config.isRedirectInput = component.isVisible && StringUtil.isNotEmpty(filePath)
          config.inputFile = filePath
        },
        { config: PythonRunConfiguration -> config.isRedirectInput })
    redirectInputFrom.actionHint = ExecutionBundle.message("read.input.from.the.specified.file")
    redirectInputFrom.setHint(ExecutionBundle.message("read.input.from.the.specified.file"))
    fragments.add(redirectInputFrom)

    val editors = mutableListOf<SettingsEditorFragment<PythonRunConfiguration, *>>()
    editors.add(runWithConsole)
    editors.add(emulateTerminal)
    editors.add(redirectInputFrom)
    addSingleSelectionListeners(editors)
  }

  private fun addSingleSelectionListeners(editors: MutableList<SettingsEditorFragment<PythonRunConfiguration, *>>) {
    for ((i, editor) in editors.withIndex()) {
      editor.component().addComponentListener(object : ComponentAdapter() {
        override fun componentShown(e: ComponentEvent?) {
          for ((j, otherEditor) in editors.withIndex()) {
            if (i != j) {
              otherEditor.isSelected = false
            }
          }
        }
      })
    }
  }
}