// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content
import java.awt.FlowLayout
import java.awt.GridLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class RenameTerminalSessionAction : TerminalSessionContextMenuActionBase() {
  override fun actionPerformed(e: AnActionEvent, activeToolWindow: ToolWindow, selectedContent: Content?) {
    val dialog = RenameTerminalSessionDialog(e.project!!, selectedContent!!.displayName)
    if (dialog.showAndGet()) {
      selectedContent.displayName = dialog.name
    }
  }

  class RenameTerminalSessionDialog(project: Project, name: String) : DialogWrapper(project) {
    private val centerPanel = RenameTerminalSessionPanel()

    public var name
      get() = centerPanel.myNameField.text
      set(value) {
        centerPanel.myNameField.text = value
      }

    init {
      this.name = name
      super.setTitle("Rename Terminal Session")
      init()
    }

    override fun getPreferredFocusedComponent(): JComponent? {
      return centerPanel.myNameField

    }

    override fun doValidate(): ValidationInfo? {
      return if (centerPanel.myNameField.text.isBlank())
        ValidationInfo("Session name should not be blank", centerPanel.myNameField)
      else
        null
    }

    override fun createCenterPanel(): JComponent? {
      return centerPanel
    }
  }
}