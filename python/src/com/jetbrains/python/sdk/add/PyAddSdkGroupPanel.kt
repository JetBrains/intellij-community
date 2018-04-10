/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.sdk.add

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.ui.components.JBRadioButton
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.ButtonGroup
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * @author vlan
 */
class PyAddSdkGroupPanel(name: String,
                         panelIcon: Icon,
                         val panels: List<PyAddSdkPanel>,
                         defaultPanel: PyAddSdkPanel) : PyAddSdkPanel() {
  override val panelName = name
  override val icon = panelIcon
  var selectedPanel: PyAddSdkPanel = defaultPanel
  private val changeListeners: MutableList<Runnable> = mutableListOf()

  override var newProjectPath: String?
    get() = selectedPanel.newProjectPath
    set(value) {
      for (panel in panels) {
        panel.newProjectPath = value
      }
    }

  init {
    layout = BorderLayout()
    val contentPanel = when (panels.size) {
      1 -> panels[0]
      else -> createRadioButtonPanel(panels, defaultPanel)
    }
    add(contentPanel, BorderLayout.NORTH)
  }

  override fun validateAll() = panels.filter { it.isEnabled }.flatMap { it.validateAll() }

  override val sdk: Sdk?
    get() = selectedPanel.sdk

  override fun getOrCreateSdk() = selectedPanel.getOrCreateSdk()

  override fun addChangeListener(listener: Runnable) {
    changeListeners += listener
    for (panel in panels) {
      panel.addChangeListener(listener)
    }
  }

  private fun createRadioButtonPanel(panels: List<PyAddSdkPanel>, defaultPanel: PyAddSdkPanel): JPanel? {
    val buttonMap = panels.map { JBRadioButton(it.panelName) to it }.toMap(linkedMapOf())
    ButtonGroup().apply {
      for (button in buttonMap.keys) {
        add(button)
      }
    }
    val formBuilder = FormBuilder.createFormBuilder()
    for ((button, panel) in buttonMap) {
      panel.border = JBUI.Borders.emptyLeft(30)
      val name: JComponent = panel.nameExtensionComponent?.let {
        JPanel(BorderLayout()).apply {
          val inner = JPanel().apply {
            add(button)
            add(it)
          }
          add(inner, BorderLayout.WEST)
        }
      } ?: button
      formBuilder.addComponent(name)
      formBuilder.addComponent(panel)
      button.addItemListener {
        for (c in panels) {
          UIUtil.setEnabled(c, c == panel, true)
          c.nameExtensionComponent?.let {
            UIUtil.setEnabled(it, c == panel, true)
          }
        }
        if (button.isSelected) {
          selectedPanel = panel
          for (listener in changeListeners) {
            listener.run()
          }
        }
      }
    }
    buttonMap.filterValues { it == defaultPanel }.keys.first().isSelected = true
    return formBuilder.panel
  }
}