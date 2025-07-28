package com.intellij.terminal.backend

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.coroutines.flow.IncrementalUpdateFlowProducer
import com.intellij.platform.util.coroutines.flow.MutableStateWithIncrementalUpdates
import com.intellij.terminal.backend.hyperlinks.BackendTerminalHyperlinkFacade
import com.intellij.terminal.session.*
import com.intellij.terminal.session.dto.toDto
import com.intellij.terminal.session.dto.toTerminalState
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import org.jetbrains.plugins.terminal.block.reworked.*
import org.jetbrains.plugins.terminal.block.reworked.hyperlinks.isSplitHyperlinksSupportEnabled
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils
import org.jetbrains.plugins.terminal.fus.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource

/**
 * TerminalSession implementation that stores the state of the [delegate] session output.
 * This state is then passed as the [TerminalInitialStateEvent] to the output flow as the first event
 * every time when [getOutputFlow] is requested.
 *
 * So, actually it allows restoring the state of UI that requests the [getOutputFlow].
 *
 * Note that it starts collecting the output of the [delegate] session,
 * so the terminal emulation continues even if the client has disconnected from the backend.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class StateAwareTerminalSession(
  project: Project,
  private val delegate: BackendTerminalSession,
  override val coroutineScope: CoroutineScope,
) : BackendTerminalSession {
  private val outputFlowProducer = IncrementalUpdateFlowProducer(State())

  private val sessionModel: TerminalSessionModel = TerminalSessionModelImpl()
  private val outputModel: TerminalOutputModel
  private val outputHyperlinkFacade: BackendTerminalHyperlinkFacade?
  private val alternateBufferModel: TerminalOutputModel
  private val alternateBufferHyperlinkFacade: BackendTerminalHyperlinkFacade?
  private val blocksModel: TerminalBlocksModel

  private val inputChannel: SendChannel<TerminalInputEvent>

  private val outputLatencyReporter = BatchLatencyReporter(batchSize = 100) { samples ->
    ReworkedTerminalUsageCollector.logBackendOutputLatency(
      totalDuration = samples.totalDuration(),
      duration90 = samples.percentile(90),
      thirdLargestDuration = samples.thirdLargest(),
    )
  }

  private val documentUpdateLatencyReporter = BatchLatencyReporter(batchSize = 100) { samples ->
    ReworkedTerminalUsageCollector.logBackendDocumentUpdateLatency(
      totalDuration = samples.totalDurationOf(DurationAndTextLength::duration),
      duration90 = samples.percentileOf(90, DurationAndTextLength::duration),
      thirdLargestDuration = samples.thirdLargestOf(DurationAndTextLength::duration),
      textLength90 = samples.percentileOf(90, DurationAndTextLength::textLength),
    )
  }

  init {
    val hyperlinkScope = coroutineScope.childScope("StateAwareTerminalSession hyperlink facades")
    // Create a Non-AWT thread document to be able to update it without switching to EDT and Write Action.
    // It is OK here to handle synchronization manually, because this document will be used only in our services.
    val outputDocument = DocumentImpl("", true)
    outputModel = TerminalOutputModelImpl(outputDocument, TerminalUiUtils.getDefaultMaxOutputLength())
    outputHyperlinkFacade = if (isSplitHyperlinksSupportEnabled()) {
      BackendTerminalHyperlinkFacade(project, hyperlinkScope, outputModel, isInAlternateBuffer = false)
    }
    else {
      null
    }

    val alternateBufferDocument = DocumentImpl("", true)
    alternateBufferModel = TerminalOutputModelImpl(alternateBufferDocument, maxOutputLength = 0)
    alternateBufferHyperlinkFacade = if (isSplitHyperlinksSupportEnabled()) {
      BackendTerminalHyperlinkFacade(project, hyperlinkScope, alternateBufferModel, isInAlternateBuffer = true)
    }
    else {
      null
    }

    blocksModel = TerminalBlocksModelImpl(outputDocument)

    coroutineScope.launch(CoroutineName("StateAwareTerminalSession: models updating")) {
      val originalOutputFlow = if (outputHyperlinkFacade != null && alternateBufferHyperlinkFacade != null) {
        merge(
          delegate.getOutputFlow(),
          outputHyperlinkFacade.resultFlow.map { listOf(it) },
          alternateBufferHyperlinkFacade.resultFlow.map { listOf(it) },
        )
      }
      else {
        delegate.getOutputFlow()
      }
      originalOutputFlow.collect { events ->
        try {
          outputFlowProducer.handleUpdate(events)
        }
        finally {
          if (events.any { it is TerminalSessionTerminatedEvent }) {
            coroutineScope.cancel()
          }
        }
      }
    }

    inputChannel = Channel<TerminalInputEvent>(capacity = Channel.UNLIMITED)
    coroutineScope.launch(CoroutineName("StateAwareTerminalSession: input channel")) {
      val original = delegate.getInputChannel()
      try {
        for (event in inputChannel) {
          original.send(event)
          handleInputEvent(event)
        }
      }
      finally {
        inputChannel.close()
        original.close()
      }
    }
  }

  override suspend fun getInputChannel(): SendChannel<TerminalInputEvent> {
    return inputChannel
  }

  private fun handleInputEvent(event: TerminalInputEvent) {
    if (event is TerminalHyperlinkClickedEvent) {
      coroutineScope.launch(CoroutineName("StateAwareTerminalSession: hyperlink click")) {
        getHyperlinkFacade(event)?.hyperlinkClicked(event.hyperlinkId)
      }
    }
  }

  private fun getHyperlinkFacade(event: TerminalHyperlinkClickedEvent): BackendTerminalHyperlinkFacade? =
    if (event.isInAlternateBuffer) alternateBufferHyperlinkFacade else outputHyperlinkFacade

  private fun getHyperlinkFacade(event: TerminalHyperlinksChangedEvent): BackendTerminalHyperlinkFacade? =
    if (event.isInAlternateBuffer) alternateBufferHyperlinkFacade else outputHyperlinkFacade

  override suspend fun getOutputFlow(): Flow<List<TerminalOutputEvent>> = outputFlowProducer.getIncrementalUpdateFlow()

  override val isClosed: Boolean
    get() = delegate.isClosed

  override suspend fun hasRunningCommands(): Boolean = delegate.hasRunningCommands()

  private inner class State : MutableStateWithIncrementalUpdates<List<TerminalOutputEvent>> {
    override suspend fun applyUpdate(update: List<TerminalOutputEvent>): List<TerminalOutputEvent>? {
      var anyHandled = false
      for (event in update) {
        try {
          anyHandled = handleEvent(event)
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (t: Throwable) {
          thisLogger().error(t)
        }
      }
      return if (anyHandled) update else null
    }

    private fun handleEvent(event: TerminalOutputEvent): Boolean {
      when (event) {
        is TerminalContentUpdatedEvent -> {
          val model = getCurrentOutputModel()
          updateOutputModelContent(model, event)

          val latency = event.readTime?.elapsedNow()
          if (latency != null) {
            outputLatencyReporter.update(latency)
          }
        }
        is TerminalCursorPositionChangedEvent -> {
          val model = getCurrentOutputModel()
          model.updateCursorPosition(event.logicalLineIndex, event.columnIndex)
        }
        is TerminalStateChangedEvent -> {
          val state = event.state.toTerminalState()
          sessionModel.updateTerminalState(state)
        }
        TerminalPromptStartedEvent -> {
          blocksModel.promptStarted(outputModel.cursorOffsetState.value)
        }
        TerminalPromptFinishedEvent -> {
          blocksModel.promptFinished(outputModel.cursorOffsetState.value)
        }
        is TerminalCommandStartedEvent -> {
          blocksModel.commandStarted(outputModel.cursorOffsetState.value)
        }
        is TerminalCommandFinishedEvent -> {
          blocksModel.commandFinished(event.exitCode)
        }
        is TerminalHyperlinksChangedEvent -> {
          val facade = getHyperlinkFacade(event)
          checkNotNull(facade) { "The hyperlink facade is null, so who sent the TerminalHyperlinksChangedEvent event then? It's a bug" }
          return facade.updateModelState(event)
        }
        else -> {
          // Do nothing: other events are not related to the models we update
        }
      }
      return true
    }

    override suspend fun takeSnapshot(): List<List<TerminalOutputEvent>> {
      val event = TerminalInitialStateEvent(
        sessionState = sessionModel.terminalState.value.toDto(),
        outputModelState = outputModel.dumpState().toDto(),
        alternateBufferState = alternateBufferModel.dumpState().toDto(),
        blocksModelState = blocksModel.dumpState().toDto(),
        outputHyperlinksState = outputHyperlinkFacade?.dumpState()?.toDto(),
        alternateBufferHyperlinksState = alternateBufferHyperlinkFacade?.dumpState()?.toDto(),
      )
      return listOf(listOf(event))
    }

    private fun getCurrentOutputModel(): TerminalOutputModel {
      return if (sessionModel.terminalState.value.isAlternateScreenBuffer) alternateBufferModel else outputModel
    }

    private fun updateOutputModelContent(model: TerminalOutputModel, event: TerminalContentUpdatedEvent) {
      val startTime = TimeSource.Monotonic.markNow()

      model.updateContent(event)

      val latencyData = DurationAndTextLength(duration = startTime.elapsedNow(), textLength = event.text.length)
      documentUpdateLatencyReporter.update(latencyData)
    }
  }
}