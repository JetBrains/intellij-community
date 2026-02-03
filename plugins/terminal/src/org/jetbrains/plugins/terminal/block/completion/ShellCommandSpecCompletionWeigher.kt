// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.CompletionWeigher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.completion.spec.ShellSuggestionType

internal class ShellCommandSpecCompletionWeigher : CompletionWeigher() {
  override fun weigh(element: LookupElement, location: CompletionLocation): Comparable<Nothing>? {
    val suggestion = element.`object` as? ShellCompletionSuggestion ?: return null
    return when (suggestion.type) {
      ShellSuggestionType.COMMAND -> 6
      ShellSuggestionType.FOLDER, ShellSuggestionType.FILE -> {
        // prioritize file names of not hidden files
        if (element.lookupString.startsWith(".")) 4 else 5
      }
      ShellSuggestionType.ARGUMENT -> 3
      ShellSuggestionType.OPTION -> {
        // prioritize short options
        if (element.lookupString.length <= 2) 2 else 1
      }
      else -> 0
    }
  }
}
