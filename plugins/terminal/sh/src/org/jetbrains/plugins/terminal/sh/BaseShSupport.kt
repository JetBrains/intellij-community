// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.sh

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.Strings
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sh.ShLanguage
import com.intellij.sh.psi.ShCommandsList
import com.intellij.sh.psi.ShFileElementType
import com.intellij.sh.psi.ShSimpleCommand
import org.jetbrains.plugins.terminal.exp.completion.TerminalShellSupport

internal abstract class BaseShSupport : TerminalShellSupport {
  override val promptContentElementType: IElementType
    get() = ShFileElementType.INSTANCE

  override val lineContinuationChar: Char = '\\'

  override fun getCommandTokens(project: Project, command: String): List<String>? {
    return getShellCommandTokens(project, command)
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

/**
 * @return the token list for the last shell command in [command] text
 */
internal fun getShellCommandTokens(project: Project, command: String): List<String>? {
  val psiFile = PsiFileFactory.getInstance(project).createFileFromText(ShLanguage.INSTANCE, command)
  val commands = PsiTreeUtil.getChildrenOfType(psiFile, ShCommandsList::class.java)?.lastOrNull() ?: return null
  val lastCommand = commands.commandList.lastOrNull { it is ShSimpleCommand } ?: return null
  val tokens = mutableListOf<String>()
  // Append trailing error elements to the previous token.
  // It is the case for Windows with paths delimited by `\`.
  // Shell Script treats the trailing `\` as an error element, while it should be appended to the last token:
  // `cd src\` -> `src` and `\` are separate tokens, but should be single.
  for (literal in lastCommand.children) {
    val text = literal.text
    if (literal is PsiErrorElement && tokens.isNotEmpty()) {
      tokens[tokens.lastIndex] = tokens.last() + text
    }
    else tokens.add(text)
  }

  // Appends ';' symbol to the last command
  // It is the case for running commands like `ls <filepath>;`.
  // Shell script treats it as command termination; however, in this case, it might be a continuation of the argument.
  val nextSibling = PsiTreeUtil.skipWhitespacesForward(lastCommand) ?: return tokens
  if (nextSibling.text == ";" && tokens.isNotEmpty()) {
    tokens[tokens.lastIndex] = tokens.last() + nextSibling.text
  }
  return tokens
}