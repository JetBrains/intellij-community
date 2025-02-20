// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.session

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.Key
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.TerminalColorPalette
import com.intellij.terminal.TerminalExecutorServiceManagerImpl
import com.jediterm.core.typeahead.TerminalTypeAheadManager
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.*
import com.jediterm.terminal.model.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TerminalUtil
import org.jetbrains.plugins.terminal.block.output.TerminalAlarmManager
import org.jetbrains.plugins.terminal.block.session.util.FutureTerminalOutputStream
import org.jetbrains.plugins.terminal.shell_integration.CommandBlockIntegration
import org.jetbrains.plugins.terminal.util.STOP_EMULATOR_TIMEOUT
import org.jetbrains.plugins.terminal.util.ShellIntegration
import org.jetbrains.plugins.terminal.util.waitFor
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Represents a shell session with injected command-block shell integration.
 * It configures a terminal emulator and non-UI components depending on it, like [ShellCommandManager].
 *
 * Disposed on terminal UI component disposing, not on the shell session termination.
 */
@ApiStatus.Internal
class BlockTerminalSession(
  val settings: JBTerminalSystemSettingsProviderBase,
  val colorPalette: TerminalColorPalette,
  val shellIntegration: ShellIntegration,
) : Disposable {

  val model: TerminalModel

  /**
   * Use [terminalOutputStream] whenever possible instead of this field.
   * @see [terminalOutputStream]
   */
  internal val terminalStarterFuture: CompletableFuture<TerminalStarter?> = CompletableFuture()

  /**
   * This stream sends input to the terminal.
   * It ensures that any data sent to the terminal is properly
   * handled even if the terminal's output stream
   * isn't immediately available at the time of the request.
   */
  val terminalOutputStream: TerminalOutputStream = FutureTerminalOutputStream(terminalStarterFuture)

  private val executorServiceManager: TerminalExecutorServiceManager = TerminalExecutorServiceManagerImpl()

  private val textBuffer: TerminalTextBuffer
  internal val controller: JediTerminal
  internal val commandManager: ShellCommandManager
  internal val commandExecutionManager: ShellCommandExecutionManager
  private val typeAheadManager: TerminalTypeAheadManager
  private val terminationListeners: MutableList<Runnable> = CopyOnWriteArrayList()
  val commandBlockIntegration: CommandBlockIntegration = shellIntegration.commandBlockIntegration!!

  init {
    val styleState = StyleState()
    textBuffer = TerminalTextBuffer(80, 24, styleState, AdvancedSettings.getInt("terminal.buffer.max.lines.count"))
    model = TerminalModel(textBuffer)
    val alarmManager = TerminalAlarmManager(settings)
    controller = JediTerminal(ModelUpdatingTerminalDisplay(alarmManager, model, settings), textBuffer, styleState)

    commandManager = ShellCommandManager(this)
    commandExecutionManager = ShellCommandExecutionManagerImpl(this, commandManager, shellIntegration, controller, this as Disposable)
    // Add AlarmManager listener now, because we can't add it in its constructor.
    // Because AlarmManager need to be created before ShellCommandManager
    commandManager.addListener(alarmManager, this)

    val typeAheadTerminalModel = JediTermTypeAheadModel(controller, textBuffer, settings)
    typeAheadManager = TerminalTypeAheadManager(typeAheadTerminalModel)
    val typeAheadDebouncer = JediTermDebouncerImpl(typeAheadManager::debounce, TerminalTypeAheadManager.MAX_TERMINAL_DELAY, executorServiceManager)
    typeAheadManager.setClearPredictionsDebouncer(typeAheadDebouncer)
  }

  fun start(ttyConnector: TtyConnector) {
    val terminalStarter = TerminalStarter(controller, ttyConnector, TtyBasedArrayDataStream(ttyConnector),
                                          typeAheadManager, executorServiceManager)
    terminalStarterFuture.complete(terminalStarter)
    executorServiceManager.unboundedExecutorService.submit {
      try {
        terminalStarter.start()
      }
      catch (t: Throwable) {
        thisLogger().error(t)
      }
      finally {
        ttyConnector.closeSafely()
        for (terminationListener in terminationListeners) {
          try {
            terminationListener.run()
          }
          catch (t: Throwable) {
            thisLogger().error("Unhandled exception in termination listener", t)
          }
        }
      }
    }
  }

  fun addTerminationCallback(onTerminated: Runnable, parentDisposable: Disposable) {
    TerminalUtil.addItem(terminationListeners, onTerminated, parentDisposable)
  }

  fun postResize(newSize: TermSize) {
    terminalStarterFuture.thenAccept {
      if (it != null && (newSize.columns != model.width || newSize.rows != model.height)) {
        typeAheadManager.onResize()
        it.postResize(newSize, RequestOrigin.User)
      }
    }
  }

  fun addCommandListener(listener: ShellCommandListener, parentDisposable: Disposable = this) {
    commandManager.addListener(listener, parentDisposable)
  }

  private fun TtyConnector.closeSafely() {
    try {
      this.close()
    }
    catch (t: Throwable) {
      thisLogger().error("Error closing TtyConnector", t)
    }
  }

  override fun dispose() {
    // Complete to avoid memory leaks with hanging callbacks. If already completed, nothing will change.
    terminalStarterFuture.complete(null)
    terminalStarterFuture.getNow(null)?.let {
      it.close() // close in background
      it.ttyConnector.waitFor(STOP_EMULATOR_TIMEOUT) {
        it.requestEmulatorStop()
      }
    }
    executorServiceManager.shutdownWhenAllExecuted()
  }

  companion object {
    val KEY: Key<BlockTerminalSession> = Key.create("TerminalSession")
    val DATA_KEY: DataKey<BlockTerminalSession> = DataKey.create("TerminalSession")
  }

}
