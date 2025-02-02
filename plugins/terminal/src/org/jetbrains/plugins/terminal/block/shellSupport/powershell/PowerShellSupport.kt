// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.shellSupport.powershell

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.tree.IElementType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.plugins.terminal.block.shellSupport.ShLangService
import org.jetbrains.plugins.terminal.exp.completion.TerminalShellSupport

internal class PowerShellSupport : TerminalShellSupport {
  override val promptContentElementType: IElementType?
    get() = serviceOrNull<ShLangService>()?.promptContentElementType

  override val lineContinuationChar: Char = '`'

  override fun getCommandTokens(project: Project, command: String): List<String>? {
    return serviceOrNull<ShLangService>()?.getShellCommandTokens(project, command)
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

  override fun parseAliases(aliasesDefinition: String): Map<String, String> {
    val aliases: List<AliasDefinition> = try {
      Json.decodeFromString(aliasesDefinition)
    }
    catch (t: Throwable) {
      LOG.error("Failed to parse aliases: '$aliasesDefinition'", t)
      emptyList()
    }
    return aliases.associate { it.name to it.definition }
  }

  @Serializable
  private data class AliasDefinition(val name: String, val definition: String)

  companion object {
    private val LOG: Logger = logger<PowerShellSupport>()
  }
}
