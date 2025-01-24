// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Key
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.block.reworked.TerminalEventDispatcher
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke

/**
 * Processes shortcuts of disabled terminal actions.
 *
 * If [TerminalEventDispatcher] lets the action system to process a key event,
 * it's possible that all terminal actions associated with the corresponding shortcut are disabled.
 * However, it's possible that there are some other platform (non-terminal) actions that have the same shortcut.
 * Yet, if the action isn't on the terminal's "allowed list," we must not allow it to be performed,
 * and must send the shortcut to the terminal instead.
 * This is exactly what this action does: it has all the shortcuts of the allowed actions,
 * but it only enables itself if none of the allowed actions are enabled.
 * It is also promoted, so it's considered before other platform actions.
 */
internal class SendShortcutToTerminalAction(
  private val dispatcher: TerminalEventDispatcher,
) : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {

  init {
    templatePresentation.putClientProperty(KEY, Unit)
  }

  internal fun register(component: JComponent) {
    val terminalShortcuts = CustomShortcutSet(
      *TerminalEventDispatcher.getActionsToSkip()
        .flatMap { it.shortcutSet.shortcuts.toList() }
        .toTypedArray()
    )
    registerCustomShortcutSet(terminalShortcuts, component)
  }

  internal fun unregister(component: JComponent) {
    unregisterCustomShortcutSet(component)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val shortcut = e.shortcut
    if (shortcut == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    for (action in TerminalEventDispatcher.getActionsToSkip()) {
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
    dispatcher.handleKeyEvent(event)
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
