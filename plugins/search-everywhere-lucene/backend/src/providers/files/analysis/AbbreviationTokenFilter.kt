package com.intellij.searchEverywhereLucene.backend.providers.files.analysis

import org.apache.lucene.analysis.TokenStream

/**
 * Derives abbreviation tokens from groups of [sourceTypes] sub-tokens (e.g., FILENAME_PART).
 *
 * Tokens of [sourceTypes] are buffered. When a non-source token arrives (or the stream ends),
 * the buffer is flushed: abbreviation tokens are emitted, then (depending on [passThrough]) the
 * buffered parts are re-emitted lowercased, followed by the flushing non-source token lowercased.
 *
 * [allowedSkip] controls how many parts may be omitted per abbreviation: all ordered subsequences
 * with ≤ [allowedSkip] omissions are emitted, always keeping the first part. Default 0 produces one abbreviation.
 *
 * Abbreviation of a token sequence: first char of each token (lowercased)
 */
class AbbreviationTokenFilter(
  input: TokenStream,
  private val sourceTypes: Set<FileTokenType>,
  private val outputType: FileTokenType,
  private val minLength: Int = 2,
  private val allowedSkip: Int = 0,
  private val skipOutputType: FileTokenType = FileTokenType.FILENAME_ABBREVIATION_WITH_SKIPS,
  private val passThrough: PassthroughOptions = PassthroughOptions.PassthroughLast,
) : TokenFilterBase(input) {

  override fun incrementToken(): Boolean = incrementWithGrouping(sourceTypes, passThrough, ::buildAbbreviations, String::lowercase)

  private fun buildAbbreviations(parts: List<BufferedToken>): List<BufferedToken> {
    val abbrevStart = parts.minOf { it.startOffset }
    val abbrevEnd = parts.maxOf { it.endOffset }
    val result = mutableListOf<BufferedToken>()
    val seen = mutableSetOf<String>()
    val minSubsetSize = maxOf(1, parts.size - allowedSkip)
    for (size in parts.size downTo minSubsetSize) {
      val type = if (size < parts.size) skipOutputType else outputType
      if (size == parts.size) {
        forEachCombination(parts.size, size) { indices ->
          val subParts = indices.map { parts[it] }
          val abbrev = buildAbbreviation(subParts)
          if (abbrev.length >= minLength && seen.add(abbrev)) {
            result.add(BufferedToken(abbrev, setOf(type), abbrevStart, abbrevEnd))
          }
        }
      } else {
        // Skip combinations: first part (index 0) is always included
        forEachCombination(parts.size - 1, size - 1) { tailIndices ->
          val subParts = listOf(parts[0]) + tailIndices.map { parts[it + 1] }
          val abbrev = buildAbbreviation(subParts)
          if (abbrev.length >= minLength && seen.add(abbrev)) {
            result.add(BufferedToken(abbrev, setOf(type), abbrevStart, abbrevEnd))
          }
        }
      }
    }
    return result
  }

  /**
   * Builds the abbreviation string: first char of each part (lowercased). */
  private fun buildAbbreviation(parts: List<BufferedToken>): String {
    val sb = StringBuilder()
    for (part in parts) {
      if (part.term.isEmpty()) continue
      sb.append(part.term[0].lowercaseChar())
    }
    return sb.toString()
  }

  private fun forEachCombination(n: Int, k: Int, action: (IntArray) -> Unit) {
    if (k > n) return
    if (k == 0) { action(IntArray(0)); return }
    val indices = IntArray(k) { it }
    while (true) {
      action(indices)
      var i = k - 1
      while (i >= 0 && indices[i] == n - k + i) i--
      if (i < 0) break
      indices[i]++
      for (j in i + 1 until k) indices[j] = indices[j - 1] + 1
    }
  }

}