package com.intellij.terminal.frontend.impl

import com.intellij.openapi.Disposable
import com.intellij.terminal.frontend.TerminalView
import com.intellij.terminal.session.TerminalSession
import com.jediterm.core.util.TermSize
import kotlinx.coroutines.CoroutineScope
import javax.swing.JComponent

internal class TerminalViewImpl(override val coroutineScope: CoroutineScope) : TerminalView {
  override val component: JComponent
    get() = TODO("Not yet implemented")
  override val preferredFocusableComponent: JComponent
    get() = TODO("Not yet implemented")
  override val size: TermSize?
    get() = TODO("Not yet implemented")

  fun connectToSession(session: TerminalSession) {
    // todo
  }

  override fun addTerminationCallback(parentDisposable: Disposable, callback: Runnable) {
    // todo
  }
}