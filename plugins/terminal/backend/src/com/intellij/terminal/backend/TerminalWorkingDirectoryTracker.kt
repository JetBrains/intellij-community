package com.intellij.terminal.backend

import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.asDisposable
import com.jediterm.terminal.TtyConnector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.arrangement.ProcessInfoUtil
import org.jetbrains.plugins.terminal.block.reworked.TerminalShellIntegrationEventsListener

internal fun addWorkingDirectoryListener(
  ttyConnector: ObservableTtyConnector,
  shellIntegrationController: TerminalShellIntegrationController,
  coroutineScope: CoroutineScope,
  listener: (String) -> Unit,
) {
  val heuristicBasedTrackerScope = coroutineScope.childScope("Heuristic based working directory tracker")
  addHeuristicBasedCwdListener(ttyConnector, heuristicBasedTrackerScope, listener)

  shellIntegrationController.addListener(object : TerminalShellIntegrationEventsListener {
    override fun initialized(currentDirectory: String) {
      // Stop heuristic-based working directory tracking if there is a shell integration.
      // We will receive the current directory from the shell integration.
      heuristicBasedTrackerScope.cancel()

      listener(currentDirectory)
    }

    override fun commandFinished(command: String, exitCode: Int, currentDirectory: String) {
      listener(currentDirectory)
    }
  })
}

/**
 * Tries to track the current working directory by checking the terminal process in an OS dependent way.
 * The checks are triggered on user actions: pressing Enter.
 * So, the listener can be triggered with a delay after the actual directory change.
 */
@OptIn(FlowPreview::class)
private fun addHeuristicBasedCwdListener(
  ttyConnector: ObservableTtyConnector,
  coroutineScope: CoroutineScope,
  listener: (String) -> Unit,
) {
  val workingDirectoryUpdateRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  ttyConnector.addListener(coroutineScope.asDisposable(), object : TtyConnectorListener {
    override fun bytesWritten(bytes: ByteArray) {
      // Trigger working directory updating after pressing user press Enter.
      if (bytes.firstOrNull()?.toInt() == '\r'.code) {
        workingDirectoryUpdateRequests.tryEmit(Unit)
      }
    }
  })

  coroutineScope.launch {
    workingDirectoryUpdateRequests
      .debounce(500)
      .collect {
        val cwd = doCalculateCurrentDirectory(ttyConnector) ?: return@collect
        listener(cwd)
      }
  }

  // TODO: Investigate why it may cause deadlock in tests were we start the real shell session.
  // Trigger the initial value calculation immediately without waiting
  //coroutineScope.launch {
  //  val cwd = doCalculateCurrentDirectory(ttyConnector) ?: return@launch
  //  listener(cwd)
  //}
}

private suspend fun doCalculateCurrentDirectory(ttyConnector: TtyConnector): String? {
  val processTtyConnector = ShellTerminalWidget.getProcessTtyConnector(ttyConnector) ?: run {
    BackendTerminalSession.LOG.warn("Failed to get process TTY connector: $ttyConnector. Working directory won't be updated.")
    return null
  }

  return try {
    ProcessInfoUtil.getInstance().getCurrentWorkingDirectory(processTtyConnector.process) ?: run {
      BackendTerminalSession.LOG.warn("Failed to get current working directory: $processTtyConnector")
      null
    }
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Exception) {
    BackendTerminalSession.LOG.warn("Failed to get current working directory: $processTtyConnector", e)
    null
  }
}