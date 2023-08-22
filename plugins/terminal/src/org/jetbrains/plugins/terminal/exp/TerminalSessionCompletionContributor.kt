// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.terminal.util.ShellType

class TerminalSessionCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val session = parameters.editor.getUserData(TerminalSession.KEY) ?: return
    val shellType = session.shellIntegration?.shellType ?: return
    when (shellType) {
      ShellType.ZSH, ShellType.BASH -> {
        val provider = ShCompletionProvider()
        provider.addCompletionVariants(parameters, ProcessingContext(), result)
      }
      else -> {}
    }
  }

  companion object {
    fun isSingleCharParameter(value: String): Boolean = value.length == 2 && value[0] == '-'
  }
}