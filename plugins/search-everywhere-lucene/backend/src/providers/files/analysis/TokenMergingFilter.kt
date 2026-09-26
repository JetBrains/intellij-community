package com.intellij.searchEverywhereLucene.backend.providers.files.analysis

import org.apache.lucene.analysis.TokenFilter
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute

/**
 * Deduplicates tokens by merging those that share the same (term, startOffset, endOffset) triple:
 * their [MultiTypeAttribute] type sets are unioned into a single token.
 *
 * Tokens are then emitted in non-decreasing startOffset order (stable sort, preserving
 * insertion order within a group of equal startOffsets). This satisfies Lucene's requirement
 * that stored-field offsets never go backwards, even if upstream filters produce tokens
 * out of offset order (e.g., a FILETYPE token at offset 1 interleaved with FILENAME_PART
 * tokens at offset 0 for hidden files like ".SomeLongFile").
 *
 * This filter reads ALL remaining tokens from its input on the first call to [incrementToken]
 * (lazy drain), builds the merged list, and then emits tokens one at a time.
 *
 */
class TokenMergingFilter(input: TokenStream) : TokenFilter(input) {

  private val termAttr = addAttribute(CharTermAttribute::class.java)
  private val multiTypeAttr = addAttribute(MultiTypeAttribute::class.java)
  private val offsetAttr = addAttribute(OffsetAttribute::class.java)
  private val wordAttr = addAttribute(WordAttribute::class.java)

  private data class MergedToken(
    val term: String,
    val types: MutableSet<FileTokenType>,
    val startOffset: Int,
    val endOffset: Int,
    val wordIndex: Int,
  )

  private val merged = ArrayDeque<MergedToken>()
  private var drained = false

  override fun incrementToken(): Boolean {
    if (!drained) {
      drain()
      drained = true
    }

    if (merged.isEmpty()) return false

    val token = merged.removeFirst()
    termAttr.setEmpty().append(token.term)
    multiTypeAttr.clearTypes().setTypes(token.types)
    offsetAttr.setOffset(token.startOffset, token.endOffset)
    wordAttr.wordIndex = token.wordIndex
    return true
  }

  private fun drain() {
    val map = LinkedHashMap<Triple<String, Int, Int>, MergedToken>()
    while (input.incrementToken()) {
      val term = termAttr.toString()
      val start = offsetAttr.startOffset()
      val end = offsetAttr.endOffset()
      val key = Triple(term, start, end)
      val existing = map.getOrPut(key) {
        MergedToken(term, mutableSetOf(), start, end, wordAttr.wordIndex)
      }
      existing.types.addAll(multiTypeAttr.activeTypes())
    }
    merged.addAll(map.values.sortedBy { it.startOffset })
  }

  override fun reset() {
    super.reset()
    merged.clear()
    drained = false
  }
}