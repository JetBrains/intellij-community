package com.intellij.terminal.frontend

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.terminal.TerminalTitle
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.session.TerminalGridSize
import javax.swing.JComponent

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalView {
  val coroutineScope: CoroutineScope

  val component: JComponent

  val preferredFocusableComponent: JComponent

  val gridSize: TerminalGridSize?

  val title: TerminalTitle

  val outputModels: TerminalOutputModelsSet

  fun addTerminationCallback(parentDisposable: Disposable, callback: () -> Unit)

  suspend fun hasChildProcesses(): Boolean

  fun getCurrentDirectory(): String?

  fun sendText(text: String)

  fun createSendTextBuilder(): TerminalSendTextBuilder

  // todo

  companion object {
    val DATA_KEY: DataKey<TerminalView> = DataKey.create("TerminalView")
  }
}

@ApiStatus.Experimental
fun TerminalView.activeOutputModel(): TerminalOutputModel {
  return outputModels.active.value
}