// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.backend

import com.intellij.openapi.diagnostic.Logger
import com.intellij.terminal.session.TerminalAliasesInfo
import com.intellij.util.EventDispatcher
import com.jediterm.terminal.Terminal
import org.jetbrains.plugins.terminal.block.reworked.TerminalShellIntegrationEventsListener
import org.jetbrains.plugins.terminal.exp.completion.TerminalShellSupport
import org.jetbrains.plugins.terminal.util.ShellType
import java.util.*

internal class TerminalShellIntegrationController(terminalController: Terminal) {
  private val dispatcher = EventDispatcher.create(TerminalShellIntegrationEventsListener::class.java)

  private var currentCommand: String? = null

  init {
    terminalController.addCustomCommandListener { args: List<String> ->
      try {
        when (args.getOrNull(0)) {
          "initialized" -> dispatcher.multicaster.initialized()
          "command_started" -> processCommandStartedEvent(args)
          "command_finished" -> processCommandFinishedEvent(args)
          "prompt_started" -> dispatcher.multicaster.promptStarted()
          "prompt_finished" -> dispatcher.multicaster.promptFinished()
          "aliases_received" -> {
            val aliasesString = args.getOrNull(1)
            val aliases = if (aliasesString?.isNotEmpty() == true) {
              parseAliases(aliasesString, ShellType.ZSH.name)
            }
            else emptyMap()
            dispatcher.multicaster.aliasesReceived(TerminalAliasesInfo(aliases))
          }
          else -> LOG.warn("Unknown shell integration event: $args")
        }
      }
      catch (t: Throwable) {
        LOG.warn("Exception during processing shell integration event: $args", t)
      }
    }
  }

  private fun parseAliases(text: String, shellName: String): Map<String, String> {
    val shellSupport = TerminalShellSupport.findByShellName(shellName)
                       ?: return emptyMap()
    return try {
      shellSupport.parseAliases(text)
    }
    catch (t: Throwable) {
      LOG.error("Failed to parse aliases: $text", t)
      emptyMap()
    }
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
      val currentDirectory = Param.CURRENT_DIRECTORY.getDecodedValue(args.getOrNull(2))
      dispatcher.multicaster.commandFinished(command, exitCode, currentDirectory)
    }
  }

  fun addListener(listener: TerminalShellIntegrationEventsListener) {
    dispatcher.addListener(listener)
  }

  fun removeListener(listener: TerminalShellIntegrationEventsListener) {
    dispatcher.removeListener(listener)
  }

  private enum class Param {
    COMMAND,
    EXIT_CODE,
    CURRENT_DIRECTORY;

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