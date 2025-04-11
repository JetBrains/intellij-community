// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.fus

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.terminal.session.TerminalContentUpdatedEvent
import com.intellij.terminal.session.TerminalInputEvent
import com.intellij.terminal.session.TerminalWriteBytesEvent
import org.jetbrains.annotations.ApiStatus
import java.awt.event.KeyEvent

@ApiStatus.Internal
interface FrontendLatencyService {

  companion object {
    @JvmStatic
    fun getInstance(): FrontendLatencyService = service()
  }

  fun startFrontendTypingActivity(e: KeyEvent): FrontendTypingActivity?

  fun getCurrentKeyEventTypingActivityOrNull(): FrontendTypingActivity?

  fun getFrontendTypingActivityOrNull(event: TerminalInputEvent): FrontendTypingActivity?

  fun startFrontendOutputActivity(
    outputEditor: EditorImpl,
    alternateBufferEditor: EditorImpl,
  ): FrontendOutputActivity
}

@ApiStatus.Internal
interface FrontendTypingActivity {
  val id: Int
  fun startTerminalInputEventProcessing(writeBytesEvent: TerminalWriteBytesEvent)
  fun finishKeyEventProcessing()
  fun reportDuration()
  fun finishTerminalInputEventProcessing()
}

@ApiStatus.Internal
interface FrontendOutputActivity {
  fun eventReceived(event: TerminalContentUpdatedEvent)
  fun beforeModelUpdate()
  fun afterModelUpdate()
}
