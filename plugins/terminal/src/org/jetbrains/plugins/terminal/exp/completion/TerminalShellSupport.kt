// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.util.KeyedLazyInstanceEP
import org.jetbrains.plugins.terminal.util.ShellType

interface TerminalShellSupport {
  val promptLanguage: Language

  /**
   * @return the token list for the parent shell command of the provided [leafElement]
   */
  fun getCommandTokens(leafElement: PsiElement): List<String>? = null

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
      return EP_NAME.extensionList.find { it.key.lowercase() == type.toString().lowercase() }?.instance
    }
  }
}