// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.fus

import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.validator.rules.impl.AllowedItemsResourceWeakRefStorage
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.util.PathUtil
import com.intellij.util.execution.ParametersListUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.Locale
import kotlin.math.min

@ApiStatus.Internal
object TerminalCommandUsageStatistics {

  private const val THIRD_PARTY = "third.party"
  private val thirdPartyCommand = CommandData(THIRD_PARTY, null)

  private val emptyCommand = CommandData("<empty>", null)
  private val whitespacesCommand = CommandData("<whitespaces>", null)
  private val relativePathCommand = CommandData("<relative path>", null)
  private val absolutePathCommand = CommandData("<absolute path>", null)
  private val knownCommandsData: KnownCommandsData = buildKnownCommandsData()

  fun getKnownCommandValuesList(): List<String> {
    val cornerCases = listOf(
      relativePathCommand.command,
      absolutePathCommand.command,
      emptyCommand.command,
      whitespacesCommand.command
    )
    return cornerCases + knownCommandsData.commands.toList()
  }

  fun getKnownSubCommandValuesList(): List<String> {
    return knownCommandsData.subCommands.toList()
  }

  internal val commandExecutableField = EventFields.String("command", getKnownCommandValuesList())
  internal val subCommandField = EventFields.String("subCommand", getKnownSubCommandValuesList())

  /**
   * Parses the provided [userCommandLine] and returns [CommandData] if command line contains known command or pattern,
   * that we are able to log in the statistics. Returns null otherwise.
   */
  fun getLoggableCommandData(userCommandLine: String): CommandData {
    return getLoggableCommandData(userCommandLine, knownCommandsData)
  }

  @VisibleForTesting
  fun getLoggableCommandData(userCommandLine: String, knownCommandsData: KnownCommandsData): CommandData {
    if (userCommandLine.isEmpty()) {
      return emptyCommand
    }
    if (userCommandLine.isBlank()) {
      return whitespacesCommand
    }

    val userCommand = ParametersListUtil.parse(userCommandLine)
    toKnownCommand(userCommand, knownCommandsData)?.let {
      return it
    }

    val executable = userCommand.getOrNull(0) ?: return thirdPartyCommand
    if (isRelativePath(executable) && executable.length > 2) {
      return if (PathUtil.toSystemIndependentName(executable).endsWith("/gradlew")) {
        val gradleCommand = listOf("gradle") + userCommand.drop(1)
        toKnownCommand(gradleCommand, knownCommandsData) ?: thirdPartyCommand
      }
      else relativePathCommand
    }
    return if (OSAgnosticPathUtil.isAbsolute(OSAgnosticPathUtil.expandUserHome(executable)) && executable.length > 3) {
      absolutePathCommand
    }
    else thirdPartyCommand
  }

  private fun isRelativePath(executable: String): Boolean {
    return executable.startsWith("./") || SystemInfo.isWindows && executable.startsWith(".\\")
  }

  private fun toKnownCommand(userCommand: List<String>, knownCommandsData: KnownCommandsData): CommandData? {
    val executable: String = (userCommand.getOrNull(0) ?: return null).let {
      if (SystemInfo.isWindows) it.removeSuffix(".exe") else it
    }.lowercase(Locale.ENGLISH)

    if (executable !in knownCommandsData.commands) {
      return null
    }

    // Find the longest known subcommand that match the user command
    val subCommandTokens = userCommand.drop(1)
    val maxTokensCount = min(subCommandTokens.size, knownCommandsData.maxSubCommandTokens)
    if (maxTokensCount > 0) {
      for (subCommandLength in maxTokensCount downTo 1) {
        val subCommand = subCommandTokens.subList(0, subCommandLength).joinToString(" ")
        if (subCommand in knownCommandsData.subCommands) {
          return CommandData(executable, subCommand)
        }
      }
    }

    return if (subCommandTokens.isNotEmpty()) {
      CommandData(executable, THIRD_PARTY)
    }
    else CommandData(executable, null)
  }

  private fun buildKnownCommandsData(): KnownCommandsData {
    val commands = HashSet<String>()
    val subCommands = HashSet<String>()
    var maxSubCommandTokens = 1

    val commandLines = AllowedItemsResourceWeakRefStorage(TerminalCommandUsageStatistics.javaClass, "known-commands.txt").items
    for (line in commandLines) {
      val command = line.substringBefore(' ')
      val subCommand = line.substring(command.length).trim().takeIf { it.isNotEmpty() }
      val subCommandTokensCount = subCommand?.let { ParametersListUtil.parse(it).size } ?: 0
      commands.add(command)
      subCommand?.let { subCommands.add(it) }
      maxSubCommandTokens = maxOf(maxSubCommandTokens, subCommandTokensCount)
    }

    return KnownCommandsData(commands, subCommands, maxSubCommandTokens)
  }

  class CommandData(val command: String, val subCommand: String?)

  @VisibleForTesting
  class KnownCommandsData(
    val commands: Set<String>,
    val subCommands: Set<String>,
    val maxSubCommandTokens: Int,
  )
}
