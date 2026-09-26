package com.intellij.searchEverywhereLucene.backend.providers.files.analysis

import org.apache.lucene.analysis.TokenFilter
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute

/**
 * Centralises [PositionIncrementAttribute] management.
 *
 * Rule: positionIncrement = 1 if the token's startOffset is strictly greater than the last emitted
 * startOffset (new position), 0 otherwise (co-positional with the previous token).
 *
 */
class PositionIncrementFromOffsetFilter(input: TokenStream) : TokenFilter(input) {

  private val offsetAttr = addAttribute(OffsetAttribute::class.java)
  private val posIncrAttr = addAttribute(PositionIncrementAttribute::class.java)

  private var lastStartOffset = -1

  override fun incrementToken(): Boolean {
    if (!input.incrementToken()) return false
    val start = offsetAttr.startOffset()
    posIncrAttr.positionIncrement = if (start > lastStartOffset) 1 else 0
    lastStartOffset = start
    return true
  }

  override fun reset() {
    super.reset()
    lastStartOffset = -1
  }
}