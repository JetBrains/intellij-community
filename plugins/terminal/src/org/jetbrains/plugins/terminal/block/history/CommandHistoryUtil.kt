// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.history

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupArranger
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.NameUtil


internal object CommandHistoryUtil {
  fun createLookup(project: Project, editor: Editor, prefix: String, history: List<String>): LookupImpl {
    val arranger = LookupArranger.DefaultArranger()
    val lookup = LookupManager.getInstance(project).createLookup(editor, emptyArray(), prefix, arranger) as LookupImpl
    val prefixMatcher = CommandHistoryPrefixMatcher(prefix)
    for (historyItem in history) {
      val element = LookupElementBuilder.create(historyItem)
      lookup.addItem(element, prefixMatcher)
    }
    return lookup
  }
}

private class CommandHistoryPrefixMatcher(prefix: String) : PrefixMatcher(prefix) {
  private val patternWords: List<String> = NameUtil.nameToWordList(prefix)

  override fun prefixMatches(name: String): Boolean {
    val nameWords = NameUtil.nameToWordList(name)
    var patternIndex = 0
    var wordIndex = 0
    // Go through each pattern word and try to find the matching word in the current name.
    // Take into account the order of the pattern words: the first pattern should be found before the second and so on.
    while (patternIndex in patternWords.indices && wordIndex in nameWords.indices) {
      val pattern = patternWords[patternIndex]
      wordIndex = nameWords.indexOfFirstFrom(wordIndex) { it.contains(pattern, ignoreCase = true) }
      if (wordIndex == -1) {
        return false
      }
      wordIndex++
      patternIndex++
    }
    // return true if we found the word for each pattern
    return patternIndex == patternWords.size
  }

  private inline fun <T> List<T>.indexOfFirstFrom(fromIndex: Int, predicate: (T) -> Boolean): Int {
    var index = fromIndex
    while (index in indices) {
      if (predicate(this[index])) {
        return index
      }
      index++
    }
    return -1
  }

  override fun cloneWithPrefix(prefix: String): PrefixMatcher {
    return CommandHistoryPrefixMatcher(prefix)
  }
}
