package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalPortForwardingId
import javax.swing.JComponent

@ApiStatus.Internal
interface TerminalPortForwardingUiProvider {
  fun createComponent(id: TerminalPortForwardingId, disposable: Disposable): JComponent?

  companion object {
    fun getInstance(project: Project): TerminalPortForwardingUiProvider = project.service()
  }
}

internal class TerminalNoPortForwardingUiProvider : TerminalPortForwardingUiProvider {
  override fun createComponent(id: TerminalPortForwardingId, disposable: Disposable): JComponent? {
    return null
  }
}