package com.intellij.terminal.backend

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.ProxyTtyConnector
import org.jetbrains.plugins.terminal.TerminalUtil
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Wrapper around [TtyConnector] that allows listening for reading chars from the original connector using [TtyConnectorListener].
 */
@ApiStatus.Internal
class ObservableTtyConnector(delegate: TtyConnector) : ProxyTtyConnector {
  override val connector: TtyConnector = delegate

  private val listeners = CopyOnWriteArrayList<TtyConnectorListener>()

  fun addListener(parentDisposable: Disposable, listener: TtyConnectorListener) {
    TerminalUtil.addItem(listeners, listener, parentDisposable)
  }

  override fun read(buf: CharArray, offset: Int, length: Int): Int {
    val charsReadCount = connector.read(buf, offset, length)
    if (charsReadCount > 0) {
      fireCharsRead(buf, offset, charsReadCount)
    }
    return charsReadCount
  }

  private fun fireCharsRead(buf: CharArray, offset: Int, length: Int) {
    for (listener in listeners) {
      try {
        listener.charsRead(buf, offset, length)
      }
      catch (t: Throwable) {
        thisLogger().error(t)
      }
    }
  }

  override fun write(bytes: ByteArray?) {
    connector.write(bytes)
  }

  override fun write(string: String?) {
    connector.write(string)
  }

  override fun isConnected(): Boolean {
    return connector.isConnected
  }

  override fun resize(termSize: TermSize) {
    return connector.resize(termSize)
  }

  override fun waitFor(): Int {
    return connector.waitFor()
  }

  override fun ready(): Boolean {
    return connector.ready()
  }

  override fun getName(): String? {
    return connector.name
  }

  override fun close() {
    connector.close()
  }
}
