package com.intellij.performanceTesting.freezes.diogen

import com.hankcs.algorithm.AhoCorasickDoubleArrayTrie

fun String.substringBetween(prefix: String, suffix: String, missingDelimiterValue: String = this): String {
  val startIndex = indexOf(prefix)
  val endIndex = indexOf(suffix, startIndex + prefix.length)
  return if (startIndex >= 0 && endIndex >= 0)
    substring(startIndex + prefix.length, endIndex)
  else missingDelimiterValue
}

typealias StringTrie = AhoCorasickDoubleArrayTrie<String>

fun buildTrie(substrings: Collection<String>): StringTrie {
  return AhoCorasickDoubleArrayTrie<String>().apply {
    build(substrings.associateWith { it })
  }
}

fun StringTrie.hasMatches(text: CharSequence): Boolean {
  var result = false
  parseText(text, AhoCorasickDoubleArrayTrie.IHitCancellable { _, _, _ ->
    result = true
    false
  })
  return result
}
