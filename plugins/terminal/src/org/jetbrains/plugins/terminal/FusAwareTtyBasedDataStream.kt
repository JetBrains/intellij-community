// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.jediterm.terminal.TtyBasedArrayDataStream
import com.jediterm.terminal.TtyConnector
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.fus.BackendOutputActivity

@ApiStatus.Internal
class FusAwareTtyBasedDataStream(
  connector: TtyConnector,
  private val fusActivity: BackendOutputActivity,
) : TtyBasedArrayDataStream(connector) {

  override fun getChar(): Char {
    val result = super.getChar()
    fusActivity.charsProcessed(1)
    return result
  }

  override fun readNonControlCharacters(maxChars: Int): String {
    val result = super.readNonControlCharacters(maxChars)
    fusActivity.charsProcessed(result.length)
    return result
  }

  override fun pushChar(c: Char) {
    fusActivity.charsProcessed(-1)
    super.pushChar(c)
  }

  override fun pushBackBuffer(bytes: CharArray, length: Int) {
    fusActivity.charsProcessed(-length)
    super.pushBackBuffer(bytes, length)
  }
}
