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
      try {
        when (it.getOrNull(0)) {
          "initialized" -> processInitialized(it)
          "prompt_shown" -> firePromptShown()
          "command_started" -> processCommandStartedEvent(it)
          "command_finished" -> processCommandFinishedEvent(it)
          "command_history" -> processCommandHistoryEvent(it)
          "generator_finished" -> processGeneratorFinishedEvent(it)
          "clear_invoked" -> fireClearInvoked()
        }
      }
      catch (t: Throwable) {
        LOG.warn("Error while processing custom command: $it", t)
      }
    })
  }

  private fun processInitialized(event: List<String>) {
    val directory = event.getOrNull(1)
    val dir = if (directory != null && directory.startsWith("current_directory=")) {
      decodeHex(directory.removePrefix("current_directory="))
    }
    else null
    fireInitialized(dir)
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
      }
      fireCommandFinished(commandRun, exitCode)
      this.commandRun = null
    }
  }

  private fun processCommandHistoryEvent(event: List<String>) {
    val history = event.getOrNull(1)
    if (history != null && history.startsWith("history_string=")) {
      fireCommandHistoryReceived(decodeHex(history.removePrefix("history_string=")))
    }
  }

  private fun processGeneratorFinishedEvent(event: List<String>) {
    val requestId = event.getOrNull(1)
    val result = event.getOrNull(2)
    if (requestId != null && requestId.startsWith("request_id=") &&
        result != null && result.startsWith("result=")) {
      val requestIdInt = requestId.removePrefix("request_id=").toIntOrNull() ?: return
      fireGeneratorFinished(requestIdInt, decodeHex(result.removePrefix("result=")))
    }
  }

  private fun fireInitialized(currentDirectory: String?) {
    if (LOG.isDebugEnabled) {
      LOG.debug("Shell event: initialized")
    }
    for (listener in listeners) {
      listener.initialized(currentDirectory)
    }
  }

  private fun firePromptShown() {
    if (LOG.isDebugEnabled) {
      LOG.debug("Shell event: prompt_shown")
    }
    for (listener in listeners) {
      listener.promptShown()
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

  private fun fireCommandFinished(commandRun: CommandRun?, exitCode: Int) {
    if (LOG.isDebugEnabled) {
      LOG.debug("Shell event: command_finished - $commandRun, exit code: $exitCode")
    }
    for (listener in listeners) {
      val duration = commandRun?.commandStartedNano?.let { TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - it) }
      listener.commandFinished(commandRun?.command, exitCode, duration)
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

  private fun fireGeneratorFinished(requestId: Int, result: String) {
    if (LOG.isDebugEnabled) {
      LOG.debug("Shell event: generator_finished with requestId $requestId and result of ${result.length} size")
    }
    for (listener in listeners) {
      listener.generatorFinished(requestId, result)
    }
  }

  private fun fireClearInvoked() {
    if (LOG.isDebugEnabled) {
      LOG.debug("Shell event: clear_invoked")
    }
    for (listener in listeners) {
      listener.clearInvoked()
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
  /**
   * Fired before the first prompt is printed
   * todo: make current directory notnull when all shell integrations adapt it
   */
  fun initialized(currentDirectory: String?) {}

  /**
   * Fired each time when prompt is printed.
   * The prompt itself is empty, so this event can be counted both as before or after prompt is printed.
   * Fired on session start after [initialized] event, after [commandFinished] event, and after completion with multiple items is finished.
   */
  fun promptShown() {}

  fun commandStarted(command: String) {}

  /** Fired after command is executed and before prompt is printed */
  fun commandFinished(command: String?, exitCode: Int, duration: Long?) {}

  fun directoryChanged(newDirectory: String) {}

  fun commandHistoryReceived(history: String) {}

  fun generatorFinished(requestId: Int, result: String) {}

  fun clearInvoked() {}
}

private class CommandRun(val commandStartedNano: Long, val workingDirectory: String, val command: String) {
  override fun toString(): String {
    return "command: $command, workingDirectory: $workingDirectory"
  }
}
