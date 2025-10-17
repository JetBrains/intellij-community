package com.intellij.terminal.frontend.view.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.block.reworked.TerminalAliasesStorage
import org.jetbrains.plugins.terminal.session.*
import org.jetbrains.plugins.terminal.session.dto.toState
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellIntegration
import org.jetbrains.plugins.terminal.view.shellIntegration.impl.TerminalShellIntegrationImpl
import java.util.concurrent.CompletableFuture

internal class TerminalShellIntegrationEventsHandler(
  private val outputModelController: TerminalOutputModelController,
  private val shellIntegrationFuture: CompletableFuture<TerminalShellIntegration>,
  private val aliasesStorage: TerminalAliasesStorage,
  private val coroutineScope: CoroutineScope,
) : TerminalOutputEventsHandler {
  private val edtContext = Dispatchers.EDT + ModalityState.any().asContextElement()

  private val shellIntegration: TerminalShellIntegrationImpl?
    get() = shellIntegrationFuture.getNow(null) as? TerminalShellIntegrationImpl

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
          shellIntegration?.blocksModel?.restoreFromState(event.blocksModelState.toState())
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
          getIntegrationOrThrow().onCommandStarted(outputModelController.model.cursorOffset)
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
      else -> {
        // do nothing
      }
    }
  }

  private fun initShellIntegration() {
    val integration = TerminalShellIntegrationImpl(
      outputModelController.model,
      coroutineScope.childScope("TerminalShellIntegration")
    )
    shellIntegrationFuture.complete(integration)
  }
}