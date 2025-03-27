package com.intellij.terminal.backend.util

import com.intellij.platform.rpc.UID
import com.intellij.terminal.session.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.fus.BackendOutputActivity

internal object BackendOutputTestFusActivity: BackendOutputActivity {
  override var sessionId: UID? = null

  override fun charsRead(count: Int) { }

  override fun charProcessingStarted() { }

  override fun charsProcessed(count: Int) { }

  override fun processedCharsReachedTextBuffer() { }

  override fun charProcessingFinished() { }

  override fun textBufferCharacterIndices(): LongRange = LongRange.EMPTY

  override fun textBufferCollected(event: TerminalContentUpdatedEvent) { }

  override fun eventCollected(event: TerminalContentUpdatedEvent) { }
}
