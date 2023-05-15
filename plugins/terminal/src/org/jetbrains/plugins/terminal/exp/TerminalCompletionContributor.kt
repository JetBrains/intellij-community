// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PlatformPatterns.psiFile
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.terminal.util.SHELL_TYPE_KEY
import org.jetbrains.plugins.terminal.util.ShellType

class TerminalCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC, psiElement().inFile(psiFile().with(ShellTypeCondition(ShellType.ZSH))), ZshCompletionProvider())
  }

  private class ShellTypeCondition(private val type: ShellType) : PatternCondition<PsiFile>("shellType") {
    override fun accepts(file: PsiFile, context: ProcessingContext?): Boolean {
      var original = file
      while (original.originalFile != original) {
        original = original.originalFile
      }
      return original.getUserData(SHELL_TYPE_KEY) == type
    }
  }

  companion object {
    fun isSingleCharParameter(value: String): Boolean = value.length == 2 && value[0] == '-'
  }
}