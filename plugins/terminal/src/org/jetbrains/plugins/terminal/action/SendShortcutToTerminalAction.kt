// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.annotations.Unmodifiable
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.block.reworked.TerminalEventDispatcher
import java.awt.event.KeyEvent
import java.util.concurrent.ConcurrentHashMap
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
internal class SendShortcutToTerminalAction : DumbAwareAction(), ActionPromoter, ActionRemoteBehaviorSpecification.Frontend {

  private val terminalShortcuts = CustomShortcutSet(
    *TerminalEventDispatcher.getActionsToSkip()
      .flatMap { it.shortcutSet.shortcuts.toList() }
      .toTypedArray()
  )

  private val registeredDispatchers = ConcurrentHashMap<JComponent, TerminalEventDispatcher>()

  override fun promote(actions: @Unmodifiable List<AnAction?>, context: DataContext): @Unmodifiable List<AnAction?>? = listOf(this)

  internal fun register(component: JComponent, dispatcher: TerminalEventDispatcher) {
    registerCustomShortcutSet(terminalShortcuts, component)
    registeredDispatchers[component] = dispatcher
  }

  internal fun unregister(component: JComponent) {
    unregisterCustomShortcutSet(component)
    registeredDispatchers.remove(component)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val shortcut = e.shortcut
    if (shortcut == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val dispatcher = getDispatcher(e)
    if (dispatcher == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    for (action in TerminalEventDispatcher.getActionsToSkip()) {
      if (action.shortcutSet.shortcuts.contains(shortcut)) {
        if (isEnabled(action, e)) {
          e.presentation.isEnabledAndVisible = false
          return
        }
      }
    }
    e.presentation.text = TerminalBundle.message("action.Terminal.SendShortcut.detailed.text", shortcut.toString())
    e.presentation.isEnabledAndVisible = true
  }

  private fun getDispatcher(e: AnActionEvent): TerminalEventDispatcher? {
    val component = e.dataContext.getData(PlatformDataKeys.CONTEXT_COMPONENT) ?: return null
    return registeredDispatchers[component]
  }

  override fun actionPerformed(e: AnActionEvent) {
    val dispatcher = getDispatcher(e) ?: return
    val event = e.inputEvent as? KeyEvent ?: return
    dispatcher.handleKeyEvent(event)
  }
}

private fun isEnabled(action: AnAction, event: AnActionEvent): Boolean {
  val fakeEvent = AnActionEvent.createEvent(
    action,
    event.dataContext,
    null,
    ActionPlaces.KEYBOARD_SHORTCUT,
    ActionUiKind.NONE,
    event.inputEvent
  )
  if (action.actionUpdateThread == ActionUpdateThread.BGT) {
    action.update(fakeEvent)
  }
  else {
    event.updateSession.compute(action, "isEnabled", ActionUpdateThread.EDT) {
      action.update(fakeEvent)
    }
  }
  return fakeEvent.presentation.isEnabled
}

private val AnActionEvent.shortcut: Shortcut?
  get() {
    val inputEvent = inputEvent ?: return null
    if (inputEvent !is KeyEvent) return null
    return KeyboardShortcut(KeyStroke.getKeyStrokeForEvent(inputEvent), null)
  }
