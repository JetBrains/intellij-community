package com.intellij.terminal.frontend.view

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.terminal.TerminalTitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.session.TerminalGridSize
import org.jetbrains.plugins.terminal.session.TerminalStartupOptions
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelsSet
import org.jetbrains.plugins.terminal.view.TerminalSendTextBuilder
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellIntegration
import javax.swing.JComponent

/**
 * Represents the frontend part of the Reworked Terminal.
 * Contains models and APIs for interaction with the terminal and the underlying shell process.
 *
 * Currently, the only way to create the Terminal View is to open a new terminal tab in the Terminal Tool Window.
 * ([com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager.createTabBuilder])
 * The lifecycle of the Terminal View (and the underlying shell process) is bound to the [coroutineScope].
 * Do not cancel the scope manually, use [com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager.closeTab] instead.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalView {
  /**
   * Represents the lifecycle of the Terminal View (and the underlying shell process).
   * Can be used to clear resources when the terminal session is terminated.
   * For example, using [kotlinx.coroutines.Job.invokeOnCompletion].
   */
  val coroutineScope: CoroutineScope

  /**
   * The UI component that renders the terminal content.
   */
  val component: JComponent

  val preferredFocusableComponent: JComponent

  /**
   * Terminal content is rendered in a grid of characters.
   * This property represents the current size of the grid.
   */
  val gridSize: TerminalGridSize?

  /**
   * The name of the terminal session.
   * Currently used to show the name in the Terminal Tool Window tab.
   */
  val title: TerminalTitle

  /**
   * @see TerminalOutputModelsSet
   * @see TerminalOutputModel
   */
  val outputModels: TerminalOutputModelsSet

  /**
   * Model that manages selection of the text in both output models from [TerminalOutputModelsSet].
   */
  val textSelectionModel: TerminalTextSelectionModel

  /**
   * Represents the current state of the connection to the shell process.
   */
  val sessionState: StateFlow<TerminalViewSessionState>

  /**
   * Flow of key events that are typed in the terminal.
   * Events consumed by the action system are not included here.
   *
   * Each key event is emitted after sending input to the shell process.
   * Note that [TerminalOutputModel] is updated asynchronously after shell receives the input and updates the screen text.
   * So, when collecting this flow, the result of typing may not be reflected in the [TerminalOutputModel] yet.
   *
   * If you need to perform some action on some specific shortcut,
   * prefer implementing [com.intellij.openapi.actionSystem.AnAction] and registering it using [TerminalAllowedActionsProvider]
   * instead of handling key events directly.
   */
  val keyEventsFlow: Flow<TerminalKeyEvent>

  /**
   * Can be used to get or await the shell integration initialization.
   *
   * Note that **it may never complete** because the shell integration may be not available
   * (for example, because of an unsupported shell or environment)
   * or it can be disabled in the Terminal settings ([org.jetbrains.plugins.terminal.TerminalOptionsProvider.shellIntegration]).
   *
   * If the started shell supports the shell integration,
   * it will be initialized before the first prompt is printed in the output.
   *
   * @see TerminalShellIntegration
   */
  val shellIntegrationDeferred: Deferred<TerminalShellIntegration>

  /**
   * Options used to start the shell process.
   * Available after the shell process is started and connected to the [TerminalView].
   */
  val startupOptionsDeferred: Deferred<TerminalStartupOptions>

  /*
   * Checks if the shell process has child processes.
   * For example, it can be a running command (even a background one).
   *
   * The checks are performed using OS-specific heuristics, so it may be not accurate in some cases.
   * For example, currently, detection of the child process doesn't work correctly when the project is opened in WSL.
   * Now it will always return false in this case.
   *
   * Returns false if the shell process is not connected yet to the [TerminalView].
   */
  suspend fun hasChildProcesses(): Boolean

  /**
   * Returns the OS-dependent absolute path of the current working directory of the shell process.
   *
   * When [TerminalShellIntegration] is available, it will be used to get the current directory.
   * Otherwise, the OS-specific heuristics and polling approach will be used.
   * So it may be not accurate in some cases.
   * For example, currently, in the case of WSL, it will always return the initial working directory.
   *
   * Returns null if the shell process is not connected yet to the [TerminalView]
   * or if the initial value is not yet received from the backend.
   */
  fun getCurrentDirectory(): String?

  /**
   * A shortcut to schedule sending the specified text to the shell process
   * without additional options from [TerminalSendTextBuilder].
   */
  fun sendText(text: String)

  /**
   * Creates the builder with additional options for sending text to the shell process.
   */
  fun createSendTextBuilder(): TerminalSendTextBuilder

  companion object {
    /**
     * The data context key for accessing the Terminal View from actions.
     *
     * @see [com.intellij.openapi.actionSystem.DataContext]
     */
    val DATA_KEY: DataKey<TerminalView> = DataKey.create("TerminalView")
  }
}

/**
 * The shortcut to get the currently active output model.
 */
@ApiStatus.Experimental
fun TerminalView.activeOutputModel(): TerminalOutputModel {
  return outputModels.active.value
}