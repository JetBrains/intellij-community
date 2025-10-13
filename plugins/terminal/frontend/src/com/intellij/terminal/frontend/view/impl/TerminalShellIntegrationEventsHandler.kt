package com.intellij.terminal.frontend.view.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.block.reworked.TerminalAliasesStorage
import org.jetbrains.plugins.terminal.block.reworked.TerminalBlocksModel
import org.jetbrains.plugins.terminal.session.*
import org.jetbrains.plugins.terminal.session.dto.toState
import org.jetbrains.plugins.terminal.view.TerminalShellIntegration
import java.util.concurrent.CompletableFuture

internal class TerminalShellIntegrationEventsHandler(
  private val outputModelController: TerminalOutputModelController,
  private val commandDetectionFuture: CompletableFuture<TerminalShellIntegration>,
  private val blocksModel: TerminalBlocksModel,
  private val aliasesStorage: TerminalAliasesStorage,
) : TerminalOutputEventsHandler {
  private val edtContext = Dispatchers.EDT + ModalityState.any().asContextElement()

  private var commandDetectionInitialized = false

  override suspend fun handleEvent(event: TerminalOutputEvent) {
    when (event) {
      is TerminalInitialStateEvent -> {
        withContext(edtContext) {
          if (!commandDetectionInitialized && event.sessionState.isShellIntegrationEnabled) {
            initCommandDetection()
          }
          blocksModel.restoreFromState(event.blocksModelState.toState())
        }
      }
      is TerminalStateChangedEvent -> {
        if (!commandDetectionInitialized && event.state.isShellIntegrationEnabled) {
          initCommandDetection()
        }
      }
      TerminalPromptStartedEvent -> {
        withContext(edtContext) {
          outputModelController.applyPendingUpdates()
          blocksModel.promptStarted(outputModelController.model.cursorOffset)
        }
      }
      TerminalPromptFinishedEvent -> {
        withContext(edtContext) {
          outputModelController.applyPendingUpdates()
          blocksModel.promptFinished(outputModelController.model.cursorOffset)
        }
      }
      is TerminalCommandStartedEvent -> {
        withContext(edtContext) {
          outputModelController.applyPendingUpdates()
          blocksModel.commandStarted(outputModelController.model.cursorOffset)
        }
      }
      is TerminalCommandFinishedEvent -> {
        withContext(edtContext) {
          outputModelController.applyPendingUpdates()
          blocksModel.commandFinished(event.exitCode)
        }
      }
      is TerminalAliasesReceivedEvent -> {
        aliasesStorage.setAliasesInfo(event.aliases)
      }
      else -> {
        // do nothing
      }
    }
  }

  private fun initCommandDetection() {
    val detection = TerminalShellIntegrationImpl(blocksModel)
    commandDetectionFuture.complete(detection)

    commandDetectionInitialized = true
  }
}