package com.intellij.terminal.backend

import com.intellij.openapi.diagnostic.thisLogger
import com.jediterm.core.input.KeyEvent
import com.jediterm.core.typeahead.TerminalTypeAheadManager
import com.jediterm.terminal.TerminalDataStream
import com.jediterm.terminal.TerminalExecutorServiceManager
import com.jediterm.terminal.TerminalStarter
import com.jediterm.terminal.TtyConnector
import com.jediterm.terminal.model.JediTerminal
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import java.io.IOException
import java.util.concurrent.ScheduledExecutorService
import kotlin.time.TimeMark

internal class TerminalStarterEx(
  terminal: JediTerminal,
  private val ttyConnector: TtyConnector,
  dataStream: TerminalDataStream,
  typeAheadManager: TerminalTypeAheadManager,
  executorServiceManager: TerminalExecutorServiceManager,
) : TerminalStarter(terminal, ttyConnector, dataStream, typeAheadManager, executorServiceManager) {
  @Volatile
  var isStopped: Boolean = false
    private set

  private val singleThreadScheduledExecutor: ScheduledExecutorService = executorServiceManager.singleThreadScheduledExecutor

  @Volatile
  private var isLastSentByteEscape = false

  override fun requestEmulatorStop() {
    super.requestEmulatorStop()
    isStopped = true
  }

  /**
   * Use for sending bytes for that typing latency should be reported.
   * [eventTime] is the moment when this writing bytes event was initialized.
   */
  fun sendTrackedBytes(bytes: ByteArray, eventId: Int, eventTime: TimeMark) {
    val length = bytes.size
    if (length > 0) {
      isLastSentByteEscape = bytes[length - 1].toInt() == KeyEvent.VK_ESCAPE
    }
    execute {
      try {
        ttyConnector.write(bytes)

        val latency = eventTime.elapsedNow()
        ReworkedTerminalUsageCollector.logBackendTypingLatency(eventId, latency)
      }
      catch (e: IOException) {
        thisLogger().info("Cannot write to TtyConnector ${ttyConnector.javaClass.getName()}, connected: ${ttyConnector.isConnected}", e)
      }
    }
  }

  override fun isLastSentByteEscape(): Boolean {
    return isLastSentByteEscape || super.isLastSentByteEscape()
  }

  private inline fun execute(crossinline action: () -> Unit) {
    if (!singleThreadScheduledExecutor.isShutdown) {
      singleThreadScheduledExecutor.execute { action() }
    }
  }
}
