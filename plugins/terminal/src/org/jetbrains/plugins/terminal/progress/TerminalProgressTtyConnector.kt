// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.progress

import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.ProxyTtyConnector

@ApiStatus.Internal
class TerminalProgressTtyConnector(
  override val connector: TtyConnector,
  progressHandler: (TerminalProgressState) -> Unit,
) : ProxyTtyConnector {
  private val progressParser = TerminalProgressParser(progressHandler)

  override fun read(buf: CharArray, offset: Int, length: Int): Int {
    val charsReadCount = connector.read(buf, offset, length)
    if (charsReadCount > 0) {
      progressParser.process(buf, offset, charsReadCount)
    }
    return charsReadCount
  }

  override fun write(bytes: ByteArray) {
    connector.write(bytes)
  }

  override fun write(string: String?) {
    connector.write(string)
  }

  override fun isConnected(): Boolean {
    return connector.isConnected
  }

  override fun resize(termSize: TermSize) {
    connector.resize(termSize)
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
