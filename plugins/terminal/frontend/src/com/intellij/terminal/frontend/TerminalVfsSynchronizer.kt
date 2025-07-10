package com.intellij.terminal.frontend

import com.intellij.ide.GeneralSettings
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.observable.util.addFocusListener
import com.intellij.openapi.util.Key
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalShellIntegrationEventsListener
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.KeyEvent
import javax.swing.JComponent

internal class TerminalVfsSynchronizer(
  sessionController: TerminalSessionController,
  outputModel: TerminalOutputModel,
  sessionModel: TerminalSessionModel,
  terminalComponent: JComponent,
  coroutineScope: CoroutineScope,
) {
  // Use a heuristic-based command finish tracker for refreshing VFS by default.
  // But if we receive the event about available shell integration, it will be canceled.
  private val heuristicBasedRefresherScope = coroutineScope.childScope("Heuristic based VFS refresher")
  private val heuristicBasedRefresher = TerminalHeuristicsBasedCommandFinishTracker(
    outputModel,
    heuristicBasedRefresherScope,
    onCommandFinish = {
      SaveAndSyncHandler.getInstance().scheduleRefresh()
      LOG.debug { "Command finished, schedule VFS refresh." }
    }
  )

  init {
    val disposable = coroutineScope.asDisposable()

    sessionController.addShellIntegrationListener(disposable, object : TerminalShellIntegrationEventsListener {
      override fun commandFinished(command: String, exitCode: Int, currentDirectory: String) {
        SaveAndSyncHandler.getInstance().scheduleRefresh()
        LOG.debug { "Command finished, schedule VFS refresh." }
      }
    })

    terminalComponent.addFocusListener(disposable, object : FocusListener {
      override fun focusGained(e: FocusEvent) {
        if (GeneralSettings.getInstance().isSaveOnFrameDeactivation) {
          WriteIntentReadAction.run {
            FileDocumentManager.getInstance().saveAllDocuments()
            LOG.debug { "Focus gained, save all documents to VFS." }
          }
        }
      }

      override fun focusLost(e: FocusEvent) {
        if (GeneralSettings.getInstance().isSyncOnFrameActivation) {
          // Like we sync the external changes when switching to the IDE window, let's do the same
          // when the focus is transferred from the built-in terminal to some other IDE place.
          // To get the updates from a long-running command in the built-in terminal.
          SaveAndSyncHandler.getInstance().scheduleRefresh()
          LOG.debug { "Focus lost, schedule VFS refresh." }
        }
      }
    })

    coroutineScope.launch {
      var shellIntegrationEnabled = false
      sessionModel.terminalState.collect { state ->
        if (state.isShellIntegrationEnabled != shellIntegrationEnabled) {
          shellIntegrationEnabled = state.isShellIntegrationEnabled
          // If we have events from the shell integration, we no more need heuristic-based refresher.
          heuristicBasedRefresherScope.cancel()
          LOG.debug { "Shell integration initialized, cancel heuristic-based VFS refresher." }
        }
      }
    }
  }

  @RequiresEdt
  fun handleKeyPressed(e: KeyEvent) {
    if (heuristicBasedRefresherScope.coroutineContext.isActive) {
      heuristicBasedRefresher.handleKeyPressed(e)
    }
  }

  companion object {
    val KEY: Key<TerminalVfsSynchronizer> = Key.create("TerminalVfsSynchronizer")

    private val LOG = logger<TerminalVfsSynchronizer>()
  }
}