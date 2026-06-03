package com.intellij.terminal.frontend.session

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.util.coroutines.flow.IncrementalUpdateFlowProducer
import com.intellij.platform.util.coroutines.flow.MutableStateWithIncrementalUpdates
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModelImpl
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils
import org.jetbrains.plugins.terminal.fus.BatchLatencyReporter
import org.jetbrains.plugins.terminal.fus.DurationAndTextLength
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import org.jetbrains.plugins.terminal.fus.percentile
import org.jetbrains.plugins.terminal.fus.percentileOf
import org.jetbrains.plugins.terminal.fus.thirdLargest
import org.jetbrains.plugins.terminal.fus.thirdLargestOf
import org.jetbrains.plugins.terminal.fus.totalDuration
import org.jetbrains.plugins.terminal.fus.totalDurationOf
import org.jetbrains.plugins.terminal.session.TerminalStartupOptions
import org.jetbrains.plugins.terminal.session.impl.TerminalCommandFinishedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCommandStartedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCursorPositionChangedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalInitialStateEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalInputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalPromptFinishedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalPromptStartedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.session.impl.TerminalSessionTerminatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalStateChangedEvent
import org.jetbrains.plugins.terminal.session.impl.dto.toDto
import org.jetbrains.plugins.terminal.session.impl.dto.toTerminalState
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModelImpl
import org.jetbrains.plugins.terminal.view.impl.updateContent
import org.jetbrains.plugins.terminal.view.shellIntegration.impl.TerminalBlocksModelImpl
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
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
  private val delegate: TerminalSession,
  private val startupOptions: TerminalStartupOptions,
  override val coroutineScope: CoroutineScope,
) : TerminalSession {
  private val outputFlowProducer = IncrementalUpdateFlowProducer(State())

  private val sessionModel: TerminalSessionModel = TerminalSessionModelImpl()
  private val outputModel: MutableTerminalOutputModel
  private val alternateBufferModel: MutableTerminalOutputModel
  private val blocksModel: TerminalBlocksModelImpl

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
    outputModel = MutableTerminalOutputModelImpl(outputDocument, TerminalUiUtils.getDefaultMaxOutputLength())

    val alternateBufferDocument = DocumentImpl("", true)
    alternateBufferModel = MutableTerminalOutputModelImpl(alternateBufferDocument, maxOutputLength = 0)

    blocksModel = TerminalBlocksModelImpl(outputModel, sessionModel, coroutineScope.asDisposable())

    coroutineScope.launch(CoroutineName("StateAwareTerminalSession: models updating")) {
      delegate.getOutputFlow().collect { events ->
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

  override suspend fun getOutputFlow(): Flow<List<TerminalOutputEvent>> {
    return channelFlow {
      try {
        outputFlowProducer.getIncrementalUpdateFlow().collect { events ->
          withTimeout(3.seconds) {
            send(events)
          }
        }
      }
      catch (_: TimeoutCancellationException) {
        // Downstream consumer is too slow, ending the flow to unblock the original session output flow processing.
        // The collector should request a new flow and receive a state snapshot
        LOG.info("Failed to emit output to the collector in 3 seconds, is there a connection problem? Terminating the output flow.")
      }
    }.buffer(Channel.RENDEZVOUS)
  }

  override val eelDescriptor: EelDescriptor
    get() = delegate.eelDescriptor

  override val processId: Long
    get() = delegate.processId

  override val isClosed: Boolean
    get() = delegate.isClosed

  override suspend fun hasRunningCommands(): Boolean = delegate.hasRunningCommands()

  companion object {
    private val LOG = logger<StateAwareTerminalSession>()
  }

  private inner class State : MutableStateWithIncrementalUpdates<List<TerminalOutputEvent>> {
    override suspend fun applyUpdate(update: List<TerminalOutputEvent>): List<TerminalOutputEvent> {
      for (event in update) {
        try {
          val replacement = handleEvent(event)
          if (replacement !== event) {
            check(update.size == 1) { "Multiple event replacement not supported, events = $update" }
            return listOfNotNull(replacement)
          }
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (t: Throwable) {
          thisLogger().error(t)
        }
      }
      return update
    }

    private fun handleEvent(event: TerminalOutputEvent): TerminalOutputEvent {
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
          blocksModel.startNewBlock(outputModel.cursorOffset)
        }
        TerminalPromptFinishedEvent -> {
          blocksModel.updateActiveCommandBlock { block ->
            block.copy(commandStartOffset = outputModel.cursorOffset)
          }
        }
        is TerminalCommandStartedEvent -> {
          blocksModel.updateActiveCommandBlock { block ->
            block.copy(outputStartOffset = outputModel.cursorOffset, executedCommand = event.command)
          }
        }
        is TerminalCommandFinishedEvent -> {
          blocksModel.updateActiveCommandBlock { block ->
            block.copy(exitCode = event.exitCode)
          }
        }
        else -> {
          // Do nothing: other events are not related to the models we update
        }
      }
      return event
    }

    override suspend fun takeSnapshot(): List<List<TerminalOutputEvent>> {
      val event = TerminalInitialStateEvent(
        startupOptions = startupOptions.toDto(),
        sessionState = sessionModel.terminalState.value.toDto(),
        outputModelState = outputModel.dumpState().toDto(),
        alternateBufferState = alternateBufferModel.dumpState().toDto(),
        blocksModelState = blocksModel.dumpState().toDto(),
      )
      return listOf(listOf(event))
    }

    private fun getCurrentOutputModel(): MutableTerminalOutputModel {
      return if (sessionModel.terminalState.value.isAlternateScreenBuffer) alternateBufferModel else outputModel
    }

    private fun updateOutputModelContent(model: MutableTerminalOutputModel, event: TerminalContentUpdatedEvent) {
      val startTime = TimeSource.Monotonic.markNow()

      model.updateContent(event)

      val latencyData = DurationAndTextLength(duration = startTime.elapsedNow(), textLength = event.text.length)
      documentUpdateLatencyReporter.update(latencyData)
    }
  }
}