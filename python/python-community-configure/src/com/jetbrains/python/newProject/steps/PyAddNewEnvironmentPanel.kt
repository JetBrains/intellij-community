// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.newProject.steps

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.sdk.PySdkSettings
import com.jetbrains.python.sdk.add.PyAddNewCondaEnvPanel
import com.jetbrains.python.sdk.add.PyAddNewEnvPanel
import com.jetbrains.python.sdk.add.PyAddNewVirtualEnvPanel
import com.jetbrains.python.sdk.add.PyAddSdkPanel
import java.awt.BorderLayout
import java.awt.event.ItemEvent
import javax.swing.JComboBox
import javax.swing.JList

/**
 * @author vlan
 */
class PyAddNewEnvironmentPanel(existingSdks: List<Sdk>, newProjectPath: String?, preferredType: String?) : PyAddSdkPanel() {
  override val panelName = "New environment using"
  override val nameExtensionComponent: JComboBox<PyAddNewEnvPanel>

  override var newProjectPath: String? = newProjectPath
    set(value) {
      field = value
      for (panel in panels) {
        panel.newProjectPath = newProjectPath
      }
    }

  private val panels = listOf(PyAddNewVirtualEnvPanel(null, existingSdks, newProjectPath),
                              PyAddNewCondaEnvPanel(null, existingSdks, newProjectPath))

  var selectedPanel = panels.find { it.envName == preferredType ?: PySdkSettings.instance.preferredEnvironmentType } ?: panels[0]

  private val listeners = mutableListOf<Runnable>()

  init {
    nameExtensionComponent = JComboBox(panels.toTypedArray()).apply {
      renderer = object : ColoredListCellRenderer<PyAddNewEnvPanel>() {
        override fun customizeCellRenderer(list: JList<out PyAddNewEnvPanel>, value: PyAddNewEnvPanel?, index: Int, selected: Boolean,
                                           hasFocus: Boolean) {
          val panel = value ?: return
          append(panel.envName)
          icon = panel.icon
        }
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

  override fun validateAll() = selectedPanel.validateAll()

  override fun addChangeListener(listener: Runnable) {
    for (panel in panels) {
      panel.addChangeListener(listener)
    }
    listeners += listener
  }
}
