// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.jediterm.terminal.Terminal
import com.jediterm.terminal.TerminalCustomCommandListener
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class ShellCommandManager(terminal: Terminal) {
  private val listeners: CopyOnWriteArrayList<ShellCommandListener> = CopyOnWriteArrayList()

  @Volatile
  private var commandRun: CommandRun? = null

  init {
    terminal.addCustomCommandListener(TerminalCustomCommandListener {
      when (it.getOrNull(0)) {
        "initialized" -> fireInitialized()
        "command_started" -> processCommandStartedEvent(it)
        "command_finished" -> processCommandFinishedEvent(it)
        "command_history" -> processCommandHistoryEvent(it)
      }
    })
  }

  private fun processCommandStartedEvent(event: List<String>) {
    val command = event.getOrNull(1)
    val currentDirectory = event.getOrNull(2)
    if (command != null && command.startsWith("command=") &&
        currentDirectory != null && currentDirectory.startsWith("current_directory=")) {
      val commandRun = CommandRun(System.nanoTime(),
                                  decodeHex(currentDirectory.removePrefix("current_directory=")),
                                  decodeHex(command.removePrefix("command=")))
      this.commandRun = commandRun
      fireCommandStarted(commandRun)
    }
  }

  private fun processCommandFinishedEvent(event: List<String>) {
    val exitCodeStr = event.getOrNull(1)
    val currentDirectoryField = event.getOrNull(2)
    if (exitCodeStr != null && exitCodeStr.startsWith("exit_code=") &&
        currentDirectoryField != null && currentDirectoryField.startsWith("current_directory=")) {
      val exitCode = try {
        exitCodeStr.removePrefix("exit_code=").toInt()
      }
      catch (_: NumberFormatException) {
        return
      }
      val commandRun = this.commandRun
      if (commandRun != null) {
        val newDirectory = decodeHex(currentDirectoryField.removePrefix("current_directory="))
        if (commandRun.workingDirectory != newDirectory) {
          fireDirectoryChanged(newDirectory)
        }
        fireCommandFinished(commandRun, exitCode)
      }
    }
  }

  private fun processCommandHistoryEvent(event: List<String>) {
    val history = event.getOrNull(1)
    if (history != null && history.startsWith("history_string=")) {
      fireCommandHistoryReceived(decodeHex(history.removePrefix("history_string=")))
    }
  }

  private fun fireInitialized() {
    if (LOG.isDebugEnabled) {
      LOG.debug("Shell event: initialized")
    }
    for (listener in listeners) {
      listener.initialized()
    }
  }

  private fun fireCommandStarted(commandRun: CommandRun) {
    if (LOG.isDebugEnabled) {
      LOG.debug("Shell event: command_started - $commandRun")
    }
    for (listener in listeners) {
      listener.commandStarted(commandRun.command)
    }
  }

  private fun fireCommandFinished(commandRun: CommandRun, exitCode: Int) {
    if (LOG.isDebugEnabled) {
      LOG.debug("Shell event: command_finished - $commandRun, exit code: $exitCode")
    }
    for (listener in listeners) {
      listener.commandFinished(commandRun.command, exitCode, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - commandRun.commandStartedNano))
    }
  }

  private fun fireDirectoryChanged(newDirectory: String) {
    for (listener in listeners) {
      listener.directoryChanged(newDirectory)
    }
    if (LOG.isDebugEnabled) {
      LOG.debug("Current directory changed from '${commandRun?.workingDirectory}' to '$newDirectory'")
    }
  }

  private fun fireCommandHistoryReceived(history: String) {
    if (LOG.isDebugEnabled) {
      LOG.debug("Shell event: command_history of ${history.length} size")
    }
    for (listener in listeners) {
      listener.commandHistoryReceived(history)
    }
  }

  fun addListener(listener: ShellCommandListener, parentDisposable: Disposable? = null) {
    listeners.add(listener)
    if (parentDisposable != null) {
      Disposer.register(parentDisposable) {
        listeners.remove(listener)
      }
    }
  }

  private fun decodeHex(hexStr: String): String {
    val bytes = HexFormat.of().parseHex(hexStr)
    return String(bytes, StandardCharsets.UTF_8)
  }

  companion object {
    val LOG = logger<ShellCommandManager>()
  }
}

interface ShellCommandListener {
  /** Fired before the first prompt is printed */
  fun initialized() {}

  fun commandStarted(command: String) {}

  /** Fired after command is executed and before prompt is printed */
  fun commandFinished(command: String, exitCode: Int, duration: Long) {}

  fun directoryChanged(newDirectory: String) {}

  fun commandHistoryReceived(history: String) {}
}

private class CommandRun(val commandStartedNano: Long, val workingDirectory: String, val command: String) {
  override fun toString(): String {
    return "command: $command, workingDirectory: $workingDirectory"
  }
}
