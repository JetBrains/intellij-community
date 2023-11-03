// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent

class TerminalFocusModel(private val project: Project,
                         private val outputView: TerminalOutputView,
                         private val promptView: TerminalPromptView) {
  private val listeners: MutableList<TerminalFocusListener> = CopyOnWriteArrayList()

  init {
    promptView.preferredFocusableComponent.addFocusListener(object : FocusAdapter() {
      override fun focusGained(e: FocusEvent?) {
        listeners.forEach { it.promptFocused() }
      }
    })
  }

  @RequiresEdt
  fun focusOutput() {
    requestFocus(outputView.preferredFocusableComponent)
  }

  @RequiresEdt
  fun focusPrompt() {
    requestFocus(promptView.preferredFocusableComponent)
  }

  fun addListener(listener: TerminalFocusListener, disposable: Disposable? = null) {
    listeners.add(listener)
    if (disposable != null) {
      Disposer.register(disposable) {
        listeners.remove(listener)
      }
    }
  }

  private fun requestFocus(target: JComponent) {
    if (!target.hasFocus()) {
      IdeFocusManager.getInstance(project).requestFocus(target, true)
    }
  }

  interface TerminalFocusListener {
    fun promptFocused() {}
  }
}