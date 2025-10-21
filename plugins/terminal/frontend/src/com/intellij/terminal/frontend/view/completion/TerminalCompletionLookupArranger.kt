package com.intellij.terminal.frontend.view.completion

import com.intellij.codeInsight.completion.CompletionLookupArrangerImpl
import com.intellij.codeInsight.completion.CompletionProcessEx
import com.intellij.codeInsight.lookup.LookupElement

/**
 * Ensures that completion items that exactly match the typed prefix are shown first.
 *
 * In terminal command completion an additional case is considered as an exact match:
 * when the completion item is the directory name with trailing file separator, for example `foo/`.
 * In this case, if user types `foo`, it should be considered as an exact match and shown first.
 */
internal class TerminalCompletionLookupArranger(process: CompletionProcessEx) : CompletionLookupArrangerImpl(process) {
  override fun isPrefixItem(item: LookupElement, exactly: Boolean): Boolean {
    if (super.isPrefixItem(item, exactly)) {
      return true
    }

    val typedPrefix = itemPattern(item)
    return canExecuteWithChosenItem(item.lookupString, typedPrefix)
  }
}