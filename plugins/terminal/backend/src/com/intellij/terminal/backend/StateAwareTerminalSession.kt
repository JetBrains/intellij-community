package com.intellij.terminal.backend

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.terminal.session.*
import com.intellij.terminal.session.dto.toDto
import com.intellij.terminal.session.dto.toStyleRange
import com.intellij.terminal.session.dto.toTerminalState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import org.jetbrains.plugins.terminal.block.reworked.*
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource

/**
 * TerminalSession implementation that stores the state of the [delegate] session output.
 * This state is then passed as the [TerminalInitialStateEvent] to the output flow as the first event
 * every time when [getOutputFlow] is requested.
 *
 * So, actually it allows restoring the state of UI that requests the [getOutputFlow].
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class StateAwareTerminalSession(private val delegate: TerminalSession) : TerminalSession {
  private val sessionModel: TerminalSessionModel = TerminalSessionModelImpl()
  private val outputModel: TerminalOutputModel
  private val alternateBufferModel: TerminalOutputModel
  private val blocksModel: TerminalBlocksModel

  init {
    // Create a Non-AWT thread document to be able to update it without switching to EDT and Write Action.
    // It is OK here to handle synchronization manually, because this document will be used only in our services.
    val outputDocument = DocumentImpl("", true)
    outputModel = TerminalOutputModelImpl(outputDocument, TerminalUiUtils.getDefaultMaxOutputLength())

    val alternateBufferDocument = DocumentImpl("", true)
    alternateBufferModel = TerminalOutputModelImpl(alternateBufferDocument, maxOutputLength = 0)

    blocksModel = TerminalBlocksModelImpl(outputDocument)
  }

  override suspend fun getInputChannel(): SendChannel<TerminalInputEvent> {
    return delegate.getInputChannel()
  }

  override suspend fun getOutputFlow(): Flow<List<TerminalOutputEvent>> {
    val originalFlow = delegate.getOutputFlow()
    val modelsAwareFlow = originalFlow.onEach {
      // Now we assume that there will be only a single simultaneous collector of this flow (Remote Dev or Monolith scenario).
      // This code should be rewritten if we need to support multiple collectors (CodeWithMe scenario).
      doHandleEvents(it)
    }

    val initialStateEvent = createInitialStateEvent()
    val initialStateEventFlow = flowOf(listOf(initialStateEvent))

    return flowOf(initialStateEventFlow, modelsAwareFlow).flattenConcat()
  }

  override val isClosed: Boolean
    get() = delegate.isClosed

  private fun doHandleEvents(events: List<TerminalOutputEvent>) {
    for (event in events) {
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
          ReworkedTerminalUsageCollector.logBackendOutputLatency(event.id, latency)
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

  private fun createInitialStateEvent(): TerminalInitialStateEvent {
    return TerminalInitialStateEvent(
      sessionState = sessionModel.terminalState.value.toDto(),
      outputModelState = outputModel.dumpState().toDto(),
      alternateBufferState = alternateBufferModel.dumpState().toDto(),
      blocksModelState = blocksModel.dumpState().toDto(),
    )
  }

  private fun getCurrentOutputModel(): TerminalOutputModel {
    return if (sessionModel.terminalState.value.isAlternateScreenBuffer) alternateBufferModel else outputModel
  }

  private fun updateOutputModelContent(model: TerminalOutputModel, event: TerminalContentUpdatedEvent) {
    val startTime = TimeSource.Monotonic.markNow()

    val styles = event.styles.map { it.toStyleRange() }
    model.updateContent(event.startLineLogicalIndex, event.text, styles)

    ReworkedTerminalUsageCollector.logBackendDocumentUpdateLatency(
      textLength = event.text.length,
      duration = startTime.elapsedNow(),
    )
  }
}