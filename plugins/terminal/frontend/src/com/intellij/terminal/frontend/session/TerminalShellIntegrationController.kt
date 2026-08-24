// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.session

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.EventDispatcher
import kotlinx.coroutines.CancellationException
import org.jetbrains.plugins.terminal.block.reworked.TerminalShellIntegrationEventsListener
import org.jetbrains.plugins.terminal.session.impl.TerminalAliasesReceivedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCommandFinishedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCommandHistoryPathReceivedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCommandStartedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCompletionFinishedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalPromptFinishedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalPromptStartedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalShellIntegrationEvent
import java.util.HexFormat
import java.util.Locale

/**
 * Parses the OSC 1341 shell-integration commands emitted by the bundled shell-integration scripts into
 * [TerminalShellIntegrationEventsListener] callbacks.
 */
internal class TerminalShellIntegrationController {
  private val dispatcher = EventDispatcher.create(TerminalShellIntegrationEventsListener::class.java)

  private var currentCommand: String? = null

  /** Whether the shell reported its integration scripts as loaded (the `initialized` event). */
  var isShellIntegrationEnabled: Boolean = false
    private set

  fun processCustomCommand(args: List<String>) {
    try {
      when (args.getOrNull(0)) {
        "initialized" -> processInitializedEvent(args)
        "command_started" -> processCommandStartedEvent(args)
        "command_finished" -> processCommandFinishedEvent(args)
        "prompt_started" -> dispatcher.multicaster.promptStarted()
        "prompt_finished" -> dispatcher.multicaster.promptFinished()
        "aliases_received" -> processAliasesReceivedEvent(args)
        "completion_finished" -> processCompletionFinishedEvent(args)
        else -> LOG.warn("Unknown shell integration event: $args")
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (t: Throwable) {
      LOG.error("Exception during processing shell integration event: $args", t)
    }
  }

  private fun processInitializedEvent(args: List<String>) {
    isShellIntegrationEnabled = true
    val currentDirectory = Param.CURRENT_DIRECTORY.getDecodedValueOrNull(args.getOrNull(1))
    val historyPath = Param.HISTORY_PATH.getDecodedNotEmptyValueOrNull(args.getOrNull(2))
    dispatcher.multicaster.initialized(currentDirectory, historyPath)
  }

  private fun processCommandStartedEvent(args: List<String>) {
    val command = Param.COMMAND.getDecodedValue(args.getOrNull(1))
    currentCommand = command
    dispatcher.multicaster.commandStarted(command)
  }

  private fun processCommandFinishedEvent(args: List<String>) {
    val command = currentCommand
    if (command != null) {
      currentCommand = null

      val exitCode = Param.EXIT_CODE.getIntValue(args.getOrNull(1))
      val currentDirectory = Param.CURRENT_DIRECTORY.getDecodedValueOrNull(args.getOrNull(2))
      dispatcher.multicaster.commandFinished(command, exitCode, currentDirectory)
    }
  }

  private fun processAliasesReceivedEvent(args: List<String>) {
    val aliasesRaw = Param.RESULT.getDecodedValue(args.getOrNull(1))
    dispatcher.multicaster.aliasesReceived(aliasesRaw)
  }

  private fun processCompletionFinishedEvent(args: List<String>) {
    val result = Param.RESULT.getDecodedValue(args.getOrNull(1))
    dispatcher.multicaster.completionFinished(result)
  }

  fun addListener(listener: TerminalShellIntegrationEventsListener) {
    dispatcher.addListener(listener)
  }

  /**
    * Registers a listener that projects the shell-integration callbacks into the corresponding
    * [TerminalShellIntegrationEvent]s and passes them to [sink], synchronously on the
    * [processCustomCommand] thread.
    */
  fun addEventSink(sink: (TerminalShellIntegrationEvent) -> Unit) {
    addListener(object : TerminalShellIntegrationEventsListener {
      override fun initialized(currentDirectory: String?, historyPath: String?) {
        sink(TerminalCommandHistoryPathReceivedEvent(historyPath))
      }

      override fun commandStarted(command: String) {
        sink(TerminalCommandStartedEvent(command))
      }

      override fun commandFinished(command: String, exitCode: Int, currentDirectory: String?) {
        sink(TerminalCommandFinishedEvent(command, exitCode, currentDirectory))
      }

      override fun promptStarted() {
        sink(TerminalPromptStartedEvent)
      }

      override fun promptFinished() {
        sink(TerminalPromptFinishedEvent)
      }

      override fun aliasesReceived(aliasesRaw: String) {
        sink(TerminalAliasesReceivedEvent(aliasesRaw))
      }

      override fun completionFinished(result: String) {
        sink(TerminalCompletionFinishedEvent(result))
      }
    })
  }

  fun removeListener(listener: TerminalShellIntegrationEventsListener) {
    dispatcher.removeListener(listener)
  }

  private enum class Param {
    COMMAND,
    EXIT_CODE,
    CURRENT_DIRECTORY,
    RESULT,
    HISTORY_PATH;

    private val paramNameWithSeparator: String = "${paramName()}="

    private fun paramName(): String = name.lowercase(Locale.ENGLISH)

    fun getIntValue(nameAndValue: String?): Int {
      return getValueOrNull(nameAndValue)?.toIntOrNull() ?: fail()
    }

    fun getDecodedValue(nameAndValue: String?): String {
      return getDecodedValueOrNull(nameAndValue) ?: fail()
    }

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

    @Throws(IllegalArgumentException::class)
    private fun decodeHex(hexStr: String): String {
      val bytes = HexFormat.of().parseHex(hexStr)
      return String(bytes, Charsets.UTF_8)
    }

    private fun fail(): Nothing = throw IllegalStateException("Cannot parse ${paramName()}")
  }

  companion object {
    private val LOG: Logger = Logger.getInstance(TerminalShellIntegrationController::class.java)
  }
}
