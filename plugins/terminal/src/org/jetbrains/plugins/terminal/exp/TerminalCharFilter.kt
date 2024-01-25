// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.util.UserDataHolder
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.exp.history.CommandHistoryPresenter

class TerminalCharFilter : CharFilter() {
  override fun acceptChar(c: Char, prefixLength: Int, lookup: Lookup): Result? {
    return if (lookup is UserDataHolder && lookup.getUserData(CommandHistoryPresenter.IS_COMMAND_HISTORY_LOOKUP_KEY) == true) {
      // It is command history lookup
      // Close lookup on any char typed
      Result.HIDE_LOOKUP
    }
    else if (lookup.editor.isPromptEditor) {
      // It is command completion lookup
      val matches = lookup.items.filter { matchesAfterAppendingChar(lookup, it, c) }
      if (matches.isNotEmpty()) {
        if (matches.all { isSingleCharParameter(it.lookupString) }
            && !lookup.items.any { isSingleCharParameter(it.lookupString) && it.lookupString[1] == c }) {
          // Close lookup if we are completing single char parameters and user typed the char,
          // that do not match any of available parameters
          Result.HIDE_LOOKUP
        }
        else Result.ADD_TO_PREFIX
      }
      else Result.HIDE_LOOKUP
    }
    else null
  }

  private fun matchesAfterAppendingChar(lookup: Lookup, item: LookupElement, c: Char): Boolean {
    val matcher = lookup.itemMatcher(item)
    return matcher.cloneWithPrefix(matcher.prefix + (lookup as LookupImpl).additionalPrefix + c).prefixMatches(item)
  }

  private fun isSingleCharParameter(value: String): Boolean = value.length == 2 && value[0] == '-'
}