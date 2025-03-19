// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.configuration

import com.intellij.execution.ui.CommandLinePanel
import com.intellij.execution.ui.SettingsEditorFragment
import com.intellij.execution.ui.SettingsEditorFragmentType
import com.intellij.ide.macro.MacrosDialog
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiFileSystemItem
import com.intellij.ui.TextAccessor
import com.intellij.ui.components.fields.ExtendableTextField
import com.jetbrains.python.PySymbolFieldWithBrowseButton
import com.jetbrains.python.extensions.ModuleBasedContextAnchor
import com.jetbrains.python.extensions.ProjectSdkContextAnchor
import com.jetbrains.python.isPythonModule
import com.jetbrains.python.PyBundle
import com.jetbrains.python.run.PythonRunConfiguration
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ItemEvent
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel


class PyScriptOrModuleFragment : SettingsEditorFragment<PythonRunConfiguration, JPanel>(
  "py.script.field", null, null, JPanel(GridBagLayout()), SettingsEditorFragmentType.COMMAND_LINE, null, null, { true }) {

  private val typeNames = listOf("script", "module")
  private val SCRIPT_MODE = 0
  private val MODULE_MODE = 1
  private var currentMode: Int? = null

  val fields: MutableList<JComponent?> = MutableList(MODULE_MODE + 1) { null }
  val hints: MutableMap<JComponent, String> = mutableMapOf()

  private val moduleModeChooser: JComboBox<String>

  init {
    CommandLinePanel.setMinimumWidth(component(), 500)
    moduleModeChooser = JComboBox()
    moduleModeChooser.addItem(typeNames[SCRIPT_MODE])
    moduleModeChooser.addItem(typeNames[MODULE_MODE])
    component().add(moduleModeChooser, GridBagConstraints())
    hints[moduleModeChooser] = PyBundle.message("python.run.configuration.fragments.chooser.hint")

    moduleModeChooser.addItemListener { e ->
      if (e?.stateChange == ItemEvent.SELECTED) {
        if ((e.item as? String) == typeNames[MODULE_MODE]) {
          showField(MODULE_MODE)
          currentMode = MODULE_MODE
        }
        else {
          showField(SCRIPT_MODE)
          currentMode = SCRIPT_MODE
        }
      }
    }
  }

  override fun resetEditorFrom(config: PythonRunConfiguration) {
    if (currentMode == null) {
      initComponents(config)
    }
    val type = if (config.isModuleMode) MODULE_MODE else SCRIPT_MODE
    if (currentMode != type) {
      currentMode = type
      moduleModeChooser.selectedItem = typeNames[type]
      showField(type)
      (fields[type] as TextAccessor).text = config.scriptName
    }
  }

  private fun showField(type: Int) {
    for (i in 0 until fields.size) {
      fields[i]?.isVisible = i == type
    }
  }

  private fun initComponents(config: PythonRunConfiguration) {
    val scriptField = TextFieldWithBrowseButton()
    scriptField.addBrowseFolderListener(TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFileDescriptor(), config.project))
    MacrosDialog.addMacroSupport(scriptField.textField as ExtendableTextField, MacrosDialog.Filters.ALL) { false }
    initComponent(scriptField, SCRIPT_MODE)
    fields[SCRIPT_MODE] = scriptField
    hints[scriptField] = PyBundle.message("python.run.configuration.fragments.script.path.hint")

    val module = config.module
    val contentAnchor = if (module == null) ProjectSdkContextAnchor(config.project, config.sdk) else ModuleBasedContextAnchor(module)
    val workingDirectory = config.workingDirectory
    val moduleField = PySymbolFieldWithBrowseButton(
      contentAnchor,
      { element -> (element is PsiFileSystemItem && isPythonModule(element)) },
      if (StringUtil.isEmpty(workingDirectory)) {
        null
      }
      else {
        { LocalFileSystem.getInstance().findFileByPath(workingDirectory)!! }
      })
    initComponent(moduleField, MODULE_MODE)
    fields[MODULE_MODE] = moduleField
    hints[moduleField] = PyBundle.message("python.run.configuration.fragments.module.name.hint")
  }

  private fun initComponent(field: JComponent, index: Int) {
    val constraints = GridBagConstraints()
    constraints.fill = GridBagConstraints.BOTH
    constraints.weightx = 1.0
    component().add(field, constraints)
    fields[index] = field
  }

  override fun applyEditorTo(s: PythonRunConfiguration) {
    val mode = currentMode
    if (mode == null) return
    s.isModuleMode = mode == MODULE_MODE
    val text = (fields[mode] as? TextAccessor)?.text?.trim()
    if (mode == MODULE_MODE) {
      s.scriptName = text
    }
    else {
      s.scriptName = text?.let { FileUtil.toSystemIndependentName(it) }
    }
  }

  override fun isRemovable(): Boolean = false

  override fun getAllComponents(): Array<JComponent> {
    return fields.filterNotNull().toTypedArray() + moduleModeChooser
  }

  override fun getHint(component: JComponent?): String? {
    return if (component == null) null else hints[component]
  }
}
