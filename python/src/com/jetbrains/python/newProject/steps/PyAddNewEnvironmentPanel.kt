// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProject.steps

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.sdk.PySdkProvider
import com.jetbrains.python.sdk.PySdkSettings
import com.jetbrains.python.sdk.add.v1.PyAddNewCondaEnvPanel
import com.jetbrains.python.sdk.add.PyAddNewEnvPanel
import com.jetbrains.python.sdk.add.v1.PyAddNewVirtualEnvPanel
import com.jetbrains.python.sdk.add.PyAddSdkPanel
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMode
import com.jetbrains.python.sdk.conda.PyCondaSdkCustomizer
import java.awt.BorderLayout
import java.awt.event.ItemEvent
import javax.swing.JComboBox

internal class PyAddNewEnvironmentPanel internal constructor(existingSdks: List<Sdk>, newProjectPath: String?, preferredInterpreterType: PythonInterpreterSelectionMode? = null) : PyAddSdkPanel() {

  @Deprecated(message = "Use constructor without ignored")
  constructor(existingSdks: List<Sdk>, newProjectPath: String?, @Suppress("UNUSED_PARAMETER") ignored: String?) : this(existingSdks, newProjectPath, preferredInterpreterType = null)

  @Suppress("unused") // Will be used by third party plugins
  constructor(existingSdks: List<Sdk>, newProjectPath: String) : this(existingSdks, newProjectPath, preferredInterpreterType = null)

  override val panelName: String get() = com.jetbrains.python.PyBundle.message("python.add.sdk.panel.name.new.environment.using")
  override val nameExtensionComponent: JComboBox<PyAddNewEnvPanel>

  override var newProjectPath: String? = newProjectPath
    set(value) {
      field = value
      for (panel in panels) {
        panel.newProjectPath = newProjectPath
      }
    }
  private val context: UserDataHolder = UserDataHolderBase()

  // TODO: Introduce a method in PyAddSdkProvider or in a Python SDK Provider
  private val panels = createPanels(existingSdks, newProjectPath)
  var selectedPanel: PyAddNewEnvPanel = panels.find { it.preferredType == (preferredInterpreterType ?: PySdkSettings.instance.preferredEnvironmentType) }
                                        ?: panels[0]

  private val listeners = mutableListOf<Runnable>()

  init {
    nameExtensionComponent = ComboBox(panels.toTypedArray()).apply {
      renderer = SimpleListCellRenderer.create { label, value, _ ->
        label.text = value?.envName ?: return@create
        label.icon = value.icon
      }
      selectedItem = selectedPanel
      addItemListener {
        if (it.stateChange == ItemEvent.SELECTED) {
          val selected = it.item as? PyAddSdkPanel ?: return@addItemListener
          for (panel in panels) {
            val isSelected = panel == selected
            panel.isVisible = isSelected
            if (isSelected) {
              selectedPanel = panel
            }
          }
          for (listener in listeners) {
            listener.run()
          }
        }
      }
    }

    layout = BorderLayout()
    val formBuilder = FormBuilder.createFormBuilder().apply {
      for (panel in panels) {
        addComponent(panel)
        panel.isVisible = panel == selectedPanel
      }
    }
    add(formBuilder.panel, BorderLayout.NORTH)
  }

  override fun getOrCreateSdk(): Sdk? {
    val createdSdk = selectedPanel.getOrCreateSdk()
    PySdkSettings.instance.preferredEnvironmentType = selectedPanel.envName
    return createdSdk
  }

  override fun getStatisticInfo(): InterpreterStatisticsInfo? {
    return selectedPanel.getStatisticInfo();
  }

  override fun validateAll(): List<ValidationInfo> = selectedPanel.validateAll()

  override fun addChangeListener(listener: Runnable) {
    for (panel in panels) {
      panel.addChangeListener(listener)
    }
    listeners += listener
  }

  private fun createPanels(existingSdks: List<Sdk>, newProjectPath: String?): List<PyAddNewEnvPanel> {
    val condaPanel = PyAddNewCondaEnvPanel(null, null, existingSdks, newProjectPath)
    val venvPanel = PyAddNewVirtualEnvPanel(null, null, existingSdks, newProjectPath, context)

    val envPanelsFromProviders = PySdkProvider.EP_NAME.extensionList
      .mapNotNull { it.createNewEnvironmentPanel(null, null, existingSdks, newProjectPath, context) }
      .toTypedArray()

    return if (PyCondaSdkCustomizer.instance.preferCondaEnvironments) {
      listOf(condaPanel, venvPanel, *envPanelsFromProviders)
    }
    else {
      listOf(venvPanel, *envPanelsFromProviders, condaPanel)
    }
  }
}
