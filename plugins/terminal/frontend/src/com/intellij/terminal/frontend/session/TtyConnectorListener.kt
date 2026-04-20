package com.intellij.terminal.frontend.session

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TtyConnectorListener {
  fun charsRead(buf: CharArray, offset: Int, length: Int) {}

  fun bytesWritten(bytes: ByteArray) {}
}