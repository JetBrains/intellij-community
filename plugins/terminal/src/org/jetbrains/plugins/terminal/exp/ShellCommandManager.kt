// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.jediterm.terminal.TerminalCustomCommandListener
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.terminal.TerminalUtil
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class ShellCommandManager(private val session: BlockTerminalSession) {
  private val listeners: CopyOnWriteArrayList<ShellCommandListener> = CopyOnWriteArrayList()

  @Volatile
  private var startedCommand: StartedCommand? = null
  internal val commandExecutionManager: ShellCommandExecutionManager = ShellCommandExecutionManager(session, this)

  init {
    session.controller.addCustomCommandListener(TerminalCustomCommandListener {
      try {
        when (it.getOrNull(0)) {
          "initialized" -> processInitialized(it)
          "prompt_shown" -> firePromptShown()
          "command_started" -> processCommandStartedEvent(it)
          "command_finished" -> processCommandFinishedEvent(it)
          "command_history" -> processCommandHistoryEvent(it)
          "generator_finished" -> processGeneratorFinishedEvent(it)
          "clear_invoked" -> fireClearInvoked()
          else -> LOG.warn("Unknown custom command: $it")
        }
      }
      catch (t: Throwable) {
        LOG.warn("Error while processing custom command: $it", t)
      }
    })
  }

  private fun processInitialized(event: List<String>) {
    val currentDirectory = Param.CURRENT_DIRECTORY.getDecodedValueOrNull(event.getOrNull(1))
    if (session.commandBlockIntegration.commandEndMarker != null) {
      debug { "Received initialized event, waiting for command end marker" }
      ShellCommandEndMarkerListener(session) {
        fireInitialized(currentDirectory)
      }
    }
    else {
      fireInitialized(currentDirectory)
    }
  }

  private fun processCommandStartedEvent(event: List<String>) {
    val command = Param.COMMAND.getDecodedValue(event.getOrNull(1))
    val currentDirectory = Param.CURRENT_DIRECTORY.getDecodedValue(event.getOrNull(2))
    val startedCommand = StartedCommand(System.nanoTime(), currentDirectory, command)
    this.startedCommand = startedCommand
    fireCommandStarted(startedCommand)
  }

  private fun processCommandFinishedEvent(event: List<String>) {
    val exitCode = Param.EXIT_CODE.getIntValue(event.getOrNull(1))
    val newCurrentDirectory = Param.CURRENT_DIRECTORY.getDecodedValue(event.getOrNull(2))
    val startedCommand = this.startedCommand
    if (startedCommand != null) {
      if (startedCommand.currentDirectory != newCurrentDirectory) {
        fireDirectoryChanged(newCurrentDirectory)
      }
    }
    if (session.commandBlockIntegration.commandEndMarker != null) {
      debug { "Received command_finished event, waiting for command end marker" }
      ShellCommandEndMarkerListener(session) {
        fireCommandFinished(startedCommand, exitCode)
        this.startedCommand = null
      }
    }
    else {
      fireCommandFinished(startedCommand, exitCode)
      this.startedCommand = null
    }
  }

  private fun processCommandHistoryEvent(event: List<String>) {
    val history = Param.HISTORY_STRING.getDecodedValue(event.getOrNull(1))
    fireCommandHistoryReceived(history)
  }

  private fun processGeneratorFinishedEvent(event: List<String>) {
    val requestId = Param.REQUEST_ID.getIntValue(event.getOrNull(1))
    val result = Param.RESULT.getDecodedValue(event.getOrNull(2))
    fireGeneratorFinished(requestId, result)
  }

  private fun fireInitialized(currentDirectory: String?) {
    debug { "Shell event: initialized" }
    for (listener in listeners) {
      listener.initialized(currentDirectory)
    }
  }

  private fun firePromptShown() {
    debug { "Shell event: prompt_shown" }
    for (listener in listeners) {
      listener.promptShown()
    }
  }

  private fun fireCommandStarted(startedCommand: StartedCommand) {
    debug { "Shell event: command_started - $startedCommand" }
    for (listener in listeners) {
      listener.commandStarted(startedCommand.command)
    }
  }

  private fun fireCommandFinished(startedCommand: StartedCommand?, exitCode: Int) {
    if (startedCommand == null) {
      LOG.info("Shell event: received command_finished without preceding command_started - skipping")
    }
    else {
      debug { "Shell event: command_finished - $startedCommand, exit code: $exitCode" }
      for (listener in listeners) {
        val duration = startedCommand.commandStartedNano.let { TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - it) }
        listener.commandFinished(startedCommand.command, exitCode, duration)
      }
    }
  }

  private fun fireDirectoryChanged(newDirectory: String) {
    for (listener in listeners) {
      listener.directoryChanged(newDirectory)
    }
    debug { "Current directory changed from '${startedCommand?.currentDirectory}' to '$newDirectory'" }
  }

  private fun fireCommandHistoryReceived(history: String) {
    debug { "Shell event: command_history of ${history.length} size" }
    for (listener in listeners) {
      listener.commandHistoryReceived(history)
    }
  }

  private fun fireGeneratorFinished(requestId: Int, result: String) {
    debug { "Shell event: generator_finished with requestId $requestId and result of ${result.length} size" }
    for (listener in listeners) {
      listener.generatorFinished(requestId, result)
    }
  }

  private fun fireClearInvoked() {
    debug { "Shell event: clear_invoked" }
    for (listener in listeners) {
      listener.clearInvoked()
    }
  }

  fun addListener(listener: ShellCommandListener, parentDisposable: Disposable) {
    TerminalUtil.addItem(listeners, listener, parentDisposable)
  }

  fun sendCommandToExecute(shellCommand: String) = commandExecutionManager.sendCommandToExecute(shellCommand)

  fun runGeneratorAsync(generatorName: String, generatorParameters: List<String>): CompletableDeferred<String> {
    return commandExecutionManager.runGeneratorAsync(generatorName, generatorParameters)
  }

  companion object {
    internal val LOG = logger<ShellCommandManager>()

    internal inline fun debug(e: Exception? = null, lazyMessage: () -> @NonNls String) {
      LOG.debug(e, lazyMessage)
    }

    @Throws(IllegalArgumentException::class)
    private fun decodeHex(hexStr: String): String {
      val bytes = HexFormat.of().parseHex(hexStr)
      return String(bytes, Charsets.UTF_8)
    }
  }

  private enum class Param {

    EXIT_CODE,
    CURRENT_DIRECTORY,
    COMMAND,
    HISTORY_STRING,
    REQUEST_ID,
    RESULT;

    private val paramNameWithSeparator: String = "${paramName()}="

    private fun paramName(): String = name.lowercase(Locale.ENGLISH)

    fun getIntValue(nameAndValue: String?): Int {
      return getValueOrNull(nameAndValue)?.toIntOrNull() ?: fail()
    }

    fun getDecodedValue(nameAndValue: String?): String = getDecodedValueOrNull(nameAndValue) ?: fail()

    fun getDecodedValueOrNull(nameAndValue: String?): String? {
      val encodedValue = getValueOrNull(nameAndValue) ?: return null
      return decodeHex(encodedValue)
    }

    private fun getValueOrNull(nameAndValue: String?): String? {
      return nameAndValue?.takeIf { it.startsWith(paramNameWithSeparator) }?.substring(paramNameWithSeparator.length)
    }

    private fun <T> fail(): T = throw IllegalStateException("Cannot parse ${paramName()}")
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

private class StartedCommand(val commandStartedNano: Long, val currentDirectory: String, val command: String) {
  override fun toString(): String {
    return "command: $command, currentDirectory: $currentDirectory"
  }
}
