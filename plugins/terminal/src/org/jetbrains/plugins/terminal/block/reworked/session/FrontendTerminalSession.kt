// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session

import com.intellij.terminal.session.TerminalInputEvent
import com.intellij.terminal.session.TerminalOutputEvent
import com.intellij.terminal.session.TerminalSession
import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionApi
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId

/**
 * [TerminalSession] implementation that is delegating the methods to [TerminalSessionApi] RPC to backend.
 *
 * Normally, it should be located in the frontend module, but it can't be moved there
 * because it should be accessible from the shared terminal widget creating API with a lot of external usages.
 */
internal class FrontendTerminalSession(private val id: TerminalSessionId) : TerminalSession {
  override suspend fun sendInputEvent(event: TerminalInputEvent) {
    TerminalSessionApi.Companion.getInstance().sendInputEvent(id, event)
  }

  override suspend fun getOutputFlow(): Flow<List<TerminalOutputEvent>> {
    return TerminalSessionApi.Companion.getInstance().getOutputFlow(id)
  }
}