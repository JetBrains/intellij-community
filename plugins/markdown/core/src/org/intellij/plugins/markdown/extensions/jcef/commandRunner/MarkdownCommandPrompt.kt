// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal
package org.intellij.plugins.markdown.extensions.jcef.commandRunner

import org.jetbrains.annotations.ApiStatus

fun trimMarkdownCommandPrompt(cmd: String, language: String = ""): String {
  val lines = cmd.lines()
  val commands = if (isConsole(language)) consoleCommands(lines) else shellCommands(lines)
  return commands
    .map(::stripTrailingShellComment)
    .filter { line -> line.isNotEmpty() }
    .joinToString("\n")
}

private fun isConsole(language: String): Boolean {
  return language.trim().takeWhile { !it.isWhitespace() }.equals("console", ignoreCase = true)
}

private fun shellCommands(lines: List<String>): List<String> {
  return lines.map { line -> if (hasPrompt(line, "$")) line.drop(1) else line }
}

private fun consoleCommands(lines: List<String>): List<String> {
  val commands = mutableListOf<String>()
  var continued = false
  for (line in lines) {
    val command = when {
      continued -> line
      hasPrompt(line, "$#") -> line.drop(1)
      else -> null
    }
    continued = command != null && line.takeLastWhile { it == '\\' }.length % 2 == 1
    if (command != null) commands.add(command)
  }
  return commands.ifEmpty { lines }
}

private fun hasPrompt(line: String, prompts: String): Boolean {
  return line.isNotEmpty() && line[0] in prompts && line.getOrNull(1)?.isWhitespace() != false
}

private fun stripTrailingShellComment(line: String): String {
  var inSingle = false
  var inDouble = false
  for (i in line.indices) {
    when (line[i]) {
      '\'' -> if (!inDouble) inSingle = !inSingle
      '"' -> if (!inSingle) inDouble = !inDouble
      '#' -> if (!inSingle && !inDouble && (i == 0 || line[i - 1].isWhitespace())) {
        return line.substring(0, i).trimEnd()
      }
    }
  }
  return line
}
