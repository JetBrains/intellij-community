// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent
import javax.swing.JPopupMenu
import javax.swing.MenuSelectionManager

@ApiStatus.Internal
class TerminalFocusModel(
  private val project: Project,
  private val terminalView: BlockTerminalView,
) {
  /** True, if focus is inside the terminal component */
  var isActive: Boolean = true
    private set(value) {
      if (value != field) {
        field = value
        listeners.forEach { it.activeStateChanged(value) }
      }
    }

  private val listeners: MutableList<TerminalFocusListener> = CopyOnWriteArrayList()

  init {
    val listener = AWTEventListener {
      if (UIUtil.isFocusAncestor(terminalView.component)) {
        isActive = true  // a simple case - focused component is descendant of terminal parent component
      }
      else {
        // consider active if a menu is invoked on some component inside the terminal
        val menu = MenuSelectionManager.defaultManager().selectedPath.firstOrNull() as? JPopupMenu
        isActive = UIUtil.isDescendingFrom(menu, terminalView.component)
      }
    }
    Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.FOCUS_EVENT_MASK)
    Disposer.register(terminalView) {
      Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
    }

    // Set the prompt view listener later, because prompt view is not initialized yet at this moment
    invokeLater {
      terminalView.promptView.preferredFocusableComponent.addFocusListener(object : FocusAdapter() {
        override fun focusGained(e: FocusEvent?) {
          listeners.forEach { it.promptFocused() }
        }
      })
    }
  }

  @RequiresEdt
  fun focusOutput() {
    requestFocus(terminalView.outputView.preferredFocusableComponent)
  }

  @RequiresEdt
  fun focusPrompt() {
    requestFocus(terminalView.promptView.preferredFocusableComponent)
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

    fun activeStateChanged(isActive: Boolean) {}
  }

  companion object {
    val KEY: DataKey<TerminalFocusModel> = DataKey.create("TerminalFocusModel")
  }
}
