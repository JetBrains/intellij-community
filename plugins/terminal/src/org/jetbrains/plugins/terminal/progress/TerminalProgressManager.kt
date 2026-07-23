// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.progress

import com.intellij.terminal.ui.TerminalWidget
import com.jediterm.terminal.TtyConnector
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
object TerminalProgressManager {
  private const val PROGRESS_LISTENER_CLIENT_PROPERTY: String = "TerminalProgressListener"

  @JvmStatic
  fun install(component: JComponent, listener: TerminalProgressListener) {
    component.putClientProperty(PROGRESS_LISTENER_CLIENT_PROPERTY, listener)
  }

  @JvmStatic
  fun wrapConnector(terminalWidget: TerminalWidget, connector: TtyConnector): TtyConnector {
    val listener = getListener(terminalWidget) ?: return connector
    return TerminalProgressTtyConnector(connector, listener::progressChanged)
  }

  @JvmStatic
  fun clear(terminalWidget: TerminalWidget) {
    getListener(terminalWidget)?.progressChanged(TerminalProgressState.NONE)
  }

  private fun getListener(terminalWidget: TerminalWidget): TerminalProgressListener? {
    return terminalWidget.component.getClientProperty(PROGRESS_LISTENER_CLIENT_PROPERTY) as? TerminalProgressListener
  }
}

@ApiStatus.Internal
fun interface TerminalProgressListener {
  fun progressChanged(progressState: TerminalProgressState)
}
