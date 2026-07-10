// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.repository.search.completion.lookup

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.text.matching.MatchedFragment
import org.jetbrains.annotations.ApiStatus

private val log = logger<DependencyCompletionFuzzyMatcher>()

/** Characters that separate words inside a single coordinate part, e.g. `spring-boot-starter` or `org.junit`. */
private const val WORD_DELIMITERS = "-._"

/** Minimum shared prefix length accepted as a partial token match (so `stater` still matches `starter`). */
private const val MIN_PARTIAL_MATCH_LENGTH = 2

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
    // Absolute start offset of each colon-separated name part within searchResult.
    val partOffsets = IntArray(nameParts.size)
    var offset = start
    for (k in nameParts.indices) {
      partOffsets[k] = offset
      offset += nameParts[k].length + 1
    }
    val result = mutableListOf<MatchedFragment>()
    var cursor = 0
    for (prefixPart in prefixParts) {
      // Among the remaining name parts, pick the one whose match covers the most prefix tokens, so a
      // coordinate like group:artifact:version highlights the artifact rather than an incidental match in
      // the group (e.g. the "boot" of "org.springframework.boot" when searching "boot-starter-actuator").
      var bestFragments: List<MatchedFragment>? = null
      var bestIndex = -1
      for (k in cursor until nameParts.size) {
        val fragments = matchPart(partOffsets[k], prefixPart, nameParts[k]) ?: continue
        if (bestFragments == null || fragments.size > bestFragments.size) {
          bestFragments = fragments
          bestIndex = k
        }
      }
      if (bestFragments == null) {
        return tryFallbackMatching(input, searchResult, start)
      }
      result.addAll(bestFragments)
      cursor = bestIndex + 1
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
   * returning one highlight fragment per matched token, or `null` if no prefix token matches at all.
   *
   * [offset] is the absolute start of [namePart] within the whole search result. Each prefix token is aligned
   * to a name token by their shared (case-insensitive) leading characters, and only those characters are
   * highlighted. A token matches when it is a full prefix of the name token (so a short token like `b` still
   * matches `beans`) or shares at least [MIN_PARTIAL_MATCH_LENGTH] characters (so a typo like `stater` still
   * highlights the `sta` of `starter`). A prefix token that matches no name token is skipped without consuming
   * any name token, so the remaining prefix tokens can still match (e.g. `boot-stater-actuator` against
   * `spring-boot-actuator` highlights `boot-` and `actuator`, skipping `stater`). When a token matches a name
   * token to its end and both are followed by the same delimiter, that delimiter is included (so `junit-` is
   * highlighted for `junit-` but only `junit` for `junit.`).
   */
  private fun matchPart(offset: Int, prefixPart: String, namePart: String): List<MatchedFragment>? {
    val prefixTokens = tokenize(prefixPart)
    if (prefixTokens.isEmpty()) return emptyList()
    val nameTokens = tokenize(namePart)
    val fragments = mutableListOf<MatchedFragment>()
    var cursor = 0
    for (prefixToken in prefixTokens) {
      // Find the next name token (at or after the cursor) that shares enough of a leading prefix.
      var index = cursor
      var matchLength = 0
      while (index < nameTokens.size) {
        val length = prefixToken.text.commonPrefixWith(nameTokens[index].text, ignoreCase = true).length
        if (length == prefixToken.text.length || length >= MIN_PARTIAL_MATCH_LENGTH) {
          matchLength = length
          break
        }
        index++
      }
      // No name token matches this prefix token: skip it without advancing the cursor, so the name tokens
      // scanned over remain available for the following prefix tokens.
      if (index == nameTokens.size) continue
      val nameToken = nameTokens[index]
      val fragmentStart = offset + nameToken.start
      var fragmentEnd = fragmentStart + matchLength
      val matchedToEnd = matchLength == nameToken.text.length
      if (matchedToEnd && prefixToken.delimiter != null && prefixToken.delimiter == nameToken.delimiter) {
        fragmentEnd++
      }
      fragments.add(MatchedFragment(fragmentStart, fragmentEnd))
      cursor = index + 1
    }
    return fragments.ifEmpty { null }
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
