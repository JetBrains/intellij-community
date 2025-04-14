package com.intellij.terminal.backend.fus

import com.intellij.openapi.Disposable
import com.intellij.terminal.backend.ObservableTtyConnector
import com.intellij.terminal.backend.TtyConnectorListener
import com.intellij.terminal.session.TerminalWriteBytesEvent
import fleet.multiplatform.shims.ConcurrentHashMap
import org.jetbrains.plugins.terminal.fus.BackendLatencyService
import org.jetbrains.plugins.terminal.fus.BackendTypingActivity
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import kotlin.time.TimeSource

internal class BackendLatencyServiceImpl : BackendLatencyService {
  override fun tryStartBackendTypingActivity(event: TerminalWriteBytesEvent) {
    val id = event.id
    val bytes = event.bytes
    val activity = BackendTypingActivityImpl(id, bytes)
    backendTypingActivityByByteArray[bytes] = activity
  }

  override fun getBackendTypingActivityOrNull(bytes: ByteArray): BackendTypingActivity? {
    return backendTypingActivityByByteArray[bytes]
  }
}

internal fun installFusListener(
  ttyConnector: ObservableTtyConnector,
  parentDisposable: Disposable,
) {
  ttyConnector.addListener(parentDisposable, object : TtyConnectorListener {
    override fun bytesWritten(bytes: ByteArray) {
      val typingActivity = BackendLatencyService.getInstance().getBackendTypingActivityOrNull(bytes) ?: return
      try {
        typingActivity.reportDuration()
      }
      finally {
        typingActivity.finishBytesProcessing()
      }
    }
  })
}

private val backendTypingActivityByByteArray = ConcurrentHashMap<ByteArray, BackendTypingActivityImpl>()

private class BackendTypingActivityImpl(override val id: Int, private val bytes: ByteArray) : BackendTypingActivity {
  private val start = TimeSource.Monotonic.markNow()

  override fun reportDuration() {
    val duration = start.elapsedNow()
    ReworkedTerminalUsageCollector.logBackendLatency(
      inputEventId = id,
      duration = duration,
    )
  }

  override fun finishBytesProcessing() {
    backendTypingActivityByByteArray.remove(bytes)
  }
}