// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.util.Alarm
import com.intellij.util.ConcurrencyUtil
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.*
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.terminal.model.JediTerminal
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalModelListener
import com.jediterm.terminal.model.TerminalTextBuffer
import org.jetbrains.plugins.terminal.AbstractTerminalRunner
import org.jetbrains.plugins.terminal.TerminalProcessOptions
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

private var sessionIndex = 1

class HeadlessTerminalSession(project: Project, termSize: TermSize): Disposable {
  private val terminalExecutor: ExecutorService = ConcurrencyUtil.newSingleScheduledThreadExecutor("FakeTerminal-${sessionIndex++}")
  private val textBuffer: TerminalTextBuffer
  private val terminalStarter: TerminalStarter
  private val promptHeaderFuture: CompletableFuture<String> = CompletableFuture()

  init {
    val terminalRunner = TerminalToolWindowManager.getInstance(project).terminalRunner as AbstractTerminalRunner<Process>
    val process = terminalRunner.createProcess(TerminalProcessOptions(null, termSize))
    val ttyConnector = terminalRunner.createTtyConnector(process)

    val styleState = StyleState()
    textBuffer = TerminalTextBuffer(termSize.columns, termSize.rows, styleState)
    val terminal = JediTerminal(FakeTerminalDisplay(termSize), textBuffer, styleState)
    terminalStarter = TerminalStarter(terminal, ttyConnector, TtyBasedArrayDataStream(ttyConnector), null)
  }

  fun start() {
    textBuffer.addModelListener(object : TerminalModelListener {
      override fun modelChanged() {
        val text = textBuffer.text
        if (text.length != 1 && text.endsWith("%")) {
          promptHeaderFuture.complete("$text ")
          textBuffer.removeModelListener(this)
        }
      }
    })
    terminalExecutor.submit { terminalStarter.start() }
  }

  fun close() {
    terminalExecutor.shutdown()
    terminalStarter.close()
  }

  fun invokeCompletion(command: String): CompletableFuture<List<String>> {
    val completionItemsFuture = CompletableFuture<List<String>>()
    promptHeaderFuture.thenAccept { promptHeader ->
      terminalStarter.sendString(command + "\t", false)
      val runnable: () -> Unit = {
        val text = textBuffer.text
        val commandAndOutput = text.removePrefix(promptHeader)
        if (command.count { it == '\n' } == commandAndOutput.count {  it == '\n' }) {
          val addedPart = commandAndOutput.removePrefix(command)
          completionItemsFuture.complete(if (addedPart.isEmpty()) emptyList() else listOf(addedPart))
        }
        else {
          val output = commandAndOutput.removePrefix(command).trim { it == ' ' || it == '\n' }
          val items = output.split(Regex("""(?<!\\)[ \n]+"""))
          completionItemsFuture.complete(items)
        }
      }
      Alarm(Alarm.ThreadToUse.POOLED_THREAD, this).addRequest(runnable, 500L)
    }
    return completionItemsFuture
  }

  override fun dispose() {
    close()
  }

  val TerminalTextBuffer.text : String
    get() = screenLines.dropLastWhile { it == ' ' || it == '\n' }

  private class FakeTerminalDisplay(private val termSize: TermSize) : TerminalDisplay {
    override fun getRowCount(): Int = termSize.rows

    override fun getColumnCount(): Int = termSize.columns

    override fun setCursor(x: Int, y: Int) {
    }

    override fun setCursorShape(shape: CursorShape?) {
    }

    override fun beep() {
    }

    override fun requestResize(newWinSize: TermSize,
                               origin: RequestOrigin?,
                               cursorX: Int,
                               cursorY: Int,
                               resizeHandler: JediTerminal.ResizeHandler?) {
    }

    override fun scrollArea(scrollRegionTop: Int, scrollRegionSize: Int, dy: Int) {
    }

    override fun setCursorVisible(shouldDrawCursor: Boolean) {
    }

    override fun setScrollingEnabled(enabled: Boolean) {
    }

    override fun setBlinkingCursor(enabled: Boolean) {
    }

    override fun getWindowTitle(): String {
      return ""
    }

    override fun setWindowTitle(name: String?) {
    }

    override fun terminalMouseModeSet(mode: MouseMode?) {
    }

    override fun setBracketedPasteMode(enabled: Boolean) {
    }

    override fun ambiguousCharsAreDoubleWidth(): Boolean {
      return false
    }
  }
}