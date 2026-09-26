package com.intellij.searchEverywhereLucene.backend.util

import com.intellij.searchEverywhereLucene.backend.providers.files.analysis.MultiTypeAttribute
import com.intellij.searchEverywhereLucene.backend.providers.files.analysis.WordAttribute
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.AnalyzerWrapper
import org.apache.lucene.analysis.TokenFilter
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute

/**
 * A flexible token filter that allows filtering tokens based on various predicates.
 * Supports filtering by type, word index, offset, and term.
 */
class TokenAttributeFilter(
  input: TokenStream,
  private val typePredicate: ((String) -> Boolean)? = null,
  private val wordIndexPredicate: ((Int) -> Boolean)? = null,
  private val offsetPredicate: ((Int, Int) -> Boolean)? = null,
  private val termPredicate: ((String) -> Boolean)? = null,
) : TokenFilter(input) {
  private val termAttr = addAttribute(CharTermAttribute::class.java)
  private val offsetAttr = addAttribute(OffsetAttribute::class.java)
  private val wordAttr = addAttribute(WordAttribute::class.java)
  private val multiTypeAttr = addAttribute(MultiTypeAttribute::class.java)

  override fun incrementToken(): Boolean {
    while (input.incrementToken()) {
      val wordIndex = wordAttr.wordIndex
      val startOffset = offsetAttr.startOffset()
      val endOffset = offsetAttr.endOffset()
      val term = termAttr.toString()

      if (typePredicate != null) {
        val effectiveTypes = multiTypeAttr.activeTypes().map { it.type }
        if (effectiveTypes.none { typePredicate.invoke(it) }) continue
      }
      if (wordIndexPredicate != null && !wordIndexPredicate.invoke(wordIndex)) continue
      if (offsetPredicate != null && !offsetPredicate.invoke(startOffset, endOffset)) continue
      if (termPredicate != null && !termPredicate.invoke(term)) continue

      // All predicates passed
      return true
    }
    return false
  }
}

/**
 * Analyzer that wraps FileSearchAnalyzer and filters tokens by type.
 * This is used for testing to ensure each token type can properly find results.
 */
internal class TokenTypeFilteringAnalyzer(
  private val wrapped: Analyzer,
  private val tokenTypeWhitelist: List<String>,
) : AnalyzerWrapper(wrapped.reuseStrategy) {

  override fun getWrappedAnalyzer(fieldName: String): Analyzer = wrapped

  override fun wrapComponents(fieldName: String, components: TokenStreamComponents): TokenStreamComponents {
    val filter = TokenAttributeFilter(components.tokenStream, typePredicate = { it in tokenTypeWhitelist })
    return TokenStreamComponents(components.source, filter)
  }
}
