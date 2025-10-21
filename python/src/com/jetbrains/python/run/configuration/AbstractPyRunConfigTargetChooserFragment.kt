// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.configuration

import com.intellij.execution.ui.CommandLinePanel
import com.intellij.execution.ui.SettingsEditorFragment
import com.intellij.execution.ui.SettingsEditorFragmentType
import com.intellij.ide.macro.MacrosDialog
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiFileSystemItem
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExtendableTextField
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PySymbolFieldWithBrowseButton
import com.jetbrains.python.extensions.ModuleBasedContextAnchor
import com.jetbrains.python.extensions.ProjectSdkContextAnchor
import com.jetbrains.python.isPythonModule
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant
import org.jetbrains.annotations.ApiStatus
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ItemEvent
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel


@ApiStatus.Internal
abstract class AbstractPyRunConfigTargetChooserFragment<T: AbstractPythonRunConfiguration<T>> :
  SettingsEditorFragment<T, JPanel>(
    "python.config.target.field",
    null,
    null,
    JPanel(GridBagLayout()),
    SettingsEditorFragmentType.COMMAND_LINE,
    null,
    null,
    { true }
) {
  private val minWidth = 500
  val typeNames = listOf("script", "module", "custom")
  val SCRIPT_MODE = 0
  private val MODULE_MODE = 1
  private val CUSTOM_MODE = 2
  var currentMode: Int? = null

  val modeToPyRunTargetVariant: Map<Int, PyRunTargetVariant> = mapOf(
    SCRIPT_MODE to PyRunTargetVariant.PATH,
    MODULE_MODE to PyRunTargetVariant.PYTHON,
    CUSTOM_MODE to PyRunTargetVariant.CUSTOM
  )

  val runTargetVariantToMode: Map<PyRunTargetVariant, Int> = mapOf(
    PyRunTargetVariant.PYTHON to MODULE_MODE,
    PyRunTargetVariant.PATH to SCRIPT_MODE,
    PyRunTargetVariant.CUSTOM to CUSTOM_MODE
  )

  val fields: MutableList<JComponent?> = MutableList(CUSTOM_MODE + 1) { null }
  val hints: MutableMap<JComponent, String> = mutableMapOf()

  val moduleModeChooser: JComboBox<String>

  init {
    CommandLinePanel.setMinimumWidth(component(), minWidth)
    moduleModeChooser = JComboBox()
    moduleModeChooser.addItem(typeNames[SCRIPT_MODE])
    moduleModeChooser.addItem(typeNames[MODULE_MODE])
    moduleModeChooser.addItem(typeNames[CUSTOM_MODE])

    component().add(moduleModeChooser, GridBagConstraints())
    hints[moduleModeChooser] = PyBundle.message("python.run.configuration.fragments.chooser.hint")

    moduleModeChooser.addItemListener { e ->
      if (e?.stateChange == ItemEvent.SELECTED) {
        when (e.item as? String) {
          typeNames[MODULE_MODE] -> {
            showField(MODULE_MODE)
            currentMode = MODULE_MODE
          }
          typeNames[SCRIPT_MODE] -> {
            showField(SCRIPT_MODE)
            currentMode = SCRIPT_MODE
          }
          typeNames[CUSTOM_MODE] -> {
            showField(CUSTOM_MODE)
            currentMode = CUSTOM_MODE
          }
        }
      }
    }
  }

  fun showField(type: Int) {
    for (i in 0 until fields.size) {
      fields[i]?.isVisible = i == type
    }
  }

  fun initComponents(config: T) {
    val scriptField = TextFieldWithBrowseButton()
    scriptField.addBrowseFolderListener(
      TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFileDescriptor(),
                               config.project)
    )
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

    val customField = JBTextField()
    initComponent(customField, CUSTOM_MODE)
    hints[customField] = PyBundle.message("python.run.configuration.fragments.custom.field.hint")
  }

  private fun initComponent(field: JComponent, index: Int) {
    val constraints = GridBagConstraints()
    constraints.fill = GridBagConstraints.BOTH
    constraints.weightx = 1.0
    component().add(field, constraints)
    fields[index] = field
  }

  override fun isRemovable(): Boolean = false

  override fun getAllComponents(): Array<JComponent> {
    return fields.filterNotNull().toTypedArray() + moduleModeChooser
  }

  override fun getHint(component: JComponent?): String? {
    return if (component == null) null else hints[component]
  }

  abstract override fun applyEditorTo(s: T)

  abstract override fun resetEditorFrom(config: T)
}