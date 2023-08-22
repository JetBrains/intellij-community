// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.CompletionWeigher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.terminal.completion.CommandSpecCompletionUtil.isFilePath
import com.intellij.terminal.completion.CommandSpecCompletionUtil.isFolder
import com.intellij.terminal.completion.ShellArgumentSuggestion
import org.jetbrains.terminal.completion.BaseSuggestion
import org.jetbrains.terminal.completion.ShellCommand
import org.jetbrains.terminal.completion.ShellOption
import org.jetbrains.terminal.completion.ShellSuggestion

class CommandSpecCompletionWeigher : CompletionWeigher() {
  override fun weigh(element: LookupElement, location: CompletionLocation): Comparable<Nothing>? {
    val suggestion = element.`object` as? BaseSuggestion ?: return null
    return when (suggestion) {
      is ShellCommand -> 7
      is ShellArgumentSuggestion -> {
        val arg = suggestion.argument
        if (arg.isFilePath() || arg.isFolder()) {
          if (element.lookupString.startsWith(".")) 5 else 6  // prioritize file names of not hidden files
        }
        else 4
      }
      is ShellSuggestion -> 3
      is ShellOption -> if (element.lookupString.length <= 2) 2 else 1  // prioritize short options
      else -> 0
    }
  }
}