// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend

import com.intellij.codeInsight.completion.CompletionPhase
import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.find.SearchReplaceComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.MockDocumentEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.frontend.fus.TerminalFusCursorPainterListener
import com.intellij.terminal.frontend.fus.TerminalFusFirstOutputListener
import com.intellij.terminal.frontend.hyperlinks.FrontendTerminalHyperlinkFacade
import com.intellij.terminal.session.TerminalHyperlinkId
import com.intellij.terminal.session.TerminalSession
import com.intellij.ui.components.JBLayeredPane
import com.intellij.util.asDisposable
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.TerminalFontSettingsListener
import org.jetbrains.plugins.terminal.TerminalFontSettingsService
import org.jetbrains.plugins.terminal.TerminalFontSizeProviderImpl
import org.jetbrains.plugins.terminal.TerminalPanelMarker
import org.jetbrains.plugins.terminal.block.TerminalContentView
import org.jetbrains.plugins.terminal.block.output.TerminalOutputEditorInputMethodSupport
import org.jetbrains.plugins.terminal.block.output.TerminalTextHighlighter
import org.jetbrains.plugins.terminal.block.reworked.*
import org.jetbrains.plugins.terminal.block.reworked.hyperlinks.TerminalHyperlinkHighlighter
import org.jetbrains.plugins.terminal.block.reworked.hyperlinks.isSplitHyperlinksSupportEnabled
import org.jetbrains.plugins.terminal.block.reworked.session.FrontendTerminalSession
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils
import org.jetbrains.plugins.terminal.block.ui.addToLayer
import org.jetbrains.plugins.terminal.block.ui.calculateTerminalSize
import org.jetbrains.plugins.terminal.block.ui.isTerminalOutputScrollChangingActionInProgress
import org.jetbrains.plugins.terminal.fus.FrontendLatencyService
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import java.awt.Component
import java.awt.Dimension
import java.awt.event.*
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import kotlin.math.min

@ApiStatus.Internal
class ReworkedTerminalView(
  private val project: Project,
  settings: JBTerminalSystemSettingsProviderBase,
  private val sessionFuture: CompletableFuture<TerminalSession>,
  startupFusInfo: TerminalStartupFusInfo?,
) : TerminalContentView {
  private val coroutineScope = terminalProjectScope(project).childScope("ReworkedTerminalView")

  private val sessionModel: TerminalSessionModel

  @VisibleForTesting
  val blocksModel: TerminalBlocksModel
  private val encodingManager: TerminalKeyEncodingManager
  private val controller: TerminalSessionController

  private val terminalInput: TerminalInput
  private val terminalSearchController: TerminalSearchController

  @VisibleForTesting
  val outputEditor: EditorEx
  private val outputHyperlinkFacade: FrontendTerminalHyperlinkFacade?
  private val alternateBufferEditor: EditorEx

  @VisibleForTesting
  val outputModel: TerminalOutputModelImpl
  private val alternateBufferHyperlinkFacade: FrontendTerminalHyperlinkFacade?
  private val scrollingModel: TerminalOutputScrollingModel
  private var isAlternateScreenBuffer = false

  private val terminalPanel: TerminalPanel
  @VisibleForTesting
  val outputEditorEventsHandler: TerminalEventsHandler

  override val component: JComponent
    get() = terminalPanel
  override val preferredFocusableComponent: JComponent
    get() = terminalPanel.preferredFocusableComponent

  init {
    Disposer.register(this) {
      coroutineScope.cancel()
    }
    val hyperlinkScope = coroutineScope.childScope("ReworkedTerminalView hyperlink facades")

    sessionModel = TerminalSessionModelImpl()
    encodingManager = TerminalKeyEncodingManager(sessionModel, coroutineScope.childScope("TerminalKeyEncodingManager"))

    terminalInput = TerminalInput(sessionFuture, sessionModel, startupFusInfo, coroutineScope.childScope("TerminalInput"), encodingManager)

    // Use the same instance of the listeners for both editors to report the metrics only once.
    // Usually, the cursor is painted or output received first in the output editor
    // because it is shown by default on a new session opening.
    // But in the case of session restoration in RemDev, there can be an alternate buffer.
    val fusCursorPaintingListener = startupFusInfo?.let { TerminalFusCursorPainterListener(it) }
    val fusFirstOutputListener = startupFusInfo?.let { TerminalFusFirstOutputListener(it) }

    alternateBufferEditor = TerminalEditorFactory.createAlternateBufferEditor(project, settings, parentDisposable = this)
    val alternateBufferModel = TerminalOutputModelImpl(alternateBufferEditor.document, maxOutputLength = 0)
    val alternateBufferEventsHandler = TerminalEventsHandlerImpl(sessionModel, alternateBufferEditor, encodingManager, terminalInput, settings, null, alternateBufferModel)
    configureOutputEditor(
      project,
      editor = alternateBufferEditor,
      model = alternateBufferModel,
      settings,
      sessionModel,
      terminalInput,
      coroutineScope.childScope("TerminalAlternateBufferModel"),
      fusCursorPaintingListener,
      fusFirstOutputListener,
      alternateBufferEventsHandler,
    )
    alternateBufferHyperlinkFacade = if (isSplitHyperlinksSupportEnabled()) {
      FrontendTerminalHyperlinkFacade(
        isInAlternateBuffer = true,
        editor = alternateBufferEditor,
        outputModel = alternateBufferModel,
        terminalInput = terminalInput,
        coroutineScope = hyperlinkScope,
      )
    }
    else {
      null
    }

    outputEditor = TerminalEditorFactory.createOutputEditor(project, settings, parentDisposable = this)
    outputEditor.putUserData(TerminalInput.KEY, terminalInput)
    outputModel = TerminalOutputModelImpl(outputEditor.document, maxOutputLength = TerminalUiUtils.getDefaultMaxOutputLength())
    updatePsiOnOutputModelChange(project, outputModel, coroutineScope.childScope("TerminalOutputPsiUpdater"))

    scrollingModel = TerminalOutputScrollingModelImpl(outputEditor, outputModel, sessionModel, coroutineScope.childScope("TerminalOutputScrollingModel"))
    outputEditor.putUserData(TerminalOutputScrollingModel.KEY, scrollingModel)
    outputEditorEventsHandler = TerminalEventsHandlerImpl(sessionModel, outputEditor, encodingManager, terminalInput, settings, scrollingModel, outputModel)

    configureOutputEditor(
      project,
      editor = outputEditor,
      model = outputModel,
      settings,
      sessionModel,
      terminalInput,
      coroutineScope.childScope("TerminalOutputModel"),
      fusCursorPaintingListener,
      fusFirstOutputListener,
      outputEditorEventsHandler,
    )

    outputEditor.putUserData(TerminalSessionModel.KEY, sessionModel)
    terminalSearchController = TerminalSearchController(project)

    blocksModel = TerminalBlocksModelImpl(outputEditor.document)
    val typeAhead = TerminalTypeAhead(outputModel, blocksModel, outputEditor)
    outputEditor.putUserData(TerminalTypeAhead.KEY, typeAhead)
    TerminalBlocksDecorator(outputEditor, blocksModel, scrollingModel, coroutineScope.childScope("TerminalBlocksDecorator"))
    outputEditor.putUserData(TerminalBlocksModel.KEY, blocksModel)
    outputHyperlinkFacade = if (isSplitHyperlinksSupportEnabled()) {
      FrontendTerminalHyperlinkFacade(
        isInAlternateBuffer = false,
        editor = outputEditor,
        outputModel = outputModel,
        terminalInput = terminalInput,
        coroutineScope = hyperlinkScope,
      )
    }
    else {
      null
    }

    outputEditor.putUserData(CompletionPhase.CUSTOM_CODE_COMPLETION_ACTION_ID, "Terminal.CommandCompletion.Gen2")

    val fusActivity = FrontendLatencyService.getInstance().startFrontendOutputActivity(
      outputEditor = outputEditor,
      alternateBufferEditor = alternateBufferEditor,
    )

    val terminalAliasesStorage = TerminalAliasesStorage()

    controller = TerminalSessionController(
      project,
      sessionModel,
      outputModel,
      outputHyperlinkFacade,
      alternateBufferModel,
      alternateBufferHyperlinkFacade,
      blocksModel,
      settings,
      coroutineScope.childScope("TerminalSessionController"),
      fusActivity,
      terminalAliasesStorage
    )
    outputEditor.putUserData(TerminalAliasesStorage.KEY, terminalAliasesStorage)

    sessionFuture.thenAccept { session ->
      controller.handleEvents(session)
    }

    configureInlineCompletion(outputEditor, outputModel, coroutineScope, parentDisposable = this)

    terminalPanel = TerminalPanel(initialContent = outputEditor)

    listenSearchController()
    listenPanelSizeChanges()
    listenAlternateBufferSwitch()

    val synchronizer = TerminalVfsSynchronizer(
      controller,
      outputModel,
      sessionModel,
      terminalPanel,
      coroutineScope.childScope("TerminalVfsSynchronizer"),
    )
    outputEditor.putUserData(TerminalVfsSynchronizer.KEY, synchronizer)
  }

  override fun addTerminationCallback(onTerminated: Runnable, parentDisposable: Disposable) {
    controller.addTerminationCallback(onTerminated, parentDisposable)
  }

  override fun sendCommandToExecute(shellCommand: String) {
    val newLineBytes = encodingManager.getCode(KeyEvent.VK_ENTER, 0)!!
    // TODO: should we always use UTF8?
    val bytes = shellCommand.toByteArray(Charsets.UTF_8) + newLineBytes
    terminalInput.sendBytes(bytes)
  }

  override fun getText(): CharSequence {
    return getCurEditor().document.immutableCharSequence
  }

  override fun getCurrentDirectory(): String? {
    return sessionModel.terminalState.value.currentDirectory
  }

  override fun getTerminalSize(): TermSize? {
    return getCurEditor().calculateTerminalSize()
  }

  override fun getTerminalSizeInitializedFuture(): CompletableFuture<*> {
    return TerminalUiUtils.getComponentSizeInitializedFuture(component)
  }

  override fun isFocused(): Boolean {
    return component.hasFocus()
  }

  fun setTopComponent(component: JComponent, disposable: Disposable) {
    val resizeListener = object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        // Update scroll position on top component size change
        // to always keep the cursor visible
        scrollingModel.scrollToCursor(force = false)
      }
    }
    component.addComponentListener(resizeListener)
    terminalPanel.setTopComponent(component)

    Disposer.register(disposable) {
      component.removeComponentListener(resizeListener)
      terminalPanel.remoteTopComponent(component)
    }
  }

  private fun listenSearchController() {
    terminalSearchController.addListener(object : TerminalSearchControllerListener {
      override fun searchSessionStarted(session: TerminalSearchSession) {
        terminalPanel.installSearchComponent(session.component)
      }

      override fun searchSessionFinished(session: TerminalSearchSession) {
        terminalPanel.removeSearchComponent(session.component)
      }
    })
  }

  private fun listenPanelSizeChanges() {
    component.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        sendResizeEvent()
      }
    })
  }

  private fun sendResizeEvent() {
    val newSize = getTerminalSize() ?: return
    terminalInput.sendResize(newSize)
  }

  private fun listenAlternateBufferSwitch() {
    coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement() + CoroutineName("Alternate buffer switch listener")) {
      sessionModel.terminalState.collect { state ->
        if (state.isAlternateScreenBuffer != isAlternateScreenBuffer) {
          isAlternateScreenBuffer = state.isAlternateScreenBuffer

          val editor = if (state.isAlternateScreenBuffer) alternateBufferEditor else outputEditor
          terminalPanel.setTerminalContent(editor)
          terminalSearchController.finishSearchSession()
          IdeFocusManager.getInstance(project).requestFocus(terminalPanel.preferredFocusableComponent, true)
        }
      }
    }
  }

  private fun getCurEditor(): EditorEx {
    return if (sessionModel.terminalState.value.isAlternateScreenBuffer) alternateBufferEditor else outputEditor
  }

  private fun configureOutputEditor(
    project: Project,
    editor: EditorImpl,
    model: TerminalOutputModel,
    settings: JBTerminalSystemSettingsProviderBase,
    sessionModel: TerminalSessionModel,
    terminalInput: TerminalInput,
    coroutineScope: CoroutineScope,
    fusCursorPainterListener: TerminalFusCursorPainterListener?,
    fusFirstOutputListener: TerminalFusFirstOutputListener?,
    eventsHandler: TerminalEventsHandlerImpl,
  ) {
    val parentDisposable = coroutineScope.asDisposable() // same lifecycle as `this@ReworkedTerminalView`

    // Document modifications can change the scroll position.
    // Mark them with the corresponding flag to indicate that this change is not caused by the explicit user action.
    model.addListener(parentDisposable, object : TerminalOutputModelListener {
      override fun beforeContentChanged(model: TerminalOutputModel) {
        editor.isTerminalOutputScrollChangingActionInProgress = true
      }

      override fun afterContentChanged(model: TerminalOutputModel, startOffset: Int, isTypeAhead: Boolean) {
        editor.isTerminalOutputScrollChangingActionInProgress = false

        // Also repaint the changed part of the document to ensure that highlightings are properly painted.
        editor.repaint(startOffset, editor.document.textLength)
      }
    })

    if (fusFirstOutputListener != null) {
      model.addListener(parentDisposable, fusFirstOutputListener)
    }

    editor.highlighter = TerminalTextHighlighter { model.getHighlightings() }

    if (!isSplitHyperlinksSupportEnabled()) {
      TerminalHyperlinkHighlighter.install(project, model, editor, coroutineScope)
    }

    val cursorPainter = TerminalCursorPainter.install(editor, model, sessionModel, coroutineScope.childScope("TerminalCursorPainter"))
    if (fusCursorPainterListener != null) {
      cursorPainter.addListener(parentDisposable, fusCursorPainterListener)
    }

    val eventsHandler = TerminalEventsHandlerImpl(sessionModel, editor, encodingManager, terminalInput, settings, scrollingModel, model)
    setupKeyEventDispatcher(editor, settings, eventsHandler, parentDisposable)
    setupMouseListener(editor, sessionModel, settings, eventsHandler, parentDisposable)

    TerminalOutputEditorInputMethodSupport(
      editor,
      coroutineScope = coroutineScope.childScope("TerminalInputMethodSupport"),
      getCaretPosition = {
        val offset = model.cursorOffsetState.value
        editor.offsetToLogicalPosition(offset)
      },
      cursorOffsetFlow = model.cursorOffsetState,
      sendInputString = { text -> terminalInput.sendString(text) },
    )

    TerminalEditorFactory.listenEditorFontChanges(editor, settings, parentDisposable) {
      editor.resizeIfShowing()
    }
  }

  private fun EditorImpl.resizeIfShowing() {
    if (component.isShowing) { // to avoid sending the resize event twice, for the regular and alternate buffer editors
      sendResizeEvent()
    }
  }

  override fun dispose() {}

  override fun connectToTty(ttyConnector: TtyConnector, initialTermSize: TermSize) {
    error("connectToTty is not supported in ReworkedTerminalView")
  }

  private fun configureInlineCompletion(editor: EditorEx, model: TerminalOutputModel, coroutineScope: CoroutineScope, parentDisposable: Disposable) {
    InlineCompletion.install(editor, coroutineScope)
    // Inline completion handler needs to be manually disposed
    Disposer.register(parentDisposable) {
      InlineCompletion.remove(editor)
    }

    model.addListener(parentDisposable, object : TerminalOutputModelListener {
      var commandText: String? = null
      var cursorPosition: Int? = null

      override fun afterContentChanged(model: TerminalOutputModel, startOffset: Int, isTypeAhead: Boolean) {
        val inlineCompletionTypingSession = InlineCompletion.getHandlerOrNull(editor)?.typingSessionTracker
        val lastBlock = editor.getUserData(TerminalBlocksModel.KEY)?.blocks?.lastOrNull() ?: return
        val lastBlockCommandStartIndex = if (lastBlock.commandStartOffset != -1) lastBlock.commandStartOffset else lastBlock.startOffset

        // When resizing the terminal, the blocks model may fall out of sync for a short time.
        // These updates will never trigger a completion, so we return early to avoid reading out of bounds.
        if (lastBlockCommandStartIndex >= editor.document.textLength) return
        val curCommandText = editor.document.text.substring(lastBlockCommandStartIndex, editor.document.textLength).trim()

        if (isTypeAhead) {
          // Trim because of differing whitespace between terminal and type ahead
          commandText = curCommandText
          editor.caretModel.moveToOffset(outputModel.cursorOffsetState.value + 1)
          inlineCompletionTypingSession?.ignoreDocumentChanges = true
          inlineCompletionTypingSession?.endTypingSession(editor)
          cursorPosition = outputModel.cursorOffsetState.value + 1
        }
        else if (commandText != null && (curCommandText != commandText || cursorPosition != outputModel.cursorOffsetState.value)) {
          inlineCompletionTypingSession?.ignoreDocumentChanges = false
          inlineCompletionTypingSession?.collectTypedCharOrInvalidateSession(MockDocumentEvent(editor.document, 0), editor)
          commandText = null
        }
      }
    })
  }

  private inner class TerminalPanel(initialContent: Editor) : BorderLayoutPanel(), UiDataProvider, TerminalPanelMarker {
    private val layeredPane = TerminalLayeredPane(initialContent)
    private var curEditor: Editor = initialContent

    val preferredFocusableComponent: JComponent
      get() = layeredPane.preferredFocusableComponent

    private val delegatingFocusListener = object : FocusListener {
      override fun focusGained(e: FocusEvent) {
        focusListeners.forEach { it.focusGained(e) }
      }

      override fun focusLost(e: FocusEvent) {
        focusListeners.forEach { it.focusLost(e) }
      }
    }

    init {
      addToCenter(layeredPane)
      updateFocusListeners(initialContent, initialContent)
    }

    override fun uiDataSnapshot(sink: DataSink) {
      sink[CommonDataKeys.EDITOR] = curEditor
      sink[TerminalInput.DATA_KEY] = terminalInput
      sink[TerminalOutputModel.KEY] = outputModel
      sink[TerminalSearchController.KEY] = terminalSearchController
      sink[TerminalSessionId.KEY] = (sessionFuture.getNow(null) as? FrontendTerminalSession?)?.id
      sink[IS_ALTERNATE_BUFFER_KEY] = isAlternateScreenBuffer
      val hyperlinkFacade = if (isAlternateScreenBuffer) alternateBufferHyperlinkFacade else outputHyperlinkFacade
      sink[TerminalHyperlinkId.KEY] = hyperlinkFacade?.getHoveredHyperlinkId()
    }

    fun setTerminalContent(editor: Editor) {
      layeredPane.setTerminalContent(editor)
      updateFocusListeners(curEditor, editor)
      curEditor = editor
    }

    fun installSearchComponent(component: SearchReplaceComponent) {
      layeredPane.installSearchComponent(component)
    }

    fun removeSearchComponent(component: SearchReplaceComponent) {
      layeredPane.removeSearchComponent(component)
    }

    fun setTopComponent(component: JComponent) {
      addToTop(component)
      revalidate()
      repaint()
    }

    fun remoteTopComponent(component: JComponent) {
      remove(component)
      revalidate()
      repaint()
    }

    private fun updateFocusListeners(prevEditor: Editor, newEditor: Editor) {
      prevEditor.contentComponent.removeFocusListener(delegatingFocusListener)
      newEditor.contentComponent.addFocusListener(delegatingFocusListener)
    }
  }

  private class TerminalLayeredPane(initialContent: Editor) : JBLayeredPane() {
    private var curEditor: Editor = initialContent

    val preferredFocusableComponent: JComponent
      get() = curEditor.contentComponent

    init {
      setTerminalContent(initialContent)
    }

    fun setTerminalContent(editor: Editor) {
      val prevEditor = curEditor
      @Suppress("SENSELESS_COMPARISON") // called from init when curEditor == null
      if (prevEditor != null) {
        remove(curEditor.component)
      }
      curEditor = editor
      addToLayer(editor.component, DEFAULT_LAYER)

      revalidate()
      repaint()
    }

    fun installSearchComponent(component: SearchReplaceComponent) {
      addToLayer(component, POPUP_LAYER)
      revalidate()
      repaint()
    }

    fun removeSearchComponent(component: SearchReplaceComponent) {
      remove(component)
      revalidate()
      repaint()
    }

    override fun getPreferredSize(): Dimension {
      return if (curEditor.document.textLength == 0) Dimension() else (curEditor as EditorImpl).preferredSize
    }

    override fun doLayout() {
      for (component in components) {
        when (component) {
          curEditor.component -> layoutEditor(component)
          is SearchReplaceComponent -> layoutSearchComponent(component)
        }
      }
    }

    private fun layoutEditor(component: Component) {
      component.setBounds(0, 0, width, height)
    }

    private fun layoutSearchComponent(component: Component) {
      val prefSize = component.preferredSize
      val maxSize = component.maximumSize
      val compWidth = minOf(width, prefSize.width, maxSize.width)
      val compHeight = min(prefSize.height, maxSize.height)
      component.setBounds(width - compWidth, 0, compWidth, compHeight)
    }
  }
}
