// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IElementType
import com.intellij.util.KeyedLazyInstanceEP
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.util.ShellType

@ApiStatus.Experimental
interface TerminalShellSupport {
  val promptContentElementType: IElementType?

  /**
   * The character that used to continue the command on the next line.
   * For example, in Bash and Zsh it is a backslash '\'.
   */
  val lineContinuationChar: Char

  /**
   * @return the token list for the last shell command in [command] text
   */
  fun getCommandTokens(project: Project, command: String): List<String>? = null

  /**
   * @param aliasesDefinition the string with all aliases of the shell
   * @return the map of aliases where the key is an alias and the value is an aliased command
   */
  fun parseAliases(aliasesDefinition: String): Map<String, String> = emptyMap()

  /**
   * @param history command history in a raw format
   * @return the list of strings where each string is a shell command from the history
   */
  fun parseCommandHistory(history: String): List<String> = emptyList()

  companion object {
    val EP_NAME: ExtensionPointName<KeyedLazyInstanceEP<TerminalShellSupport>> =
      ExtensionPointName.create("org.jetbrains.plugins.terminal.shellSupport")

    fun findByShellType(type: ShellType): TerminalShellSupport? {
      return findByShellName(type.toString())
    }

    fun findByShellName(shellName: String): TerminalShellSupport? {
      return EP_NAME.extensionList.find { it.key.lowercase() == shellName.lowercase() }?.instance
    }
  }
}
