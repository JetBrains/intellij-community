// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.util.ConcurrencyUtil
import com.jediterm.core.typeahead.TerminalTypeAheadManager
import com.jediterm.terminal.TerminalStarter
import com.jediterm.terminal.TtyBasedArrayDataStream
import com.jediterm.terminal.model.*
import org.jetbrains.plugins.terminal.AbstractTerminalRunner
import org.jetbrains.plugins.terminal.TerminalProcessOptions
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

private var sessionIndex = 1

class TerminalSession(private val project: Project,
                      private val settings: JBTerminalSystemSettingsProviderBase,
                      private val size: Dimension): Disposable {
  val model: TerminalModel
  val terminalStarter: TerminalStarter

  private val terminalExecutor: ExecutorService = ConcurrencyUtil.newSingleScheduledThreadExecutor("Terminal-${sessionIndex++}")
  private val textBuffer: TerminalTextBuffer
  private val promptHeaderFuture: CompletableFuture<String> = CompletableFuture()

  init {
    val terminalRunner = TerminalToolWindowManager.getInstance(project).terminalRunner as AbstractTerminalRunner<Process>
    val process = terminalRunner.createProcess(TerminalProcessOptions(null, size.width, size.height))
    val ttyConnector = terminalRunner.createTtyConnector(process)

    val styleState = StyleState()
    styleState.setDefaultStyle(settings.defaultStyle)
    textBuffer = TerminalTextBuffer(size.width, size.height, styleState)
    model = TerminalModel(textBuffer, styleState)
    val controller = TerminalController(model, settings)

    val typeAheadTerminalModel = JediTermTypeAheadModel(controller, textBuffer, settings)
    val typeAheadManager = TerminalTypeAheadManager(typeAheadTerminalModel)
    val typeAheadDebouncer = JediTermDebouncerImpl(typeAheadManager::debounce, TerminalTypeAheadManager.MAX_TERMINAL_DELAY)
    typeAheadManager.setClearPredictionsDebouncer(typeAheadDebouncer)

    terminalStarter = TerminalStarter(controller, ttyConnector, TtyBasedArrayDataStream(ttyConnector), typeAheadManager)
  }

  fun start() {
    textBuffer.addModelListener(object : TerminalModelListener {
      override fun modelChanged() {
        val text = textBuffer.screenLines.dropLastWhile { it == ' ' || it == '\n' }
        if (text.length != 1 && text.endsWith("%")) {
          promptHeaderFuture.complete("$text ")
          textBuffer.removeModelListener(this)
        }
      }
    })
    terminalExecutor.submit { terminalStarter.start() }
  }

  fun executeCommand(command: String) {
    promptHeaderFuture.thenAccept {
      val enterCode = terminalStarter.getCode(KeyEvent.VK_ENTER, 0)
      terminalStarter.sendString(command, false)
      terminalStarter.sendBytes(enterCode, false)
    }
  }

  override fun dispose() {
    terminalExecutor.shutdown()
    terminalStarter.close()
  }
}