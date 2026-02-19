package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalPortForwardingId
import javax.swing.JComponent

@ApiStatus.Internal
interface TerminalPortForwardingUiProvider {
  /**
   * Returns the UI component that allows configuring the port forwarding for the session with [id].
   * Can suspend until the port forwarding session with this ID is created.
   */
  suspend fun createComponent(id: TerminalPortForwardingId, disposable: Disposable): JComponent?

  companion object {
    fun getInstance(project: Project): TerminalPortForwardingUiProvider = project.service()
  }
}

internal class TerminalNoPortForwardingUiProvider : TerminalPortForwardingUiProvider {
  override suspend fun createComponent(id: TerminalPortForwardingId, disposable: Disposable): JComponent? {
    return null
  }
}