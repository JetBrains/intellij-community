// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.util.KeyedLazyInstanceEP
import org.jetbrains.plugins.terminal.util.ShellType

interface TerminalShellSupport {
  val promptLanguage: Language

  fun getCommandTokens(leafElement: PsiElement): List<String>? = null

  companion object {
    val EP_NAME: ExtensionPointName<KeyedLazyInstanceEP<TerminalShellSupport>> =
      ExtensionPointName.create("org.jetbrains.plugins.terminal.shellSupport")

    fun findByShellType(type: ShellType): TerminalShellSupport? {
      return EP_NAME.extensionList.find { it.key.lowercase() == type.toString().lowercase() }?.instance
    }
  }
}