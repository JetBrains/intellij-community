// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.util.ConcurrencyUtil
import com.jediterm.core.typeahead.TerminalTypeAheadManager
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.RequestOrigin
import com.jediterm.terminal.TerminalStarter
import com.jediterm.terminal.TtyBasedArrayDataStream
import com.jediterm.terminal.TtyConnector
import com.jediterm.terminal.model.JediTermDebouncerImpl
import com.jediterm.terminal.model.JediTermTypeAheadModel
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer
import java.awt.event.KeyEvent
import java.util.concurrent.ExecutorService

private var sessionIndex = 1

class TerminalSession(private val project: Project,
                      private val settings: JBTerminalSystemSettingsProviderBase) : Disposable {
  val model: TerminalModel
  lateinit var terminalStarter: TerminalStarter
  val completionManager: TerminalCompletionManager

  private val terminalExecutor: ExecutorService = ConcurrencyUtil.newSingleScheduledThreadExecutor("Terminal-${sessionIndex++}")

  private val textBuffer: TerminalTextBuffer
  private val controller: TerminalController
  private val commandManager: ShellCommandManager
  private val typeAheadManager: TerminalTypeAheadManager

  init {
    val styleState = StyleState()
    styleState.setDefaultStyle(settings.defaultStyle)
    textBuffer = TerminalTextBufferEx(80, 24, styleState)
    model = TerminalModel(textBuffer, styleState)
    controller = TerminalController(model, settings)

    commandManager = ShellCommandManager(controller)
    completionManager = TerminalCompletionManager(model) { terminalStarter }

    val typeAheadTerminalModel = JediTermTypeAheadModel(controller, textBuffer, settings)
    typeAheadManager = TerminalTypeAheadManager(typeAheadTerminalModel)
    val typeAheadDebouncer = JediTermDebouncerImpl(typeAheadManager::debounce, TerminalTypeAheadManager.MAX_TERMINAL_DELAY)
    typeAheadManager.setClearPredictionsDebouncer(typeAheadDebouncer)
  }

  fun start(ttyConnector: TtyConnector) {
    terminalStarter = TerminalStarter(controller, ttyConnector, TtyBasedArrayDataStream(ttyConnector), typeAheadManager)
    terminalExecutor.submit {
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
    if (this::terminalStarter.isInitialized) {
      terminalStarter.postResize(newSize, RequestOrigin.User)
    }
  }

  fun addCommandListener(listener: ShellCommandListener, parentDisposable: Disposable) {
    commandManager.addListener(listener, parentDisposable)
  }

  override fun dispose() {
    terminalExecutor.shutdown()
    // Can be disposed before session is started
    if (this::terminalStarter.isInitialized) {
      terminalStarter.close()
    }
  }
}