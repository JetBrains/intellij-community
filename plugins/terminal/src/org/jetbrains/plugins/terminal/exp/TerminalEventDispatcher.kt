// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.jediterm.terminal.emulator.mouse.MouseMode
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.terminal.exp.TerminalSelectionModel.TerminalSelectionListener
import java.awt.AWTEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelListener
import javax.swing.KeyStroke

/**
 * Adds "Override IDE shortcuts" terminal feature allowing terminal to process all the key events.
 * Without own IdeEventQueue.EventDispatcher, terminal won't receive key events corresponding to IDE action shortcuts.
 */
internal abstract class TerminalEventDispatcher(private val parentDisposable: Disposable) : IdeEventQueue.EventDispatcher {
  private var myRegistered = false
  private var actionsToSkip: List<AnAction> = emptyList()

  override fun dispatch(e: AWTEvent): Boolean {
    if (e is KeyEvent) {
      dispatchKeyEvent(e)
    }
    return false
  }

  private fun dispatchKeyEvent(e: KeyEvent) {
    if (!skipAction(e)) {
      handleKeyEvent(e)
    }
  }

  protected abstract fun handleKeyEvent(e: KeyEvent)

  fun register(actionsToSkip: List<AnAction>) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    this.actionsToSkip = actionsToSkip
    if (!myRegistered) {
      IdeEventQueue.getInstance().addDispatcher(this, parentDisposable)
      myRegistered = true
    }
  }

  fun unregister() {
    ApplicationManager.getApplication().assertIsDispatchThread()
    if (myRegistered) {
      IdeEventQueue.getInstance().removeDispatcher(this)
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
      "TerminalResetFontSize"
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
  val eventDispatcher: TerminalEventDispatcher = object : TerminalEventDispatcher(disposable) {
    override fun handleKeyEvent(e: KeyEvent) {
      if (e.id == KeyEvent.KEY_TYPED) {
        eventsHandler.handleKeyTyped(e)
      }
      else if (e.id == KeyEvent.KEY_PRESSED) {
        eventsHandler.handleKeyPressed(e)
      }
    }
  }

  editor.addFocusListener(object : FocusChangeListener {
    override fun focusGained(editor: Editor) {
      if (settings.overrideIdeShortcuts()) {
        val selectedBlock = selectionModel.primarySelection
        if (selectedBlock == null || selectedBlock == outputModel.getLastBlock()) {
          val actionsToSkip = TerminalEventDispatcher.getActionsToSkip()
          eventDispatcher.register(actionsToSkip)
        }
      }
      else {
        eventDispatcher.unregister()
      }
      if (GeneralSettings.getInstance().isSaveOnFrameDeactivation) {
        invokeLater(ModalityState.nonModal()) {
          FileDocumentManager.getInstance().saveAllDocuments()
        }
      }
    }

    override fun focusLost(editor: Editor) {
      eventDispatcher.unregister()
      SaveAndSyncHandler.getInstance().scheduleRefresh()
    }
  }, disposable)

  selectionModel.addListener(object : TerminalSelectionListener {
    override fun selectionChanged(oldSelection: List<CommandBlock>, newSelection: List<CommandBlock>) {
      val selectedBlock = selectionModel.primarySelection
      if (selectedBlock == null || selectedBlock == outputModel.getLastBlock()) {
        val actionsToSkip = TerminalEventDispatcher.getActionsToSkip()
        eventDispatcher.register(actionsToSkip)
      }
      else eventDispatcher.unregister()
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
        eventsHandler.handleMousePressed(p.column, p.line + historyLinesCount(), event.mouseEvent)
      }
    }

    override fun mouseReleased(event: EditorMouseEvent) {
      if (settings.enableMouseReporting() && isRemoteMouseAction(event.mouseEvent)) {
        val p = event.logicalPosition
        eventsHandler.handleMouseReleased(p.column, p.line + historyLinesCount(), event.mouseEvent)
      }
    }
  }, disposable)

  editor.addEditorMouseMotionListener(object : EditorMouseMotionListener {
    override fun mouseMoved(event: EditorMouseEvent) {
      if (settings.enableMouseReporting() && isRemoteMouseAction(event.mouseEvent)) {
        val p = event.logicalPosition
        eventsHandler.handleMouseMoved(p.column, p.line + historyLinesCount(), event.mouseEvent)
      }
    }

    override fun mouseDragged(event: EditorMouseEvent) {
      if (settings.enableMouseReporting() && isRemoteMouseAction(event.mouseEvent)) {
        val p = event.logicalPosition
        eventsHandler.handleMouseDragged(p.column, p.line + historyLinesCount(), event.mouseEvent)
      }
    }
  }, disposable)

  val mouseWheelListener = MouseWheelListener { event ->
    if (settings.enableMouseReporting() && isRemoteMouseAction(event)) {
      editor.selectionModel.removeSelection()
      val p = editor.xyToLogicalPosition(event.point)
      eventsHandler.handleMouseWheelMoved(p.column, p.line + historyLinesCount(), event)
    }
  }
  editor.scrollPane.addMouseWheelListener(mouseWheelListener)
  Disposer.register(disposable, Disposable {
    editor.scrollPane.removeMouseWheelListener(mouseWheelListener)
  })
}