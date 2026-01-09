package com.intellij.terminal.frontend.view.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.util.asDisposable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.block.reworked.TerminalAliasesStorage
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel
import org.jetbrains.plugins.terminal.session.impl.*
import org.jetbrains.plugins.terminal.session.impl.dto.toState
import org.jetbrains.plugins.terminal.util.getNow
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellIntegration
import org.jetbrains.plugins.terminal.view.shellIntegration.impl.TerminalShellIntegrationImpl

internal class TerminalShellIntegrationEventsHandler(
  private val outputModelController: TerminalOutputModelController,
  private val sessionModel: TerminalSessionModel,
  private val shellIntegrationDeferred: CompletableDeferred<TerminalShellIntegration>,
  private val aliasesStorage: TerminalAliasesStorage,
  private val coroutineScope: CoroutineScope,
) : TerminalOutputEventsHandler {
  private val edtContext = Dispatchers.EDT + ModalityState.any().asContextElement()

  private val shellIntegration: TerminalShellIntegrationImpl?
    get() = shellIntegrationDeferred.getNow() as? TerminalShellIntegrationImpl

  private fun getIntegrationOrThrow(): TerminalShellIntegrationImpl {
    return shellIntegration ?: error("Shell integration is not initialized yet")
  }

  override suspend fun handleEvent(event: TerminalOutputEvent) {
    when (event) {
      is TerminalInitialStateEvent -> {
        withContext(edtContext) {
          if (shellIntegration == null && event.sessionState.isShellIntegrationEnabled) {
            initShellIntegration()
          }
          shellIntegration?.restoreFromState(event.blocksModelState.toState())
        }
      }
      is TerminalStateChangedEvent -> {
        if (shellIntegration == null && event.state.isShellIntegrationEnabled) {
          initShellIntegration()
        }
      }
      // It is expected that shell integration is initialized before the below events arrive.
      // So, throw an exception if it is not initialized yet to find such cases quickly.
      TerminalPromptStartedEvent -> {
        withContext(edtContext) {
          outputModelController.applyPendingUpdates()
          getIntegrationOrThrow().onPromptStarted(outputModelController.model.cursorOffset)
        }
      }
      TerminalPromptFinishedEvent -> {
        withContext(edtContext) {
          outputModelController.applyPendingUpdates()
          getIntegrationOrThrow().onPromptFinished(outputModelController.model.cursorOffset)
        }
      }
      is TerminalCommandStartedEvent -> {
        withContext(edtContext) {
          outputModelController.applyPendingUpdates()
          getIntegrationOrThrow().onCommandStarted(outputModelController.model.cursorOffset, event.command)
        }
      }
      is TerminalCommandFinishedEvent -> {
        withContext(edtContext) {
          outputModelController.applyPendingUpdates()
          getIntegrationOrThrow().onCommandFinished(event.exitCode)
        }
      }
      is TerminalAliasesReceivedEvent -> {
        aliasesStorage.setAliasesInfo(event.aliases)
      }
      is TerminalCompletionFinishedEvent -> {
        getIntegrationOrThrow().onCompletionFinished(event.result)
      }
      else -> {
        // do nothing
      }
    }
  }

  private fun initShellIntegration() {
    val integration = TerminalShellIntegrationImpl(
      outputModelController.model,
      sessionModel,
      coroutineScope.asDisposable()
    )
    shellIntegrationDeferred.complete(integration)
  }
}