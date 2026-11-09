package com.intellij.terminal.frontend.session

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.path.EelPath
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
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.arrangement.ProcessInfoUtil
import org.jetbrains.plugins.terminal.block.reworked.TerminalShellIntegrationEventsListener
import org.jetbrains.plugins.terminal.startup.TerminalProcessType

/**
 * Returns the directory that the session can report before the process reports one.
 */
internal fun getInitialWorkingDirectory(options: ShellStartupOptions): EelPath? {
  if (options.processType == TerminalProcessType.SHELL) {
    // Do not use a directory requested for a shell process as an initial directory.
    // Because shell may actually decide to start in a different directory.
    // For example, CMD starts in the home directory if the process is started with a WSL path.
    return null
  }
  // But if it is a NON_SHELL process, it is safe to use the directory where the process started.
  // Because it shouldn't change.
  return options.workingDirectoryEelPathNotNull
}

internal fun addWorkingDirectoryListener(
  ttyConnector: ObservableTtyConnector,
  shellIntegrationController: TerminalShellIntegrationController,
  coroutineScope: CoroutineScope,
  listener: (String) -> Unit,
) {
  val heuristicBasedTrackerScope = coroutineScope.childScope("Heuristic based working directory tracker")
  addHeuristicBasedCwdListener(ttyConnector, heuristicBasedTrackerScope, listener)

  shellIntegrationController.addListener(object : TerminalShellIntegrationEventsListener {
    override fun initialized(currentDirectory: String?, historyPath: String?) {
      // Stop heuristic-based working directory tracking if there is a shell integration.
      // We will receive the current directory from the shell integration.
      heuristicBasedTrackerScope.cancel()

      if (currentDirectory != null) {
        listener(currentDirectory)
      }
    }

    override fun commandFinished(command: String, exitCode: Int, currentDirectory: String?) {
      if (currentDirectory != null) {
        listener(currentDirectory)
      }
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
    LOG.warn("Failed to get process TTY connector: $ttyConnector. Working directory won't be updated.")
    return null
  }

  return try {
    ProcessInfoUtil.getInstance().getCurrentWorkingDirectory(processTtyConnector.process) ?: run {
      LOG.warn("Failed to get current working directory: $processTtyConnector")
      null
    }
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Exception) {
    LOG.warn("Failed to get current working directory: $processTtyConnector", e)
    null
  }
}

private val LOG = fileLogger()