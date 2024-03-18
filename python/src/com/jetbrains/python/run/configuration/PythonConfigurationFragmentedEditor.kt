// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.configuration

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.ui.CommandLinePanel
import com.intellij.execution.ui.SettingsEditorFragment
import com.intellij.execution.ui.SettingsEditorFragmentType
import com.intellij.ide.macro.MacrosDialog
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.components.TextComponentEmptyText
import com.jetbrains.python.PyBundle
import com.jetbrains.python.run.PythonRunConfiguration
import java.awt.BorderLayout

class PythonConfigurationFragmentedEditor(runConfiguration: PythonRunConfiguration) :
  AbstractPythonConfigurationFragmentedEditor<PythonRunConfiguration>(runConfiguration) {

  override fun customizeFragments(fragments: MutableList<SettingsEditorFragment<PythonRunConfiguration, *>>) {
    fragments.add(PyScriptOrModuleFragment())

    val parametersEditor = RawCommandLineEditor()
    CommandLinePanel.setMinimumWidth(parametersEditor, MIN_FRAGMENT_WIDTH)
    val scriptParametersFragment: SettingsEditorFragment<PythonRunConfiguration, RawCommandLineEditor> = SettingsEditorFragment<PythonRunConfiguration, RawCommandLineEditor>(
      "py.script.parameters",
      PyBundle.message("python.run.configuration.fragments.script.parameters"),
      PyBundle.message("python.run.configuration.fragments.python.group"),
      parametersEditor, SettingsEditorFragmentType.COMMAND_LINE,
      { config: PythonRunConfiguration, field: RawCommandLineEditor -> field.text = config.scriptParameters },
      { config: PythonRunConfiguration, field: RawCommandLineEditor -> config.scriptParameters = field.text.trim() },
      { true })
    MacrosDialog.addMacroSupport(parametersEditor.editorField, MacrosDialog.Filters.ALL) { false }
    parametersEditor.editorField.emptyText.setText(PyBundle.message("python.run.configuration.fragments.script.parameters.hint"))
    TextComponentEmptyText.setupPlaceholderVisibility(parametersEditor.editorField)
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
        labeledComponent,
        SettingsEditorFragmentType.EDITOR,
        { config, component ->
          component.component.text = config.inputFile
        },
        { config, component ->
          val filePath = component.component.text
          config.isRedirectInput = component.isVisible && StringUtil.isNotEmpty(filePath)
          config.inputFile = filePath
        },
        { config -> config.isRedirectInput })
    redirectInputFrom.actionHint = ExecutionBundle.message("read.input.from.the.specified.file")
    addToFragmentsBeforeEditors(fragments, redirectInputFrom)

    val editors = mutableListOf<SettingsEditorFragment<PythonRunConfiguration, *>>()
    editors.add(runWithConsole)
    editors.add(emulateTerminal)
    editors.add(redirectInputFrom)
    addSingleSelectionListeners(editors)
  }
}
