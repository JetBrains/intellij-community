package com.intellij.terminal.backend

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalPortForwardingId

@ApiStatus.Internal
interface TerminalPortForwardingManager {
  fun setupPortForwarding(ttyConnector: ObservableTtyConnector, disposable: Disposable): TerminalPortForwardingId?

  companion object {
    fun getInstance(project: Project): TerminalPortForwardingManager = project.service()
  }
}

/**
 * Default implementation of the [TerminalPortForwardingManager]: no port forwarding is provided.
 */
internal class TerminalNoPortForwardingManager : TerminalPortForwardingManager {
  override fun setupPortForwarding(ttyConnector: ObservableTtyConnector, disposable: Disposable): TerminalPortForwardingId? {
    return null
  }
}