// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session

import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class TerminalSessionImpl(
  private val inputChannel: SendChannel<TerminalInputEvent>,
  private val outputFlow: Flow<List<TerminalOutputEvent>>,
) : TerminalSession {
  override suspend fun sendInputEvent(event: TerminalInputEvent) {
    inputChannel.send(event)
  }

  override suspend fun getOutputFlow(): Flow<List<TerminalOutputEvent>> {
    return outputFlow
  }
}