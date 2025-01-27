// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session

import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.terminal.block.reworked.session.output.TerminalOutputEvent

internal interface TerminalSession {
  val inputChannel: SendChannel<TerminalInputEvent>

  suspend fun getOutputFlow(): Flow<List<TerminalOutputEvent>>
}