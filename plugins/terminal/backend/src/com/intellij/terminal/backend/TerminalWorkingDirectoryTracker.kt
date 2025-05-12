package com.intellij.terminal.backend

import com.intellij.util.asDisposable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.arrangement.ProcessInfoUtil

@OptIn(FlowPreview::class)
internal fun addWorkingDirectoryListener(
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
        val processTtyConnector = ShellTerminalWidget.getProcessTtyConnector(ttyConnector) ?: run {
          BackendTerminalSession.LOG.warn("Failed to get process TTY connector: $ttyConnector. Working directory won't be updated.")
          return@collect
        }

        val cwd = try {
          ProcessInfoUtil.getInstance().getCurrentWorkingDirectory(processTtyConnector.process) ?: run {
            BackendTerminalSession.LOG.warn("Failed to get current working directory: $processTtyConnector")
            return@collect
          }
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Exception) {
          BackendTerminalSession.LOG.warn("Failed to get current working directory: $processTtyConnector", e)
          return@collect
        }

        listener(cwd)
      }
  }
}