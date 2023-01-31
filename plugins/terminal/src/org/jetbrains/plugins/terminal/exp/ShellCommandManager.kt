// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.jediterm.terminal.model.TerminalLine
import java.awt.event.KeyEvent
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

class ShellCommandManager(private val model: TerminalModel) {
  private val listeners: CopyOnWriteArrayList<ShellCommandListener> = CopyOnWriteArrayList()
  private val prompt: Prompt = Prompt(model)

  @Volatile
  private var commandRun: CommandRun? = null

  init {
    model.addContentListener(object : TerminalModel.ContentListener {
      override fun onContentChanged() {
        val command = commandRun
        if (command != null) {
          val finished: Boolean = prompt.processTerminalBuffer {
            val terminalLine = prompt.getLineAtCursor()
            val text = prompt.getLineTextUpToCursor(terminalLine)
            text.isNotEmpty() && text == command.prompt
          }
          if (finished) {
            commandRun = null
            fireCommandFinished(command)
          }
        }
      }
    })
  }

  fun onKeyPressed(e: KeyEvent) {
    if (e.id == KeyEvent.KEY_PRESSED) {
      if (e.keyCode == KeyEvent.VK_ENTER) {
        val commandRun: CommandRun = prompt.processTerminalBuffer {
          val command = prompt.getTypedShellCommand()
          val prompt = model.promptText
          return@processTerminalBuffer CommandRun(System.nanoTime(), prompt, command)
        }
        if (commandRun.command.isNotEmpty() && commandRun.prompt.isNotEmpty()) {
          prompt.reset()
          this.commandRun = commandRun
          fireCommandStarted(commandRun)
        }
      }
      else {
        prompt.onKeyPressed()
      }
    }
  }

  private fun fireCommandStarted(commandRun: CommandRun) {
    for (listener in listeners) {
      listener.commandStarted(commandRun.command)
    }
  }

  private fun fireCommandFinished(commandRun: CommandRun) {
    for (listener in listeners) {
      listener.commandFinished(commandRun.command, 0, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - commandRun.commandStartedNano))
    }
  }

  fun addListener(listener: ShellCommandListener, parentDisposable: Disposable) {
    listeners.add(listener)
    Disposer.register(parentDisposable) {
      listeners.remove(listener)
    }
  }
}

interface ShellCommandListener {
  fun commandStarted(command: String)
  fun commandFinished(command: String, exitCode: Int, duration: Long)
}

private class Prompt(val model: TerminalModel) {
  private val typings = AtomicInteger(0)
  private var terminalLine: TerminalLine? = null
  private var maxCursorX = -1

  fun reset() {
    typings.set(0)
    terminalLine = null
    maxCursorX = -1
  }

  fun onKeyPressed() {
    val terminalLine: TerminalLine = processTerminalBuffer {
      getLineAtCursor()
    }
    if (terminalLine != this.terminalLine) {
      typings.set(0)
      this.terminalLine = terminalLine
      maxCursorX = -1
    }
    val prompt = getLineTextUpToCursor(terminalLine)
    if (typings.get() == 0) {
      model.promptText = prompt
      this.terminalLine = terminalLine
      if (LOG.isDebugEnabled) {
        LOG.debug("Guessed shell prompt: ${model.promptText}")
      }
    }
    else {
      if (prompt.startsWith(model.promptText)) {
        if (LOG.isDebugEnabled) {
          LOG.debug("Guessed prompt confirmed by typing# " + (typings.get() + 1))
        }
      }
      else {
        if (LOG.isDebugEnabled) {
          LOG.debug("Prompt rejected by typing#" + (typings.get() + 1) + ", new prompt: " + prompt)
        }
        model.promptText = prompt
        typings.set(1)
      }
    }
    typings.incrementAndGet()
  }

  fun getTypedShellCommand(): String {
    if (typings.get() == 0) {
      return ""
    }
    val terminalLine: TerminalLine = processTerminalBuffer {
      getLineAtCursor()
    }
    if (terminalLine != this.terminalLine) {
      return ""
    }
    val lineTextUpToCursor = getLineTextUpToCursor(terminalLine)
    val promptText = model.promptText
    if (lineTextUpToCursor.startsWith(promptText)) {
      return lineTextUpToCursor.substring(promptText.length)
    }
    return ""
  }

  fun getLineAtCursor(): TerminalLine {
    return model.getLine(getLineNumberAtCursor())
  }

  fun getLineTextUpToCursor(line: TerminalLine?): String {
    line ?: return ""
    return processTerminalBuffer {
      val cursorX: Int = model.cursorX - 1
      val lineStr = line.text
      var maxCursorX = max(maxCursorX, cursorX)
      while (maxCursorX < lineStr.length && !Character.isWhitespace(lineStr[maxCursorX])) {
        maxCursorX++
      }
      this.maxCursorX = maxCursorX
      lineStr.substring(0, min(maxCursorX, lineStr.length))
    }
  }

  private fun getLineNumberAtCursor(): Int {
    return max(0, min(model.cursorY - 1, model.height - 1))
  }

  fun <T> processTerminalBuffer(processor: () -> T): T {
    model.lock()
    return try {
      processor()
    }
    finally {
      model.unlock()
    }
  }

  companion object {
    private val LOG = logger<Prompt>()
  }
}

private class CommandRun(val commandStartedNano: Long, val prompt: String, val command: String)
