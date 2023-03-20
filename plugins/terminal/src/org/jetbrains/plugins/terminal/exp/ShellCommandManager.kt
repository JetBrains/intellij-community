// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.jediterm.terminal.Terminal
import com.jediterm.terminal.TerminalCustomCommandListener
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class ShellCommandManager(terminal: Terminal) {
  private val listeners: CopyOnWriteArrayList<ShellCommandListener> = CopyOnWriteArrayList()

  @Volatile
  private var commandRun: CommandRun? = null

  init {
    terminal.addCustomCommandListener(TerminalCustomCommandListener {
      when (it.getOrNull(0)) {
        "command_started" -> processCommandStartedEvent(it)
        "command_finished" -> processCommandFinishedEvent(it)
      }
    })
  }

  private fun processCommandStartedEvent(event: List<String>) {
    val command = event.getOrNull(1)
    val currentDirectory = event.getOrNull(2)
    if (command != null && command.startsWith("command=") &&
        currentDirectory != null && currentDirectory.startsWith("current_directory=")) {
      val commandRun = CommandRun(System.nanoTime(), currentDirectory.removePrefix("current_directory="),
                                  command.removePrefix("command="))
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
        val newDirectory = currentDirectoryField.removePrefix("current_directory=")
        if (commandRun.workingDirectory != newDirectory) {
          fireDirectoryChanged(newDirectory)
        }
        fireCommandFinished(commandRun, exitCode)
      }
    }
  }

  private fun fireCommandStarted(commandRun: CommandRun) {
    for (listener in listeners) {
      listener.commandStarted(commandRun.command)
    }
    if (LOG.isDebugEnabled) {
      LOG.debug("Started $commandRun")
    }
  }

  private fun fireCommandFinished(commandRun: CommandRun, exitCode: Int) {
    for (listener in listeners) {
      listener.commandFinished(commandRun.command, exitCode, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - commandRun.commandStartedNano))
    }
    if (LOG.isDebugEnabled) {
      LOG.debug("Finished $commandRun, exit code: $exitCode")
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

  fun addListener(listener: ShellCommandListener, parentDisposable: Disposable) {
    listeners.add(listener)
    Disposer.register(parentDisposable) {
      listeners.remove(listener)
    }
  }

  companion object {
    val LOG = logger<ShellCommandManager>()
  }
}

interface ShellCommandListener {
  fun commandStarted(command: String) {}

  fun commandFinished(command: String, exitCode: Int, duration: Long) {}

  fun directoryChanged(newDirectory: String) {}
}

private class CommandRun(val commandStartedNano: Long, val workingDirectory: String, val command: String) {
  override fun toString(): String {
    return "command: $command, workingDirectory: $workingDirectory"
  }
}
