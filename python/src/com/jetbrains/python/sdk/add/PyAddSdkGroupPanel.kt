// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBRadioButton
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.util.function.Supplier
import javax.swing.ButtonGroup
import javax.swing.Icon
import javax.swing.JPanel
import kotlin.collections.iterator

class PyAddSdkGroupPanel(private val nameGetter: Supplier<@Nls String>,
                         panelIcon: Icon,
                         val panels: List<PyAddSdkPanel>,
                         defaultPanel: PyAddSdkPanel
) : PyAddSdkPanel() {
  override val panelName: String get() = nameGetter.get() // NON-NLS
  override val icon: Icon = panelIcon
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

  override fun validateAll(): List<ValidationInfo> = panels.filter { it.isEnabled }.flatMap { it.validateAll() }

  override val sdk: Sdk?
    get() = selectedPanel.sdk

  override fun getOrCreateSdk(): Sdk? = selectedPanel.getOrCreateSdk()

  override fun addChangeListener(listener: Runnable) {
    changeListeners += listener
    for (panel in panels) {
      panel.addChangeListener(listener)
    }
  }

  private fun createRadioButtonPanel(panels: List<PyAddSdkPanel>, defaultPanel: PyAddSdkPanel): JPanel {
    val buttonMap = panels.map { JBRadioButton(it.panelName) to it }.toMap(linkedMapOf())
    ButtonGroup().apply {
      for (button in buttonMap.keys) {
        add(button)
      }
    }
    val formBuilder = FormBuilder.createFormBuilder()
    for ((button, panel) in buttonMap) {
      panel.border = JBUI.Borders.emptyLeft(30)
      val name = JPanel(BorderLayout()).apply {
        val inner = JPanel().apply {
          add(button)
          panel.nameExtensionComponent?.also { add(it) }
        }
        add(inner, BorderLayout.WEST)
      }
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