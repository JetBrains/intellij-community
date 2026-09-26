package com.intellij.terminal.frontend.view.impl

import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandExecutionListener
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandFinishedEvent

private val LOG = fileLogger()

internal fun refreshVfsOnCommandFinish(
  terminalView: TerminalView,
  coroutineScope: CoroutineScope,
) {
  val disposable = coroutineScope.asDisposable()

  // Use a heuristic-based command finish tracker for refreshing VFS by default.
  // But if we receive the event about available shell integration, it will be canceled.
  val heuristicBasedRefresherScope = coroutineScope.childScope("Heuristic based VFS refresher")
  TerminalHeuristicsBasedCommandFinishTracker.install(
    terminalView,
    heuristicBasedRefresherScope,
    onCommandFinish = {
      LOG.debug { "Command finished, schedule VFS refresh." }
      SaveAndSyncHandler.getInstance().scheduleRefresh()
    }
  )

  coroutineScope.launch(CoroutineName("Shell integration awaiting")) {
    val shellIntegration = terminalView.shellIntegrationDeferred.await()

    // If we have events from the shell integration, we no more need heuristic-based refresher.
    heuristicBasedRefresherScope.cancel()
    LOG.debug { "Shell integration initialized, cancel heuristic-based VFS refresher." }

    shellIntegration.addCommandExecutionListener(disposable, object : TerminalCommandExecutionListener {
      override fun commandFinished(event: TerminalCommandFinishedEvent) {
        LOG.debug { "Command finished, schedule VFS refresh." }
        SaveAndSyncHandler.getInstance().scheduleRefresh()
      }
    })
  }
}