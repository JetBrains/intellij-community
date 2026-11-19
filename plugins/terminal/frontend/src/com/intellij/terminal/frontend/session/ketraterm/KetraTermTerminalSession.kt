// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.session.ketraterm

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.session.addHeuristicBasedCwdListener
import com.intellij.terminal.frontend.session.addWorkingDirectoryListener
import com.intellij.terminal.frontend.session.ObservableTtyConnector
import com.intellij.terminal.frontend.session.TerminalShellIntegrationController
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import com.jediterm.terminal.TtyConnector
import io.github.ketraterm.core.TerminalBuffers
import io.github.ketraterm.host.HostEventSink
import io.github.ketraterm.input.event.TerminalKey
import io.github.ketraterm.input.event.TerminalKeyEvent
import io.github.ketraterm.input.event.TerminalPasteEvent
import io.github.ketraterm.input.policy.PasteLineEndingPolicy
import io.github.ketraterm.input.policy.TerminalInputPolicy
import io.github.ketraterm.parser.api.TerminalCustomOscHandler
import io.github.ketraterm.parser.api.TerminalParsers
import io.github.ketraterm.protocol.MouseEncodingMode
import io.github.ketraterm.protocol.MouseTrackingMode
import io.github.ketraterm.protocol.ShellIntegrationEvent
import io.github.ketraterm.protocol.ShellIntegrationMarker
import io.github.ketraterm.render.api.TerminalColorPalette
import io.github.ketraterm.render.api.TerminalRenderBufferKind
import io.github.ketraterm.render.api.TerminalRenderCursorShape
import io.github.ketraterm.session.TerminalInputAdmission
import io.github.ketraterm.session.TerminalSessionState
import io.github.ketraterm.session.TerminalShellCommandLineSnapshot
import io.github.ketraterm.session.TerminalShellCommandLineState
import io.github.ketraterm.session.TerminalShellIntegrationFactory
import io.github.ketraterm.session.TerminalShellIntegrationState
import io.github.ketraterm.session.TerminalShellIntegrationView
import io.github.ketraterm.shell.launch.TerminalShellLaunch
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.net.URI
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.TerminalShellIntegrationEventsListener
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils
import org.jetbrains.plugins.terminal.LocalTerminalTtyConnector
import org.jetbrains.plugins.terminal.original
import org.jetbrains.plugins.terminal.session.impl.dto.CursorShapeDto
import org.jetbrains.plugins.terminal.session.impl.dto.KeyEventProcessingResultDto
import org.jetbrains.plugins.terminal.session.impl.dto.MouseFormatDto
import org.jetbrains.plugins.terminal.session.impl.dto.MouseModeDto
import org.jetbrains.plugins.terminal.session.impl.dto.TerminalColorSchemeDto
import org.jetbrains.plugins.terminal.session.impl.dto.TerminalStateDto
import org.jetbrains.plugins.terminal.session.impl.TerminalBeepEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalClearBufferEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCloseEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCommandFinishedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCommandStartedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalInputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalPromptFinishedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalPromptStartedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalResizeEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.session.impl.TerminalSessionTerminatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSetColorSchemeEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSetDefaultCursorShapeEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalStartupOptionsImpl
import org.jetbrains.plugins.terminal.session.impl.TerminalStateChangedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalWriteBytesEvent
import org.jetbrains.plugins.terminal.session.TerminalStartupOptions
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.TerminalUtil
import org.jetbrains.plugins.terminal.view.impl.TerminalSendTextOptions
import io.github.ketraterm.input.event.TerminalInputEvent as KetraInputEvent
import io.github.ketraterm.session.TerminalSession as KetraSession

/** Owns a KetraTerm pipeline and the ordered IntelliJ compatibility event stream. */
@ApiStatus.Internal
class KetraTermTerminalSession internal constructor(
  private val ttyConnector: TtyConnector,
  options: ShellStartupOptions,
  settings: JBTerminalSystemSettingsProviderBase,
  override val coroutineScope: CoroutineScope,
  useDocumentRenderer: Boolean = false,
  private val shellLaunch: TerminalShellLaunch? = null,
) : TerminalSession {
  internal val compatibilityEnabled = useDocumentRenderer || isKetraTermDocumentProjectionEnabled()
  private val initialSize = requireNotNull(options.initialTermSize)
  private val controller = TerminalShellIntegrationController()
  private val hostShellState by lazy(LazyThreadSafetyMode.NONE) { TerminalShellIntegrationState() }
  val shellState: TerminalShellIntegrationView get() = terminalSession.shellIntegrationState
  val usesNativeShellIntegration: Boolean get() = shellLaunch != null
  private var nativeShellReady = false
  private var nativeCwdTrackerScope: CoroutineScope? = null
  private var nativeCommand: String? = null
  private val mutableCommandLine = MutableStateFlow<TerminalShellCommandLineSnapshot?>(null)
  private val authoritativeCommandLine = TerminalShellCommandLineState()
  val commandLine: StateFlow<TerminalShellCommandLineSnapshot?> = mutableCommandLine
  private val mutablePromptReady = MutableStateFlow(false)
  private var editingPrompt = false
  val promptReady: StateFlow<Boolean> = mutablePromptReady
  private val projectionLock = ReentrantLock()
  private val projector by lazy(LazyThreadSafetyMode.NONE) { KetraTermOutputProjector() }
  private val pendingEvents = ConcurrentLinkedQueue<TerminalOutputEvent>()
  private val output = Channel<List<TerminalOutputEvent>>(64)
  private val outputFlow = output.consumeAsFlow()
  private val input = Channel<TerminalInputEvent>(64)
  private val documentProjectionRequests = if (useDocumentRenderer) Channel<Unit>(Channel.CONFLATED) else null
  private var lastState: TerminalStateDto? = null
  private var lastCursor: org.jetbrains.plugins.terminal.session.impl.TerminalCursorPositionChangedEvent? = null
  @Volatile private var outputCompleted = false
  private val commandReader by lazy(LazyThreadSafetyMode.NONE) { KetraTermCommandLineReader() }
  @Volatile private var inputPending = false
  @Volatile private var title = ""
  @Volatile private var directory = options.workingDirectory
  private val localConnector = ttyConnector.original as? LocalTerminalTtyConnector

  override val eelDescriptor: EelDescriptor
    get() = localConnector?.eelDescriptor ?: LocalEelDescriptor
  override val processId: Long
    get() = localConnector?.shellEelProcess?.eelProcess?.pid?.value ?: -1

  val startupOptions: TerminalStartupOptions = TerminalStartupOptionsImpl(
    shellCommand = options.shellCommand.orEmpty(),
    workingDirectory = options.workingDirectory.orEmpty(),
    envVariables = options.envVariables,
    processType = options.processType,
    pid = localConnector?.shellEelProcess?.eelProcess?.pid?.value,
  )

  val terminalSession: KetraSession = KetraSession.create(
    inputPolicy = TerminalInputPolicy(pasteLineEndingPolicy = PasteLineEndingPolicy.CARRIAGE_RETURN),
    terminal = TerminalBuffers.create(
      initialSize.columns,
      initialSize.rows,
      maxHistory = (TerminalUiUtils.getDefaultMaxOutputLength() / initialSize.columns).coerceAtLeast(initialSize.rows),
    ),
    connector = KetraTermTtyConnector(ttyConnector, projectionLock) { pending ->
      inputPending = pending
      updateCommandLine()
      documentProjectionRequests?.trySend(Unit)
    },
    hostEvents = object : HostEventSink {
      override fun bell() {
        if (settings.audibleBell()) pendingEvents.add(TerminalBeepEvent)
      }
      override fun iconTitleChanged(title: String) {}
      override fun windowTitleChanged(title: String) {
        this@KetraTermTerminalSession.title = title
      }
      override fun resizeWindow(rows: Int, columns: Int) {}
      override fun currentWorkingDirectoryChanged(uri: String) {
        if (usesNativeShellIntegration) {
          localKetraTermDirectory(uri)?.let {
            directory = it
            nativeCwdTrackerScope?.cancel()
            nativeCwdTrackerScope = null
          }
        }
      }
      override fun shellIntegrationMarker(event: ShellIntegrationEvent) {
        if (usesNativeShellIntegration) processNativeShellMarker(event)
      }
    },
    shellIntegration = shellLaunch?.shellIntegration ?: TerminalShellIntegrationFactory.host(hostShellState, authoritativeCommandLine, promptReady),
    parserFactory = if (usesNativeShellIntegration) null else { sink, clipboardLimit ->
      TerminalParsers.create(sink, clipboardLimit, 1024 * 1024, TerminalCustomOscHandler { command, payload, offset, length ->
        if (command == 1341) processShellCommand(String(payload, offset, length, Charsets.UTF_8))
      })
    },
  )

  override val isClosed: Boolean
    get() = terminalSession.isClosed

  @OptIn(AwaitCancellationAndInvoke::class)
  internal fun start() {
    coroutineScope.awaitCancellationAndInvoke {
      input.cancel()
      if (!outputCompleted) output.cancel()
      documentProjectionRequests?.cancel()
      try {
        terminalSession.close()
      }
      finally {
        shellLaunch?.close()
      }
    }
    controller.addListener(object : TerminalShellIntegrationEventsListener {
      override fun initialized(currentDirectory: String?, historyPath: String?) {
        currentDirectory?.let(::updateDirectory)
      }
      override fun commandFinished(command: String, exitCode: Int, currentDirectory: String?) {
        currentDirectory?.let(::updateDirectory)
      }
    })
    controller.addEventSink { event ->
      projectionLock.withLock {
        var lineId = 0L
        var includeLine = false
        terminalSession.readRenderFrame { frame ->
          lineId = frame.lineId(frame.cursor.row)
          includeLine = frame.cursor.column == 0
          if (event == TerminalPromptFinishedEvent) commandReader.setAnchor(frame)
        }
        when (event) {
          TerminalPromptStartedEvent -> {
            editingPrompt = false
            mutablePromptReady.value = false
            publishCommandLine(null)
            if (lineId > 0) hostShellState.recordPromptStart(lineId)
          }
          TerminalPromptFinishedEvent -> {
            if (lineId > 0) hostShellState.recordPromptEnd(lineId)
            editingPrompt = true
            mutablePromptReady.value = true
          }
          is TerminalCommandStartedEvent -> {
            editingPrompt = false
            mutablePromptReady.value = false
            publishCommandLine(null)
            if (lineId > 0) hostShellState.recordCommandStart(lineId, includeLine, event.command, shellState.currentWorkingDirectoryUri())
          }
          is TerminalCommandFinishedEvent -> {
            editingPrompt = false
            mutablePromptReady.value = false
            publishCommandLine(null)
            if (lineId > 0) hostShellState.recordCommandFinished(lineId, event.exitCode)
          }
          else -> Unit
        }
        emitProjection()
        sendBatch(listOf(event))
      }
    }
    if (usesNativeShellIntegration) {
      (ttyConnector as? ObservableTtyConnector)?.let { connector ->
        val localConnector = connector.original as? LocalTerminalTtyConnector ?: return@let
        val trackerScope = coroutineScope.childScope("KetraTerm working directory fallback")
        nativeCwdTrackerScope = trackerScope
        addHeuristicBasedCwdListener(connector, localConnector, trackerScope) { value ->
          projectionLock.withLock {
            if (nativeCwdTrackerScope != null) {
              directory = value
              emitProjection()
            }
          }
        }
      }
    }
    else {
      (ttyConnector as? ObservableTtyConnector)?.let {
        addWorkingDirectoryListener(it, controller, coroutineScope) { path -> updateDirectory(path.toString()) }
      }
      directory?.let(::updateDirectory)
    }
    coroutineScope.launch(Dispatchers.IO + CoroutineName("KetraTerm platform input")) {
      for (event in input) {
        try {
          handleInput(event)
        }
        catch (error: IllegalArgumentException) {
          LOG.warn("KetraTerm rejected an unsupported platform input event", error)
        }
      }
    }
    shellLaunch?.attach(terminalSession)
    terminalSession.start(initialSize.columns, initialSize.rows)
    val projectionJob = coroutineScope.launch(Dispatchers.IO + CoroutineName("KetraTerm compatibility projection")) {
      val requests = documentProjectionRequests
      if (requests == null) {
        terminalSession.renderGeneration.collectKetraTermProjections({ inputPending }) { prioritizeVisible ->
          projectionLock.withLock {
            emitProjection(prioritizeVisible = prioritizeVisible, requirePublishedFrame = true)
            compatibilityEnabled && projector.hasPendingHistory
          }
        }
      }
      else coroutineScope {
        launch {
          terminalSession.renderGeneration.collect { requests.trySend(Unit) }
        }
        requests.collectKetraTermProjections({ inputPending }) { prioritizeVisible ->
          projectionLock.withLock {
            emitProjection(prioritizeVisible = prioritizeVisible, deferSynchronizedOutput = true)
            compatibilityEnabled && projector.hasPendingHistory
          }
        }
      }
    }
    coroutineScope.launch(Dispatchers.IO + CoroutineName("KetraTerm process exit")) {
      terminalSession.state.first { it is TerminalSessionState.Closed }
      projectionJob.cancel()
      projectionLock.withLock {
        emitProjection()
        sendBatch(listOf(TerminalSessionTerminatedEvent))
        output.close()
        outputCompleted = true
        input.close()
      }
      coroutineScope.cancel()
    }
  }

  override suspend fun getInputChannel(): SendChannel<TerminalInputEvent> = input

  override suspend fun getOutputFlow(): Flow<List<TerminalOutputEvent>> = outputFlow

  override suspend fun hasRunningCommands(): Boolean = !isClosed && withContext(Dispatchers.IO) {
    TerminalUtil.hasRunningCommands(ttyConnector)
  }

  override fun processMouseEvent(e: MouseEvent, x: Int, y: Int): ByteArray? = null

  override fun processKeyEvent(e: KeyEvent): KeyEventProcessingResultDto = KeyEventProcessingResultDto.Unhandled

  fun sendText(options: TerminalSendTextOptions): Boolean =
    prepareKetraTermTextInput(options)?.let(::sendPreparedText) == true

  internal fun sendPreparedText(input: KetraTermTextInput): Boolean {
    if (isClosed) return false
    val modes = terminalSession.modeSnapshot
    if (input.requireBracketedPasteMode && !modes.isBracketedPasteEnabled) return false
    if (!input.useBracketedPasteMode && (modes.kittyKeyboardFlags != 0 || modes.isNewLineMode && input.hasLineBreaks)) return false
    return terminalSession.submitInput(input.events) == TerminalInputAdmission.ACCEPTED
  }

  private fun handleInput(event: TerminalInputEvent) {
    when (event) {
      is TerminalWriteBytesEvent -> {
        val admission = terminalSession.submitBytes(event.bytes)
        if (admission != TerminalInputAdmission.ACCEPTED && admission != TerminalInputAdmission.CLOSED) {
          LOG.warn("KetraTerm rejected platform input: $admission")
        }
      }
      is TerminalResizeEvent -> terminalSession.tryResizeViewport(event.newGeometry.gridSize.columns, event.newGeometry.gridSize.rows)
      is TerminalCloseEvent -> terminalSession.close()
      is TerminalClearBufferEvent -> projectionLock.withLock {
        if (terminalSession.clearBuffer()) {
          var primary = false
          terminalSession.readRenderFrame { primary = it.activeBuffer == TerminalRenderBufferKind.PRIMARY }
          if (primary) {
            editingPrompt = false
            mutablePromptReady.value = false
            publishCommandLine(null)
            nativeCommand = null
            if (!usesNativeShellIntegration) {
              hostShellState.clear()
              directory?.let(::updateDirectory)
            }
          }
          emitProjection()
        }
      }
      is TerminalSetColorSchemeEvent -> setPalette(event.colorScheme)
      is TerminalSetDefaultCursorShapeEvent -> terminalSession.setCursorShape(when (event.cursorShape) {
        CursorShapeDto.BLINK_BLOCK, CursorShapeDto.STEADY_BLOCK -> TerminalRenderCursorShape.BLOCK
        CursorShapeDto.BLINK_UNDERLINE, CursorShapeDto.STEADY_UNDERLINE -> TerminalRenderCursorShape.UNDERLINE
        CursorShapeDto.BLINK_VERTICAL_BAR, CursorShapeDto.STEADY_VERTICAL_BAR -> TerminalRenderCursorShape.BAR
      })
    }
  }

  private fun processShellCommand(command: String) {
    projectionLock.withLock {
      controller.processCustomCommand(command.split(';'))
      updateCommandLine()
    }
  }

  private fun processNativeShellMarker(event: ShellIntegrationEvent) {
    projectionLock.withLock {
      nativeShellReady = true
      val projected = when (event.marker) {
        ShellIntegrationMarker.PROMPT_START -> {
          nativeCommand = null
          TerminalPromptStartedEvent
        }
        ShellIntegrationMarker.PROMPT_END -> TerminalPromptFinishedEvent
        ShellIntegrationMarker.COMMAND_START -> {
          val command = shellState.commandText(shellState.latestCommandRecordId()).orEmpty()
          nativeCommand = command
          TerminalCommandStartedEvent(command)
        }
        ShellIntegrationMarker.COMMAND_FINISHED -> {
          val command = nativeCommand
          val exitCode = event.exitCode
          nativeCommand = null
          if (command == null || exitCode == null) null else TerminalCommandFinishedEvent(command, exitCode, directory)
        }
      }
      emitProjection()
      if (projected != null) sendBatch(listOf(projected))
    }
  }

  private fun updateCommandLine() {
    projectionLock.withLock {
      if (!editingPrompt) return
      terminalSession.readRenderFrame { frame ->
        val ready = editingPrompt && frame.activeBuffer == TerminalRenderBufferKind.PRIMARY
        mutablePromptReady.value = ready
        publishCommandLine(if (ready) commandReader.read(frame) else null)
      }
    }
  }

  private fun publishCommandLine(value: TerminalShellCommandLineSnapshot?) {
    if (authoritativeCommandLine.value !== value) authoritativeCommandLine.value = value
    mutableCommandLine.value = value
  }

  private fun updateDirectory(value: String) {
    projectionLock.withLock {
      if (directory == value && shellState.currentWorkingDirectoryUri() != null) return
      directory = value
      try {
        val path = value.replace('\\', '/')
        val uriPath = if (path.startsWith('/')) path else "/$path"
        hostShellState.recordCurrentWorkingDirectory(URI("file", "", uriPath, null).toASCIIString())
      }
      catch (error: Exception) {
        rethrowControlFlowException(error)
        LOG.warn("Cannot map the shell directory to a file URI", error)
      }
      emitProjection()
    }
  }

  private fun emitProjection(
    prioritizeVisible: Boolean = false,
    requirePublishedFrame: Boolean = false,
    deferSynchronizedOutput: Boolean = false,
  ) {
    val publishedFrameGeneration = if (compatibilityEnabled && requirePublishedFrame) {
      terminalSession.readPublishedFrame { it.frameGeneration } ?: return
    }
    else null
    var content: TerminalContentUpdatedEvent? = null
    var state: TerminalStateDto? = null
    val readState: (io.github.ketraterm.render.api.TerminalRenderFrame) -> Unit = readState@ { frame ->
      val modes = terminalSession.modeSnapshot
      if (deferSynchronizedOutput && modes.isSynchronizedOutput) return@readState
      if (compatibilityEnabled) {
        content = projector.project(frame, prioritizeVisible = prioritizeVisible, publishedFrameGeneration = publishedFrameGeneration)
      }
      val cursor = frame.cursor
      val cursorShape = when (cursor.shape) {
        TerminalRenderCursorShape.BLOCK -> if (cursor.blinking) CursorShapeDto.BLINK_BLOCK else CursorShapeDto.STEADY_BLOCK
        TerminalRenderCursorShape.UNDERLINE -> if (cursor.blinking) CursorShapeDto.BLINK_UNDERLINE else CursorShapeDto.STEADY_UNDERLINE
        TerminalRenderCursorShape.BAR -> if (cursor.blinking) CursorShapeDto.BLINK_VERTICAL_BAR else CursorShapeDto.STEADY_VERTICAL_BAR
      }
      state = TerminalStateDto(
        modes.isCursorVisible, cursorShape,
        when (modes.mouseTrackingMode) {
          MouseTrackingMode.OFF -> MouseModeDto.MOUSE_REPORTING_NONE
          MouseTrackingMode.X10, MouseTrackingMode.NORMAL -> MouseModeDto.MOUSE_REPORTING_NORMAL
          MouseTrackingMode.BUTTON_EVENT -> MouseModeDto.MOUSE_REPORTING_BUTTON_MOTION
          MouseTrackingMode.ANY_EVENT -> MouseModeDto.MOUSE_REPORTING_ALL_MOTION
        },
        when (modes.mouseEncodingMode) {
          MouseEncodingMode.DEFAULT -> MouseFormatDto.MOUSE_FORMAT_XTERM
          MouseEncodingMode.UTF8 -> MouseFormatDto.MOUSE_FORMAT_XTERM_EXT
          MouseEncodingMode.SGR, MouseEncodingMode.SGR_PIXELS -> MouseFormatDto.MOUSE_FORMAT_SGR
          MouseEncodingMode.URXVT -> MouseFormatDto.MOUSE_FORMAT_URXVT
        },
        frame.activeBuffer == TerminalRenderBufferKind.ALTERNATE,
        modes.isApplicationCursorKeys, modes.isApplicationKeypad, modes.isNewLineMode, false,
        modes.isBracketedPasteEnabled, title, nativeShellReady || controller.isShellIntegrationEnabled, directory,
      )
    }
    if (compatibilityEnabled) terminalSession.readRenderFrameForAbsoluteRange(0, Long.MAX_VALUE, readState)
    else terminalSession.readRenderFrame(readState)
    if (state == null) return
    val events = ArrayList<TerminalOutputEvent>(4)
    if (lastState != state) {
      lastState = state
      events.add(TerminalStateChangedEvent(requireNotNull(state)))
    }
    if (content != null) events.add(requireNotNull(content))
    else if (compatibilityEnabled && lastCursor != projector.cursor) events.add(projector.cursor)
    if (compatibilityEnabled) lastCursor = projector.cursor
    while (true) events.add(pendingEvents.poll() ?: break)
    if (events.isNotEmpty()) sendBatch(events)
  }

  private fun sendBatch(events: List<TerminalOutputEvent>) {
    output.trySendBlocking(events)
  }

  private fun setPalette(scheme: TerminalColorSchemeDto) {
    val colors = terminalSession.palette.toIndexedColorsArray()
    scheme.ansiColors.forEachIndexed { index, color ->
      colors[index] = -0x1000000 or (color.red shl 16) or (color.green shl 8) or color.blue
    }
    terminalSession.setThemePalette(TerminalColorPalette(
      defaultForeground = -0x1000000 or (scheme.foreground.red shl 16) or (scheme.foreground.green shl 8) or scheme.foreground.blue,
      defaultBackground = -0x1000000 or (scheme.background.red shl 16) or (scheme.background.green shl 8) or scheme.background.blue,
      indexedColors = colors,
      isDark = scheme.isDark,
    ))
  }

  companion object {
    private val LOG = logger<KetraTermTerminalSession>()
  }
}

internal fun createKetraTermTerminalSession(
  project: Project?,
  ttyConnector: TtyConnector,
  options: ShellStartupOptions,
  settings: JBTerminalSystemSettingsProviderBase,
  coroutineScope: CoroutineScope,
  useDocumentRenderer: Boolean = false,
  shellLaunch: TerminalShellLaunch? = null,
): KetraTermTerminalSession {
  var session: KetraTermTerminalSession? = null
  var started = false
  try {
    coroutineScope.ensureActive()
    session = KetraTermTerminalSession(ttyConnector, options, settings, coroutineScope, useDocumentRenderer, shellLaunch)
    session.start()
    started = true
    return session
  }
  finally {
    if (!started) {
      try {
        if (session == null) ttyConnector.close() else session.terminalSession.close()
      }
      finally {
        try {
          shellLaunch?.close()
        }
        finally {
          coroutineScope.cancel()
        }
      }
    }
  }
}
private const val MAX_TEXT_INPUT_EVENTS = 256
internal const val MAX_PENDING_TEXT_INPUTS = 16
internal const val MAX_PENDING_TEXT_UNITS = 16 * 1024 * 1024

internal class KetraTermTextInput(
  val events: List<KetraInputEvent>,
  val useBracketedPasteMode: Boolean,
  val requireBracketedPasteMode: Boolean,
  val hasLineBreaks: Boolean,
  val workUnits: Long,
)

internal fun prepareKetraTermTextInput(options: TerminalSendTextOptions): KetraTermTextInput? {
  if (options.text.isEmpty()) return null
  val usePaste = options.useBracketedPasteMode
  if (!usePaste && !isKetraTermTextSupported(options.text, allowLineBreaks = true)) return null
  val text = if (usePaste) options.text else options.text.replace("\r\n", "\n").replace('\r', '\n')
  val execute = options.shouldExecute && (usePaste || !text.endsWith('\n'))
  val workUnits = text.length.toLong() + (if (options.sendEndKeyBeforeText) 1 else 0) + (if (execute) 1 else 0)
  if (workUnits > MAX_PENDING_TEXT_UNITS) return null

  val events = ArrayList<KetraInputEvent>()
  if (options.sendEndKeyBeforeText) events.add(TerminalKeyEvent.key(TerminalKey.END))
  var hasLineBreaks = false
  if (usePaste) {
    events.add(TerminalPasteEvent(text))
  }
  else {
    var start = 0
    for (index in text.indices) {
      if (text[index] != '\n') continue
      if (index > start) events.add(TerminalKeyEvent.text(text.substring(start, index)))
      events.add(TerminalKeyEvent.key(TerminalKey.ENTER))
      if (events.size > MAX_TEXT_INPUT_EVENTS) return null
      start = index + 1
    }
    hasLineBreaks = start > 0
    if (start < text.length) events.add(TerminalKeyEvent.text(text.substring(start)))
  }
  if (execute) events.add(TerminalKeyEvent.key(TerminalKey.ENTER))
  if (events.size > MAX_TEXT_INPUT_EVENTS) return null
  return KetraTermTextInput(events, usePaste, options.requireBracketedPasteMode, hasLineBreaks, workUnits)
}

internal fun isKetraTermDocumentProjectionEnabled(): Boolean =
  System.getProperty("ketraterm.experimental.disableDocumentModel")?.let { !it.toBoolean() }
  ?: java.lang.Boolean.getBoolean("ketraterm.experimental.enableDocumentModel")

internal fun localKetraTermDirectory(value: String): String? = try {
  val uri = URI(value)
  if (uri.scheme == "file" && uri.rawUserInfo == null && uri.port == -1 && uri.rawQuery == null && uri.rawFragment == null &&
      (uri.rawAuthority.isNullOrEmpty() || uri.host.equals("localhost", ignoreCase = true))) {
    java.nio.file.Path.of(URI("file", "", uri.path, null)).toString()
  }
  else null
}
catch (_: java.net.URISyntaxException) {
  null
}
catch (_: IllegalArgumentException) {
  null
}

internal fun isKetraTermTextSupported(text: String, allowLineBreaks: Boolean): Boolean {
  var index = 0
  while (index < text.length) {
    val value = text[index++]
    if (allowLineBreaks && (value == '\r' || value == '\n')) continue
    if (value < ' ' || value in '\u007f'..'\u009f') return false
    if (value.isHighSurrogate()) {
      if (index == text.length || !text[index++].isLowSurrogate()) return false
    }
    else if (value.isLowSurrogate()) return false
  }
  return true
}

/** Coalesces frame changes and completes pending history after quiet input or sixteen visible passes. */
internal suspend fun StateFlow<Long>.collectKetraTermProjections(
  inputPending: () -> Boolean = { false },
  project: (Boolean) -> Boolean,
) {
  var observed = Long.MIN_VALUE
  collectKetraTermProjections(
    awaitChange = { first { it != observed } },
    consumeChanges = { observed = value },
    inputPending = inputPending,
    project = project,
  )
}

internal suspend fun ReceiveChannel<Unit>.collectKetraTermProjections(
  inputPending: () -> Boolean = { false },
  project: (Boolean) -> Boolean,
) {
  collectKetraTermProjections(
    awaitChange = { receive() },
    consumeChanges = { tryReceive() },
    inputPending = inputPending,
    project = project,
    intervalMillis = 16,
  )
}

private suspend fun collectKetraTermProjections(
  awaitChange: suspend () -> Unit,
  consumeChanges: () -> Unit,
  inputPending: () -> Boolean,
  project: (Boolean) -> Boolean,
  intervalMillis: Long = 4,
) {
  while (true) {
    awaitChange()
    var visiblePasses = 0
    while (true) {
      delay(intervalMillis)
      consumeChanges()
      if (!project(true)) break
      visiblePasses++
      val quiet = withTimeoutOrNull(8) { awaitChange() } == null
      if (visiblePasses == 16 || (quiet && !inputPending())) {
        project(false)
        break
      }
    }
  }
}
