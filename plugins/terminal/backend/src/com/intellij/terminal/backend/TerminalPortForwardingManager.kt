package com.intellij.terminal.backend

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalPortForwardingId

@ApiStatus.Internal
interface TerminalPortForwardingManager {
  suspend fun setupPortForwarding(ttyConnector: ObservableTtyConnector, coroutineScope: CoroutineScope): TerminalPortForwardingId?

  companion object {
    fun getInstance(project: Project): TerminalPortForwardingManager = project.service()
  }
}

/**
 * Default implementation of the [TerminalPortForwardingManager]: no port forwarding is provided.
 */
internal class TerminalNoPortForwardingManager : TerminalPortForwardingManager {
  override suspend fun setupPortForwarding(ttyConnector: ObservableTtyConnector, coroutineScope: CoroutineScope): TerminalPortForwardingId? {
    return null
  }
}