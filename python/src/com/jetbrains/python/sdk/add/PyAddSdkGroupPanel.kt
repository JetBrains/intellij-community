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

import com.intellij.ui.components.JBRadioButton
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.ButtonGroup
import javax.swing.Icon
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
  private var selectedPanel: PyAddSdkPanel? = null

  init {
    layout = BorderLayout()
    add(createRadioButtonPanel(panels, defaultPanel), BorderLayout.NORTH)
  }

  override fun validateAll() = panels.filter { it.isEnabled }.flatMap { it.validateAll() }

  override fun getOrCreateSdk() = selectedPanel?.getOrCreateSdk()

  private fun createRadioButtonPanel(panels: List<PyAddSdkPanel>, defaultPanel: PyAddSdkPanel): JPanel? {
    val radioButtons = panels.map { JBRadioButton(it.panelName) to it }.toMap(linkedMapOf())
    ButtonGroup().apply {
      for (button in radioButtons.keys) {
        add(button)
      }
    }
    val formBuilder = FormBuilder.createFormBuilder()
    for ((button, panel) in radioButtons) {
      panel.border = JBUI.Borders.emptyLeft(30)
      formBuilder.addComponent(button)
      formBuilder.addComponent(panel)
      button.addItemListener {
        selectedPanel = panel
        for (c in panels) {
          UIUtil.setEnabled(c, c == panel, true)
        }
      }
    }
    selectedPanel = defaultPanel
    radioButtons.filterValues { it == defaultPanel }.keys.first().isSelected = true
    return formBuilder.panel
  }
}