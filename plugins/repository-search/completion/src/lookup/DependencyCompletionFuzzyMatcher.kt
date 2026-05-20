// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.repository.search.completion.lookup

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.text.matching.MatchedFragment
import org.jetbrains.annotations.ApiStatus

private val log = logger<DependencyCompletionFuzzyMatcher>()

@ApiStatus.Experimental
open class DependencyCompletionFuzzyMatcher(prefix: String) : PrefixMatcher(prefix) {
  override fun prefixMatches(name: String): Boolean = true

  override fun cloneWithPrefix(prefix: String): PrefixMatcher = DependencyCompletionFuzzyMatcher(prefix)

  override fun getMatchingFragments(prefix: String, name: String): List<MatchedFragment> {
    try {
      return tryGetMatchingFragments(prefix, name)
    }
    catch (e: Throwable) {
      log.error("Error while matching fragments, name $name, prefix $prefix", e)
      return emptyList()
    }
  }

  private fun tryGetMatchingFragments(input: String, searchResult: String): List<MatchedFragment> {
    val start = startOffset(searchResult)
    val prefixParts = input.split(":")
    val nameParts = searchResult.substring(start).split(":")
    val result = mutableListOf<MatchedFragment>()
    var offset = start
    var j = 0
    for (i in prefixParts.indices) {
      var matchingFragment: MatchedFragment? = null
      while (j < nameParts.size && matchingFragment == null) {
        matchingFragment = getMatchingFragment(offset, prefixParts[i], nameParts[j])
        offset += nameParts[j].length + 1
        j++
      }
      if (matchingFragment == null) {
        return tryFallbackMatching(input, searchResult, start)
      }
      result.add(matchingFragment)
    }
    return result
  }

  /** Returns the index in [searchResult] from which matching should begin. */
  protected open fun startOffset(searchResult: String): Int = 0

  private fun tryFallbackMatching(input: String, searchResult: String, start: Int): List<MatchedFragment> {
    val searchable = searchResult.substring(start)
    for (len in input.length downTo 1) {
      for (subStart in 0..input.length - len) {
        val sub = input.substring(subStart, subStart + len)
        val idx = searchable.indexOf(sub, ignoreCase = true)
        if (idx != -1) {
          val absoluteStart = start + idx
          return listOf(MatchedFragment(absoluteStart, absoluteStart + sub.length))
        }
      }
    }
    return emptyList()
  }

  private fun getMatchingFragment(offset: Int, prefixPart: String, name: String): MatchedFragment? {
    val from = name.indexOf(prefixPart)
    if (from == -1) return null
    return MatchedFragment(from + offset, from + offset + prefixPart.length)
  }
}
