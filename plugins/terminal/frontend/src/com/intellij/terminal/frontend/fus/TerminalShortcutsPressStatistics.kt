// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.fus

import com.intellij.openapi.project.Project
import com.intellij.terminal.frontend.toolwindow.impl.getRunningProcessExecutableForFus
import com.intellij.terminal.frontend.view.TerminalKeyEvent
import com.intellij.terminal.frontend.view.TerminalKeyEventsListener
import com.intellij.terminal.frontend.view.TerminalView
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

/**
 * Reports to FUS each key press in the terminal that types no character.
 * For example, Ctrl+C, Shift+Tab, Shift+Enter, Escape, or an arrow key.
 * Does not report unmodified Enter, Tab, or Backspace because these keys occur frequently.
 */
internal class TerminalShortcutsPressStatistics(
  private val project: Project,
  private val terminalView: TerminalView,
) : TerminalKeyEventsListener {
  override fun afterKeyEvent(event: TerminalKeyEvent) {
    val keyEvent = event.awtEvent
    if (isIgnoredShortcut(keyEvent)) return
    if (isShortcut(keyEvent)) {
      ReworkedTerminalUsageCollector.logShortcutPressed(project, keyEvent, terminalView.getRunningProcessExecutableForFus())
    }
  }

  private fun isShortcut(e: KeyEvent): Boolean {
    if (e.id != KeyEvent.KEY_PRESSED || e.keyCode in MODIFIER_KEY_CODES) return false
    return e.modifiersEx and SHORTCUT_MODIFIERS_MASK != 0 || e.keyChar == KeyEvent.CHAR_UNDEFINED || e.keyChar.isISOControl()
  }

  private fun isIgnoredShortcut(e: KeyEvent): Boolean {
    return (e.id == KeyEvent.KEY_PRESSED && e.keyCode == KeyEvent.VK_ENTER && e.modifiersEx == 0) ||
           (e.id == KeyEvent.KEY_PRESSED && e.keyCode == KeyEvent.VK_TAB && e.modifiersEx == 0) ||
           (e.id == KeyEvent.KEY_PRESSED && e.keyCode == KeyEvent.VK_BACK_SPACE && e.modifiersEx == 0)
  }
}

private const val SHORTCUT_MODIFIERS_MASK = InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK or InputEvent.META_DOWN_MASK

private val MODIFIER_KEY_CODES = setOf(KeyEvent.VK_SHIFT, KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_META, KeyEvent.VK_ALT_GRAPH)
