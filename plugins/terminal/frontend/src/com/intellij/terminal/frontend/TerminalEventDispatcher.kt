// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.frontend.action.SendShortcutToTerminalAction
import com.intellij.util.concurrency.ThreadingAssertions
import com.jediterm.terminal.emulator.mouse.MouseMode
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.terminal.block.output.TerminalEventsHandler
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel
import java.awt.AWTEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelListener
import javax.swing.KeyStroke

/**
 * Adds "Override IDE shortcuts" terminal feature allowing terminal to process all the key events.
 * Without own IdeEventQueue.EventDispatcher, the terminal won't receive key events corresponding to IDE action shortcuts.
 * The algorithm is the following:
 * 1. Sort out other events except KeyEvents in [dispatch]
 * 2. If the key event corresponds to one of the AnActions from our list, we do not process it,
 * allowing the platform to execute the corresponding AnAction.
 * 3. All other key events are handled directly by [handleKeyEvent], and sent to the Terminal process.
 *
 * For the key event sent to the platform at step 2, there are two possibilities:
 *
 * 1. One of the actions from our list corresponding to the current shortcut is enabled.
 * In this case, this action is performed by the platform as usual.
 * 2. Otherwise, the special [SendShortcutToTerminalAction] is performed.
 * This action only enables itself if all the actions from our list corresponding to the same shortcut are disabled.
 * It then sends the received shortcut to the terminal using the same [handleKeyEvent] mentioned above.
 */
internal abstract class TerminalEventDispatcher(
  private val editor: EditorEx,
  private val settings: JBTerminalSystemSettingsProviderBase,
  private val parentDisposable: Disposable,
) : IdeEventQueue.EventDispatcher {
  private val sendShortcutAction = SendShortcutToTerminalAction(this)
  private var myRegistered = false
  private var actionsToSkip: List<AnAction> = emptyList()

  private var ignoreNextKeyTypedEvent: Boolean = false

  override fun dispatch(e: AWTEvent): Boolean {
    if (e is KeyEvent) {
      dispatchKeyEvent(e)
    }
    return false
  }

  private fun dispatchKeyEvent(e: KeyEvent) {
    if (!skipAction(e)) {
      if (e.id != KeyEvent.KEY_TYPED || !ignoreNextKeyTypedEvent) {
        ignoreNextKeyTypedEvent = false
        handleKeyEvent(e)
      }
    }
    else {
      // KeyEvent will be handled by action system, so we need to remember that the next KeyTyped event is not needed
      ignoreNextKeyTypedEvent = true
    }
  }

  internal abstract fun handleKeyEvent(e: KeyEvent)

  fun register() {
    ThreadingAssertions.assertEventDispatchThread()
    this.actionsToSkip = getActionsToSkip()
    if (!myRegistered) {
      IdeEventQueue.getInstance().addDispatcher(this, parentDisposable)
      sendShortcutAction.register(editor.contentComponent, getActionsToSkip())
      myRegistered = true
    }
  }

  fun unregister() {
    ThreadingAssertions.assertEventDispatchThread()
    if (myRegistered) {
      IdeEventQueue.getInstance().removeDispatcher(this)
      sendShortcutAction.unregister(editor.contentComponent)
      actionsToSkip = emptyList()
      myRegistered = false
    }
  }

  private fun skipAction(e: KeyEvent): Boolean {
    val eventShortcut = KeyboardShortcut(KeyStroke.getKeyStrokeForEvent(e), null)
    for (action in actionsToSkip) {
      for (sc in action.shortcutSet.shortcuts) {
        if (sc.isKeyboard && sc.startsWith(eventShortcut)) {
          if (!Registry.`is`("terminal.Ctrl-E.opens.RecentFiles.popup",
                             false) && IdeActions.ACTION_RECENT_FILES == ActionManager.getInstance().getId(action)) {
            if (e.modifiersEx == InputEvent.CTRL_DOWN_MASK && e.keyCode == KeyEvent.VK_E) {
              return false
            }
          }
          return true
        }
      }
    }
    return false
  }

  private fun getActionsToSkip(): List<AnAction> {
    val actionManager = ActionManager.getInstance()
    val allowedActionIDs = if (settings.overrideIdeShortcuts()) OPTIONAL_ACTIONS + TERMINAL_ACTIONS else TERMINAL_ACTIONS
    return allowedActionIDs.mapNotNull { actionId -> actionManager.getAction(actionId) }
  }

  companion object {
    /**
     * The list of actions that can be invoked by shortcuts only if enabled in the settings.
     */
    @Language("devkit-action-id")
    @NonNls
    private val OPTIONAL_ACTIONS = listOf(
      "ActivateTerminalToolWindow",
      "ActivateProjectToolWindow",
      "ActivateFavoritesToolWindow",
      "ActivateBookmarksToolWindow",
      "ActivateFindToolWindow",
      "ActivateRunToolWindow",
      "ActivateDebugToolWindow",
      "ActivateProblemsViewToolWindow",
      "ActivateTODOToolWindow",
      "ActivateStructureToolWindow",
      "ActivateHierarchyToolWindow",
      "ActivateServicesToolWindow",
      "ActivateCommitToolWindow",
      "ActivateVersionControlToolWindow",
      "HideActiveWindow",
      "HideAllWindows",
      "NextWindow",
      "PreviousWindow",
      "NextProjectWindow",
      "PreviousProjectWindow",
      "ShowBookmarks",
      "ShowTypeBookmarks",
      "FindInPath",
      "GotoBookmark0",
      "GotoBookmark1",
      "GotoBookmark2",
      "GotoBookmark3",
      "GotoBookmark4",
      "GotoBookmark5",
      "GotoBookmark6",
      "GotoBookmark7",
      "GotoBookmark8",
      "GotoBookmark9",
      "GotoAction",
      "GotoFile",
      "GotoClass",
      "GotoSymbol",
      "Vcs.Push",
      "ShowSettings",
      "RecentFiles",
      "Switcher",
      "ResizeToolWindowLeft",
      "ResizeToolWindowRight",
      "ResizeToolWindowUp",
      "ResizeToolWindowDown",
      "MaximizeToolWindow",
      // terminal actions, but included here because they're not essential
      "TerminalIncreaseFontSize",
      "TerminalDecreaseFontSize",
      "TerminalResetFontSize",
    )

    /**
     * The list of actions that can always be invoked by shortcuts in the terminal.
     */
    @Language("devkit-action-id")
    @NonNls
    private val TERMINAL_ACTIONS = listOf(
      // not exactly terminal actions, but we want these to always work anyway
      "NextTab",
      "PreviousTab",
      "ShowContent",
      // true terminal actions
      "Terminal.Escape",
      "Terminal.CopySelectedText",
      "Terminal.Paste",
      "Terminal.LineUp",
      "Terminal.LineDown",
      "Terminal.PageUp",
      "Terminal.PageDown",
      "Terminal.RenameSession",
      "Terminal.NewTab",
      "Terminal.CloseTab",
      "Terminal.SplitVertically",
      "Terminal.SplitHorizontally",
      "Terminal.NextSplitter",
      "Terminal.PrevSplitter",
      "Terminal.MoveToolWindowTabLeft",
      "Terminal.MoveToolWindowTabRight",
      "Terminal.ClearBuffer",
      "Terminal.Find",
      "Terminal.CommandCompletion",
      "Terminal.EnterCommandCompletion",
      "Terminal.UpCommandCompletion",
      "Terminal.DownCommandCompletion",
    )
  }
}

internal fun setupKeyEventDispatcher(
  editor: EditorEx,
  settings: JBTerminalSystemSettingsProviderBase,
  eventsHandler: TerminalEventsHandler,
  disposable: Disposable,
) {
  // Key events forwarding from the editor to the shell
  val eventDispatcher: TerminalEventDispatcher = object : TerminalEventDispatcher(editor, settings, disposable) {
    override fun handleKeyEvent(e: KeyEvent) {
      if (e.id == KeyEvent.KEY_TYPED) {
        eventsHandler.keyTyped(e)
      }
      else if (e.id == KeyEvent.KEY_PRESSED) {
        eventsHandler.keyPressed(e)
      }
    }
  }

  editor.addFocusListener(object : FocusChangeListener {
    override fun focusGained(editor: Editor) {
      eventDispatcher.register()
    }

    override fun focusLost(editor: Editor) {
      eventDispatcher.unregister()
    }
  }, disposable)

  if (editor.contentComponent.hasFocus()) {
    eventDispatcher.register()
  }
}

internal fun setupMouseListener(
  editor: EditorEx,
  sessionModel: TerminalSessionModel,
  settings: JBTerminalSystemSettingsProviderBase,
  eventsHandler: TerminalEventsHandler,
  disposable: Disposable,
) {
  fun isRemoteMouseAction(e: MouseEvent): Boolean {
    return sessionModel.terminalState.value.mouseMode != MouseMode.MOUSE_REPORTING_NONE && !e.isShiftDown
  }

  // TODO: I suspect that Y positions should be screen-start based (without history).
  //  But it is not clear how to track the screen start. Need to investigate.
  editor.addEditorMouseListener(object : EditorMouseListener {
    override fun mousePressed(event: EditorMouseEvent) {
      if (settings.enableMouseReporting() && isRemoteMouseAction(event.mouseEvent)) {
        val p = event.visualPosition
        eventsHandler.mousePressed(p.column, p.line, event.mouseEvent)
      }
    }

    override fun mouseReleased(event: EditorMouseEvent) {
      if (settings.enableMouseReporting() && isRemoteMouseAction(event.mouseEvent)) {
        val p = event.visualPosition
        eventsHandler.mouseReleased(p.column, p.line, event.mouseEvent)
      }
    }
  }, disposable)

  editor.addEditorMouseMotionListener(object : EditorMouseMotionListener {
    override fun mouseMoved(event: EditorMouseEvent) {
      if (settings.enableMouseReporting() && isRemoteMouseAction(event.mouseEvent)) {
        val p = event.visualPosition
        eventsHandler.mouseMoved(p.column, p.line, event.mouseEvent)
      }
    }

    override fun mouseDragged(event: EditorMouseEvent) {
      if (settings.enableMouseReporting() && isRemoteMouseAction(event.mouseEvent)) {
        val p = event.visualPosition
        eventsHandler.mouseDragged(p.column, p.line, event.mouseEvent)
      }
    }
  }, disposable)

  val mouseWheelListener = MouseWheelListener { event ->
    val p = editor.xyToVisualPosition(event.point)
    eventsHandler.mouseWheelMoved(p.column, p.line, event)
  }
  editor.scrollPane.addMouseWheelListener(mouseWheelListener)
  Disposer.register(disposable, Disposable {
    editor.scrollPane.removeMouseWheelListener(mouseWheelListener)
  })
}