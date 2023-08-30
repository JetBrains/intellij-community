// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import javax.swing.JComponent

class TerminalFocusModel(private val project: Project,
                         private val outputView: TerminalOutputView,
                         private val promptView: TerminalPromptView) {
  fun focusOutput() {
    requestFocus(outputView.preferredFocusableComponent)
  }

  fun focusPrompt() {
    requestFocus(promptView.preferredFocusableComponent)
  }

  private fun requestFocus(target: JComponent) {
    IdeFocusManager.getInstance(project).requestFocus(target, true)
  }
}