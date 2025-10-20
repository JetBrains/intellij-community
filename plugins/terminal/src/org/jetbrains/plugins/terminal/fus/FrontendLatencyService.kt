// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.fus

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.impl.EditorImpl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent

@ApiStatus.Internal
interface FrontendLatencyService {
  fun startFrontendOutputActivity(
    outputEditor: EditorImpl,
    alternateBufferEditor: EditorImpl,
  ): FrontendOutputActivity

  companion object {
    @JvmStatic
    fun getInstance(): FrontendLatencyService = service()
  }
}

@ApiStatus.Internal
interface FrontendOutputActivity {
  fun eventReceived(event: TerminalContentUpdatedEvent)
  fun beforeModelUpdate()
  fun afterModelUpdate()
}
