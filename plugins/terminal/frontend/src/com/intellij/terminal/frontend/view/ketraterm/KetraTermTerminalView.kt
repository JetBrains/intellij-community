// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.ketraterm

import com.intellij.ide.ActivityTracker
import com.intellij.ide.CopyProvider
import com.intellij.ide.PasteProvider
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.util.coroutines.flow.mapStateIn
import com.intellij.terminal.frontend.fus.TerminalShortcutsPressStatistics
import com.intellij.terminal.frontend.session.ketraterm.KetraTermTextInput
import com.intellij.terminal.frontend.session.ketraterm.MAX_PENDING_TEXT_INPUTS
import com.intellij.terminal.frontend.session.ketraterm.MAX_PENDING_TEXT_UNITS
import com.intellij.terminal.frontend.session.ketraterm.prepareKetraTermTextInput
import com.intellij.terminal.frontend.session.ketraterm.isKetraTermDocumentProjectionEnabled
import com.intellij.terminal.frontend.session.ketraterm.KetraTermTerminalSession
import com.intellij.terminal.frontend.toolwindow.impl.TerminalClipboard
import com.intellij.terminal.frontend.view.impl.dnd.installTerminalDropHandler
import com.intellij.terminal.frontend.view.impl.refreshVfsOnCommandFinish
import com.intellij.terminal.frontend.view.impl.setupComponentKeyEventsHandling
import com.intellij.terminal.frontend.view.impl.TerminalOutputModelControllerImpl
import com.intellij.terminal.frontend.view.impl.TerminalSessionController
import com.intellij.terminal.frontend.view.impl.TerminalShellIntegrationEventsHandler
import com.intellij.terminal.frontend.view.impl.TerminalShellLifecycleModel
import com.intellij.terminal.frontend.view.TerminalKeyEvent
import com.intellij.terminal.frontend.view.TerminalKeyEventImpl
import com.intellij.terminal.frontend.view.TerminalKeyEventsListener
import com.intellij.terminal.frontend.view.TerminalTextSelectionModel
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.refreshVfsOnFocusChange
import com.intellij.terminal.TerminalFontSizeProvider
import com.intellij.terminal.TerminalTitle
import com.intellij.util.asDisposable
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.ui.components.panels.ListLayout
import com.intellij.util.ui.UIUtil
import io.github.ketraterm.ui.swing.api.SwingTerminal
import io.github.ketraterm.ui.swing.api.SwingTerminalHostKeyHandler
import io.github.ketraterm.ui.swing.host.SwingTerminalOverlayPane
import io.github.ketraterm.ui.swing.host.SwingTerminalSearchBar
import io.github.ketraterm.ui.swing.host.SwingTerminalSearchColors
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent
import javax.swing.JPanel
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModelImpl
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils
import org.jetbrains.plugins.terminal.session.impl.dto.toTerminalState
import org.jetbrains.plugins.terminal.session.impl.dto.CellPixelSizeDto
import org.jetbrains.plugins.terminal.session.impl.dto.TerminalGridGeometryDto
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.session.impl.TerminalClearBufferEvent
import org.jetbrains.plugins.terminal.session.TerminalGridSize
import org.jetbrains.plugins.terminal.session.TerminalStartupOptions
import org.jetbrains.plugins.terminal.TerminalFontSizeProviderImpl
import org.jetbrains.plugins.terminal.TerminalPanelMarker
import org.jetbrains.plugins.terminal.TerminalSelectAllProvider
import org.jetbrains.plugins.terminal.util.convertNativePathToNioPath
import org.jetbrains.plugins.terminal.util.getNow
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModelImpl
import org.jetbrains.plugins.terminal.view.impl.TerminalOutputModelsSetImpl
import org.jetbrains.plugins.terminal.view.impl.TerminalSendTextBuilderImpl
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellIntegration
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalSendTextBuilder

/** Embeds the Swing renderer and reports shell activity to IntelliJ. */
@ApiStatus.Internal
internal class KetraTermTerminalView(
  private val project: Project,
  settings: JBTerminalSystemSettingsProviderBase,
  override val coroutineScope: CoroutineScope,
) : TerminalView {
  override val sessionDeferred = CompletableDeferred<TerminalSession>(coroutineScope.coroutineContext.job)
  override val startupOptionsDeferred = CompletableDeferred<TerminalStartupOptions>(coroutineScope.coroutineContext.job)
  override val shellIntegrationDeferred = CompletableDeferred<TerminalShellIntegration>(coroutineScope.coroutineContext.job)
  override val title = TerminalTitle()

  private val state = MutableStateFlow<TerminalViewSessionState>(TerminalViewSessionState.NotStarted)
  override val sessionState = state.asStateFlow()
  internal val sessionModel = TerminalSessionModelImpl()
  private val compatibilityEnabled = isKetraTermDocumentProjectionEnabled()
  override val supportsOutputModel: Boolean get() = compatibilityEnabled
  override val shellLifecycle = TerminalShellLifecycleModel()
  override val typedCommand: String?
    get() = if (shellLifecycle.outputStatus.value == TerminalOutputStatus.TypingCommand) {
      nativeSession?.commandLine?.value?.commandText
    }
    else null
  private val regular by lazy { createModel() }
  private val alternative by lazy { createModel(maxOutputLength = 0) }
  override val outputModels by lazy {
    check(compatibilityEnabled) { "Enable ketraterm.experimental.enableDocumentModel to use document APIs with KetraTerm Swing" }
    TerminalOutputModelsSetImpl(regular, alternative)
  }
  override val workingDirectoryFlow = sessionModel.terminalState.mapStateIn(coroutineScope) { terminalState ->
    val directory = terminalState.currentDirectory
    val session = sessionDeferred.getNow()
    if (directory == null || session == null) null else convertNativePathToNioPath(directory, session.eelDescriptor)
  }
  override val bracketedPasteMode = sessionModel.terminalState.mapStateIn(coroutineScope) { it.isBracketedPasteMode }

  private val events = MutableSharedFlow<TerminalKeyEvent>(
    extraBufferCapacity = 100,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )
  override val keyEventsFlow = events.asSharedFlow()
  private val nativeKeys = MutableSharedFlow<KeyEvent>(extraBufferCapacity = 100, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  internal val nativeKeyEvents = nativeKeys.asSharedFlow()
  private val keyListeners = CopyOnWriteArrayList<TerminalKeyEventsListener>()
  private val shortcutStatistics = TerminalShortcutsPressStatistics(project, this)
  private val appearance = KetraTermSettings(settings)
  private val hyperlinkDetector = KetraTermHyperlinkDetector(
    project,
    launchDirectory = {
      nativeSession?.let { session ->
        session.startupOptions.workingDirectory?.let { convertNativePathToNioPath(it, session.eelDescriptor) }
      }
    },
    directoryForLine = { lineId ->
      nativeSession?.shellState?.let { shell ->
        if (lineId > 0) shell.commandWorkingDirectoryUri(shell.commandRecordIdAtLine(lineId)) else null
      }
    },
    localFileLinks = { nativeSession?.eelDescriptor == LocalEelDescriptor },
  )
  private val controller: TerminalSessionController?
  @Volatile private var nativeSession: KetraTermTerminalSession? = null
  private val inputLock = Any()
  private val pendingInput = ArrayDeque<KetraTermTextInput>()
  private var topComponentsPanel: JPanel? = null
  @Volatile private var completion: KetraTermCompletion? = null

  val terminal = SwingTerminal(
    settingsProvider = appearance,
    hostServices = createKetraTermHostServices(
      settings,
      SwingTerminalHostKeyHandler { event -> completion?.handleKeyPressed(event) == true },
      pasteClipboard = { TerminalClipboard.pasteClipboardContent(project, this, preferSystemSelection = false) },
      hyperlinkDetector = hyperlinkDetector,
    ),
  )

  private val search = SwingTerminalSearchBar(terminal)

  internal fun showSearch() { search.open() }

  internal val canClearBuffer: Boolean
    get() = nativeSession?.isClosed == false

  internal fun clearBuffer() {
    val session = nativeSession?.takeUnless { it.isClosed } ?: return
    coroutineScope.launch {
      try {
        session.getInputChannel().send(TerminalClearBufferEvent())
      }
      catch (_: ClosedSendChannelException) {
        // The process can close after the action checks its state.
      }
    }
  }
  internal fun isCompletionAvailable(): Boolean = completion?.isAvailable() == true
  internal fun invokeCompletion(): Boolean = completion?.invokeCompletion() == true

  override val preferredFocusableComponent: JComponent get() = terminal
  override val gridSize: TerminalGridSize
    get() = terminal.visibleGridSize().let { TerminalGridSize(it.width, it.height) }
  override val gridGeometry: TerminalGridGeometryDto
    get() {
      val oneCell = terminal.preferredGridSize(1, 1)
      val twoCells = terminal.preferredGridSize(2, 2)
      val cell = CellPixelSizeDto(
        twoCells.width - oneCell.width, twoCells.height - oneCell.height,
      )
      return TerminalGridGeometryDto(gridSize, cell)
    }

  override val textSelectionModel: TerminalTextSelectionModel
    get() = throw UnsupportedOperationException("The native terminal view does not use IntelliJ document selection.")

  private val copyProvider = object : CopyProvider {
    override fun performCopy(dataContext: DataContext) { terminal.copySelectionToClipboard() }
    override fun isCopyEnabled(dataContext: DataContext): Boolean = terminal.currentSelection()?.isEmpty == false
    override fun isCopyVisible(dataContext: DataContext): Boolean = true
  }
  private val selectAllProvider = TerminalSelectAllProvider { terminal.selectAll() }
  private val pasteProvider = object : PasteProvider {
    override fun performPaste(dataContext: DataContext) {
      TerminalClipboard.pasteClipboardContent(project, this@KetraTermTerminalView, preferSystemSelection = false)
    }
    override fun isPastePossible(dataContext: DataContext): Boolean = true
    override fun isPasteEnabled(dataContext: DataContext): Boolean = state.value == TerminalViewSessionState.Running
  }
  override val component: JComponent = object : BorderLayoutPanel(), UiDataProvider, TerminalPanelMarker.KetraTerm {
    init { addToCenter(SwingTerminalOverlayPane(terminal, search.component)) }

    override fun uiDataSnapshot(sink: DataSink) {
      sink[CommonDataKeys.PROJECT] = project
      sink[TerminalFontSizeProvider.DATA_KEY] = TerminalFontSizeProviderImpl.getInstance()
      sink[TerminalView.DATA_KEY] = this@KetraTermTerminalView
      if (compatibilityEnabled) sink[TerminalOutputModel.DATA_KEY] = outputModels.active.value
      sink[PlatformDataKeys.COPY_PROVIDER] = copyProvider
      sink[PlatformDataKeys.PASTE_PROVIDER] = pasteProvider
      sink[TerminalSelectAllProvider.DATA_KEY] = selectAllProvider
      terminal.selectedText()?.let { sink[PlatformDataKeys.PREDEFINED_TEXT] = it }
    }
  }

  init {
    refreshSearchColors()
    val disposable = coroutineScope.asDisposable()
    controller = if (compatibilityEnabled) {
      val regularController = TerminalOutputModelControllerImpl(project, regular)
      val result = TerminalSessionController(
        sessionModel, regularController, TerminalOutputModelControllerImpl(project, alternative),
        startupOptionsDeferred, settings, coroutineScope.childScope("KetraTerm output model"),
      )
      result.addEventsHandler(shellLifecycle)
      result.addEventsHandler(TerminalShellIntegrationEventsHandler(
        regularController, sessionModel, shellIntegrationDeferred, sessionDeferred,
        startupOptionsDeferred, coroutineScope.childScope("KetraTerm shell integration"),
      ))
      result.addTerminationCallback(disposable) {
        state.value = TerminalViewSessionState.Terminated
        completion?.close()
        completion = null
      }
      result
    } else null

    val refreshShortcuts = setupComponentKeyEventsHandling(project, terminal, settings, disposable)
    appearance.installListeners(coroutineScope, onHostSettingsChanged = refreshShortcuts) {
      terminal.reloadSettings()
      refreshSearchColors()
      completion?.refreshSettings()
    }
    val keyListener = object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent) = notifyKey(e)
      override fun keyTyped(e: KeyEvent) = notifyKey(e)
    }
    terminal.addKeyListener(keyListener)
    installTerminalDropHandler(project, terminal::scrollToLiveViewport)
    refreshVfsOnFocusChange(terminal, coroutineScope.childScope("KetraTerm VFS refresh"))
    refreshVfsOnCommandFinish(this, coroutineScope.childScope("KetraTerm VFS refresh on command finish"))

    coroutineScope.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
      sessionModel.terminalState.collect { value ->
        if (compatibilityEnabled) outputModels.setActiveModel(value.isAlternateScreenBuffer)
        if (appearance.setAlternateScreen(value.isAlternateScreenBuffer)) terminal.reloadSettings()
        title.change { applicationTitle = value.windowTitle }
      }
    }
    coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
      try {
        awaitCancellation()
      }
      finally {
        withContext(NonCancellable + Dispatchers.UI) {
          synchronized(inputLock) { pendingInput.clear() }
          terminal.removeKeyListener(keyListener)
          keyListeners.clear()
          completion?.close()
          search.close()
          terminal.dispose()
        }
      }
    }
  }

  suspend fun connectToSession(session: TerminalSession) {
    require(session is KetraTermTerminalSession)
    withContext(Dispatchers.UI) {
      synchronized(inputLock) {
        check(nativeSession == null) { "The terminal view is already bound" }
        nativeSession = session
        while (pendingInput.isNotEmpty() && !session.isClosed) {
          if (!session.sendPreparedText(pendingInput.removeFirst())) {
            LOG.warn("KetraTerm could not submit text buffered before startup")
          }
        }
        pendingInput.clear()
      }
      startupOptionsDeferred.complete(session.startupOptions)
      sessionDeferred.complete(session)
      check(compatibilityEnabled == session.compatibilityEnabled)
      if (controller != null) controller.handleEvents(session)
      else {
        coroutineScope.launch {
          session.getOutputFlow().collect { batch ->
            withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
              for (event in batch) {
                shellLifecycle.handleEvent(event)
                when (event) {
                  is org.jetbrains.plugins.terminal.session.impl.TerminalStateChangedEvent ->
                    sessionModel.updateTerminalState(event.state.toTerminalState())
                  org.jetbrains.plugins.terminal.session.impl.TerminalBeepEvent -> java.awt.Toolkit.getDefaultToolkit().beep()
                  org.jetbrains.plugins.terminal.session.impl.TerminalSessionTerminatedEvent -> {
                    state.value = TerminalViewSessionState.Terminated
                    completion?.close()
                    completion = null
                  }
                  else -> Unit
                }
              }
            }
          }
        }
      }
      terminal.bind(session.terminalSession)
      if (!session.isClosed) {
        completion = KetraTermCompletion(project, this@KetraTermTerminalView, session, coroutineScope)
      }
      state.value = TerminalViewSessionState.Running
      ActivityTracker.getInstance().inc()
    }
  }

  override suspend fun hasChildProcesses(): Boolean =
    withContext(Dispatchers.Default) { nativeSession?.hasRunningCommands() ?: false }

  override fun getCurrentDirectory(): String? = sessionModel.terminalState.value.currentDirectory
  override fun sendText(text: String) { createSendTextBuilder().send(text) }

  override fun createSendTextBuilder(): TerminalSendTextBuilder = TerminalSendTextBuilderImpl { options ->
    synchronized(inputLock) {
      if (!coroutineScope.isActive) return@synchronized false
      val input = prepareKetraTermTextInput(options) ?: return@synchronized false
      val session = nativeSession
      if (session != null) {
        completion?.invalidateInput()
        session.sendPreparedText(input)
      }
      else if (input.requireBracketedPasteMode ||
               pendingInput.size >= MAX_PENDING_TEXT_INPUTS ||
               pendingInput.sumOf { it.workUnits } + input.workUnits > MAX_PENDING_TEXT_UNITS) false
      else {
        pendingInput.addLast(input)
        true
      }
    }
  }

  override fun addKeyEventsListener(parentDisposable: Disposable, listener: TerminalKeyEventsListener) {
    keyListeners.add(listener)
    Disposer.register(parentDisposable) { keyListeners.remove(listener) }
  }

  override fun setTopComponent(component: JComponent, disposable: Disposable) {
    val panel = topComponentsPanel ?: JPanel(ListLayout.vertical(0)).also {
      topComponentsPanel = it
      this.component.add(it, BorderLayout.NORTH)
    }
    panel.add(component)
    this.component.revalidate()
    this.component.repaint()
    Disposer.register(disposable) {
      UIUtil.invokeLaterIfNeeded {
        panel.remove(component)
        if (panel.componentCount == 0) {
          this.component.remove(panel)
          topComponentsPanel = null
        }
        this.component.revalidate()
        this.component.repaint()
      }
    }
  }

  private fun notifyKey(event: KeyEvent) {
    shortcutStatistics.afterKeyEvent(event)
    nativeKeys.tryEmit(event)
    if (!compatibilityEnabled) return
    val model = outputModels.active.value
    val terminalEvent = TerminalKeyEventImpl(event, model.cursorOffset, model)
    events.tryEmit(terminalEvent)
    for (listener in keyListeners) listener.afterKeyEvent(terminalEvent)
  }

  private fun createModel(maxOutputLength: Int = TerminalUiUtils.getDefaultMaxOutputLength()): MutableTerminalOutputModel =
    MutableTerminalOutputModelImpl(DocumentImpl("", true), maxOutputLength)

  companion object {
    private val LOG = logger<KetraTermTerminalView>()
  }

  private fun refreshSearchColors() {
    search.refreshColors(SwingTerminalSearchColors.create {
      it.panelBackground = UIUtil.getPanelBackground()
      it.foreground = UIUtil.getLabelForeground()
      it.counterForeground = UIUtil.getContextHelpForeground()
      it.textFieldBackground = UIUtil.getTextFieldBackground()
      it.textFieldBorder = UIUtil.getBoundsColor()
      it.panelBorder = UIUtil.getBoundsColor()
      it.textFieldFocusBorder = UIUtil.getListSelectionBackground(true)
      it.textFieldPlaceholder = UIUtil.getContextHelpForeground()
      it.searchIconForeground = UIUtil.getLabelForeground()
      it.buttonHoverBackground = UIUtil.getListSelectionBackground(false)
      it.buttonPressedBackground = UIUtil.getListSelectionBackground(true)
      it.buttonSelectedBackground = UIUtil.getListSelectionBackground(true)
      it.buttonSelectedForeground = UIUtil.getListSelectionForeground(true)
    })
  }
}
