// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.impl.run

import com.intellij.application.options.ModulesComboBox
import com.intellij.execution.ui.CommandLinePanel
import com.intellij.execution.ui.SettingsEditorFragment
import com.intellij.execution.ui.SettingsEditorFragmentType
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.ui.ComboBox
import com.jetbrains.python.PyBundle
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.configuration.PyInterpreterModeNotifier
import com.jetbrains.python.run.configuration.PySdkComboBox
import com.jetbrains.python.sdk.PythonSdkUtil
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ItemEvent
import java.util.function.Consumer
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

class PyPluginSdkFragment<T : AbstractPythonRunConfiguration<*>> : SettingsEditorFragment<T, JPanel>(
  "py.plugin.interpreter", null, null, JPanel(GridBagLayout()), SettingsEditorFragmentType.COMMAND_LINE, null, null,
  { true }), PyInterpreterModeNotifier {

  private val typeNames = listOf(PyBundle.message("python.run.configuration.fragments.plugin.sdk.of.module"),
                                 PyBundle.message("python.run.configuration.fragments.plugin.specified.interpreter"))
  private val SDK_OF_MODULE = 0
  private val SDK_FROM_LIST = 1
  private var currentMode: Int? = null

  private var modulesCombo: ModulesComboBox? = null
  private var interpreterCombo: PySdkComboBox? = null

  val fields: MutableList<JComponent?> = MutableList(SDK_FROM_LIST + 1) { null }
  private val hints: MutableMap<JComponent, String> = mutableMapOf()
  private val sdkChooser: JComboBox<String>
  private val interpreterModeListeners: MutableList<Consumer<Boolean>> = mutableListOf()

  init {
    sdkChooser = JComboBox()
    sdkChooser.addItem(typeNames[SDK_OF_MODULE])
    sdkChooser.addItem(typeNames[SDK_FROM_LIST])
    CommandLinePanel.setMinimumWidth(sdkChooser, 200)
    component().add(sdkChooser, GridBagConstraints())
    hints[sdkChooser] = PyBundle.message("python.run.configuration.fragments.plugin.sdk.chooser.hint")

    sdkChooser.addItemListener { e ->
      if (e?.stateChange == ItemEvent.SELECTED) {
        if ((e.item as? String) == typeNames[SDK_FROM_LIST]) {
          showField(SDK_FROM_LIST)
          currentMode = SDK_FROM_LIST
        }
        else {
          showField(SDK_OF_MODULE)
          currentMode = SDK_OF_MODULE
        }
      }
    }
    sdkChooser.addActionListener { updateRemoteInterpreterMode() }
  }

  override fun resetEditorFrom(config: T) {
    if (currentMode == null) {
      initComponents(config)
    }
    val type = if (config.isUseModuleSdk) SDK_OF_MODULE else SDK_FROM_LIST
    if (currentMode != type) {
      sdkChooser.selectedItem = typeNames[type]
      (fields[SDK_OF_MODULE] as? ComboBox<*>)?.selectedItem = config.module
      interpreterCombo?.reset(config)
      showField(type)
    }
    updateRemoteInterpreterMode()
  }

  private fun showField(type: Int) {
    for (i in 0 until fields.size) {
      fields[i]?.isVisible = i == type
    }
  }

  private fun initComponents(config: T) {
    val modulesComponent = ModulesComboBox()
    initComponent(modulesComponent, SDK_OF_MODULE)
    val validModules: List<Module> = config.commonOptionsFormData.validModules
    modulesComponent.setModules(validModules)
    modulesComponent.selectedModule = config.module
    fields[SDK_OF_MODULE] = modulesComponent
    modulesCombo = modulesComponent
    CommandLinePanel.setMinimumWidth(modulesCombo, 400)
    hints[modulesComponent] = PyBundle.message("python.run.configuration.fragments.plugin.sdk.of.module.hint")
    modulesCombo?.addActionListener { updateRemoteInterpreterMode() }

    val interpreterComponent = PySdkComboBox(false) { modulesComponent.selectedModule }
    initComponent(interpreterComponent, SDK_FROM_LIST)
    interpreterComponent.reset(config)
    fields[SDK_FROM_LIST] = interpreterComponent
    interpreterCombo = interpreterComponent
    CommandLinePanel.setMinimumWidth(interpreterCombo, 400)
    hints[interpreterComponent] = PyBundle.message("python.run.configuration.fragments.plugin.specified.interpreter.hint")
    interpreterCombo?.addActionListener { updateRemoteInterpreterMode() }
  }

  private fun initComponent(field: JComponent, index: Int) {
    val constraints = GridBagConstraints()
    constraints.fill = GridBagConstraints.BOTH
    constraints.weightx = 1.0
    component().add(field, constraints)
    fields[index] = field
  }

  override fun applyEditorTo(s: T) {
    if (currentMode == SDK_OF_MODULE) {
      s.isUseModuleSdk = true
      val module = (modulesCombo?.selectedItem) as? Module
      s.module = module
      module?.let {
        val moduleSdk = ModuleRootManager.getInstance(it).sdk
        s.setSdk(moduleSdk)
      }
    }
    else if (currentMode == SDK_FROM_LIST) {
      s.isUseModuleSdk = false
      interpreterCombo?.apply(s)
    }
  }

  override fun isRemovable(): Boolean = false

  override fun getAllComponents(): Array<JComponent> {
    return fields.filterNotNull().toTypedArray() + sdkChooser
  }

  override fun getHint(component: JComponent?): String? {
    return if (component == null) null else hints[component]
  }

  override fun isRemoteSelected(): Boolean = PythonSdkUtil.isRemote(getSelectedSdk())

  override fun addInterpreterModeListener(listener: Consumer<Boolean>) {
    interpreterModeListeners.add(listener)
  }

  private fun updateRemoteInterpreterMode() {
    val isRemote = isRemoteSelected()
    for (listener in interpreterModeListeners) {
      listener.accept(isRemote)
    }
  }

  private fun getSelectedSdk(): Sdk? {
    if (currentMode == SDK_OF_MODULE) {
      ((modulesCombo?.selectedItem) as? Module)?.let {
        return ModuleRootManager.getInstance(it).sdk
      }
    }
    else if (currentMode == SDK_FROM_LIST) {
      return interpreterCombo?.getSelectedSdk()
    }
    return null
  }
}