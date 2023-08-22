// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Key
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.TerminalExecutorServiceManagerImpl
import com.jediterm.core.typeahead.TerminalTypeAheadManager
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.*
import com.jediterm.terminal.model.JediTermDebouncerImpl
import com.jediterm.terminal.model.JediTermTypeAheadModel
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer
import org.jetbrains.plugins.terminal.util.ShellIntegration
import java.awt.event.KeyEvent

class TerminalSession(settings: JBTerminalSystemSettingsProviderBase) : Disposable {
  val model: TerminalModel
  lateinit var terminalStarter: TerminalStarter

  private val executorServiceManager: TerminalExecutorServiceManager = TerminalExecutorServiceManagerImpl()

  private val textBuffer: TerminalTextBuffer
  val controller: TerminalController
  private val commandManager: ShellCommandManager
  private val typeAheadManager: TerminalTypeAheadManager
  @Volatile
  var shellIntegration: ShellIntegration? = null

  init {
    val styleState = StyleState()
    styleState.setDefaultStyle(settings.defaultStyle)
    textBuffer = TerminalTextBufferEx(80, 24, styleState)
    model = TerminalModel(textBuffer, styleState)
    controller = TerminalController(model, settings)

    commandManager = ShellCommandManager(controller)

    val typeAheadTerminalModel = JediTermTypeAheadModel(controller, textBuffer, settings)
    typeAheadManager = TerminalTypeAheadManager(typeAheadTerminalModel)
    val typeAheadDebouncer = JediTermDebouncerImpl(typeAheadManager::debounce, TerminalTypeAheadManager.MAX_TERMINAL_DELAY, executorServiceManager)
    typeAheadManager.setClearPredictionsDebouncer(typeAheadDebouncer)
  }

  fun start(ttyConnector: TtyConnector) {
    terminalStarter = TerminalStarter(controller, ttyConnector, TtyBasedArrayDataStream(ttyConnector), typeAheadManager, executorServiceManager)
    executorServiceManager.unboundedExecutorService.submit {
      terminalStarter.start()
    }
  }

  fun executeCommand(command: String) {
    val enterCode = terminalStarter.getCode(KeyEvent.VK_ENTER, 0)
    terminalStarter.sendString(command, false)
    terminalStarter.sendBytes(enterCode, false)
  }

  fun postResize(newSize: TermSize) {
    // it can be executed right after component is shown,
    // terminal starter can not be initialized at this point
    if (this::terminalStarter.isInitialized && (newSize.columns != model.width || newSize.rows != model.height)) {
      // TODO: is it needed?
      //myTypeAheadManager.onResize()
      terminalStarter.postResize(newSize, RequestOrigin.User)
    }
  }

  fun addCommandListener(listener: ShellCommandListener, parentDisposable: Disposable? = null) {
    commandManager.addListener(listener, parentDisposable)
  }

  override fun dispose() {
    executorServiceManager.shutdownWhenAllExecuted()
    // Can be disposed before session is started
    if (this::terminalStarter.isInitialized) {
      terminalStarter.close()
    }
  }

  companion object {
    val KEY: Key<TerminalSession> = Key.create("TerminalSession")
  }
}