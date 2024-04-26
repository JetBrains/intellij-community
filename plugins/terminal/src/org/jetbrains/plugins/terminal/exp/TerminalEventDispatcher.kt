// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

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
import com.intellij.openapi.observable.util.addKeyListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.util.concurrency.ThreadingAssertions
import com.jediterm.terminal.emulator.mouse.MouseMode
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.terminal.exp.TerminalEventDispatcher.MyKeyEventsListener
import java.awt.AWTEvent
import java.awt.event.*
import javax.swing.KeyStroke

/**
 * Adds "Override IDE shortcuts" terminal feature allowing terminal to process all the key events.
 * Without own IdeEventQueue.EventDispatcher, the terminal won't receive key events corresponding to IDE action shortcuts.
 * The algorithm is the following:
 * 1. Sort out other events except KeyEvents in [dispatch]
 * 2. If the key event corresponds to one of the AnActions from our list, we do not process it,
 * allowing the platform to execute the corresponding AnAction.
 * 3. All other key events are handled directly by [handleKeyEvent], and sent to the Terminal process.
 * 4. If the platform failed to find the enabled action for the event from step 2,
 * we catch it again using [MyKeyEventsListener] and process it using [handleKeyEvent] (by sending to the Terminal process)
 */
internal abstract class TerminalEventDispatcher(
  private val editor: EditorEx,
  private val parentDisposable: Disposable
) : IdeEventQueue.EventDispatcher {
  private val keyListener: KeyListener = MyKeyEventsListener()
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

  protected abstract fun handleKeyEvent(e: KeyEvent)

  fun register(actionsToSkip: List<AnAction>) {
    ThreadingAssertions.assertEventDispatchThread()
    this.actionsToSkip = actionsToSkip
    if (!myRegistered) {
      IdeEventQueue.getInstance().addDispatcher(this, parentDisposable)
      editor.contentComponent.addKeyListener(parentDisposable, keyListener)
      myRegistered = true
    }
  }

  fun unregister() {
    ThreadingAssertions.assertEventDispatchThread()
    if (myRegistered) {
      IdeEventQueue.getInstance().removeDispatcher(this)
      editor.contentComponent.removeKeyListener(keyListener)
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
            return e.modifiersEx == InputEvent.CTRL_DOWN_MASK && e.keyCode == KeyEvent.VK_E
          }
          return true
        }
      }
    }
    return false
  }

  private inner class MyKeyEventsListener : KeyAdapter() {
    override fun keyTyped(e: KeyEvent) {
      handleKeyEvent(e)
    }

    override fun keyPressed(e: KeyEvent) {
      // Action system has not consumed this KeyPressed event, so, next KeyTyped should be handled.
      ignoreNextKeyTypedEvent = false
      handleKeyEvent(e)
    }
  }

  companion object {
    @NonNls
    private val ACTIONS_TO_SKIP = listOf(
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
      "EditorEscape",
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
      "MaintenanceAction",
      "TerminalIncreaseFontSize",
      "TerminalDecreaseFontSize",
      "TerminalResetFontSize",
      "Terminal.Paste",
      "Terminal.CopySelectedText",
      "Terminal.CopyBlock"
    )

    fun getActionsToSkip(): List<AnAction> {
      val actionManager = ActionManager.getInstance()
      return ACTIONS_TO_SKIP.mapNotNull { actionId -> actionManager.getAction(actionId) }
    }
  }
}

internal fun setupKeyEventDispatcher(editor: EditorEx,
                                     settings: JBTerminalSystemSettingsProviderBase,
                                     eventsHandler: TerminalEventsHandler,
                                     outputModel: TerminalOutputModel,
                                     selectionModel: TerminalSelectionModel,
                                     disposable: Disposable) {
  // Key events forwarding from the editor to terminal panel
  val eventDispatcher: TerminalEventDispatcher = object : TerminalEventDispatcher(editor, disposable) {
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
      val actionsToSkip = TerminalEventDispatcher.getActionsToSkip()
      eventDispatcher.register(actionsToSkip)
    }

    override fun focusLost(editor: Editor) {
      eventDispatcher.unregister()
    }
  }, disposable)
}

internal fun setupMouseListener(editor: EditorEx,
                                settings: JBTerminalSystemSettingsProviderBase,
                                model: TerminalModel,
                                eventsHandler: TerminalEventsHandler,
                                disposable: Disposable) {
  fun isRemoteMouseAction(e: MouseEvent): Boolean {
    return model.mouseMode != MouseMode.MOUSE_REPORTING_NONE && !e.isShiftDown
  }

  fun historyLinesCount(): Int = if (model.useAlternateBuffer) 0 else model.historyLinesCount

  editor.addEditorMouseListener(object : EditorMouseListener {
    override fun mousePressed(event: EditorMouseEvent) {
      if (settings.enableMouseReporting() && isRemoteMouseAction(event.mouseEvent)) {
        val p = event.logicalPosition
        eventsHandler.mousePressed(p.column, p.line + historyLinesCount(), event.mouseEvent)
      }
    }

    override fun mouseReleased(event: EditorMouseEvent) {
      if (settings.enableMouseReporting() && isRemoteMouseAction(event.mouseEvent)) {
        val p = event.logicalPosition
        eventsHandler.mouseReleased(p.column, p.line + historyLinesCount(), event.mouseEvent)
      }
    }
  }, disposable)

  editor.addEditorMouseMotionListener(object : EditorMouseMotionListener {
    override fun mouseMoved(event: EditorMouseEvent) {
      if (settings.enableMouseReporting() && isRemoteMouseAction(event.mouseEvent)) {
        val p = event.logicalPosition
        eventsHandler.mouseMoved(p.column, p.line + historyLinesCount(), event.mouseEvent)
      }
    }

    override fun mouseDragged(event: EditorMouseEvent) {
      if (settings.enableMouseReporting() && isRemoteMouseAction(event.mouseEvent)) {
        val p = event.logicalPosition
        eventsHandler.mouseDragged(p.column, p.line + historyLinesCount(), event.mouseEvent)
      }
    }
  }, disposable)

  val mouseWheelListener = MouseWheelListener { event ->
    val p = editor.xyToLogicalPosition(event.point)
    eventsHandler.mouseWheelMoved(p.column, p.line + historyLinesCount(), event)
  }
  editor.scrollPane.addMouseWheelListener(mouseWheelListener)
  Disposer.register(disposable, Disposable {
    editor.scrollPane.removeMouseWheelListener(mouseWheelListener)
  })
}