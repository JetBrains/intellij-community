package com.intellij.terminal.frontend.view.impl

import com.intellij.execution.impl.EditorTextDecorationApplier
import com.intellij.execution.impl.createEditorTextDecorationApplier
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.isFocusAncestor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.coroutines.flow.mapStateIn
import com.intellij.psi.PsiDocumentManager
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.actions.TerminalActionUtil
import com.intellij.terminal.frontend.fus.TerminalFusCursorPainterListener
import com.intellij.terminal.frontend.fus.TerminalFusFirstOutputListener
import com.intellij.terminal.frontend.view.TerminalKeyEvent
import com.intellij.terminal.frontend.view.TerminalKeyEventsListener
import com.intellij.terminal.frontend.view.TerminalTextSelectionModel
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.intellij.terminal.frontend.view.completion.ShellDataGeneratorsExecutorReworkedImpl
import com.intellij.terminal.frontend.view.completion.ShellRuntimeContextProviderReworkedImpl
import com.intellij.terminal.frontend.view.completion.TerminalCommandCompletionTypingListener
import com.intellij.terminal.frontend.view.hyperlinks.FrontendTerminalHyperlinkFacade
import com.intellij.terminal.frontend.view.hyperlinks.installHyperlinksProcessing
import com.intellij.terminal.frontend.view.hyperlinks.installOsc8HyperlinksProcessing
import com.intellij.terminal.frontend.view.inlineCompletion.TerminalInlineCompletionController
import com.intellij.terminal.frontend.view.typeahead.TerminalTypeAhead
import com.intellij.terminal.frontend.view.typeahead.TerminalTypeAheadOutputModelController
import com.intellij.terminal.frontend.view.typeahead.TerminalTypeAheadOutputModelControllerV1
import com.intellij.terminal.frontend.view.typeahead.TerminalTypeAheadOutputModelControllerV2
import com.intellij.terminal.refreshVfsOnFocusChange
import com.intellij.ui.components.JBLayeredPane
import com.intellij.ui.components.panels.ListLayout
import com.intellij.util.asDisposable
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.TerminalPanelMarker
import org.jetbrains.plugins.terminal.block.completion.ShellCommandSpecsManagerImpl
import org.jetbrains.plugins.terminal.block.completion.spec.impl.TerminalCommandCompletionServices
import org.jetbrains.plugins.terminal.block.output.TerminalOutputEditorInputMethodSupport
import org.jetbrains.plugins.terminal.block.output.TerminalTextHighlighter
import org.jetbrains.plugins.terminal.block.reworked.TerminalAiInlineCompletion
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModelImpl
import org.jetbrains.plugins.terminal.block.reworked.lang.TerminalOutputPsiFile
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils
import org.jetbrains.plugins.terminal.block.ui.addToLayer
import org.jetbrains.plugins.terminal.block.ui.calculateTerminalSize
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkId
import org.jetbrains.plugins.terminal.hyperlinks.TerminalSourceNavigationInfo
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksSessionId
import org.jetbrains.plugins.terminal.session.TerminalGridSize
import org.jetbrains.plugins.terminal.session.TerminalStartupOptions
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.util.convertNativePathToNioPath
import org.jetbrains.plugins.terminal.util.getNow
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalCursorOffsetChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import org.jetbrains.plugins.terminal.view.TerminalOutputModelsSet
import org.jetbrains.plugins.terminal.view.TerminalSendTextBuilder
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModelImpl
import org.jetbrains.plugins.terminal.view.impl.TerminalOutputModelsSetImpl
import org.jetbrains.plugins.terminal.view.impl.TerminalSendTextBuilderImpl
import org.jetbrains.plugins.terminal.view.impl.TerminalSendTextOptions
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalBlocksModel
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellIntegration
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.KeyEvent
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.min

@Suppress("TestOnlyProblems")
@ApiStatus.Internal
class TerminalViewImpl(
  private val project: Project,
  settings: JBTerminalSystemSettingsProviderBase,
  startupFusInfo: TerminalStartupFusInfo?,
  override val coroutineScope: CoroutineScope,
  sourceNavigationProjectPath: String? = null,
) : TerminalView {
  override val sessionDeferred: CompletableDeferred<TerminalSession> = CompletableDeferred(coroutineScope.coroutineContext.job)

  @VisibleForTesting
  val sessionModel: TerminalSessionModel

  private val encodingManager: TerminalKeyEncodingManager
  private val controller: TerminalSessionController

  private val terminalInput: TerminalInput
  private val terminalSearchController: TerminalSearchController

  @VisibleForTesting
  val outputEditor: EditorEx
  private val alternateBufferEditor: EditorEx

  @VisibleForTesting
  val outputEditorDecorationApplier: EditorTextDecorationApplier

  private val scrollingModel: TerminalOutputScrollingModel
  private var isAlternateScreenBuffer = false

  private val terminalPanel: TerminalPanel

  @VisibleForTesting
  val outputEditorKeyEventsHandler: TerminalKeyEventsHandler

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

  private val mutableKeyEventsFlow = MutableSharedFlow<TerminalKeyEvent>(
    replay = 0,                 // Do not use replay cache because we don't need to send the last event to the new collector
    extraBufferCapacity = 100,  // Add some meaningful buffer for slow collectors.
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  override val keyEventsFlow: Flow<TerminalKeyEvent> = mutableKeyEventsFlow.asSharedFlow()
  private val keyEventsListeners = ContainerUtil.createLockFreeCopyOnWriteList<TerminalKeyEventsListener>().apply {
    add(object : TerminalKeyEventsListener {
      override fun afterKeyEvent(event: TerminalKeyEvent) {
        check(mutableKeyEventsFlow.tryEmit(event))
      }
    })
  }

  override val workingDirectoryFlow: StateFlow<Path?>

  override val shellIntegrationDeferred: CompletableDeferred<TerminalShellIntegration> =
    CompletableDeferred(coroutineScope.coroutineContext.job)
  override val startupOptionsDeferred: CompletableDeferred<TerminalStartupOptions> =
    CompletableDeferred(coroutineScope.coroutineContext.job)

  private var outputBufferHyperlinksFacade: FrontendTerminalHyperlinkFacade? = null
  private var alternateBufferHyperlinksFacade: FrontendTerminalHyperlinkFacade? = null

  init {
    sessionModel = TerminalSessionModelImpl()
    workingDirectoryFlow = sessionModel.terminalState.mapStateIn(coroutineScope.childScope("workingDirectoryFlow")) {
      val directoryString = it.currentDirectory ?: return@mapStateIn null
      val eelDescriptor = sessionDeferred.getNow()?.eelDescriptor ?: return@mapStateIn null
      convertNativePathToNioPath(directoryString, eelDescriptor)
    }

    encodingManager = TerminalKeyEncodingManager(sessionModel, coroutineScope.childScope("TerminalKeyEncodingManager"))

    terminalInput = TerminalInput(
      sessionDeferred,
      sessionModel,
      startupFusInfo,
      coroutineScope.childScope("TerminalInput"),
      encodingManager
    )

    // Use the same instance of the listeners for both editors to report the metrics only once.
    // Usually, the cursor is painted or output received first in the output editor
    // because it is shown by default on a new session opening.
    // But in the case of session restoration in RemDev, there can be an alternate buffer.
    val fusCursorPaintingListener = if (startupFusInfo?.triggerTime != null) {
      TerminalFusCursorPainterListener(startupFusInfo.triggerTime!!, startupFusInfo.way)
    }
    else null
    val fusFirstOutputListener = if (startupFusInfo?.triggerTime != null) {
      TerminalFusFirstOutputListener(startupFusInfo.triggerTime!!, startupFusInfo.way)
    }
    else null

    alternateBufferEditor = TerminalEditorFactory.createAlternateBufferEditor(
      project,
      settings,
      coroutineScope.childScope("TerminalAlternateBufferEditor")
    )
    TerminalSourceNavigationInfo.setProjectPath(alternateBufferEditor, sourceNavigationProjectPath)
    val alternateBufferModel = MutableTerminalOutputModelImpl(alternateBufferEditor.document, maxOutputLength = 0)
    val alternateBufferModelController = TerminalOutputModelControllerImpl(alternateBufferModel)
    val alternateBufferKeyEventsHandler = TerminalKeyEventsHandlerImpl(
      alternateBufferEditor,
      terminalInput,
      scrollingModel = null,
      alternateBufferModel,
      typeAhead = null,
      keyEventsListeners = keyEventsListeners,
      sessionDeferred = sessionDeferred,
      coroutineScope = coroutineScope.childScope("TerminalAlternateBufferKeyEvents"),
    )
    val alternateBufferMouseEventsHandler = TerminalMouseEventsHandlerImpl(
      alternateBufferEditor,
      terminalInput,
      sessionDeferred,
    )

    // Should be created before "configureOutputEditor" is called where mouse reporting is configured (TerminalMouseEventsHandlerImpl).
    // To make mouse events first handled by hyperlinks logic and only then reported to the process.
    val alternateBufferDecorationApplier = createEditorTextDecorationApplier(
      alternateBufferEditor, coroutineScope.asDisposable(), consumeOnlyOnCtrlClick = true,
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
      alternateBufferKeyEventsHandler,
      alternateBufferMouseEventsHandler,
    )

    outputEditor = TerminalEditorFactory.createOutputEditor(project, settings, coroutineScope.childScope("TerminalOutputEditor"))
    TerminalSourceNavigationInfo.setProjectPath(outputEditor, sourceNavigationProjectPath)
    outputEditor.putUserData(TerminalInput.KEY, terminalInput)
    val outputModel = MutableTerminalOutputModelImpl(outputEditor.document, maxOutputLength = TerminalUiUtils.getDefaultMaxOutputLength())

    scrollingModel = TerminalOutputScrollingModelImpl(outputEditor, outputModel, sessionModel,
                                                      coroutineScope.childScope("TerminalOutputScrollingModel"))
    outputEditor.putUserData(TerminalOutputScrollingModel.KEY, scrollingModel)

    val outputModelController = createOutputModelController(
      project,
      outputModel,
      shellIntegrationDeferred,
      coroutineScope.childScope("TerminalTypeAheadOutputModelController")
    )
    outputEditor.putUserData(TerminalTypeAhead.KEY, outputModelController)

    outputEditorKeyEventsHandler = TerminalKeyEventsHandlerImpl(
      outputEditor,
      terminalInput,
      scrollingModel,
      outputModel,
      typeAhead = outputModelController,
      keyEventsListeners = keyEventsListeners,
      sessionDeferred = sessionDeferred,
      coroutineScope = coroutineScope.childScope("TerminalOutputKeyEvents"),
    )
    val outputEditorMouseEventsHandler = TerminalMouseEventsHandlerImpl(
      outputEditor,
      terminalInput,
      sessionDeferred,
    )

    // Should be created before "configureOutputEditor" is called where mouse reporting is configured (TerminalMouseEventsHandlerImpl).
    // To make mouse events first handled by hyperlinks logic and only then reported to the process.
    outputEditorDecorationApplier = createEditorTextDecorationApplier(
      outputEditor, coroutineScope.asDisposable(), consumeOnlyOnCtrlClick = true,
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
      outputEditorKeyEventsHandler,
      outputEditorMouseEventsHandler,
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

    controller = TerminalSessionController(
      sessionModel,
      outputModelController,
      alternateBufferModelController,
      startupOptionsDeferred,
      settings,
      coroutineScope.childScope("TerminalSessionController")
    )
    val shellIntegrationEventsHandler = TerminalShellIntegrationEventsHandler(
      outputModelController,
      sessionModel,
      shellIntegrationDeferred,
      startupOptionsDeferred,
      coroutineScope.childScope("TerminalShellIntegrationEventsHandler"),
    )
    controller.addEventsHandler(shellIntegrationEventsHandler)

    controller.addTerminationCallback(coroutineScope.asDisposable()) {
      mutableSessionState.value = TerminalViewSessionState.Terminated
      // Hide the cursor on process termination
      val currentState = sessionModel.terminalState.value
      sessionModel.updateTerminalState(currentState.copy(isCursorVisible = false))
    }

    terminalPanel = TerminalPanel(initialContent = outputEditor)

    listenSearchController()
    listenPanelSizeChanges()
    listenAlternateBufferSwitch()
    listenApplicationTitleChanges()
    listenKeyEvents()

    refreshVfsOnFocusChange(
      component = terminalPanel,
      coroutineScope.childScope("Terminal VFS refresh on focus change"),
    )
    refreshVfsOnCommandFinish(
      terminalView = this,
      coroutineScope.childScope("Terminal VFS refresh on command finish")
    )

    // Configure hyperlinks' processing.
    // The filter-based and OSC8 hyperlinks of an editor must share a single decoration applier,
    // because its click/hover handling operates on editor-global markup.
    coroutineScope.launch {
      val eelDescriptor = sessionDeferred.await().eelDescriptor
      outputBufferHyperlinksFacade = installHyperlinksProcessing(
        project = project,
        outputModel = outputModel,
        decorationApplier = outputEditorDecorationApplier,
        sessionModel = sessionModel,
        eelDescriptor = eelDescriptor,
        coroutineScope = coroutineScope.childScope("Output Buffer Hyperlinks"),
      )
      alternateBufferHyperlinksFacade = installHyperlinksProcessing(
        project = project,
        outputModel = alternateBufferModel,
        decorationApplier = alternateBufferDecorationApplier,
        sessionModel = sessionModel,
        eelDescriptor = eelDescriptor,
        coroutineScope = coroutineScope.childScope("Alternate Buffer Hyperlinks"),
      )
    }

    // Configure OSC8 hyperlinks' processing (rendered via the same appliers as above).
    installOsc8HyperlinksProcessing(
      project = project,
      outputModel = outputModel,
      editor = outputEditor,
      applier = outputEditorDecorationApplier,
      coroutineScope = coroutineScope.childScope("Output Buffer OSC8 Hyperlinks"),
    )
    installOsc8HyperlinksProcessing(
      project = project,
      outputModel = alternateBufferModel,
      editor = alternateBufferEditor,
      applier = alternateBufferDecorationApplier,
      coroutineScope = coroutineScope.childScope("Alternate Buffer OSC8 Hyperlinks"),
    )

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
        configureInlineCompletion(outputModel, shellIntegration)
      }

      val startupOptions = startupOptionsDeferred.await()
      configureCommandCompletion(
        terminalView = this@TerminalViewImpl,
        outputEditor,
        sessionModel,
        shellIntegration,
        startupOptions.envVariables,
        coroutineScope.childScope("TerminalCommandCompletion")
      )
    }
  }

  fun connectToSession(session: TerminalSession) {
    sessionDeferred.complete(session)
    controller.handleEvents(session)
    mutableSessionState.value = TerminalViewSessionState.Running
  }

  override suspend fun hasChildProcesses(): Boolean {
    val session = sessionDeferred.getNow() ?: return false
    return withContext(Dispatchers.Default) {
      session.hasRunningCommands()
    }
  }

  override fun getCurrentDirectory(): String? {
    return sessionModel.terminalState.value.currentDirectory
  }

  override fun sendText(text: String) {
    createSendTextBuilder().send(text)
  }

  override fun createSendTextBuilder(): TerminalSendTextBuilder {
    return TerminalSendTextBuilderImpl(this::doSendText)
  }

  override fun addKeyEventsListener(parentDisposable: Disposable, listener: TerminalKeyEventsListener) {
    keyEventsListeners.add(listener)
    Disposer.register(parentDisposable) {
      keyEventsListeners.remove(listener)
    }
  }

  private fun doSendText(options: TerminalSendTextOptions): Boolean {
    return terminalInput.sendText(options)
  }

  override fun setTopComponent(component: JComponent, disposable: Disposable) {
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
      terminalPanel.removeTopComponent(component)
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

          // Check the whole terminal panel to not miss the case of focus in a search bar.
          // And check both editors because buffer change requests can arrive in a row, before the previous focus change is processed.
          val terminalWasFocused = terminalPanel.isFocusAncestor()
                                   || outputEditor.component.isFocusAncestor()
                                   || alternateBufferEditor.component.isFocusAncestor()
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

  private fun listenApplicationTitleChanges() {
    coroutineScope.launch {
      sessionModel.terminalState.collect { state ->
        title.change {
          @Suppress("HardCodedStringLiteral")
          applicationTitle = state.windowTitle
        }
      }
    }
  }

  /** Logic that can be performed asynchronously with typing */
  private fun listenKeyEvents() {
    coroutineScope.launch(Dispatchers.UI + CoroutineName("Key events listener")) {
      keyEventsFlow.collect { e ->
        if (e.awtEvent.id == KeyEvent.KEY_TYPED) {
          outputEditor.selectionModel.let { if (it.hasSelection()) it.removeSelection() }
          alternateBufferEditor.selectionModel.let { if (it.hasSelection()) it.removeSelection() }
        }
      }
    }
  }

  private fun createOutputModelController(
    project: Project,
    outputModel: MutableTerminalOutputModel,
    shellIntegrationDeferred: Deferred<TerminalShellIntegration>,
    coroutineScope: CoroutineScope,
  ): TerminalTypeAheadOutputModelController {
    return if (Registry.`is`("terminal.type.ahead.v2", false)) {
      TerminalTypeAheadOutputModelControllerV2(project, outputModel, shellIntegrationDeferred, coroutineScope)
    }
    else {
      TerminalTypeAheadOutputModelControllerV1(project, outputModel, shellIntegrationDeferred, coroutineScope)
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
    keyEventsHandler: TerminalKeyEventsHandlerImpl,
    mouseEventsHandler: TerminalMouseEventsHandlerImpl,
  ) {
    val parentDisposable = coroutineScope.asDisposable() // same lifecycle as `this@ReworkedTerminalView`

    editor.putUserData(TerminalOutputModel.KEY, model)

    // Document modifications can change the scroll position.
    // Mark them with the corresponding flag to indicate that this change is not caused by the explicit user action.
    model.addListener(parentDisposable, object : TerminalOutputModelListener {
      override fun afterContentChanged(event: TerminalContentChangeEvent) {
        // Repaint the whole screen to update all changed highlightings.
        repaintEditorScreen(editor)

        // Update the PSI file content
        val psiFile =
          PsiDocumentManager.getInstance(project).getPsiFile((model as MutableTerminalOutputModel).document) as? TerminalOutputPsiFile
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

    setupKeyEventsHandling(editor, settings, keyEventsHandler, parentDisposable)
    setupMouseEventsHandling(editor, mouseEventsHandler, parentDisposable)

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
    val screenTopLine = editor.xyToLogicalPosition(visibleArea.location).line.coerceAtMost(document.lineCount - 1)
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

  private fun configureCommandCompletion(
    terminalView: TerminalView,
    editor: EditorEx,
    sessionModel: TerminalSessionModel,
    shellIntegration: TerminalShellIntegration,
    envVariables: Map<String, String>,
    coroutineScope: CoroutineScope,
  ) {
    val eelDescriptor = LocalEelDescriptor // TODO: it should be determined by where shell is running to work properly in WSL and Docker
    val services = TerminalCommandCompletionServices(
      commandSpecsManager = ShellCommandSpecsManagerImpl.getInstance(),
      runtimeContextProvider = ShellRuntimeContextProviderReworkedImpl(project, sessionModel, envVariables, eelDescriptor),
      dataGeneratorsExecutor = ShellDataGeneratorsExecutorReworkedImpl(
        shellIntegration,
        coroutineScope.childScope("ShellDataGeneratorsExecutorReworkedImpl")
      )
    )
    editor.putUserData(TerminalCommandCompletionServices.KEY, services)

    TerminalCommandCompletionTypingListener.install(
      terminalView,
      editor,
      coroutineScope.childScope("TerminalCommandCompletionTypingListener")
    )
  }

  private fun configureInlineCompletion(
    outputModel: MutableTerminalOutputModel,
    shellIntegration: TerminalShellIntegration,
  ) {
    val inlineCompletionScope = coroutineScope.childScope("TerminalInlineCompletion")
    val inlineCompletionController =
      TerminalInlineCompletionController(project, outputEditor, outputModel, shellIntegration, inlineCompletionScope)
    inlineCompletionController.install()
    addKeyEventsListener(inlineCompletionScope.asDisposable(), object : TerminalKeyEventsListener {
      override fun beforeKeyEvent(event: TerminalKeyEvent): Boolean {
        inlineCompletionController.handleKeyEvent(event)
        return false
      }
    })
    outputModel.addListener(inlineCompletionScope.asDisposable(), object : TerminalOutputModelListener {
      override fun afterContentChanged(event: TerminalContentChangeEvent) {
        inlineCompletionController.handleContentChanged()
      }

      override fun cursorOffsetChanged(event: TerminalCursorOffsetChangeEvent) {
        inlineCompletionController.handleCursorOffsetChanged()
      }
    })
  }

  override fun toString(): String {
    val commandText = startupOptionsDeferred.getNow()?.let { "${it.shellCommand}" }
    return "TerminalViewImpl(state=${sessionState.value}, command=$commandText, cwd=${workingDirectoryFlow.value})"
  }

  private inner class TerminalPanel(initialContent: Editor) : BorderLayoutPanel(), UiDataProvider, TerminalPanelMarker {
    private val layeredPane = TerminalLayeredPane(initialContent)
    private var curEditor: Editor = initialContent
    private var topComponentsPanel: JPanel? = null

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
      sink.setNull(PlatformDataKeys.COPY_PROVIDER)

      // Add selection text to the data context, so features like Search Everywhere and Find in Files
      // can use it as an initial query.
      val selection = textSelectionModel.selection
      if (selection != null) {
        val selectionText = outputModels.active.value.getText(selection.startOffset, selection.endOffset).toString()
        sink[PlatformDataKeys.PREDEFINED_TEXT] = selectionText
      }

      // Hyperlinks data
      val hyperlinksFacade = if (isAlternateScreenBuffer) alternateBufferHyperlinksFacade else outputBufferHyperlinksFacade
      sink[TerminalHyperlinksSessionId.DATA_KEY] = hyperlinksFacade?.sessionId
      sink[TerminalHyperlinkId.KEY] = hyperlinksFacade?.getHoveredHyperlinkId()
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
      val panel = topComponentsPanel ?: JPanel(ListLayout.vertical(0)).also {
        topComponentsPanel = it
        addToTop(it)
      }
      panel.add(component)
      revalidate()
      repaint()
    }

    fun removeTopComponent(component: JComponent) {
      val panel = topComponentsPanel ?: return
      panel.remove(component)
      if (panel.componentCount == 0) {
        remove(panel)
        topComponentsPanel = null
      }
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

  companion object {
    private val LOG = logger<TerminalViewImpl>()
  }
}

internal fun TerminalOffset.toRelative(model: TerminalOutputModel): Int = (this - model.startOffset).toInt()

@get:ApiStatus.Internal
val TerminalOutputModel.cursorOffsetFlow: Flow<TerminalOffset>
  get() = callbackFlow {
    addListener(asDisposable(), object : TerminalOutputModelListener {
      override fun cursorOffsetChanged(event: TerminalCursorOffsetChangeEvent) {
        trySendBlocking(event.newOffset)
      }
    })
    awaitClose()
  }
