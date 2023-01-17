// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.util.Disposer
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class TerminalBlocksContainer(private val project: Project) : JPanel(), ComponentContainer {
  private val promptPanel: TerminalPromptPanel

  init {
    val commandExecutor = object : TerminalCommandExecutor {
      override fun startCommandExecution(command: String) {

      }
    }
    promptPanel = TerminalPromptPanel(project, commandExecutor)
    Disposer.register(this, promptPanel)

    layout = BorderLayout()
    add(promptPanel, BorderLayout.CENTER)
  }

  fun isFocused(): Boolean {
    return promptPanel.isFocused()
  }

  override fun getComponent(): JComponent = this

  override fun getPreferredFocusableComponent(): JComponent = promptPanel.preferredFocusableComponent

  override fun dispose() {

  }
}