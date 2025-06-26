// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend

import com.intellij.codeInsight.completion.CompletionPhase
import com.intellij.codeInsight.highlighting.BackgroundHighlightingUtil
import com.intellij.find.SearchReplaceComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.actions.ChangeEditorFontSizeStrategy
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.SoftWrapModelImpl
import com.intellij.openapi.editor.impl.softwrap.EmptySoftWrapPainter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.observable.util.addFocusListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.TerminalFontSizeProvider
import com.intellij.terminal.frontend.fus.TerminalFusCursorPainterListener
import com.intellij.terminal.frontend.fus.TerminalFusFirstOutputListener
import com.intellij.terminal.session.TerminalSession
import com.intellij.ui.components.JBLayeredPane
import com.intellij.util.asDisposable
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import kotlinx.coroutines.*
import org.jetbrains.plugins.terminal.TerminalFontSettingsListener
import org.jetbrains.plugins.terminal.TerminalFontSettingsService
import org.jetbrains.plugins.terminal.TerminalFontSizeProviderImpl
import org.jetbrains.plugins.terminal.TerminalPanelMarker
import org.jetbrains.plugins.terminal.block.TerminalContentView
import org.jetbrains.plugins.terminal.block.output.TerminalOutputEditorInputMethodSupport
import org.jetbrains.plugins.terminal.block.output.TerminalTextHighlighter
import org.jetbrains.plugins.terminal.block.reworked.*
import org.jetbrains.plugins.terminal.block.reworked.hyperlinks.TerminalHyperlinkHighlighter
import org.jetbrains.plugins.terminal.block.ui.*
import org.jetbrains.plugins.terminal.block.ui.TerminalUi.useTerminalDefaultBackground
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils
import org.jetbrains.plugins.terminal.fus.FrontendLatencyService
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import java.awt.Component
import java.awt.Dimension
import java.awt.event.*
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import javax.swing.JScrollPane
import kotlin.math.min

internal class ReworkedTerminalView(
  private val project: Project,
  settings: JBTerminalSystemSettingsProviderBase,
  private val sessionFuture: CompletableFuture<TerminalSession>,
  startupFusInfo: TerminalStartupFusInfo?,
) : TerminalContentView {
  private val coroutineScope = terminalProjectScope(project).childScope("ReworkedTerminalView")

  private val sessionModel: TerminalSessionModel
  private val blocksModel: TerminalBlocksModel
  private val encodingManager: TerminalKeyEncodingManager
  private val controller: TerminalSessionController

  private val terminalInput: TerminalInput
  private val terminalSearchController: TerminalSearchController

  private val outputEditor: EditorEx
  private val alternateBufferEditor: EditorEx
  private val outputModel: TerminalOutputModelImpl
  private val scrollingModel: TerminalOutputScrollingModel

  private val terminalPanel: TerminalPanel

  override val component: JComponent
    get() = terminalPanel
  override val preferredFocusableComponent: JComponent
    get() = terminalPanel.preferredFocusableComponent

  init {
    Disposer.register(this) {
      coroutineScope.cancel()
    }

    sessionModel = TerminalSessionModelImpl()
    encodingManager = TerminalKeyEncodingManager(sessionModel, coroutineScope.childScope("TerminalKeyEncodingManager"))

    terminalInput = TerminalInput(sessionFuture, sessionModel, startupFusInfo, coroutineScope.childScope("TerminalInput"))

    // Use the same instance of the listeners for both editors to report the metrics only once.
    // Usually, the cursor is painted or output received first in the output editor
    // because it is shown by default on a new session opening.
    // But in the case of session restoration in RemDev, there can be an alternate buffer.
    val fusCursorPaintingListener = startupFusInfo?.let { TerminalFusCursorPainterListener(it) }
    val fusFirstOutputListener = startupFusInfo?.let { TerminalFusFirstOutputListener(it) }

    alternateBufferEditor = createAlternateBufferEditor(settings, parentDisposable = this)
    val alternateBufferModel = TerminalOutputModelImpl(alternateBufferEditor.document, maxOutputLength = 0)
    configureOutputEditor(
      project,
      editor = alternateBufferEditor,
      model = alternateBufferModel,
      settings,
      sessionModel,
      encodingManager,
      terminalInput,
      typeAhead = null,
      coroutineScope.childScope("TerminalAlternateBufferModel"),
      scrollingModel = null,
      fusCursorPaintingListener,
      fusFirstOutputListener,
      withTopAndBottomInsets = false,
    )

    outputEditor = createOutputEditor(settings, parentDisposable = this)
    outputEditor.putUserData(TerminalInput.KEY, terminalInput)
    outputModel = TerminalOutputModelImpl(outputEditor.document, maxOutputLength = TerminalUiUtils.getDefaultMaxOutputLength())
    updatePsiOnOutputModelChange(project, outputModel, coroutineScope.childScope("TerminalOutputPsiUpdater"))

    scrollingModel = TerminalOutputScrollingModelImpl(outputEditor, outputModel, sessionModel, coroutineScope.childScope("TerminalOutputScrollingModel"))
    outputEditor.putUserData(TerminalOutputScrollingModel.KEY, scrollingModel)

    val typeAhead = TerminalTypeAhead(outputModel)
    configureOutputEditor(
      project,
      editor = outputEditor,
      model = outputModel,
      settings,
      sessionModel,
      encodingManager,
      terminalInput,
      typeAhead,
      coroutineScope.childScope("TerminalOutputModel"),
      scrollingModel,
      fusCursorPaintingListener,
      fusFirstOutputListener,
      withTopAndBottomInsets = true,
    )

    outputEditor.putUserData(TerminalSessionModel.KEY, sessionModel)
    terminalSearchController = TerminalSearchController(project)

    blocksModel = TerminalBlocksModelImpl(outputEditor.document)
    TerminalBlocksDecorator(outputEditor, blocksModel, scrollingModel, coroutineScope.childScope("TerminalBlocksDecorator"))
    outputEditor.putUserData(TerminalBlocksModel.KEY, blocksModel)

    outputEditor.putUserData(CompletionPhase.CUSTOM_CODE_COMPLETION_ACTION_ID, "Terminal.CommandCompletion")

    val fusActivity = FrontendLatencyService.getInstance().startFrontendOutputActivity(
      outputEditor = outputEditor as EditorImpl,
      alternateBufferEditor = alternateBufferEditor as EditorImpl,
    )

    val terminalAliasesStorage = TerminalAliasesStorage()

    controller = TerminalSessionController(
      project,
      sessionModel,
      outputModel,
      alternateBufferModel,
      blocksModel,
      settings,
      coroutineScope.childScope("TerminalSessionController"),
      fusActivity,
      terminalAliasesStorage
    )
    outputEditor.putUserData(TerminalAliasesStorage.KEY, terminalAliasesStorage)
    controller.addShellIntegrationListener(this, typeAhead)

    sessionFuture.thenAccept { session ->
      controller.handleEvents(session)
    }

    terminalPanel = TerminalPanel(initialContent = outputEditor)

    listenSearchController()
    listenPanelSizeChanges()
    listenAlternateBufferSwitch()

    TerminalVfsSynchronizer.install(controller, ::addFocusListener, this)
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
      var isAlternateScreenBuffer = false
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
    editor: EditorEx,
    model: TerminalOutputModel,
    settings: JBTerminalSystemSettingsProviderBase,
    sessionModel: TerminalSessionModel,
    encodingManager: TerminalKeyEncodingManager,
    terminalInput: TerminalInput,
    typeAhead: TerminalTypeAhead?,
    coroutineScope: CoroutineScope,
    scrollingModel: TerminalOutputScrollingModel?,
    fusCursorPainterListener: TerminalFusCursorPainterListener?,
    fusFirstOutputListener: TerminalFusFirstOutputListener?,
    withTopAndBottomInsets: Boolean,
  ) {
    val parentDisposable = coroutineScope.asDisposable() // same lifecycle as `this@ReworkedTerminalView`

    // Document modifications can change the scroll position.
    // Mark them with the corresponding flag to indicate that this change is not caused by the explicit user action.
    model.addListener(parentDisposable, object : TerminalOutputModelListener {
      override fun beforeContentChanged(model: TerminalOutputModel) {
        editor.isTerminalOutputScrollChangingActionInProgress = true
      }

      override fun afterContentChanged(model: TerminalOutputModel, startOffset: Int) {
        editor.isTerminalOutputScrollChangingActionInProgress = false

        // Also repaint the changed part of the document to ensure that highlightings are properly painted.
        editor.repaint(startOffset, editor.document.textLength)
      }
    })

    if (fusFirstOutputListener != null) {
      model.addListener(parentDisposable, fusFirstOutputListener)
    }

    editor.highlighter = TerminalTextHighlighter { model.getHighlightings() }

    TerminalHyperlinkHighlighter.install(project, model, editor, coroutineScope)

    val cursorPainter = TerminalCursorPainter.install(editor, model, sessionModel, coroutineScope.childScope("TerminalCursorPainter"))
    if (fusCursorPainterListener != null) {
      cursorPainter.addListener(parentDisposable, fusCursorPainterListener)
    }

    if (withTopAndBottomInsets) {
      addTopAndBottomInsets(editor)
    }

    val eventsHandler = TerminalEventsHandlerImpl(sessionModel, editor, encodingManager, terminalInput, settings, scrollingModel, model, typeAhead)
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

    CopyOnSelectionHandler.install(editor, settings)

    (editor.softWrapModel as? SoftWrapModelImpl)?.setSoftWrapPainter(EmptySoftWrapPainter)
  }

  private fun addTopAndBottomInsets(editor: Editor) {
    val inlayModel = editor.inlayModel

    val topRenderer = VerticalSpaceInlayRenderer(TerminalUi.blockTopInset)
    inlayModel.addBlockElement(0, false, true, TerminalUi.terminalTopInlayPriority, topRenderer)!!

    val bottomRenderer = VerticalSpaceInlayRenderer(TerminalUi.blockBottomInset)
    inlayModel.addBlockElement(editor.document.textLength, true, false, TerminalUi.terminalBottomInlayPriority, bottomRenderer)
  }

  private fun createOutputEditor(settings: JBTerminalSystemSettingsProviderBase, parentDisposable: Disposable): EditorEx {
    val document = createDocument(withLanguage = true)
    val editor = createEditor(document, settings, parentDisposable)
    editor.putUserData(TerminalDataContextUtils.IS_OUTPUT_MODEL_EDITOR_KEY, true)
    editor.settings.isUseSoftWraps = true
    editor.useTerminalDefaultBackground(parentDisposable = this)

    BackgroundHighlightingUtil.disableBackgroundHighlightingForeverIn(editor)
    TextEditorProvider.putTextEditor(editor, TerminalOutputTextEditor(editor))

    Disposer.register(parentDisposable) {
      EditorFactory.getInstance().releaseEditor(editor)
    }
    return editor
  }

  private fun createAlternateBufferEditor(settings: JBTerminalSystemSettingsProviderBase, parentDisposable: Disposable): EditorEx {
    val document = createDocument(withLanguage = false)
    val editor = createEditor(document, settings, parentDisposable)
    editor.putUserData(TerminalDataContextUtils.IS_ALTERNATE_BUFFER_MODEL_EDITOR_KEY, true)
    editor.useTerminalDefaultBackground(parentDisposable = this)
    editor.scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_NEVER
    editor.scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER

    Disposer.register(parentDisposable) {
      EditorFactory.getInstance().releaseEditor(editor)
    }
    return editor
  }

  private fun createEditor(
    document: Document,
    settings: JBTerminalSystemSettingsProviderBase,
    parentDisposable: Disposable,
  ): EditorImpl {
    val result = TerminalUiUtils.createOutputEditor(document, project, settings, installContextMenu = false)

    result.contextMenuGroupId = "Terminal.ReworkedTerminalContextMenu"
    result.softWrapModel.applianceManager.setLineWrapPositionStrategy(TerminalLineWrapPositionStrategy())
    result.softWrapModel.applianceManager.setSoftWrapsUnderScrollBar(true)

    result.putUserData(ChangeEditorFontSizeStrategy.KEY, ChangeTerminalFontSizeStrategy)
    result.putUserData(TerminalFontSizeProvider.KEY, TerminalFontSizeProviderImpl.getInstance())

    val fontSettingsListener = object : TerminalFontSettingsListener {
      override fun fontSettingsChanged() {
        result.applyFontSettings(settings)
        result.reinitSettings()
        result.resizeIfShowing()
      }
    }
    TerminalFontSettingsService.getInstance().addListener(fontSettingsListener, parentDisposable)

    TerminalFontSizeProviderImpl.getInstance().addListener(parentDisposable, object : TerminalFontSizeProvider.Listener {
      override fun fontChanged(showZoomIndicator: Boolean) {
        result.setTerminalFontSize(
          fontSize = TerminalFontSizeProviderImpl.getInstance().getFontSize(),
          showZoomIndicator = showZoomIndicator,
        )
        result.resizeIfShowing()
      }
    })

    return result
  }

  private fun EditorImpl.resizeIfShowing() {
    if (component.isShowing) { // to avoid sending the resize event twice, for the regular and alternate buffer editors
      sendResizeEvent()
    }
  }

  private fun createDocument(withLanguage: Boolean): Document {
    return if (withLanguage) {
      FileDocumentManager.getInstance().getDocument(TerminalOutputVirtualFile())!!
    }
    else DocumentImpl("", true)
  }

  override fun dispose() {}

  override fun connectToTty(ttyConnector: TtyConnector, initialTermSize: TermSize) {
    error("connectToTty is not supported in ReworkedTerminalView")
  }

  private fun addFocusListener(parentDisposable: Disposable, listener: FocusListener) {
    terminalPanel.addFocusListener(parentDisposable, listener)
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
