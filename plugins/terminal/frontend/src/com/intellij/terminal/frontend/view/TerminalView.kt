package com.intellij.terminal.frontend.view

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.terminal.TerminalTitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.session.TerminalGridSize
import org.jetbrains.plugins.terminal.view.TerminalOutputModelsSet
import org.jetbrains.plugins.terminal.view.TerminalSendTextBuilder
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellIntegration
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

  val sessionState: StateFlow<TerminalViewSessionState>

  suspend fun hasChildProcesses(): Boolean

  fun getCurrentDirectory(): String?

  fun sendText(text: String)

  fun createSendTextBuilder(): TerminalSendTextBuilder

  fun getShellIntegration(): TerminalShellIntegration?

  suspend fun awaitShellIntegrationInitialized(): TerminalShellIntegration

  // todo

  companion object {
    val DATA_KEY: DataKey<TerminalView> = DataKey.create("TerminalView")
  }
}

@ApiStatus.Experimental
fun TerminalView.activeOutputModel(): TerminalOutputModel {
  return outputModels.active.value
}