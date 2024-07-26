// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.session

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.jediterm.terminal.Terminal
import com.jediterm.terminal.TerminalCustomCommandListener
import com.jediterm.terminal.model.TerminalTextBuffer
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.terminal.TerminalUtil
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptState
import org.jetbrains.plugins.terminal.util.ShellIntegration
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal class ShellCommandManager(
  private val session: BlockTerminalSession,
  private val commandEndMarker: String?,
  private val terminal: Terminal,
  private val shellIntegration: ShellIntegration,
  private val parentDisposable: Disposable,
  private val terminalTextBuffer: TerminalTextBuffer
) {

  constructor(session: BlockTerminalSession) : this(
    session,
    session.commandBlockIntegration.commandEndMarker,
    session.controller,
    session.shellIntegration,
    session as Disposable,
    session.model.textBuffer
  )

  private val listeners: CopyOnWriteArrayList<ShellCommandListener> = CopyOnWriteArrayList()

  @Volatile
  private var startedCommand: StartedCommand? = null

  init {
    terminal.addCustomCommandListener(TerminalCustomCommandListener {
      try {
        when (it.getOrNull(0)) {
          "initialized" -> processInitialized(it)
          "prompt_shown" -> firePromptShown()
          "command_started" -> processCommandStartedEvent(it)
          "command_finished" -> processCommandFinishedEvent(it)
          "prompt_state_updated" -> processPromptStateUpdatedEvent(it)
          "command_history" -> processCommandHistoryEvent(it)
          "shell_editor_buffer_reported" -> fireShellEditorBufferReported(it)
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
    val shellInfo = Param.SHELL_INFO.getDecodedValueOrNull(event.getOrNull(1)) ?: "{}"
    if (commandEndMarker != null) {
      debug { "Received initialized event, waiting for command end marker" }
      ShellCommandEndMarkerListener(terminalTextBuffer, commandEndMarker, parentDisposable) {
        fireInitialized(shellInfo)
      }
    }
    else {
      fireInitialized(shellInfo)
    }
  }

  private fun processCommandStartedEvent(event: List<String>) {
    val command = Param.COMMAND.getDecodedValue(event.getOrNull(1))
    val currentDirectory = Param.CURRENT_DIRECTORY.getDecodedValue(event.getOrNull(2))
    val startedCommand = StartedCommand(command, currentDirectory, TimeSource.Monotonic.markNow())
    this.startedCommand = startedCommand
    fireCommandStarted(startedCommand)
  }

  private fun processCommandFinishedEvent(event: List<String>) {
    val exitCode = Param.EXIT_CODE.getIntValue(event.getOrNull(1))
    val startedCommand = this.startedCommand
    if (commandEndMarker != null) {
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

  private fun processPromptStateUpdatedEvent(event: List<String>) {
    val state = TerminalPromptState(
      currentDirectory = Param.CURRENT_DIRECTORY.getDecodedValue(event.getOrNull(1)),
      userName = Param.USER_NAME.getDecodedNotEmptyValueOrNull(event.getOrNull(2)),
      userHome = Param.USER_HOME.getDecodedNotEmptyValueOrNull(event.getOrNull(3)),
      gitBranch = Param.GIT_BRANCH.getDecodedNotEmptyValueOrNull(event.getOrNull(4)),
      virtualEnv = Param.VIRTUAL_ENV.getDecodedNotEmptyValueOrNull(event.getOrNull(5)),
      condaEnv = Param.CONDA_ENV.getDecodedNotEmptyValueOrNull(event.getOrNull(6)),
      originalPrompt = Param.ORIGINAL_PROMPT.getDecodedNotEmptyValueOrNull(event.getOrNull(7)),
      originalRightPrompt = Param.ORIGINAL_RIGHT_PROMPT.getDecodedNotEmptyValueOrNull(event.getOrNull(8)),
      shellName = shellIntegration.shellType.toString().lowercase()
    )
    firePromptStateUpdated(state)
  }

  private fun processCommandHistoryEvent(event: List<String>) {
    val history = Param.HISTORY_STRING.getDecodedValue(event.getOrNull(1))
    fireCommandHistoryReceived(history)
  }

  private fun processGeneratorFinishedEvent(event: List<String>) {
    val requestId = Param.REQUEST_ID.getIntValue(event.getOrNull(1))
    val result = Param.RESULT.getDecodedValue(event.getOrNull(2))
    val exitCode = Param.EXIT_CODE.getIntValue(event.getOrNull(3))
    if (commandEndMarker != null) {
      debug { "Received generator_finished event, waiting for command end marker" }
      ShellCommandEndMarkerListener(terminalTextBuffer, commandEndMarker, parentDisposable) {
        fireGeneratorFinished(requestId, result, exitCode)
      }
    }
    else {
      fireGeneratorFinished(requestId, result, exitCode)
    }
  }

  /**
   * Clear backing terminal text buffer on command/generator termination.
   * This helps to ensure that IDE doesn't see previous command/generator output when a new command starts.
   * A possible problem with this approach is that it might not work on ConPTY as it synchronizes buffers sometimes.
   * In this case (the worst case), the problem is that the fix won't work as intended, but nothing else bad will happen.
   *
   * It might be solved differently - start scraping command output on command_started event, but it won't work
   * reliably on ConPTY where command_started event is delivered asynchronously with command output.
   *
   * There is a plan to get rid of command_started event and replace this code with a new clear_all generator running
   * on command termination.
   */
  private fun clearTerminal() {
    session.terminalStarterFuture.getNow(null)?.let {
      debug { "force clearing terminal" }
      session.model.clearAllAndMoveCursorToTopLeftCorner(it.terminal)
    }
  }

  private fun fireInitialized(rawShellInfo: String) {
    debug { "Shell event: initialized. Shell info: $rawShellInfo" }
    for (listener in listeners) {
      listener.shellInfoReceived(rawShellInfo)
      listener.initialized()
    }
    clearTerminal()
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
      val event = CommandFinishedEvent(startedCommand.command, exitCode, startedCommand.commandStarted.elapsedNow())
      debug { "Shell event: command_finished - $event" }
      for (listener in listeners) {
        listener.commandFinished(event)
      }
    }
    clearTerminal()
  }

  private fun firePromptStateUpdated(state: TerminalPromptState) {
    for (listener in listeners) {
      listener.promptStateUpdated(state)
    }
    debug { "Prompt state updated: $state" }
  }

  private fun fireCommandHistoryReceived(history: String) {
    debug { "Shell event: command_history of ${history.length} size" }
    for (listener in listeners) {
      listener.commandHistoryReceived(history)
    }
  }

  private fun fireShellEditorBufferReported(event: List<String>) {
    val buffer = Param.SHELL_EDITOR_BUFFER.getDecodedValue(event.getOrNull(1))
    debug { "Shell event: shell_editor_buffer_reported of ${buffer.length} size" }
    for (listener in listeners) {
      listener.commandBufferReceived(buffer)
    }
  }

  private fun fireGeneratorFinished(requestId: Int, result: String, exitCode: Int) {
    debug { "Shell event: generator_finished with requestId $requestId and result of ${result.length} size" }
    for (listener in listeners) {
      listener.generatorFinished(GeneratorFinishedEvent(requestId, result, exitCode))
    }
    clearTerminal()
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
    COMMAND,
    HISTORY_STRING,
    REQUEST_ID,
    RESULT,
    CURRENT_DIRECTORY,
    USER_NAME,
    USER_HOME,
    GIT_BRANCH,
    VIRTUAL_ENV,
    CONDA_ENV,
    SHELL_EDITOR_BUFFER,
    ORIGINAL_PROMPT,
    ORIGINAL_RIGHT_PROMPT,

    /** Json with the following content [org.jetbrains.plugins.terminal.fus.TerminalShellInfoStatistics.ShellInfo] */
    SHELL_INFO;

    private val paramNameWithSeparator: String = "${paramName()}="

    private fun paramName(): String = name.lowercase(Locale.ENGLISH)

    fun getIntValue(nameAndValue: String?): Int {
      return getValueOrNull(nameAndValue)?.toIntOrNull() ?: fail()
    }

    fun getDecodedValue(nameAndValue: String?): String = getDecodedValueOrNull(nameAndValue) ?: fail()

    fun getDecodedNotEmptyValueOrNull(nameAndValue: String?): String? {
      return getDecodedValueOrNull(nameAndValue)?.takeIf { it.isNotEmpty() }
    }

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

internal interface ShellCommandListener {
  fun initialized() {}

  /**
   * Fired each time when prompt is printed.
   * The prompt itself is empty, so this event can be counted both as before or after prompt is printed.
   * Fired on session start after [initialized] event, after [commandFinished] event, and after completion with multiple items is finished.
   */
  fun promptShown() {}

  fun commandStarted(command: String) {}

  /** Fired after command is executed and before prompt is printed */
  fun commandFinished(event: CommandFinishedEvent) {}

  fun promptStateUpdated(newState: TerminalPromptState) {}

  fun commandHistoryReceived(history: String) {}

  fun commandBufferReceived(buffer: String) {}

  fun shellInfoReceived(rawShellInfo: String) {}

  fun generatorFinished(event: GeneratorFinishedEvent) {}

  fun clearInvoked() {}
}

internal data class CommandFinishedEvent(val command: String, val exitCode: Int, val duration: Duration)

internal data class GeneratorFinishedEvent(val requestId: Int, val output: String, val exitCode: Int)

private data class StartedCommand(val command: String, val currentDirectory: String, val commandStarted: TimeMark)
