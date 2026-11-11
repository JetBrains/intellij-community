package com.intellij.terminal.frontend.session

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.asDisposable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.LocalTerminalTtyConnector
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.arrangement.ProcessInfoUtil
import org.jetbrains.plugins.terminal.block.reworked.TerminalShellIntegrationEventsListener
import org.jetbrains.plugins.terminal.original
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import org.jetbrains.plugins.terminal.util.convertNativePathToNioPath
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds

/**
 * Returns the directory that the session can report before the process reports one.
 */
internal fun getInitialWorkingDirectory(options: ShellStartupOptions): Path? {
  if (options.processType == TerminalProcessType.SHELL) {
    // Do not use a directory requested for a shell process as an initial directory.
    // Because shell may actually decide to start in a different directory.
    // For example, CMD starts in the home directory if the process is started with a WSL path.
    return null
  }
  // But if it is a NON_SHELL process, it is safe to use the directory where the process started.
  // Because it shouldn't change.
  return options.workingDirectoryEelPathNotNull.asNioPath()
}

/**
 * Reports the working directory of the process to [listener].
 * Reports nothing if [ttyConnector] does not wrap a [LocalTerminalTtyConnector],
 * because only this connector knows the Eel environment of the process.
 */
internal fun addWorkingDirectoryListener(
  ttyConnector: ObservableTtyConnector,
  shellIntegrationController: TerminalShellIntegrationController,
  coroutineScope: CoroutineScope,
  listener: (Path) -> Unit,
) {
  val localTtyConnector = ttyConnector.original as? LocalTerminalTtyConnector ?: run {
    LOG.info("Failed to get local TTY connector: $ttyConnector. Working directory won't be updated.")
    return
  }
  val eelDescriptor = localTtyConnector.eelDescriptor
  val reportDirectory = { directory: String ->
    val path = convertNativePathToNioPath(directory, eelDescriptor)
    if (path != null) {
      listener(path)
    }
    else {
      LOG.warn("Failed to convert working directory '$directory' to a path on $eelDescriptor")
    }
  }

  val heuristicBasedTrackerScope = coroutineScope.childScope("Heuristic based working directory tracker")
  addHeuristicBasedCwdListener(ttyConnector, localTtyConnector, heuristicBasedTrackerScope, reportDirectory)

  shellIntegrationController.addListener(object : TerminalShellIntegrationEventsListener {
    override fun initialized(currentDirectory: String?, historyPath: String?) {
      // Stop heuristic-based working directory tracking if there is a shell integration.
      // We will receive the current directory from the shell integration.
      heuristicBasedTrackerScope.cancel()

      if (currentDirectory != null) {
        reportDirectory(currentDirectory)
      }
    }

    override fun commandFinished(command: String, exitCode: Int, currentDirectory: String?) {
      if (currentDirectory != null) {
        reportDirectory(currentDirectory)
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
  localTtyConnector: LocalTerminalTtyConnector,
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
      .debounce(500.milliseconds)
      .collect {
        val cwd = doCalculateCurrentDirectory(localTtyConnector) ?: return@collect
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

private suspend fun doCalculateCurrentDirectory(processTtyConnector: LocalTerminalTtyConnector): String? {
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