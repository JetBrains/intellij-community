package com.intellij.terminal.frontend

import com.intellij.openapi.Disposable
import com.intellij.terminal.TerminalTitle
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

  val title: TerminalTitle

  fun addTerminationCallback(parentDisposable: Disposable, callback: () -> Unit)

  suspend fun hasChildProcesses(): Boolean

  fun getCurrentDirectory(): String?

  fun sendText(text: String)

  fun createSendTextBuilder(): TerminalSendTextBuilder

  // todo
}