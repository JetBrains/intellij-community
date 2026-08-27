package com.intellij.terminal.frontend.view.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.util.coroutines.childScope
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
import org.jetbrains.plugins.terminal.session.impl.TerminalCommandHistoryPathReceivedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCommandStartedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCompletionFinishedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalInitialStateEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalPromptFinishedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalPromptStartedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.session.impl.TerminalStateChangedEvent
import org.jetbrains.plugins.terminal.session.impl.dto.toState
import org.jetbrains.plugins.terminal.util.getNow
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellIntegration
import org.jetbrains.plugins.terminal.view.shellIntegration.impl.TerminalShellIntegrationImpl

internal class TerminalShellIntegrationEventsHandler(
  private val outputModelController: TerminalOutputModelController,
  private val sessionModel: TerminalSessionModel,
  private val shellIntegrationDeferred: CompletableDeferred<TerminalShellIntegration>,
  private val sessionDeferred: Deferred<TerminalSession>,
  private val startupOptionsDeferred: Deferred<TerminalStartupOptions>,
  private val coroutineScope: CoroutineScope,
) : TerminalOutputEventsHandler {
  private val shellIntegration: TerminalShellIntegrationImpl?
    get() = shellIntegrationDeferred.getNow() as? TerminalShellIntegrationImpl

  override suspend fun handleEvent(event: TerminalOutputEvent) {
    when (event) {
      is TerminalInitialStateEvent -> {
        if (shellIntegration == null && event.sessionState.isShellIntegrationEnabled) {
          initShellIntegration()
        }
        shellIntegration?.restoreFromState(event.blocksModelState.toState())
      }
      is TerminalStateChangedEvent -> {
        if (shellIntegration == null && event.state.isShellIntegrationEnabled) {
          initShellIntegration()
        }
      }
      // It is expected that shell integration is initialized before the below events arrive.
      // So, throw an exception if it is not initialized yet to find such cases quickly.
      TerminalPromptStartedEvent -> {
        outputModelController.applyPendingUpdates()
        withIntegrationOrThrow { it.onPromptStarted(outputModelController.model.cursorOffset) }
      }
      TerminalPromptFinishedEvent -> {
        outputModelController.applyPendingUpdates()
        withIntegrationOrThrow { it.onPromptFinished(outputModelController.model.cursorOffset) }
      }
      is TerminalCommandStartedEvent -> {
        outputModelController.applyPendingUpdates()
        withIntegrationOrThrow { it.onCommandStarted(outputModelController.model.cursorOffset, event.command) }
      }
      is TerminalCommandFinishedEvent -> {
        outputModelController.applyPendingUpdates()
        withIntegrationOrThrow { it.onCommandFinished(event.exitCode) }
      }
      is TerminalAliasesReceivedEvent -> {
        withContext(Dispatchers.Default) {
          val startupOptions = startupOptionsDeferred.getNow()
          when {
            startupOptions != null -> {
              val aliases = parseAliases(event.aliasesRaw, startupOptions.guessShellName())
              withIntegrationOrThrow { it.onAliasesReceived(aliases) }
            }
            // The view was disposed while this event was in flight: expected shutdown, nothing to report.
            startupOptionsDeferred.isCancelled -> Unit
            else -> LOG.error("Failed to parse aliases: startup options are not initialized yet")
          }
        }
      }
      is TerminalCompletionFinishedEvent -> {
        withIntegrationOrThrow { it.onCompletionFinished(event.result) }
      }
      is TerminalCommandHistoryPathReceivedEvent -> {
        withIntegrationOrThrow { it.onCommandHistoryFilePathReceived(event.path) }
      }
      else -> {
        // do nothing
      }
    }
  }

  private fun initShellIntegration() {
    val startupOptions = startupOptionsDeferred.getNow() ?: run {
      // The view was disposed while this event was in flight: expected shutdown, nothing to initialize.
      if (startupOptionsDeferred.isCancelled) return
      error("Startup options are null but should be already initialized at this point")
    }
    val session = sessionDeferred.getNow() ?: run {
      if (sessionDeferred.isCancelled) return
      error("Terminal session is null but should be already initialized at this point")
    }
    val integration = TerminalShellIntegrationImpl(
      outputModelController.model,
      sessionModel,
      coroutineScope.childScope("TerminalShellIntegration"),
      session.eelDescriptor,
      startupOptions.guessShellName(),
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

  /**
   * Runs [action] with the initialized shell integration.
   *
   * It is expected that shell integration is initialized before this method is called, so this asserts if it is not,
   * to find such cases quickly.
   * The one exception is a cancelled [shellIntegrationDeferred]: that means the view was disposed while this event was in flight,
   * which is expected shutdown, not a bug, so it is skipped quietly instead.
   */
  private inline fun withIntegrationOrThrow(action: (TerminalShellIntegrationImpl) -> Unit) {
    val integration = shellIntegration
    if (integration != null) {
      action(integration)
      return
    }
    if (shellIntegrationDeferred.isCancelled) return
    error("Shell integration is not initialized yet")
  }

  companion object {
    private val LOG = logger<TerminalShellIntegrationEventsHandler>()
  }
}
