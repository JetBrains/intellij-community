// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.action

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Key
import com.intellij.terminal.frontend.TerminalEventsHandler
import com.intellij.terminal.frontend.TimedKeyEvent
import com.intellij.terminal.frontend.handleKeyEvent
import org.jetbrains.plugins.terminal.TerminalBundle
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke

/**
 * Processes shortcuts of disabled terminal actions.
 *
 * If [com.intellij.terminal.frontend.TerminalEventDispatcher] lets the action system to process a key event,
 * it's possible that all terminal actions associated with the corresponding shortcut are disabled.
 * However, it's possible that there are some other platform (non-terminal) actions that have the same shortcut.
 * Yet, if the action isn't on the terminal's "allowed list," we must not allow it to be performed,
 * and must send the shortcut to the terminal instead.
 * This is exactly what this action does: it has all the shortcuts of the allowed actions,
 * but it only enables itself if none of the allowed actions are enabled.
 * It is also promoted, so it's considered before other platform actions.
 */
internal class SendShortcutToTerminalAction(
  private val handler: TerminalEventsHandler,
) : DumbAwareAction() {

  private var actions: List<AnAction> = emptyList()

  init {
    templatePresentation.putClientProperty(KEY, Unit)
  }

  internal fun register(component: JComponent, actions: List<AnAction>) {
    val terminalShortcuts = CustomShortcutSet(
      *actions
        .flatMap { it.shortcutSet.shortcuts.toList() }
        .toTypedArray()
    )
    registerCustomShortcutSet(terminalShortcuts, component)
    this.actions = actions
  }

  internal fun unregister(component: JComponent) {
    unregisterCustomShortcutSet(component)
    this.actions = emptyList()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val shortcut = e.shortcut
    if (shortcut == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    for (action in actions) {
      if (action.shortcutSet.shortcuts.contains(shortcut)) {
        if (e.updateSession.presentation(action).isEnabled) {
          e.presentation.isEnabledAndVisible = false
          return
        }
      }
    }
    e.presentation.text = TerminalBundle.message("action.Terminal.SendShortcut.detailed.text", shortcut.toString())
    e.presentation.isEnabledAndVisible = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val event = e.inputEvent as? KeyEvent ?: return
    LOG.trace { "All actions bound to this shortcut are disabled, sending key event to terminal: $event" }
    handler.handleKeyEvent(TimedKeyEvent(event))
  }

  companion object {
    private val LOG = logger<SendShortcutToTerminalAction>()
  }
}

private val KEY = Key.create<Unit>("SendShortcutToTerminalAction")

internal class SendShortcutToTerminalActionPromoter : ActionPromoter {
  override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
    // Promoters often use instanceof checks, but they're prone to break with action delegates / wrappers,
    // so we use a client property instead.
    val action = actions.find { it.templatePresentation.getClientProperty(KEY) != null }
    return if (action != null) listOf(action) else emptyList()
  }
}

private val AnActionEvent.shortcut: Shortcut?
  get() {
    val inputEvent = inputEvent ?: return null
    if (inputEvent !is KeyEvent) return null
    return KeyboardShortcut(KeyStroke.getKeyStrokeForEvent(inputEvent), null)
  }
