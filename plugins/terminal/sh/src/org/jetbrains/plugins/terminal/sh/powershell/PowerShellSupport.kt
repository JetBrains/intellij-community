package org.jetbrains.plugins.terminal.sh.powershell

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.terminal.exp.completion.TerminalShellSupport
import org.jetbrains.plugins.terminal.sh.getShellCommandTokens

class PowerShellSupport : TerminalShellSupport {
  override val promptLanguage: Language
    get() = PlainTextLanguage.INSTANCE

  override val lineContinuationChar: Char = '`'

  override fun getCommandTokens(project: Project, command: String): List<String>? {
    return getShellCommandTokens(project, command)
  }

  override fun parseCommandHistory(history: String): List<String> {
    val trimmedHistory = StringUtil.convertLineSeparators(history, "\n").trimEnd()
    if (trimmedHistory.isBlank()) {
      return emptyList()
    }
    val lines = trimmedHistory.split("\n")
    val historyItems = mutableListOf<String>()
    var ind = 0
    // Some commands can consist of multiple lines. The indication of it is a '`' character at the end of the line.
    while (ind < lines.size) {
      val line = lines[ind]
      if (line.endsWith("`")) {
        // it is a multiline command, collect the lines until the line with no '`' at the end is found
        val builder = StringBuilder(line.removeSuffix("`")).append("\n")
        ind++
        while (ind < lines.size && lines[ind].endsWith("`")) {
          builder.append(lines[ind].removeSuffix("`")).append("\n")
          ind++
        }
        if (ind < lines.size) {
          builder.append(lines[ind])
        }
        historyItems.add(builder.toString())
      }
      else {
        historyItems.add(line)
      }
      ind++
    }
    return historyItems
  }
}