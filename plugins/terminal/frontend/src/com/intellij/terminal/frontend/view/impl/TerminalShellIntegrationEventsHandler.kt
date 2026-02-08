package com.intellij.terminal.frontend.view.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.asDisposable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel
import org.jetbrains.plugins.terminal.exp.completion.TerminalShellSupport
import org.jetbrains.plugins.terminal.session.ShellName
import org.jetbrains.plugins.terminal.session.TerminalStartupOptions
import org.jetbrains.plugins.terminal.session.guessShellName
import org.jetbrains.plugins.terminal.session.impl.TerminalAliasesReceivedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCommandFinishedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCommandStartedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCompletionFinishedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalInitialStateEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalPromptFinishedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalPromptStartedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalStateChangedEvent
import org.jetbrains.plugins.terminal.session.impl.dto.toState
import org.jetbrains.plugins.terminal.util.getNow
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellIntegration
import org.jetbrains.plugins.terminal.view.shellIntegration.impl.TerminalShellIntegrationImpl

internal class TerminalShellIntegrationEventsHandler(
  private val outputModelController: TerminalOutputModelController,
  private val sessionModel: TerminalSessionModel,
  private val shellIntegrationDeferred: CompletableDeferred<TerminalShellIntegration>,
  private val startupOptionsDeferred: Deferred<TerminalStartupOptions>,
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
        val shellName = startupOptionsDeferred.getNow()?.guessShellName()
        if (shellName != null) {
          val aliases = parseAliases(event.aliasesRaw, shellName)
          getIntegrationOrThrow().onAliasesReceived(aliases)
        }
        else {
          LOG.error("Failed to parse aliases: startup options are not initialized yet")
        }
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

  private fun parseAliases(text: String, shellName: ShellName): Map<String, String> {
    if (text.isBlank()) return emptyMap()

    val adjustedShellName = if (shellName == ShellName.PWSH) ShellName.POWERSHELL else shellName
    val shellSupport = TerminalShellSupport.findByShellName(adjustedShellName.value)
                       ?: return emptyMap()
    return try {
      shellSupport.parseAliases(text)
    }
    catch (ex: Exception) {
      LOG.error("Failed to parse aliases for ${adjustedShellName.value}: $text", ex)
      emptyMap()
    }
  }

  companion object {
    private val LOG = logger<TerminalShellIntegrationEventsHandler>()
  }
}