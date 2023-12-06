// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.fus

import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.eventLog.validator.rules.impl.AllowedItemsResourceWeakRefStorage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.util.PathUtil
import com.intellij.util.execution.ParametersListUtil
import java.util.Set.*

internal object TerminalCommandUsageStatistics {

  private val emptyCommand = TerminalCommandEventData("<empty>", null)
  private val whitespacesCommand = TerminalCommandEventData("<whitespaces>", null)
  private val relativePathCommand = TerminalCommandEventData("<relative path>", null)
  private val absolutePathCommand = TerminalCommandEventData("<absolute path>", null)
  private val knownCommandToSubCommandsMap: Map<String, Set<String>> = buildKnownCommandToSubCommandMap()

  internal val commandExecutableField = EventFields.String("command", listOf(relativePathCommand.command, absolutePathCommand.command,
                                                                             emptyCommand.command, whitespacesCommand.command)
                                                                     + knownCommandToSubCommandsMap.keys)
  internal val subCommandField = EventFields.String("subCommand", knownCommandToSubCommandsMap.values.flatten())

  fun triggerCommandExecuted(commandExecutedEvent: EventId2<String?, String?>, project: Project, userCommandLine: String) {
    val eventData = toEventData(userCommandLine)
    commandExecutedEvent.log(project, eventData?.command, eventData?.subCommand)
  }

  private fun toEventData(userCommandLine: String): TerminalCommandEventData? {
    if (userCommandLine.isEmpty()) {
      return emptyCommand
    }
    if (userCommandLine.isBlank()) {
      return whitespacesCommand
    }
    val userCommand = ParametersListUtil.parse(userCommandLine)
    toKnownCommand(userCommand)?.let {
      return it
    }
    val executable = userCommand.getOrNull(0) ?: return null
    if (isRelativePath(executable) && executable.length > 2) {
      if (PathUtil.toSystemIndependentName(executable).startsWith("./gradlew")) {
        return toKnownCommand(listOf("gradle"))
      }
      return relativePathCommand
    }
    return if (OSAgnosticPathUtil.isAbsolute(executable) && executable.length > 3) absolutePathCommand else null
  }

  private fun isRelativePath(executable: String): Boolean {
    return executable.startsWith("./") || SystemInfo.isWindows && executable.startsWith(".\\")
  }

  private fun toKnownCommand(userCommand: List<String>): TerminalCommandEventData? {
    val executable: String = (userCommand.getOrNull(0) ?: return null).let {
      if (SystemInfo.isWindows) it.removeSuffix(".exe") else it
    }
    val knownSubCommands: Set<String> = knownCommandToSubCommandsMap[executable] ?: return null
    val subCommand = userCommand.getOrNull(1)?.let {
      if (knownSubCommands.contains(it)) it else null
    }
    return TerminalCommandEventData(executable, subCommand)
  }

  private fun buildKnownCommandToSubCommandMap(): Map<String, Set<String>> {
    val commandLines = AllowedItemsResourceWeakRefStorage(TerminalCommandUsageStatistics.javaClass, "known-commands.txt").items
    val result: Map<String, List<String?>> = commandLines.asSequence().mapNotNull {
      val command = ParametersListUtil.parse(it)
      val executable = command.getOrNull(0)
      if (executable != null) executable to command.getOrNull(1) else null
    }.groupBy({ it.first }, { it.second })
    return result.map { it.key to copyOf(it.value.filterNotNull()) }.associateTo(HashMap(result.size)) { it }
  }

  private class TerminalCommandEventData(val command: String, val subCommand: String?)
}
