// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.fus

import com.intellij.openapi.components.service
import com.intellij.platform.rpc.UID
import com.intellij.terminal.session.TerminalContentUpdatedEvent
import com.intellij.terminal.session.TerminalWriteBytesEvent
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface BackendLatencyService {
  companion object {
    @JvmStatic
    fun getInstance(): BackendLatencyService = service()
  }
  fun tryStartBackendTypingActivity(event: TerminalWriteBytesEvent)
  fun getBackendTypingActivityOrNull(bytes: ByteArray): BackendTypingActivity?
  fun startBackendOutputActivity(): BackendOutputActivity
}

@ApiStatus.Internal
interface BackendTypingActivity {
  val id: Int
  fun reportDuration()
  fun finishBytesProcessing()
}

@ApiStatus.Internal
interface BackendOutputActivity {
  var sessionId: UID?
  fun charsRead(count: Int)
  fun charProcessingStarted()
  fun charsProcessed(count: Int)
  fun processedCharsReachedTextBuffer()
  fun charProcessingFinished()
  fun textBufferCharacterIndices(): LongRange
  fun textBufferCollected(event: TerminalContentUpdatedEvent)
  fun eventCollected(event: TerminalContentUpdatedEvent)
}
