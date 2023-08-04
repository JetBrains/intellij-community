// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.sh

import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sh.ShLanguage
import com.intellij.sh.psi.ShSimpleCommand
import org.jetbrains.plugins.terminal.exp.completion.TerminalShellSupport

abstract class BaseShSupport : TerminalShellSupport {
  override val promptLanguage: Language
    get() = ShLanguage.INSTANCE

  override fun getCommandTokens(leafElement: PsiElement): List<String>? {
    val commandElement: ShSimpleCommand = PsiTreeUtil.getParentOfType(leafElement, ShSimpleCommand::class.java)
                                          ?: return emptyList()
    val curElementEndOffset = leafElement.textRange.endOffset
    return commandElement.children.filter { it.textRange.endOffset <= curElementEndOffset }
      .map { it.text.replace(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED, "") }
  }
}