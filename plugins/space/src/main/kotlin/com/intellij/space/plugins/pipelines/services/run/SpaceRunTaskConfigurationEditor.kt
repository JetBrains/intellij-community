// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.plugins.pipelines.services.run

import com.intellij.openapi.options.SettingsEditor
import com.intellij.space.messages.SpaceBundle
import com.intellij.ui.components.JBTextField
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class SpaceRunTaskConfigurationEditor : SettingsEditor<SpaceRunConfiguration>() {
  private val taskNameField = JBTextField()
  override fun resetEditorFrom(s: SpaceRunConfiguration) {
    val options = s.options
    taskNameField.text = options.taskName
  }

  override fun createEditor(): JComponent {
    val panel = JPanel()
    panel.layout = BoxLayout(panel, BoxLayout.LINE_AXIS)
    panel.add(JLabel(SpaceBundle.message("run.configuration.editor.task.name.label")))
    panel.add(taskNameField)
    return panel
  }

  override fun applyEditorTo(s: SpaceRunConfiguration) {
    val options = s.options
    options.taskName = taskNameField.text
  }
}
