package com.intellij.terminal.backend

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.platform.util.coroutines.flow.IncrementalUpdateFlowProducer
import com.intellij.platform.util.coroutines.flow.MutableStateWithIncrementalUpdates
import com.intellij.terminal.session.*
import com.intellij.terminal.session.dto.toDto
import com.intellij.terminal.session.dto.toStyleRange
import com.intellij.terminal.session.dto.toTerminalState
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.terminal.block.reworked.*
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
  private val delegate: BackendTerminalSession,
  override val coroutineScope: CoroutineScope,
) : BackendTerminalSession {
  private val outputFlowProducer = IncrementalUpdateFlowProducer(State())

  private val sessionModel: TerminalSessionModel = TerminalSessionModelImpl()
  private val outputModel: TerminalOutputModel
  private val alternateBufferModel: TerminalOutputModel
  private val blocksModel: TerminalBlocksModel

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
    // Create a Non-AWT thread document to be able to update it without switching to EDT and Write Action.
    // It is OK here to handle synchronization manually, because this document will be used only in our services.
    val outputDocument = DocumentImpl("", true)
    outputModel = TerminalOutputModelImpl(outputDocument, TerminalUiUtils.getDefaultMaxOutputLength())

    val alternateBufferDocument = DocumentImpl("", true)
    alternateBufferModel = TerminalOutputModelImpl(alternateBufferDocument, maxOutputLength = 0)

    blocksModel = TerminalBlocksModelImpl(outputDocument)

    coroutineScope.launch(CoroutineName("StateAwareTerminalSession: models updating")) {
      val originalOutputFlow = delegate.getOutputFlow()
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
  }

  override suspend fun getInputChannel(): SendChannel<TerminalInputEvent> {
    return delegate.getInputChannel()
  }

  override suspend fun getOutputFlow(): Flow<List<TerminalOutputEvent>> = outputFlowProducer.getIncrementalUpdateFlow()

  override val isClosed: Boolean
    get() = delegate.isClosed

  override suspend fun hasRunningCommands(): Boolean = delegate.hasRunningCommands()
  
  private inner class State : MutableStateWithIncrementalUpdates<List<TerminalOutputEvent>> {
    override suspend fun applyUpdate(update: List<TerminalOutputEvent>) {
      for (event in update) {
        try {
          handleEvent(event)
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (t: Throwable) {
          thisLogger().error(t)
        }
      }
    }

    private fun handleEvent(event: TerminalOutputEvent) {
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
        else -> {
          // Do nothing: other events are not related to the models we update
        }
      }
    }

    override suspend fun takeSnapshot(): List<List<TerminalOutputEvent>> {
      val event = TerminalInitialStateEvent(
        sessionState = sessionModel.terminalState.value.toDto(),
        outputModelState = outputModel.dumpState().toDto(),
        alternateBufferState = alternateBufferModel.dumpState().toDto(),
        blocksModelState = blocksModel.dumpState().toDto(),
      )
      return listOf(listOf(event))
    }

    private fun getCurrentOutputModel(): TerminalOutputModel {
      return if (sessionModel.terminalState.value.isAlternateScreenBuffer) alternateBufferModel else outputModel
    }

    private fun updateOutputModelContent(model: TerminalOutputModel, event: TerminalContentUpdatedEvent) {
      val startTime = TimeSource.Monotonic.markNow()

      val styles = event.styles.map { it.toStyleRange() }
      model.updateContent(event.startLineLogicalIndex, event.text, styles)

      val latencyData = DurationAndTextLength(duration = startTime.elapsedNow(), textLength = event.text.length)
      documentUpdateLatencyReporter.update(latencyData)
    }
  }
}