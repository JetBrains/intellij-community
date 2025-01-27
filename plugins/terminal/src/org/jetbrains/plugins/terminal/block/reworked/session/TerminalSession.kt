// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session

import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TerminalSession {
  suspend fun sendInputEvent(event: TerminalInputEvent)

  suspend fun getOutputFlow(): Flow<List<TerminalOutputEvent>>
}