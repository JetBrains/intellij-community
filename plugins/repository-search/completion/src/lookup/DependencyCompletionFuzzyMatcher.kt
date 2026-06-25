// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.repository.search.completion.lookup

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.text.matching.MatchedFragment
import org.jetbrains.annotations.ApiStatus

private val log = logger<DependencyCompletionFuzzyMatcher>()

/** Characters that separate words inside a single coordinate part, e.g. `spring-boot-starter` or `org.junit`. */
private const val WORD_DELIMITERS = "-._"

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
      var partFragments: List<MatchedFragment>? = null
      while (j < nameParts.size && partFragments == null) {
        partFragments = matchPart(offset, prefixParts[i], nameParts[j])
        offset += nameParts[j].length + 1
        j++
      }
      if (partFragments == null) {
        return tryFallbackMatching(input, searchResult, start)
      }
      result.addAll(partFragments)
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

  /**
   * Matches the word tokens of [prefixPart] against the word tokens of [namePart] as an in-order subsequence,
   * returning one highlight fragment per matched token, or `null` if any prefix token has no match.
   *
   * [offset] is the absolute start of [namePart] within the whole search result. Matching is case-insensitive.
   * When a prefix token matches a name token to its end and both are followed by the same delimiter, that
   * delimiter is included in the fragment (so `junit-` is highlighted for `junit-` but only `junit` for `junit.`).
   */
  private fun matchPart(offset: Int, prefixPart: String, namePart: String): List<MatchedFragment>? {
    val prefixTokens = tokenize(prefixPart)
    if (prefixTokens.isEmpty()) return emptyList()
    val nameTokens = tokenize(namePart)
    val fragments = mutableListOf<MatchedFragment>()
    var cursor = 0
    for (prefixToken in prefixTokens) {
      var matched = false
      while (cursor < nameTokens.size) {
        val nameToken = nameTokens[cursor]
        cursor++
        val idx = nameToken.text.indexOf(prefixToken.text, ignoreCase = true)
        if (idx != -1) {
          val fragmentStart = offset + nameToken.start + idx
          var fragmentEnd = fragmentStart + prefixToken.text.length
          val matchedToEnd = idx + prefixToken.text.length == nameToken.text.length
          if (matchedToEnd && prefixToken.delimiter != null && prefixToken.delimiter == nameToken.delimiter) {
            fragmentEnd++
          }
          fragments.add(MatchedFragment(fragmentStart, fragmentEnd))
          matched = true
          break
        }
      }
      if (!matched) return null
    }
    return fragments
  }

  private class Token(val text: String, val start: Int, val delimiter: Char?)

  /** Splits [part] on [WORD_DELIMITERS], keeping each token's start index and the delimiter that immediately follows it. */
  private fun tokenize(part: String): List<Token> {
    val tokens = mutableListOf<Token>()
    var tokenStart = -1
    for (i in part.indices) {
      val c = part[i]
      if (c in WORD_DELIMITERS) {
        if (tokenStart != -1) {
          tokens.add(Token(part.substring(tokenStart, i), tokenStart, c))
          tokenStart = -1
        }
      }
      else if (tokenStart == -1) {
        tokenStart = i
      }
    }
    if (tokenStart != -1) {
      tokens.add(Token(part.substring(tokenStart), tokenStart, null))
    }
    return tokens
  }
}
