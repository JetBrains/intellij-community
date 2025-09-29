package com.intellij.terminal.frontend

import com.intellij.openapi.Disposable
import com.jediterm.core.util.TermSize
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalView {
  val coroutineScope: CoroutineScope

  val component: JComponent

  val preferredFocusableComponent: JComponent

  val size: TermSize?

  fun addTerminationCallback(parentDisposable: Disposable, callback: Runnable)

  // todo
}