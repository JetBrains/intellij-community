// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.util.addFocusListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.event.FocusListener
import javax.swing.JComponent

class TerminalFocusModel(private val project: Project,
                         private val outputView: TerminalOutputView,
                         private val promptView: TerminalPromptView) {
  @RequiresEdt
  fun focusOutput() {
    requestFocus(outputView.preferredFocusableComponent)
  }

  @RequiresEdt
  fun focusPrompt() {
    requestFocus(promptView.preferredFocusableComponent)
  }

  @RequiresEdt
  fun addPromptFocusListener(focusListener: FocusListener, disposable: Disposable? = null) {
    promptView.preferredFocusableComponent.addFocusListener(disposable, focusListener)
  }

  private fun requestFocus(target: JComponent) {
    if (!target.hasFocus()) {
      IdeFocusManager.getInstance(project).requestFocus(target, true)
    }
  }
}