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
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jediterm.terminal.StyledTextConsumer
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.emulator.ColorPalette
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.terminal.model.CharBuffer
import com.jediterm.terminal.ui.AwtTransformers
import org.jetbrains.annotations.NonNls
import java.awt.*
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.border.Border
import kotlin.math.max

class TerminalPanel(private val project: Project,
                    private val settings: JBTerminalSystemSettingsProviderBase,
                    private val model: TerminalModel,
                    private val eventsHandler: TerminalEventsHandler) : JPanel(), ComponentContainer {
  private val document: Document
  private val editor: EditorImpl

  // disposable for updating content and forwarding mouse events
  private val runningDisposable: Disposable = Disposer.newDisposable()
  private val keyEventsForwardingDisposable = Disposable { eventDispatcher.unregister() }

  private val palette: ColorPalette
    get() = settings.terminalColorPalette

  private val eventDispatcher: TerminalEventDispatcher = TerminalEventDispatcher()

  init {
    document = DocumentImpl("", true)
    editor = TerminalUiUtils.createEditor(document, project, settings)
    Disposer.register(this) {
      EditorFactory.getInstance().releaseEditor(editor)
    }
    Disposer.register(this, runningDisposable)
    Disposer.register(this, keyEventsForwardingDisposable)

    setupContentListener()
    setupEventDispatcher()
    setupMouseListener()

    border = createBorder(isFullScreen = false)

    layout = BorderLayout()
    add(editor.component, BorderLayout.CENTER)

    updateEditorContent()
  }

  fun makeReadOnly(done: (Editor) -> Unit) {
    // remove listening for content changes and forwarding events to terminal process
    Disposer.dispose(runningDisposable)

    invokeLater {
      Disposer.dispose(keyEventsForwardingDisposable)

      // remove empty line at the end of output
      val output = editor.document.text
      editor.document.setText(output.trimEnd())

      editor.setCaretEnabled(false)
      editor.isViewer = true
      done(editor)
    }
  }

  fun toggleFullScreen(isFullScreen: Boolean) {
    border = createBorder(isFullScreen)
  }

  private fun createBorder(isFullScreen: Boolean): Border {
    return if (!isFullScreen) {
      val innerBorder = JBUI.Borders.customLine(UIUtil.getTextFieldBackground(), 6, 0, 6, 0)
      val outerBorder = JBUI.Borders.customLineTop(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
      JBUI.Borders.compound(outerBorder, innerBorder)!!
    }
    else JBUI.Borders.empty()
  }

  private fun setupContentListener() {
    model.addContentListener(object : TerminalModel.ContentListener {
      override fun onContentChanged() {
        updateEditorContent()
      }
    }, runningDisposable)
  }

  private fun updateEditorContent() {
    val content: TerminalContent = computeTerminalContent()
    // Can not use invokeAndWait here because deadlock may happen. TerminalTextBuffer is locked at this place,
    // and EDT can be frozen now trying to acquire this lock
    invokeLater(ModalityState.any()) {
      updateEditor(content)
    }
  }

  private fun computeTerminalContent(): TerminalContent {
    val builder = StringBuilder()
    val highlightings = mutableListOf<HighlightingInfo>()
    val consumer = object : StyledTextConsumer {
      override fun consume(x: Int,
                           y: Int,
                           style: TextStyle,
                           characters: CharBuffer,
                           startRow: Int) {
        val startOffset = builder.length
        builder.append(characters.toString())
        val attributes = style.toTextAttributes()
        highlightings.add(HighlightingInfo(startOffset, builder.length, attributes))
      }

      override fun consumeNul(x: Int,
                              y: Int,
                              nulIndex: Int,
                              style: TextStyle,
                              characters: CharBuffer,
                              startRow: Int) {
        val startOffset = builder.length
        repeat(characters.buf.size) {
          builder.append(' ')
        }
        highlightings.add(HighlightingInfo(startOffset, builder.length, TextStyle.EMPTY.toTextAttributes()))
      }

      override fun consumeQueue(x: Int, y: Int, nulIndex: Int, startRow: Int) {
        builder.append("\n")
        highlightings.add(HighlightingInfo(builder.length - 1, builder.length, TextStyle.EMPTY.toTextAttributes()))
      }
    }

    if (model.useAlternateBuffer) {
      model.processScreenLines(0, model.screenLinesCount, consumer)
    }
    else {
      model.processHistoryAndScreenLines(-model.historyLinesCount, model.historyLinesCount + model.cursorY, consumer)
    }

    while (builder.lastOrNull() == '\n') {
      builder.deleteCharAt(builder.lastIndex)
      highlightings.removeLast()
    }
    return TerminalContent(builder.toString(), highlightings)
  }

  private fun updateEditor(content: TerminalContent) {
    document.setText(content.text)
    editor.highlighter = TerminalHighlighter(content.highlightings)
    if (model.useAlternateBuffer) {
      editor.setCaretEnabled(false)
    }
    else {
      editor.setCaretEnabled(model.isCursorVisible)
      val line = model.historyLinesCount + model.cursorY - 1
      editor.caretModel.moveToLogicalPosition(LogicalPosition(line, model.cursorX))
      editor.scrollingModel.scrollToCaret(ScrollType.CENTER_DOWN)
    }
  }

  private fun TextStyle.toTextAttributes(): TextAttributes {
    return TextAttributes().also { attr ->
      attr.backgroundColor = AwtTransformers.toAwtColor(palette.getBackground(model.styleState.getBackground(backgroundForRun)))
      attr.foregroundColor = getStyleForeground(this)
      if (hasOption(TextStyle.Option.BOLD)) {
        attr.fontType = attr.fontType or Font.BOLD
      }
      if (hasOption(TextStyle.Option.ITALIC)) {
        attr.fontType = attr.fontType or Font.ITALIC
      }
      if (hasOption(TextStyle.Option.UNDERLINED)) {
        attr.withAdditionalEffect(EffectType.LINE_UNDERSCORE, attr.foregroundColor)
      }
    }
  }

  private fun getStyleForeground(style: TextStyle): Color {
    val foreground = palette.getForeground(model.styleState.getForeground(style.foregroundForRun))
    return if (style.hasOption(TextStyle.Option.DIM)) {
      val background = palette.getBackground(model.styleState.getBackground(style.backgroundForRun))
      Color((foreground.red + background.red) / 2,
            (foreground.green + background.green) / 2,
            (foreground.blue + background.blue) / 2,
            foreground.alpha)
    }
    else AwtTransformers.toAwtColor(foreground)!!
  }

  private fun setupEventDispatcher() {
    // Key events forwarding from the editor to terminal panel
    editor.addFocusListener(object : FocusChangeListener {
      override fun focusGained(editor: Editor) {
        if (settings.overrideIdeShortcuts()) {
          val actionsToSkip = setupActionsToSkip()
          eventDispatcher.register(actionsToSkip)
        }
        else {
          eventDispatcher.unregister()
        }
        if (GeneralSettings.getInstance().isSaveOnFrameDeactivation) {
          invokeLater(ModalityState.NON_MODAL) {
            FileDocumentManager.getInstance().saveAllDocuments()
          }
        }
      }

      override fun focusLost(editor: Editor) {
        eventDispatcher.unregister()
        SaveAndSyncHandler.getInstance().scheduleRefresh()
      }
    }, runningDisposable)
  }

  override fun processKeyEvent(e: KeyEvent) {
    if (e.id == KeyEvent.KEY_TYPED) {
      eventsHandler.handleKeyTyped(e)
    }
    else if (e.id == KeyEvent.KEY_PRESSED) {
      eventsHandler.handleKeyPressed(e)
    }
  }

  private fun setupMouseListener() {
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
    }, runningDisposable)

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
    }, runningDisposable)

    val mouseWheelListener = MouseWheelListener { event ->
      if (settings.enableMouseReporting() && isRemoteMouseAction(event)) {
        editor.selectionModel.removeSelection()
        val p = editor.xyToLogicalPosition(event.point)
        eventsHandler.handleMouseWheelMoved(p.column, p.line + historyLinesCount(), event)
      }
    }
    editor.scrollPane.addMouseWheelListener(mouseWheelListener)
    Disposer.register(runningDisposable, Disposable {
      editor.scrollPane.removeMouseWheelListener(mouseWheelListener)
    })
  }

  fun isFocused(): Boolean = editor.contentComponent.hasFocus()

  override fun getPreferredSize(): Dimension {
    val baseSize = super.getPreferredSize()
    JBInsets.addTo(baseSize, insets)
    val lineCount = max(editor.document.lineCount, 1)
    return Dimension(baseSize.width, lineCount * editor.lineHeight + insets.top + insets.bottom)
  }

  override fun getComponent(): JComponent = this

  override fun getPreferredFocusableComponent(): JComponent = editor.contentComponent

  override fun dispose() {
  }

  private data class TerminalContent(val text: String, val highlightings: List<HighlightingInfo>)

  /**
   * Adds "Override IDE shortcuts" terminal feature allowing terminal to process all the key events.
   * Without own IdeEventQueue.EventDispatcher, terminal won't receive key events corresponding to IDE action shortcuts.
   */
  private inner class TerminalEventDispatcher : IdeEventQueue.EventDispatcher {
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
        if (this@TerminalPanel.isFocused()) {
          this@TerminalPanel.processKeyEvent(e)
        }
        else unregister()
      }
    }

    fun register(actionsToSkip: List<AnAction>) {
      ApplicationManager.getApplication().assertIsDispatchThread()
      this.actionsToSkip = actionsToSkip
      if (!myRegistered) {
        IdeEventQueue.getInstance().addDispatcher(this, this@TerminalPanel)
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

    private fun setupActionsToSkip(): List<AnAction> {
      val actionManager = ActionManager.getInstance()
      return ACTIONS_TO_SKIP.mapNotNull { actionId -> actionManager.getAction(actionId) }
    }
  }
}