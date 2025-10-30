package com.intellij.terminal.frontend.view.impl

import com.intellij.codeInsight.completion.CompletionPhase
import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.MockDocumentEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.isFocusAncestor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiDocumentManager
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.actions.TerminalActionUtil
import com.intellij.terminal.frontend.fus.TerminalFusCursorPainterListener
import com.intellij.terminal.frontend.fus.TerminalFusFirstOutputListener
import com.intellij.terminal.frontend.view.TerminalTextSelectionModel
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.intellij.terminal.frontend.view.completion.ShellDataGeneratorsExecutorReworkedImpl
import com.intellij.terminal.frontend.view.completion.ShellRuntimeContextProviderReworkedImpl
import com.intellij.terminal.frontend.view.hyperlinks.FrontendTerminalHyperlinkFacade
import com.intellij.ui.components.JBLayeredPane
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.asDisposable
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.text.nullize
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.TerminalPanelMarker
import org.jetbrains.plugins.terminal.block.completion.ShellCommandSpecsManagerImpl
import org.jetbrains.plugins.terminal.block.completion.spec.impl.TerminalCommandCompletionServices
import org.jetbrains.plugins.terminal.block.output.TerminalOutputEditorInputMethodSupport
import org.jetbrains.plugins.terminal.block.output.TerminalTextHighlighter
import org.jetbrains.plugins.terminal.block.reworked.TerminalAiInlineCompletion
import org.jetbrains.plugins.terminal.block.reworked.TerminalAliasesStorage
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModelImpl
import org.jetbrains.plugins.terminal.block.reworked.lang.TerminalOutputPsiFile
import org.jetbrains.plugins.terminal.block.reworked.session.FrontendTerminalSession
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils
import org.jetbrains.plugins.terminal.block.ui.addToLayer
import org.jetbrains.plugins.terminal.block.ui.calculateTerminalSize
import org.jetbrains.plugins.terminal.block.ui.isTerminalOutputScrollChangingActionInProgress
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo
import org.jetbrains.plugins.terminal.session.TerminalGridSize
import org.jetbrains.plugins.terminal.session.TerminalStartupOptions
import org.jetbrains.plugins.terminal.session.impl.TerminalHyperlinkId
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.view.*
import org.jetbrains.plugins.terminal.view.impl.*
import org.jetbrains.plugins.terminal.view.shellIntegration.*
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import kotlin.math.min

@Suppress("TestOnlyProblems")
@ApiStatus.Internal
class TerminalViewImpl(
  private val project: Project,
  settings: JBTerminalSystemSettingsProviderBase,
  startupFusInfo: TerminalStartupFusInfo?,
  override val coroutineScope: CoroutineScope,
) : TerminalView {
  private val sessionFuture: CompletableFuture<TerminalSession> = CompletableFuture()

  @VisibleForTesting
  val sessionModel: TerminalSessionModel

  private val encodingManager: TerminalKeyEncodingManager
  private val controller: TerminalSessionController

  private val terminalInput: TerminalInput
  private val terminalSearchController: TerminalSearchController

  @VisibleForTesting
  val outputEditor: EditorEx
  private val outputHyperlinkFacade: FrontendTerminalHyperlinkFacade
  private val alternateBufferEditor: EditorEx

  private val alternateBufferHyperlinkFacade: FrontendTerminalHyperlinkFacade
  private val scrollingModel: TerminalOutputScrollingModel
  private var isAlternateScreenBuffer = false

  private val terminalPanel: TerminalPanel
  @VisibleForTesting
  val outputEditorEventsHandler: TerminalEventsHandler

  @VisibleForTesting
  val shellIntegrationFeaturesInitJob: Job

  override val component: JComponent
    get() = terminalPanel
  override val preferredFocusableComponent: JComponent
    get() = terminalPanel.preferredFocusableComponent
  override val gridSize: TerminalGridSize?
    get() = getCurEditor().calculateTerminalSize()
  override val title: TerminalTitle = TerminalTitle()

  private val mutableOutputModels: TerminalOutputModelsSetImpl
  override val outputModels: TerminalOutputModelsSet

  override val textSelectionModel: TerminalTextSelectionModel

  private val mutableSessionState: MutableStateFlow<TerminalViewSessionState> = MutableStateFlow(TerminalViewSessionState.NotStarted)
  override val sessionState: StateFlow<TerminalViewSessionState> = mutableSessionState.asStateFlow()

  override val shellIntegrationDeferred: CompletableDeferred<TerminalShellIntegration> = CompletableDeferred(coroutineScope.coroutineContext.job)
  override val startupOptionsDeferred: CompletableDeferred<TerminalStartupOptions> = CompletableDeferred(coroutineScope.coroutineContext.job)

  init {
    // Cancell the hanging callbacks that wait for future completion if the coroutine scope is cancelled.
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      sessionFuture.cancel(true)
    }

    val hyperlinkScope = coroutineScope.childScope("TerminalViewImpl hyperlink facades")

    sessionModel = TerminalSessionModelImpl()
    encodingManager = TerminalKeyEncodingManager(sessionModel, coroutineScope.childScope("TerminalKeyEncodingManager"))

    terminalInput = TerminalInput(sessionFuture, sessionModel, startupFusInfo, coroutineScope.childScope("TerminalInput"), encodingManager)

    // Use the same instance of the listeners for both editors to report the metrics only once.
    // Usually, the cursor is painted or output received first in the output editor
    // because it is shown by default on a new session opening.
    // But in the case of session restoration in RemDev, there can be an alternate buffer.
    val fusCursorPaintingListener = startupFusInfo?.let { TerminalFusCursorPainterListener(it) }
    val fusFirstOutputListener = startupFusInfo?.let { TerminalFusFirstOutputListener(it) }

    alternateBufferEditor = TerminalEditorFactory.createAlternateBufferEditor(
      project,
      settings,
      coroutineScope.childScope("TerminalAlternateBufferEditor")
    )
    val alternateBufferModel = MutableTerminalOutputModelImpl(alternateBufferEditor.document, maxOutputLength = 0)
    val alternateBufferModelController = TerminalOutputModelControllerImpl(alternateBufferModel)
    val alternateBufferEventsHandler = TerminalEventsHandlerImpl(
      sessionModel,
      alternateBufferEditor,
      encodingManager,
      terminalInput,
      settings,
      scrollingModel = null,
      alternateBufferModel,
      shellIntegrationDeferred = null,
      startupOptionsDeferred = null,
      typeAhead = null,
    )
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
    alternateBufferHyperlinkFacade = FrontendTerminalHyperlinkFacade(
      isInAlternateBuffer = true,
      editor = alternateBufferEditor,
      outputModel = alternateBufferModel,
      terminalInput = terminalInput,
      coroutineScope = hyperlinkScope,
    )

    outputEditor = TerminalEditorFactory.createOutputEditor(project, settings, coroutineScope.childScope("TerminalOutputEditor"))
    outputEditor.putUserData(TerminalInput.KEY, terminalInput)
    val outputModel = MutableTerminalOutputModelImpl(outputEditor.document, maxOutputLength = TerminalUiUtils.getDefaultMaxOutputLength())

    scrollingModel = TerminalOutputScrollingModelImpl(outputEditor, outputModel, sessionModel,
                                                      coroutineScope.childScope("TerminalOutputScrollingModel"))
    outputEditor.putUserData(TerminalOutputScrollingModel.KEY, scrollingModel)

    val outputModelController = TerminalTypeAheadOutputModelController(
      project,
      outputModel,
      shellIntegrationDeferred,
      coroutineScope.childScope("TerminalTypeAheadOutputModelController")
    )
    outputEditor.putUserData(TerminalTypeAhead.KEY, outputModelController)

    outputEditorEventsHandler = TerminalEventsHandlerImpl(
      sessionModel,
      outputEditor,
      encodingManager,
      terminalInput,
      settings,
      scrollingModel,
      outputModel,
      shellIntegrationDeferred,
      startupOptionsDeferred,
      typeAhead = outputModelController
    )

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

    mutableOutputModels = TerminalOutputModelsSetImpl(outputModel, alternateBufferModel)
    outputModels = mutableOutputModels

    textSelectionModel = TerminalTextSelectionModelImpl(
      outputModels,
      outputEditor,
      alternateBufferEditor,
      coroutineScope.childScope("TerminalTextSelectionModel")
    )

    terminalSearchController = TerminalSearchController(project)

    outputHyperlinkFacade = FrontendTerminalHyperlinkFacade(
      isInAlternateBuffer = false,
      editor = outputEditor,
      outputModel = outputModel,
      terminalInput = terminalInput,
      coroutineScope = hyperlinkScope,
    )

    outputEditor.putUserData(CompletionPhase.CUSTOM_CODE_COMPLETION_ACTION_ID, "Terminal.CommandCompletion.Invoke")

    val terminalAliasesStorage = TerminalAliasesStorage()
    outputEditor.putUserData(TerminalAliasesStorage.KEY, terminalAliasesStorage)

    controller = TerminalSessionController(
      sessionModel,
      outputModelController,
      outputHyperlinkFacade,
      alternateBufferModelController,
      alternateBufferHyperlinkFacade,
      startupOptionsDeferred,
      settings,
      coroutineScope.childScope("TerminalSessionController")
    )
    val shellIntegrationEventsHandler = TerminalShellIntegrationEventsHandler(
      outputModelController,
      sessionModel,
      shellIntegrationDeferred,
      terminalAliasesStorage,
      coroutineScope.childScope("TerminalShellIntegrationEventsHandler"),
    )
    controller.addEventsHandler(shellIntegrationEventsHandler)

    controller.addTerminationCallback(coroutineScope.asDisposable()) {
      mutableSessionState.value = TerminalViewSessionState.Terminated
    }

    terminalPanel = TerminalPanel(initialContent = outputEditor)

    listenSearchController()
    listenPanelSizeChanges()
    listenAlternateBufferSwitch()

    val synchronizer = TerminalVfsSynchronizer(
      shellIntegrationDeferred,
      outputModel,
      terminalPanel,
      coroutineScope.childScope("TerminalVfsSynchronizer"),
    )
    outputEditor.putUserData(TerminalVfsSynchronizer.KEY, synchronizer)

    shellIntegrationFeaturesInitJob = coroutineScope.launch(
      Dispatchers.EDT +
      ModalityState.any().asContextElement() +
      CoroutineName("Shell integration features init")
    ) {
      val shellIntegration = shellIntegrationDeferred.await()

      outputEditor.putUserData(TerminalBlocksModel.KEY, shellIntegration.blocksModel)
      TerminalBlocksDecorator(
        outputEditor,
        outputModel,
        shellIntegration.blocksModel,
        scrollingModel,
        coroutineScope.childScope("TerminalBlocksDecorator")
      )

      if (TerminalAiInlineCompletion.isEnabled()) {
        configureInlineCompletion(
          outputEditor,
          outputModel,
          shellIntegration,
          coroutineScope.childScope("TerminalInlineCompletion")
        )
      }

      configureCommandCompletion(
        outputEditor,
        sessionModel,
        shellIntegration,
        coroutineScope.childScope("TerminalCommandCompletion")
      )
    }
  }

  fun connectToSession(session: TerminalSession) {
    sessionFuture.complete(session)
    controller.handleEvents(session)
    mutableSessionState.value = TerminalViewSessionState.Running
  }

  override suspend fun hasChildProcesses(): Boolean {
    val session = sessionFuture.getNow(null) ?: return false
    return withContext(Dispatchers.IO) {
      session.hasRunningCommands()
    }
  }

  override fun getCurrentDirectory(): String? {
    // The initial value of the current directory is an empty string, return null in this case.
    return sessionModel.terminalState.value.currentDirectory.nullize()
  }

  override fun sendText(text: String) {
    createSendTextBuilder().send(text)
  }

  override fun createSendTextBuilder(): TerminalSendTextBuilder {
    return TerminalSendTextBuilderImpl(this::doSendText)
  }

  private fun doSendText(options: TerminalSendTextOptions) {
    terminalInput.sendText(options)
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
        terminalPanel.installSearchComponent(session.wrapper)
      }

      override fun searchSessionFinished(session: TerminalSearchSession) {
        terminalPanel.removeSearchComponent(session.wrapper)
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
    val newSize = gridSize ?: return
    terminalInput.sendResize(newSize)
  }

  private fun listenAlternateBufferSwitch() {
    coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement() + CoroutineName("Alternate buffer switch listener")) {
      sessionModel.terminalState.collect { state ->
        if (state.isAlternateScreenBuffer != isAlternateScreenBuffer) {
          isAlternateScreenBuffer = state.isAlternateScreenBuffer

          val terminalWasFocused = terminalPanel.isFocusAncestor()
          val editor = if (state.isAlternateScreenBuffer) alternateBufferEditor else outputEditor
          terminalPanel.setTerminalContent(editor)
          terminalSearchController.finishSearchSession()
          mutableOutputModels.setActiveModel(state.isAlternateScreenBuffer)

          if (terminalWasFocused) {
            IdeFocusManager.getInstance(project).requestFocus(terminalPanel.preferredFocusableComponent, true)
          }
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

    editor.putUserData(TerminalOutputModel.KEY, model)

    // Document modifications can change the scroll position.
    // Mark them with the corresponding flag to indicate that this change is not caused by the explicit user action.
    model.addListener(parentDisposable, object : TerminalOutputModelListener {
      override fun beforeContentChanged(model: TerminalOutputModel) {
        editor.isTerminalOutputScrollChangingActionInProgress = true
      }

      override fun afterContentChanged(event: TerminalContentChangeEvent) {
        editor.isTerminalOutputScrollChangingActionInProgress = false

        // Repaint the whole screen to update all changed highlightings.
        repaintEditorScreen(editor)

        // Update the PSI file content
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile((model as MutableTerminalOutputModel).document) as? TerminalOutputPsiFile
        psiFile?.charsSequence = model.document.immutableCharSequence  // must be the snapshot
      }
    })

    if (fusFirstOutputListener != null) {
      model.addListener(parentDisposable, fusFirstOutputListener)
    }

    editor.highlighter = TerminalTextHighlighter { model.getHighlightings() }

    val cursorPainter = TerminalCursorPainter.install(editor, model, sessionModel, coroutineScope.childScope("TerminalCursorPainter"))
    if (fusCursorPainterListener != null) {
      cursorPainter.addListener(parentDisposable, fusCursorPainterListener)
    }

    setupKeyEventHandling(editor, settings, eventsHandler, parentDisposable)
    setupMouseListener(editor, sessionModel, settings, eventsHandler, parentDisposable)

    TerminalOutputEditorInputMethodSupport(
      editor,
      coroutineScope = coroutineScope.childScope("TerminalInputMethodSupport"),
      getCaretPosition = {
        val offset = model.cursorOffset.toRelative(model)
        editor.offsetToLogicalPosition(offset)
      },
      cursorOffsetFlow = model.cursorOffsetFlow.map { it.toRelative(model) },
      sendInputString = { text -> terminalInput.sendString(text) },
    )

    TerminalEditorFactory.listenEditorFontChanges(editor, settings, parentDisposable) {
      editor.resizeIfShowing()
    }
  }

  private fun repaintEditorScreen(editor: EditorEx) {
    val document = editor.document
    if (document.textLength == 0 || document.lineCount == 0) return

    val visibleArea = editor.scrollingModel.visibleArea
    val screenTopLine = editor.xyToLogicalPosition(visibleArea.location).line
    val screenBottomPoint = Point(visibleArea.x + visibleArea.width, visibleArea.y + visibleArea.height)
    val screenBottomLine = editor.xyToLogicalPosition(screenBottomPoint).line.coerceAtMost(document.lineCount - 1)
    val screenStartOffset = editor.document.getLineStartOffset(screenTopLine)
    val screenEndOffset = editor.document.getLineEndOffset(screenBottomLine)
    editor.repaint(screenStartOffset, screenEndOffset)
  }

  private fun EditorImpl.resizeIfShowing() {
    if (component.isShowing) { // to avoid sending the resize event twice, for the regular and alternate buffer editors
      sendResizeEvent()
    }
  }

  @OptIn(AwaitCancellationAndInvoke::class)
  private fun configureInlineCompletion(
    editor: EditorEx,
    model: TerminalOutputModel,
    shellIntegration: TerminalShellIntegration,
    coroutineScope: CoroutineScope,
  ) {
    InlineCompletion.install(editor, coroutineScope)
    // Inline completion handler needs to be manually disposed
    coroutineScope.awaitCancellationAndInvoke(Dispatchers.UiWithModelAccess) {
      InlineCompletion.remove(editor)
    }

    model.addListener(coroutineScope.asDisposable(), object : TerminalOutputModelListener {
      var commandText: String? = null
      var cursorPosition: Int? = null

      override fun afterContentChanged(event: TerminalContentChangeEvent) {
        if (shellIntegration.outputStatus.value != TerminalOutputStatus.TypingCommand) {
          commandText = null
          cursorPosition = null
          return
        }

        val commandBlock = shellIntegration.blocksModel.activeBlock as? TerminalCommandBlock ?: return
        val curCommandText = commandBlock.getTypedCommandText(model) ?: return

        val inlineCompletionTypingSession = InlineCompletion.getHandlerOrNull(editor)?.typingSessionTracker
        if (event.isTypeAhead) {
          // Trim because of differing whitespace between terminal and type ahead
          commandText = curCommandText
          val newCursorOffset = model.cursorOffset.toRelative(model) + 1
          editor.caretModel.moveToOffset(newCursorOffset)
          inlineCompletionTypingSession?.ignoreDocumentChanges = true
          inlineCompletionTypingSession?.endTypingSession(editor)
          cursorPosition = newCursorOffset
        }
        else if (commandText != null && (curCommandText != commandText || cursorPosition != model.cursorOffset.toRelative(model))) {
          inlineCompletionTypingSession?.ignoreDocumentChanges = false
          inlineCompletionTypingSession?.collectTypedCharOrInvalidateSession(MockDocumentEvent(editor.document, 0), editor)
          commandText = null
        }
      }
    })
  }

  private fun configureCommandCompletion(
    editor: Editor,
    sessionModel: TerminalSessionModel,
    shellIntegration: TerminalShellIntegration,
    coroutineScope: CoroutineScope,
  ) {
    val eelDescriptor = LocalEelDescriptor // TODO: it should be determined by where shell is running to work properly in WSL and Docker
    val services = TerminalCommandCompletionServices(
      commandSpecsManager = ShellCommandSpecsManagerImpl.getInstance(),
      runtimeContextProvider = ShellRuntimeContextProviderReworkedImpl(project, sessionModel, eelDescriptor),
      dataGeneratorsExecutor = ShellDataGeneratorsExecutorReworkedImpl(
        shellIntegration,
        coroutineScope.childScope("ShellDataGeneratorsExecutorReworkedImpl")
      )
    )
    editor.putUserData(TerminalCommandCompletionServices.KEY, services)
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

    @Suppress("unused")
    @ApiStatus.Internal
    @VisibleForTesting
    fun getActiveOutputModel(): TerminalOutputModel {
      return outputModels.active.value
    }

    override fun uiDataSnapshot(sink: DataSink) {
      sink[TerminalView.DATA_KEY] = this@TerminalViewImpl
      sink[TerminalActionUtil.EDITOR_KEY] = curEditor
      sink[TerminalInput.DATA_KEY] = terminalInput
      sink[TerminalOutputModel.DATA_KEY] = outputModels.active.value
      sink[TerminalSearchController.KEY] = terminalSearchController
      sink[TerminalSessionId.KEY] = (sessionFuture.getNow(null) as? FrontendTerminalSession?)?.id
      sink[TerminalDataContextUtils.IS_ALTERNATE_BUFFER_DATA_KEY] = isAlternateScreenBuffer
      val hyperlinkFacade = if (isAlternateScreenBuffer) alternateBufferHyperlinkFacade else outputHyperlinkFacade
      sink[TerminalHyperlinkId.KEY] = hyperlinkFacade.getHoveredHyperlinkId()
      sink.setNull(PlatformDataKeys.COPY_PROVIDER)
    }

    fun setTerminalContent(editor: Editor) {
      layeredPane.setTerminalContent(editor)
      updateFocusListeners(curEditor, editor)
      curEditor = editor
    }

    fun installSearchComponent(component: JComponent) {
      layeredPane.installSearchComponent(component)
    }

    fun removeSearchComponent(component: JComponent) {
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

    fun installSearchComponent(component: JComponent) {
      addToLayer(component, POPUP_LAYER)
      revalidate()
      repaint()
    }

    fun removeSearchComponent(component: JComponent) {
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
          else -> layoutSearchComponent(component)
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

internal fun TerminalOffset.toRelative(model: TerminalOutputModel): Int = (this - model.startOffset).toInt()

@get:ApiStatus.Internal
@get:VisibleForTesting
val TerminalOutputModel.cursorOffsetFlow: Flow<TerminalOffset>
  get() = callbackFlow {
    addListener(asDisposable(), object : TerminalOutputModelListener {
      override fun cursorOffsetChanged(event: TerminalCursorOffsetChangeEvent) {
        trySendBlocking(event.newOffset)
      }
    })
    awaitClose()
  }
