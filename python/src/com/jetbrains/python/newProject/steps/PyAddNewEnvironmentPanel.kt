// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.newProject.steps

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.sdk.PySdkProvider
import com.jetbrains.python.sdk.PySdkSettings
import com.jetbrains.python.sdk.add.PyAddNewCondaEnvPanel
import com.jetbrains.python.sdk.add.PyAddNewEnvPanel
import com.jetbrains.python.sdk.add.PyAddNewVirtualEnvPanel
import com.jetbrains.python.sdk.add.PyAddSdkPanel
import com.jetbrains.python.sdk.conda.PyCondaSdkCustomizer
import java.awt.BorderLayout
import java.awt.event.ItemEvent
import javax.swing.JComboBox
import kotlin.streams.toList

/**
 * @author vlan
 */
class PyAddNewEnvironmentPanel(existingSdks: List<Sdk>, newProjectPath: String?, preferredType: String?) : PyAddSdkPanel() {
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
  var selectedPanel: PyAddNewEnvPanel = panels.find { it.envName == preferredType ?: PySdkSettings.instance.preferredEnvironmentType } ?: panels[0]

  private val listeners = mutableListOf<Runnable>()

  init {
    nameExtensionComponent = ComboBox(panels.toTypedArray()).apply {
      renderer = SimpleListCellRenderer.create {label, value, _ ->
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

    val envPanelsFromProviders = PySdkProvider.EP_NAME.extensions()
      .map { it.createNewEnvironmentPanel(null, null, existingSdks, newProjectPath, context) }
      .toList().toTypedArray()

    return if (PyCondaSdkCustomizer.instance.preferCondaEnvironments) {
      listOf(condaPanel, venvPanel, *envPanelsFromProviders)
    }
    else {
      listOf(venvPanel, *envPanelsFromProviders, condaPanel)
    }
  }
}
