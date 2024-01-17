// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.sh

import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.Strings
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sh.ShLanguage
import com.intellij.sh.psi.ShCommandsList
import com.intellij.sh.psi.ShSimpleCommand
import org.jetbrains.plugins.terminal.exp.completion.TerminalShellSupport

abstract class BaseShSupport : TerminalShellSupport {
  override val promptLanguage: Language
    get() = ShLanguage.INSTANCE

  override val lineContinuationChar: Char = '\\'

  override fun getCommandTokens(leafElement: PsiElement): List<String>? {
    val commandElement: ShSimpleCommand = PsiTreeUtil.getParentOfType(leafElement, ShSimpleCommand::class.java)
                                          ?: return null
    val curElementEndOffset = leafElement.textRange.endOffset
    return commandElement.children.filter { it.textRange.endOffset <= curElementEndOffset }
      .map { it.text.replace(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED, "") }
  }

  override fun getCommandTokens(project: Project, command: String): List<String>? {
    val psiFile = PsiFileFactory.getInstance(project).createFileFromText(promptLanguage, command)
    val commands = PsiTreeUtil.getChildrenOfType(psiFile, ShCommandsList::class.java)?.lastOrNull() ?: return null
    val lastCommand = commands.commandList.lastOrNull { it is ShSimpleCommand } ?: return null
    return lastCommand.children.map { it.text }
  }

  override fun parseAliases(aliasesDefinition: String): Map<String, String> {
    val aliases = splitAliases(aliasesDefinition)
    return aliases.asSequence().mapNotNull {
      parseAlias(it) ?: run {
        LOG.warn("Failed to parse alias: '$it'")
        null
      }
    }.toMap()
  }

  protected abstract fun splitAliases(aliasesDefinition: String): List<String>

  /**
   * Parses an alias definition string and returns a Pair containing the alias name and the aliased command
   * or null if the [aliasDefinition] is invalid.
   */
  private fun parseAlias(aliasDefinition: String): Pair<String, String>? {
    // '=' delimits the alias and the command
    val equalsIndex = aliasDefinition.indexOf('=').takeIf { it != -1 } ?: return null
    // Quotes can surround the alias in Zsh
    val alias = aliasDefinition.substring(0, equalsIndex)
                  .removeSurrounding("'")
                  .takeIf { it.isNotBlank() } ?: return null
    // Quotes can surround the command, also there can be preceding '$' before the quotes in Zsh
    val command = aliasDefinition.substring(equalsIndex + 1)
      .removePrefix("$")
      .removeSurrounding("'")
    return alias to command
  }

  override fun parseCommandHistory(history: String): List<String> {
    val escapedHistory = Strings.replace(history, listOf("\r", "\b", "\t", "\u000c"), listOf("\\r", "\\b", "\\t", "\\f"))
    return escapedHistory.split("\n").mapNotNull { row ->
      // the row is in the format <spaces><row_number><spaces><command><spaces>
      // retrieve command from the row
      row.trimStart().trimStart { Character.isDigit(it) }.trim().takeIf { it.isNotEmpty() }
    }
  }

  companion object {
    private val LOG: Logger = logger<BaseShSupport>()
  }
}